package com.paylinker.api.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.notification.dto.request.ResendRequestActionRequest;
import com.paylinker.api.notification.dto.response.CheckItemListResponse;
import com.paylinker.api.notification.dto.response.CheckItemSummary;
import com.paylinker.api.notification.dto.response.ResendRequestActionResponse;
import com.paylinker.api.notification.dto.response.ResendRequestItem;
import com.paylinker.api.notification.dto.response.ResendRequestListResponse;
import com.paylinker.api.notification.repository.CheckItemRepository;
import com.paylinker.api.notification.repository.ResendRequestRepository;
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
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final CheckItemRepository checkItemRepository;
    private final ResendRequestRepository resendRequestRepository;
    private final SecureLinkRepository secureLinkRepository;
    private final SendJobRepository sendJobRepository;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.email-queue-url}")
    private String sqsQueueUrl;

    // REQ-003
    public CheckItemListResponse getCheckItems(String itemType, String checkStatus,
                                               String campaignId, int page, int pageSize) {
        List<Map<String, AttributeValue>> all = checkItemRepository.findAll();

        long openCount = all.stream()
                .filter(i -> "OPEN".equals(str(i, "check_status")))
                .count();
        long inProgressCount = all.stream()
                .filter(i -> "IN_PROGRESS".equals(str(i, "check_status")))
                .count();

        List<CheckItemSummary> filtered = all.stream()
                .filter(i -> itemType == null || itemType.equals(str(i, "item_type")))
                .filter(i -> checkStatus == null || checkStatus.equals(str(i, "check_status")))
                .filter(i -> campaignId == null || campaignId.equals(str(i, "campaign_id")))
                .map(this::toCheckItemSummary)
                .sorted(checkItemComparator())
                .toList();

        int totalCount = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        List<CheckItemSummary> pageItems = fromIndex < totalCount
                ? filtered.subList(fromIndex, Math.min(fromIndex + pageSize, totalCount))
                : List.of();

        return CheckItemListResponse.builder()
                .totalCount(totalCount)
                .openCount((int) openCount)
                .inProgressCount((int) inProgressCount)
                .page(page)
                .pageSize(pageSize)
                .items(pageItems)
                .build();
    }

    // REQ-001
    public ResendRequestListResponse getResendRequests(String status, String campaignId,
                                                       int page, int pageSize) {
        List<Map<String, AttributeValue>> all = resendRequestRepository.findAll();

        long pendingCount = all.stream()
                .filter(i -> "REQUESTED".equals(str(i, "status")))
                .count();

        List<ResendRequestItem> filtered = all.stream()
                .filter(i -> status == null || status.equals(str(i, "status")))
                .filter(i -> campaignId == null || campaignId.equals(str(i, "campaign_id")))
                .map(this::toResendRequestItem)
                .sorted(Comparator.comparing(ResendRequestItem::requestedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int totalCount = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        List<ResendRequestItem> pageItems = fromIndex < totalCount
                ? filtered.subList(fromIndex, Math.min(fromIndex + pageSize, totalCount))
                : List.of();

        return ResendRequestListResponse.builder()
                .totalCount(totalCount)
                .pendingCount((int) pendingCount)
                .page(page)
                .pageSize(pageSize)
                .items(pageItems)
                .build();
    }

    // REQ-002
    public ResendRequestActionResponse processResendRequest(String requestId,
                                                            ResendRequestActionRequest request,
                                                            String processedBy) {
        Map<String, AttributeValue> resendRequest = resendRequestRepository.findById(requestId)
                .orElseThrow(() -> new CustomException(ErrorCode.RESEND_REQUEST_NOT_FOUND));

        if (!"REQUESTED".equals(str(resendRequest, "status"))) {
            throw new CustomException(ErrorCode.RESEND_REQUEST_ALREADY_PROCESSED);
        }

        String processedAt = ZonedDateTime.now(KST).format(ISO_OFFSET);

        if ("APPROVE".equals(request.getAction())) {
            return approve(requestId, resendRequest, processedBy, processedAt);
        }
        return reject(requestId, processedBy, processedAt);
    }

    private ResendRequestActionResponse approve(String requestId,
                                                Map<String, AttributeValue> resendRequest,
                                                String processedBy, String processedAt) {
        String campaignRecipientId = str(resendRequest, "campaign_recipient_id");
        String campaignId = str(resendRequest, "campaign_id");

        // 1. 기존 활성 보안 링크 무효화
        secureLinkRepository.findActiveByRecipientId(campaignRecipientId)
                .ifPresent(link -> secureLinkRepository.invalidate(str(link, "secure_link_id")));

        // 2. 새 보안 링크 생성
        String newLinkExpiresAt = ZonedDateTime.now(KST).plusDays(2).format(ISO_OFFSET);
        String secureLinkId = "sl_" + UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(UUID.randomUUID().toString().replace("-", ""));
        secureLinkRepository.save(secureLinkId, campaignRecipientId, campaignId, tokenHash, newLinkExpiresAt);

        // 3. 발송 작업 생성 + SQS 큐잉
        String sendJobId = "sj_" + UUID.randomUUID().toString().replace("-", "");
        String now = ZonedDateTime.now(KST).format(ISO_OFFSET);
        sendJobRepository.save(sendJobId, campaignRecipientId, campaignId, "RESEND", secureLinkId, now);
        enqueueSendJob(sendJobId);

        // 4. 재전송 요청 상태 갱신
        resendRequestRepository.updateToCompleted(requestId, processedBy, processedAt);

        // 5. 연결된 check-item 상태 갱신
        checkItemRepository.findByRelatedRequestId(requestId)
                .ifPresent(item -> checkItemRepository.updateCheckStatus(str(item, "check_item_id"), "RESOLVED"));

        return ResendRequestActionResponse.builder()
                .requestId(requestId)
                .status("COMPLETED")
                .processedAt(processedAt)
                .newSendJobId(sendJobId)
                .newLinkExpiresAt(newLinkExpiresAt)
                .build();
    }

    private ResendRequestActionResponse reject(String requestId, String processedBy, String processedAt) {
        resendRequestRepository.updateToRejected(requestId, processedBy, processedAt);

        checkItemRepository.findByRelatedRequestId(requestId)
                .ifPresent(item -> checkItemRepository.updateCheckStatus(str(item, "check_item_id"), "REJECTED"));

        return ResendRequestActionResponse.builder()
                .requestId(requestId)
                .status("REJECTED")
                .processedAt(processedAt)
                .build();
    }

    private void enqueueSendJob(String sendJobId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of("sendJobId", sendJobId));
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

    private CheckItemSummary toCheckItemSummary(Map<String, AttributeValue> item) {
        return CheckItemSummary.builder()
                .checkItemId(str(item, "check_item_id"))
                .itemType(str(item, "item_type"))
                .checkStatus(str(item, "check_status"))
                .campaignId(str(item, "campaign_id"))
                .campaignName(str(item, "campaign_name"))
                .recipientName(str(item, "recipient_name"))
                .relatedRequestId(str(item, "related_request_id"))
                .createdAt(str(item, "created_at"))
                .deepLink(str(item, "deep_link"))
                .build();
    }

    private ResendRequestItem toResendRequestItem(Map<String, AttributeValue> item) {
        return ResendRequestItem.builder()
                .requestId(str(item, "request_id"))
                .campaignId(str(item, "campaign_id"))
                .campaignName(str(item, "campaign_name"))
                .recipientName(str(item, "recipient_name"))
                .employeeNo(str(item, "employee_no"))
                .email(str(item, "email"))
                .resendReason(str(item, "resend_reason"))
                .status(str(item, "status"))
                .requestedAt(str(item, "requested_at"))
                .processedAt(str(item, "processed_at"))
                .processedBy(str(item, "processed_by"))
                .build();
    }

    private Comparator<CheckItemSummary> checkItemComparator() {
        return Comparator
                .<CheckItemSummary, Integer>comparing(i -> statusPriority(i.checkStatus()))
                .thenComparing(Comparator.comparing(
                        CheckItemSummary::createdAt, Comparator.nullsLast(Comparator.reverseOrder())));
    }

    private int statusPriority(String status) {
        if (status == null) return 3;
        return switch (status) {
            case "OPEN" -> 0;
            case "IN_PROGRESS" -> 1;
            default -> 2;
        };
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }
}
