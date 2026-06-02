package com.paylinker.api.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.campaign.dto.request.ManualResendRequest;
import com.paylinker.api.campaign.dto.request.ReminderRequest;
import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.campaign.dto.response.ManualResendResponse;
import com.paylinker.api.campaign.dto.response.ReminderResponse;
import com.paylinker.api.campaign.dto.response.SendFailureItem;
import com.paylinker.api.campaign.dto.response.SendFailureResponse;
import com.paylinker.api.campaign.dto.response.ViewHistoryItem;
import com.paylinker.api.campaign.dto.response.ViewHistoryResponse;
import com.paylinker.api.campaign.repository.AuditLogRepository;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.entity.PaylinkerCampaign;
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
    private static final int BOUNCED_RETRY_LIMIT = 3;

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

    // ────────────────────────────────────────────────
    // 캠페인 목록 조회
    // ────────────────────────────────────────────────
    // 1. GSI1 인덱스를 통해 해당 관리자의 모든 캠페인 조회
    public CampaignListResponse getCampaigns(String adminId, String status, String keyword,
                                             int page, int pageSize, String sort) {
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
            comparator = Comparator.comparing(PaylinkerCampaign::getSendCompletedAt,
                    Comparator.nullsLast(String::compareTo)).reversed();
        } else {
            // 기본값: createdAt 내림차순
            comparator = Comparator.comparing(PaylinkerCampaign::getCreatedAt,
                    Comparator.nullsLast(String::compareTo)).reversed();
        }

        List<CampaignListResponse.CampaignListItem> items = filteredList.stream()
                .sorted(comparator)
                .skip(fromIndex)
                .limit(pageSize)
                .filter(c -> c.getCampaignId() != null && c.getCampaignName() != null && c.getCreatedAt() != null)
                .map(campaign -> new CampaignListResponse.CampaignListItem(
                        campaign.getCampaignId(),
                        campaign.getCampaignName(),
                        campaign.getStatus(),
                        campaign.getScheduledSendAt(),
                        campaign.getSendCompletedAt(),
                        campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0,
                        campaign.getSendSuccessCount() != null ? campaign.getSendSuccessCount() : 0,
                        campaign.getSendFailedCount() != null ? campaign.getSendFailedCount() : 0,
                        campaign.getViewedCount() != null ? campaign.getViewedCount() : 0,
                        campaign.getCreatedAt()))
                .toList();

        return new CampaignListResponse(totalCount, page, pageSize, items);
    }

    // ────────────────────────────────────────────────
    // RST-002: 명세서 열람 이력 조회
    // ────────────────────────────────────────────────

    public ViewHistoryResponse getViewHistory(String campaignId, String filter,
                                              int page, int pageSize, String requesterId) {
        validateCampaignOwnership(campaignId, requesterId);

        List<Map<String, AttributeValue>> all = campaignRecipientRepository.findByCampaignId(campaignId);

        int viewedCount = (int) all.stream()
                .filter(i -> Boolean.TRUE.equals(bool(i, "is_viewed")))
                .count();
        int unviewedCount = (int) all.stream()
                .filter(i -> !Boolean.TRUE.equals(bool(i, "is_viewed")))
                .count();

        List<Map<String, AttributeValue>> filteredRaw = all.stream()
                .filter(i -> {
                    if ("VIEWED".equals(filter)) return Boolean.TRUE.equals(bool(i, "is_viewed"));
                    if ("UNVIEWED".equals(filter)) return !Boolean.TRUE.equals(bool(i, "is_viewed"));
                    return true;
                })
                .sorted(rawViewHistoryComparator(filter))
                .toList();

        int totalCount = filteredRaw.size();
        int fromIndex = (page - 1) * pageSize;
        List<ViewHistoryItem> pageItems = fromIndex < totalCount
                ? filteredRaw.subList(fromIndex, Math.min(fromIndex + pageSize, totalCount))
                        .stream().map(this::toViewHistoryItem).toList()
                : List.of();

        return ViewHistoryResponse.builder()
                .campaignId(campaignId)
                .totalCount(totalCount)
                .viewedCount(viewedCount)
                .unviewedCount(unviewedCount)
                .page(page)
                .pageSize(pageSize)
                .items(pageItems)
                .build();
    }

    // ────────────────────────────────────────────────
    // RST-001: 실패 대상자 목록 조회
    // ────────────────────────────────────────────────

    public SendFailureResponse getSendFailures(String campaignId, String failureReason,
                                               int page, int pageSize, String requesterId) {
        validateCampaignOwnership(campaignId, requesterId);

        List<Map<String, AttributeValue>> all = campaignRecipientRepository.findFailedByCampaignId(campaignId);

        List<SendFailureItem> filtered = all.stream()
                .filter(i -> failureReason == null || failureReason.equals(str(i, "send_failure_reason")))
                .map(this::toSendFailureItem)
                .sorted(Comparator.comparing(SendFailureItem::failedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        int totalCount = filtered.size();
        int fromIndex = (page - 1) * pageSize;
        List<SendFailureItem> pageItems = fromIndex < totalCount
                ? filtered.subList(fromIndex, Math.min(fromIndex + pageSize, totalCount))
                : List.of();

        return SendFailureResponse.builder()
                .campaignId(campaignId)
                .totalCount(totalCount)
                .page(page)
                .pageSize(pageSize)
                .items(pageItems)
                .build();
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

            if (link.isEmpty() || isLinkExpired(str(link.get(), "expires_at"), requestedAt)) {
                skippedExpiredCount++;
                continue;
            }

            String secureLinkId = str(link.get(), "secure_link_id");
            String sendJobId = newId("sj");
            persistReminderJob(sendJobId, recipientId, campaignId, secureLinkId, requestedAt);

            if (tryEnqueueReminderJob(sendJobId, secureLinkId, campaignId, recipientId)) {
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
            if (isPermanentFailure(recipient, excludeReasons)) {
                skippedPermanentFailureCount++;
                continue;
            }

            String recipientId = str(recipient, "campaign_recipient_id");
            String plainToken = UUID.randomUUID().toString().replace("-", "");
            String sendJobId = persistResendJob(recipientId, campaignId, plainToken, requestedAt);

            if (tryEnqueueResendJob(sendJobId, plainToken, campaignId, recipientId)) {
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

    /** 캠페인 존재 여부 + 소유자 검증 (조회용, 상태 무관) */
    private void validateCampaignOwnership(String campaignId, String requesterId) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        String ownerId = str(campaign, "admin_id");
        if (ownerId != null && !ownerId.equals(requesterId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }
    }

    /** 캠페인 존재 여부 + 소유자 검증 + 상태 검증 (발송용) */
    private void validateCampaignAccess(String campaignId, String requesterId, ErrorCode statusCode) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        String ownerId = str(campaign, "admin_id");
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

    private void persistReminderJob(String sendJobId, String recipientId,
                                    String campaignId, String secureLinkId, String requestedAt) {
        List<TransactWriteItem> txItems = List.of(
                sendJobRepository.saveTxItem(sendJobId, recipientId, campaignId, "REMINDER", secureLinkId, requestedAt),
                campaignRecipientRepository.incrementReminderCountTxItem(recipientId, requestedAt));
        dynamoDbClient.transactWriteItems(
                TransactWriteItemsRequest.builder().transactItems(txItems).build());
    }

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
    // SQS 큐잉
    // ────────────────────────────────────────────────

    private boolean tryEnqueueReminderJob(String sendJobId, String secureLinkId,
                                          String campaignId, String campaignRecipientId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "secureLinkId", secureLinkId,
                    "campaignId", campaignId,
                    "campaignRecipientId", campaignRecipientId));
            enqueue(body);
            return true;
        } catch (Exception e) {
            log.error("SQS 큐잉 실패(REMINDER), sendJob FAILED 처리: sendJobId={}", sendJobId, e);
            sendJobRepository.updateToFailed(sendJobId);
            return false;
        }
    }

    private boolean tryEnqueueResendJob(String sendJobId, String plainToken,
                                        String campaignId, String campaignRecipientId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "plainToken", plainToken,
                    "campaignId", campaignId,
                    "campaignRecipientId", campaignRecipientId));
            enqueue(body);
            return true;
        } catch (Exception e) {
            log.error("SQS 큐잉 실패(RESEND), sendJob FAILED 처리: sendJobId={}", sendJobId, e);
            sendJobRepository.updateToFailed(sendJobId);
            return false;
        }
    }

    private void enqueue(String messageBody) {
        sqsClient.sendMessage(SendMessageRequest.builder()
                .queueUrl(sqsQueueUrl)
                .messageBody(messageBody)
                .build());
    }

    // ────────────────────────────────────────────────
    // DTO 변환
    // ────────────────────────────────────────────────

    private ViewHistoryItem toViewHistoryItem(Map<String, AttributeValue> item) {
        return ViewHistoryItem.builder()
                .campaignRecipientId(str(item, "campaign_recipient_id"))
                .name(str(item, "name"))
                .employeeNo(str(item, "employee_no"))
                .email(str(item, "email"))
                .isViewed(bool(item, "is_viewed"))
                .firstViewedAt(str(item, "first_viewed_at"))
                .viewCount(num(item, "view_count"))
                .linkStatus(str(item, "link_status"))
                .linkExpiresAt(str(item, "link_expires_at"))
                .build();
    }

    private SendFailureItem toSendFailureItem(Map<String, AttributeValue> item) {
        return SendFailureItem.builder()
                .campaignRecipientId(str(item, "campaign_recipient_id"))
                .name(str(item, "name"))
                .department(str(item, "department"))
                .email(str(item, "email"))
                .failureReason(str(item, "send_failure_reason"))
                .failedAt(str(item, "failed_at"))
                .retryCount(num(item, "retry_count"))
                .currentStatus(str(item, "status"))
                .lastSendJobId(str(item, "last_send_job_id"))
                .build();
    }

    private Comparator<Map<String, AttributeValue>> rawViewHistoryComparator(String filter) {
        if ("VIEWED".equals(filter)) {
            return (a, b) -> nullSafeCompare(str(b, "first_viewed_at"), str(a, "first_viewed_at"));
        }
        if ("UNVIEWED".equals(filter)) {
            return (a, b) -> nullSafeCompare(str(a, "created_at"), str(b, "created_at"));
        }
        return (a, b) -> nullSafeCompare(str(a, "name"), str(b, "name"));
    }

    // ────────────────────────────────────────────────
    // 유틸
    // ────────────────────────────────────────────────

    private boolean isLinkExpired(String expiresAt, String now) {
        return expiresAt == null || expiresAt.compareTo(now) <= 0;
    }

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
        if (reason == null) return false;
        if (excludeReasons.contains(reason)) return true;
        // D-1: BOUNCED 는 retry_count >= 3 도달 시 영구 실패로 격상
        if ("BOUNCED".equals(reason)) {
            Integer retryCount = num(recipient, "retry_count");
            return retryCount != null && retryCount >= BOUNCED_RETRY_LIMIT;
        }
        return false;
    }

    private int nullSafeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
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

    private Boolean bool(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        if (val == null) return null;
        if (val.bool() != null) return val.bool();
        if (val.n() != null) return !"0".equals(val.n());
        if (val.s() != null) return Boolean.parseBoolean(val.s());
        return null;
    }

    private Integer num(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        if (val == null || val.n() == null) return null;
        return Integer.parseInt(val.n());
    }
}