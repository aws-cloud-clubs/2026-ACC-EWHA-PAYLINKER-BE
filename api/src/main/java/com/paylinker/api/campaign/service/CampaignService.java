package com.paylinker.api.campaign.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.campaign.dto.request.ManualResendRequest;
import com.paylinker.api.campaign.dto.request.ReminderRequest;
import com.paylinker.api.campaign.dto.response.ManualResendResponse;
import com.paylinker.api.campaign.dto.response.ReminderResponse;
import com.paylinker.api.campaign.repository.AuditLogRepository;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.notification.repository.SecureLinkRepository;
import com.paylinker.api.notification.repository.SendJobRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Set<String> ALLOWED_CAMPAIGN_STATUSES = Set.of("SENT", "PARTIAL_FAILED");
    private static final List<String> DEFAULT_PERMANENT_FAILURE_REASONS =
            List.of("INVALID_EMAIL", "BLOCKED", "COMPLAINT");

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final AuditLogRepository auditLogRepository;
    private final SecureLinkRepository secureLinkRepository;
    private final SendJobRepository sendJobRepository;
    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.email-queue-url}")
    private String sqsQueueUrl;

    // SND-001
    public ReminderResponse sendReminder(String campaignId, ReminderRequest request) {
        validateCampaignStatus(campaignId, ErrorCode.REMINDER_NOT_ALLOWED);

        if ("SELECTED".equals(request.getTarget()) &&
                (request.getCampaignRecipientIds() == null || request.getCampaignRecipientIds().isEmpty())) {
            throw new CustomException(ErrorCode.REMINDER_SELECTED_REQUIRED);
        }

        List<Map<String, AttributeValue>> candidates = resolveUnviewedCandidates(campaignId, request);
        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.REMINDER_NO_TARGET);
        }

        String requestedAt = ZonedDateTime.now(KST).format(ISO_OFFSET);
        int queuedCount = 0;
        int skippedExpiredCount = 0;

        for (Map<String, AttributeValue> recipient : candidates) {
            String recipientId = str(recipient, "campaign_recipient_id");
            Optional<Map<String, AttributeValue>> link = secureLinkRepository.findActiveByRecipientId(recipientId);

            // 링크가 없거나 만료된 수신자는 제외
            if (link.isEmpty() || isLinkExpired(str(link.get(), "expires_at"), requestedAt)) {
                skippedExpiredCount++;
                continue;
            }

            String secureLinkId = str(link.get(), "secure_link_id");
            String sendJobId = "sj_" + UUID.randomUUID().toString().replace("-", "");

            List<TransactWriteItem> txItems = List.of(
                    sendJobRepository.saveTxItem(sendJobId, recipientId, campaignId, "REMINDER", secureLinkId, requestedAt),
                    campaignRecipientRepository.incrementReminderCountTxItem(recipientId, requestedAt));

            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(txItems)
                    .build());

            // 리마인드는 기존 링크 재사용: secureLinkId를 워커에 전달
            enqueueReminderJob(sendJobId, secureLinkId);
            queuedCount++;
        }

        if (queuedCount == 0 && skippedExpiredCount > 0) {
            throw new CustomException(ErrorCode.REMINDER_NO_TARGET);
        }

        auditLogRepository.save(
                "al_" + UUID.randomUUID().toString().replace("-", ""),
                campaignId, "REMINDER_REQUEST", requestedAt);

        return ReminderResponse.builder()
                .campaignId(campaignId)
                .queuedCount(queuedCount)
                .skippedExpiredCount(skippedExpiredCount)
                .requestedAt(requestedAt)
                .build();
    }

    // SND-002
    public ManualResendResponse manualResend(String campaignId, ManualResendRequest request) {
        validateCampaignStatus(campaignId, ErrorCode.MANUAL_RESEND_NOT_ALLOWED);

        if ("SELECTED".equals(request.getTarget()) &&
                (request.getCampaignRecipientIds() == null || request.getCampaignRecipientIds().isEmpty())) {
            throw new CustomException(ErrorCode.MANUAL_RESEND_SELECTED_REQUIRED);
        }

        List<String> excludeReasons = request.getExcludeFailureReasons() != null
                ? request.getExcludeFailureReasons()
                : DEFAULT_PERMANENT_FAILURE_REASONS;

        List<Map<String, AttributeValue>> candidates = resolveFailedCandidates(campaignId, request);
        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.MANUAL_RESEND_NO_TARGET);
        }

        String requestedAt = ZonedDateTime.now(KST).format(ISO_OFFSET);
        int queuedCount = 0;
        int skippedPermanentFailureCount = 0;

        for (Map<String, AttributeValue> recipient : candidates) {
            String failureReason = str(recipient, "send_failure_reason");

            // 영구 실패 수신자는 자동 제외
            if (failureReason != null && excludeReasons.contains(failureReason)) {
                skippedPermanentFailureCount++;
                continue;
            }

            String recipientId = str(recipient, "campaign_recipient_id");
            String plainToken = UUID.randomUUID().toString().replace("-", "");
            String tokenHash = sha256(plainToken);
            String secureLinkId = "sl_" + UUID.randomUUID().toString().replace("-", "");
            String newLinkExpiresAt = ZonedDateTime.now(KST).plusDays(2).format(ISO_OFFSET);
            String sendJobId = "sj_" + UUID.randomUUID().toString().replace("-", "");

            Optional<Map<String, AttributeValue>> existingLink =
                    secureLinkRepository.findActiveByRecipientId(recipientId);

            List<TransactWriteItem> txItems = new ArrayList<>();
            existingLink.ifPresent(link ->
                    txItems.add(secureLinkRepository.invalidateTxItem(str(link, "secure_link_id"))));
            txItems.add(secureLinkRepository.saveTxItem(secureLinkId, recipientId, campaignId, tokenHash, newLinkExpiresAt));
            txItems.add(sendJobRepository.saveTxItem(sendJobId, recipientId, campaignId, "RESEND", secureLinkId, requestedAt));
            txItems.add(campaignRecipientRepository.updateToRetryingTxItem(recipientId));

            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(txItems)
                    .build());

            enqueueResendJob(sendJobId, plainToken);
            queuedCount++;
        }

        if (queuedCount == 0 && skippedPermanentFailureCount > 0) {
            throw new CustomException(ErrorCode.MANUAL_RESEND_NO_TARGET);
        }

        auditLogRepository.save(
                "al_" + UUID.randomUUID().toString().replace("-", ""),
                campaignId, "MANUAL_RESEND", requestedAt);

        return ManualResendResponse.builder()
                .campaignId(campaignId)
                .queuedCount(queuedCount)
                .skippedPermanentFailureCount(skippedPermanentFailureCount)
                .requestedAt(requestedAt)
                .build();
    }

    private void validateCampaignStatus(String campaignId, ErrorCode notAllowedCode) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!ALLOWED_CAMPAIGN_STATUSES.contains(str(campaign, "status"))) {
            throw new CustomException(notAllowedCode);
        }
    }

    private List<Map<String, AttributeValue>> resolveUnviewedCandidates(
            String campaignId, ReminderRequest request) {
        if ("ALL_UNVIEWED".equals(request.getTarget())) {
            return campaignRecipientRepository.findUnviewedByCampaignId(campaignId);
        }
        // SELECTED: 지정된 수신자 중 미열람자만
        return request.getCampaignRecipientIds().stream()
                .map(campaignRecipientRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> "0".equals(str(r, "viewed")) || r.get("viewed") == null)
                .toList();
    }

    private List<Map<String, AttributeValue>> resolveFailedCandidates(
            String campaignId, ManualResendRequest request) {
        if ("ALL_FAILED".equals(request.getTarget())) {
            return campaignRecipientRepository.findFailedByCampaignId(campaignId);
        }
        // SELECTED: 지정된 수신자 중 실패 상태만
        return request.getCampaignRecipientIds().stream()
                .map(campaignRecipientRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> "FAILED".equals(str(r, "send_status")))
                .toList();
    }

    private boolean isLinkExpired(String expiresAt, String now) {
        if (expiresAt == null) return true;
        return expiresAt.compareTo(now) <= 0;
    }

    private void enqueueReminderJob(String sendJobId, String secureLinkId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "secureLinkId", secureLinkId));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(body)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("SQS 메시지 직렬화 실패: sendJobId={}", sendJobId, e);
            throw new RuntimeException("SQS 메시지 직렬화 실패", e);
        }
    }

    private void enqueueResendJob(String sendJobId, String plainToken) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "plainToken", plainToken));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(body)
                    .build());
        } catch (JsonProcessingException e) {
            log.error("SQS 메시지 직렬화 실패: sendJobId={}", sendJobId, e);
            throw new RuntimeException("SQS 메시지 직렬화 실패", e);
        }
    }

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }
}
