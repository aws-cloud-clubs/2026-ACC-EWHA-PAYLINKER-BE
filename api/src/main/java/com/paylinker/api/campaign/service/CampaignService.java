package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.request.ManualResendRequest;
import com.paylinker.api.campaign.dto.request.ReminderRequest;
import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.campaign.dto.response.ManualResendResponse;
import com.paylinker.api.campaign.dto.response.ReminderResponse;
import com.paylinker.api.campaign.repository.AuditLogRepository;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.notification.repository.SecureLinkRepository;
import com.paylinker.api.notification.repository.SendJobRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Stream;
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
    private static final Set<String> ALLOWED_STATUSES = Set.of("SENT", "PARTIAL_FAILED");
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

    public CampaignListResponse getCampaigns(String adminId, String status, String keyword, int page, int pageSize, String sort) {

        // 1. GSI1 인덱스를 통해 해당 관리자의 모든 캠페인 조회
        List<PaylinkerCampaign> allCampaigns = campaignRepository.findAllByAdminId(adminId);

        // 2. 필터링 (상태 및 키워드)
        Stream<PaylinkerCampaign> stream = allCampaigns.stream();

        if (status != null && !status.isBlank()) {
            stream = stream.filter(c -> c.getStatus() != null && c.getStatus().name().equals(status));
        }

        if (keyword != null && !keyword.isBlank()) {
            stream = stream.filter(c -> c.getCampaignName() != null && c.getCampaignName().contains(keyword));
        }

        List<PaylinkerCampaign> filteredList = stream.toList();
        int totalCount = filteredList.size();

        // 3. 비즈니스 단 페이징 검증 (데이터가 있는데 범위를 초과한 경우 예외 발생)
        int fromIndex = (page - 1) * pageSize;
        if (totalCount > 0 && fromIndex >= totalCount) {
            throw new CustomException(ErrorCode.INVALID_PAGINATION);
        }

        // 데이터가 없으면 빈 리스트 반환
        if (totalCount == 0) {
            return new CampaignListResponse(0, page, pageSize, List.of());
        }

        // 4. 정렬 처리
        Comparator<PaylinkerCampaign> comparator;
        if ("sendCompletedAt:desc".equals(sort)) {
            comparator = Comparator.comparing(PaylinkerCampaign::getSendCompletedAt, Comparator.nullsLast(String::compareTo)).reversed();
        } else {
            // 기본값: createdAt 내림차순
            comparator = Comparator.comparing(PaylinkerCampaign::getCreatedAt, Comparator.nullsLast(String::compareTo)).reversed();
        }

        // 5. 정렬 적용 및 페이징 분할
        int toIndex = Math.min(fromIndex + pageSize, totalCount);
        List<CampaignListResponse.CampaignListItem> items = filteredList.stream()
                .sorted(comparator)
                .skip(fromIndex)
                .limit(pageSize)
                // 필수 값이 누락된(오염된) DB 데이터는 DTO 생성 전 필터링하여 에러 방지
                .filter(c -> c.getCampaignId() != null && c.getCampaignName() != null && c.getCreatedAt() != null)
                .map(campaign -> new CampaignListResponse.CampaignListItem(
                        campaign.getCampaignId(),
                        campaign.getCampaignName(),
                        campaign.getStatus(),
                        campaign.getScheduledSendAt(),
                        campaign.getSendCompletedAt(),
                        // 숫자 필드 null 방어 (NPE 방지)
                        campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0,
                        campaign.getSendSuccessCount() != null ? campaign.getSendSuccessCount() : 0,
                        campaign.getSendFailedCount() != null ? campaign.getSendFailedCount() : 0,
                        campaign.getViewedCount() != null ? campaign.getViewedCount() : 0,
                        campaign.getCreatedAt()
                )).toList();

        return new CampaignListResponse(totalCount, page, pageSize, items);
    }
    // ────────────────────────────────────────────────
    // SND-001: 미확인 수신자 리마인드 발송
    // ────────────────────────────────────────────────

    public ReminderResponse sendReminder(String campaignId, ReminderRequest request, String requesterId) {
        validateCampaignAccess(campaignId, requesterId, ErrorCode.REMINDER_NOT_ALLOWED);
        validateSelectedIds(request.getTarget(), request.getCampaignRecipientIds(),
                ErrorCode.REMINDER_SELECTED_REQUIRED);

        List<Map<String, AttributeValue>> candidates = resolveUnviewedCandidates(campaignId, request);
        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.REMINDER_NO_TARGET);
        }

        String requestedAt = now();
        int queuedCount = 0;
        int skippedExpiredCount = 0;

        for (Map<String, AttributeValue> recipient : candidates) {
            String recipientId = str(recipient, "campaign_recipient_id");
            Optional<Map<String, AttributeValue>> link =
                    secureLinkRepository.findActiveByRecipientId(recipientId);

            // 링크가 없거나 만료된 수신자는 제외
            if (link.isEmpty() || isLinkExpired(str(link.get(), "expires_at"), requestedAt)) {
                skippedExpiredCount++;
                continue;
            }

            String secureLinkId = str(link.get(), "secure_link_id");
            String sendJobId = newId("sj");
            persistReminderJob(sendJobId, recipientId, campaignId, secureLinkId, requestedAt);

            if (tryEnqueueReminderJob(sendJobId, secureLinkId)) {
                queuedCount++;
            }
        }

        if (queuedCount == 0) {
            throw new CustomException(ErrorCode.REMINDER_NO_TARGET);
        }

        saveAuditLog(campaignId, "REMINDER_REQUEST", requestedAt);

        return ReminderResponse.builder()
                .campaignId(campaignId)
                .queuedCount(queuedCount)
                .skippedExpiredCount(skippedExpiredCount)
                .requestedAt(requestedAt)
                .build();
    }

    // ────────────────────────────────────────────────
    // SND-002: 실패 대상자 수동 재발송
    // ────────────────────────────────────────────────

    public ManualResendResponse manualResend(String campaignId, ManualResendRequest request, String requesterId) {
        validateCampaignAccess(campaignId, requesterId, ErrorCode.MANUAL_RESEND_NOT_ALLOWED);
        validateSelectedIds(request.getTarget(), request.getCampaignRecipientIds(),
                ErrorCode.MANUAL_RESEND_SELECTED_REQUIRED);

        List<String> excludeReasons = request.getExcludeFailureReasons() != null
                ? request.getExcludeFailureReasons()
                : DEFAULT_PERMANENT_FAILURE_REASONS;

        List<Map<String, AttributeValue>> candidates = resolveFailedCandidates(campaignId, request);
        if (candidates.isEmpty()) {
            throw new CustomException(ErrorCode.MANUAL_RESEND_NO_TARGET);
        }

        String requestedAt = now();
        int queuedCount = 0;
        int skippedPermanentFailureCount = 0;

        for (Map<String, AttributeValue> recipient : candidates) {
            // 영구 실패 수신자 자동 제외
            if (isPermanentFailure(recipient, excludeReasons)) {
                skippedPermanentFailureCount++;
                continue;
            }

            String recipientId = str(recipient, "campaign_recipient_id");
            String plainToken = UUID.randomUUID().toString().replace("-", "");
            String sendJobId = persistResendJob(recipientId, campaignId, plainToken, requestedAt);

            if (tryEnqueueResendJob(sendJobId, plainToken)) {
                queuedCount++;
            }
        }

        if (queuedCount == 0) {
            throw new CustomException(ErrorCode.MANUAL_RESEND_NO_TARGET);
        }

        saveAuditLog(campaignId, "MANUAL_RESEND", requestedAt);

        return ManualResendResponse.builder()
                .campaignId(campaignId)
                .queuedCount(queuedCount)
                .skippedPermanentFailureCount(skippedPermanentFailureCount)
                .requestedAt(requestedAt)
                .build();
    }

    // ────────────────────────────────────────────────
    // 검증
    // ────────────────────────────────────────────────

    /** 캠페인 존재 여부 + 소유자 검증 + 상태 검증을 한 번에 처리 */
    private void validateCampaignAccess(String campaignId, String requesterId, ErrorCode statusCode) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));

        // 소유자 검증: campaign의 owner_id와 JWT sub 비교
        String ownerId = str(campaign, "owner_id");
        if (ownerId != null && !ownerId.equals(requesterId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        if (!ALLOWED_STATUSES.contains(str(campaign, "status"))) {
            throw new CustomException(statusCode);
        }
    }

    private void validateSelectedIds(String target, List<String> ids, ErrorCode code) {
        if ("SELECTED".equals(target) && (ids == null || ids.isEmpty())) {
            throw new CustomException(code);
        }
    }

    // ────────────────────────────────────────────────
    // 대상 수신자 결정
    // ────────────────────────────────────────────────

    private List<Map<String, AttributeValue>> resolveUnviewedCandidates(
            String campaignId, ReminderRequest request) {
        if ("ALL_UNVIEWED".equals(request.getTarget())) {
            return campaignRecipientRepository.findUnviewedByCampaignId(campaignId);
        }
        // SELECTED: 지정 수신자 중 미열람만
        return request.getCampaignRecipientIds().stream()
                .map(campaignRecipientRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(this::isUnviewed)
                .toList();
    }

    private List<Map<String, AttributeValue>> resolveFailedCandidates(
            String campaignId, ManualResendRequest request) {
        if ("ALL_FAILED".equals(request.getTarget())) {
            return campaignRecipientRepository.findFailedByCampaignId(campaignId);
        }
        // SELECTED: 지정 수신자 중 FAILED 상태만
        return request.getCampaignRecipientIds().stream()
                .map(campaignRecipientRepository::findById)
                .filter(Optional::isPresent)
                .map(Optional::get)
                .filter(r -> "FAILED".equals(str(r, "send_status")))
                .toList();
    }

    // ────────────────────────────────────────────────
    // DB 쓰기
    // ────────────────────────────────────────────────

    /** 리마인드: sendJob 저장 + reminder_count 증가 원자적 처리 */
    private void persistReminderJob(String sendJobId, String recipientId,
                                    String campaignId, String secureLinkId, String requestedAt) {
        List<TransactWriteItem> txItems = List.of(
                sendJobRepository.saveTxItem(sendJobId, recipientId, campaignId, "REMINDER", secureLinkId, requestedAt),
                campaignRecipientRepository.incrementReminderCountTxItem(recipientId, requestedAt));
        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder().transactItems(txItems).build());
    }

    /** 재발송: 기존 링크 무효화 + 신규 링크 발급 + sendJob 저장 + 수신자 상태 갱신 원자적 처리 */
    private String persistResendJob(String recipientId, String campaignId,
                                    String plainToken, String requestedAt) {
        String secureLinkId = newId("sl");
        String sendJobId = newId("sj");
        String newLinkExpiresAt = ZonedDateTime.now(KST).plusDays(2).format(ISO_OFFSET);

        Optional<Map<String, AttributeValue>> existingLink =
                secureLinkRepository.findActiveByRecipientId(recipientId);

        List<TransactWriteItem> txItems = new ArrayList<>();
        existingLink.ifPresent(link ->
                txItems.add(secureLinkRepository.invalidateTxItem(str(link, "secure_link_id"))));
        txItems.add(secureLinkRepository.saveTxItem(
                secureLinkId, recipientId, campaignId, sha256(plainToken), newLinkExpiresAt));
        txItems.add(sendJobRepository.saveTxItem(
                sendJobId, recipientId, campaignId, "RESEND", secureLinkId, requestedAt));
        txItems.add(campaignRecipientRepository.updateToRetryingTxItem(recipientId));

        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder().transactItems(txItems).build());
        return sendJobId;
    }

    private void saveAuditLog(String campaignId, String actionType, String requestedAt) {
        auditLogRepository.save(newId("al"), campaignId, actionType, requestedAt);
    }

    // ────────────────────────────────────────────────
    // SQS 큐잉 — 실패 시 sendJob FAILED 마킹(orphan 방지)
    // ────────────────────────────────────────────────

    /** 리마인드: 기존 링크 재사용이므로 secureLinkId를 워커에 전달 */
    private boolean tryEnqueueReminderJob(String sendJobId, String secureLinkId) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("sendJobId", sendJobId, "secureLinkId", secureLinkId));
            enqueue(body);
            return true;
        } catch (Exception e) {
            log.error("SQS 큐잉 실패(REMINDER), sendJob FAILED 처리: sendJobId={}", sendJobId, e);
            sendJobRepository.updateToFailed(sendJobId);
            return false;
        }
    }

    /** 재발송: 신규 링크의 plainToken을 워커에 전달해 URL 생성 */
    private boolean tryEnqueueResendJob(String sendJobId, String plainToken) {
        try {
            String body = objectMapper.writeValueAsString(
                    Map.of("sendJobId", sendJobId, "plainToken", plainToken));
            enqueue(body);
            return true;
        } catch (Exception e) {
            log.error("SQS 큐잉 실패(RESEND), sendJob FAILED 처리: sendJobId={}", sendJobId, e);
            sendJobRepository.updateToFailed(sendJobId);
            return false;
        }
    }

    private void enqueue(String messageBody) throws JsonProcessingException {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageBody)
                .build());
    }

    // ────────────────────────────────────────────────
    // 유틸
    // ────────────────────────────────────────────────

    private boolean isLinkExpired(String expiresAt, String now) {
        return expiresAt == null || expiresAt.compareTo(now) <= 0;
    }

    /**
     * viewed 필드는 N 타입(0=미열람, 1=열람) 기준.
     * BOOL·S 타입 혼재 환경을 위해 폴백 처리 포함.
     */
    private boolean isUnviewed(Map<String, AttributeValue> recipient) {
        AttributeValue viewed = recipient.get("viewed");
        if (viewed == null) return true;
        if (viewed.n() != null) return "0".equals(viewed.n());
        if (viewed.bool() != null) return !viewed.bool();
        return "0".equals(viewed.s()) || "false".equalsIgnoreCase(viewed.s());
    }

    private boolean isPermanentFailure(Map<String, AttributeValue> recipient,
                                       List<String> excludeReasons) {
        String reason = str(recipient, "send_failure_reason");
        return reason != null && excludeReasons.contains(reason);
    }

    private String now() {
        return ZonedDateTime.now(KST).format(ISO_OFFSET);
    }

    private String newId(String prefix) {
        return prefix + "_" + UUID.randomUUID().toString().replace("-", "");
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