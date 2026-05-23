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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactionCanceledException;
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
    private final DynamoDbClient dynamoDbClient;
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

        // 1. 읽기: 기존 활성 링크 및 연결된 check-item 조회 (트랜잭션 외부에서)
        Optional<Map<String, AttributeValue>> existingLink =
                secureLinkRepository.findActiveByRecipientId(campaignRecipientId);
        Optional<Map<String, AttributeValue>> checkItem =
                checkItemRepository.findByRelatedRequestId(requestId);

        // 2. 새 보안 링크 값 준비: plainToken은 워커가 URL 생성에 사용, DB에는 해시만 저장
        String plainToken = UUID.randomUUID().toString().replace("-", "");
        String tokenHash = sha256(plainToken);
        String secureLinkId = "sl_" + UUID.randomUUID().toString().replace("-", "");
        String newLinkExpiresAt = ZonedDateTime.now(KST).plusDays(2).format(ISO_OFFSET);
        String sendJobId = "sj_" + UUID.randomUUID().toString().replace("-", "");
        String now = ZonedDateTime.now(KST).format(ISO_OFFSET);

        // 3. TransactWriteItems 구성: 모든 쓰기를 원자적으로 처리
        List<TransactWriteItem> txItems = new ArrayList<>();
        existingLink.ifPresent(link ->
                txItems.add(secureLinkRepository.invalidateTxItem(str(link, "secure_link_id"))));
        txItems.add(secureLinkRepository.saveTxItem(secureLinkId, campaignRecipientId, campaignId, tokenHash, newLinkExpiresAt));
        txItems.add(sendJobRepository.saveTxItem(sendJobId, campaignRecipientId, campaignId, "RESEND", secureLinkId, now));
        // ConditionExpression으로 레이스 컨디션 방지: REQUESTED 상태가 아니면 TransactionCanceledException 발생
        txItems.add(resendRequestRepository.updateToCompletedTxItem(requestId, processedBy, processedAt));
        checkItem.ifPresent(item ->
                txItems.add(checkItemRepository.updateCheckStatusTxItem(str(item, "check_item_id"), "RESOLVED")));

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(txItems)
                    .build());
        } catch (TransactionCanceledException e) {
            throw new CustomException(ErrorCode.RESEND_REQUEST_ALREADY_PROCESSED);
        }

        // 4. SQS 큐잉은 모든 DB 작업 완료 후 마지막에 수행
        //    plainToken을 포함해 워커가 보안 링크 URL을 생성할 수 있도록 전달
        enqueueSendJob(sendJobId, plainToken);

        return ResendRequestActionResponse.builder()
                .requestId(requestId)
                .status("COMPLETED")
                .processedAt(processedAt)
                .newSendJobId(sendJobId)
                .newLinkExpiresAt(newLinkExpiresAt)
                .build();
    }

    private ResendRequestActionResponse reject(String requestId, String processedBy, String processedAt) {
        Optional<Map<String, AttributeValue>> checkItem =
                checkItemRepository.findByRelatedRequestId(requestId);

        List<TransactWriteItem> txItems = new ArrayList<>();
        txItems.add(resendRequestRepository.updateToRejectedTxItem(requestId, processedBy, processedAt));
        checkItem.ifPresent(item ->
                txItems.add(checkItemRepository.updateCheckStatusTxItem(str(item, "check_item_id"), "REJECTED")));

        try {
            dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                    .transactItems(txItems)
                    .build());
        } catch (TransactionCanceledException e) {
            throw new CustomException(ErrorCode.RESEND_REQUEST_ALREADY_PROCESSED);
        }

        return ResendRequestActionResponse.builder()
                .requestId(requestId)
                .status("REJECTED")
                .processedAt(processedAt)
                .build();
    }

    private void enqueueSendJob(String sendJobId, String plainToken) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "plainToken", plainToken
            ));
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
