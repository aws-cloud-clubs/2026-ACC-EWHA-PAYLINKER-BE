package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.SendFailureItem;
import com.paylinker.api.campaign.dto.response.SendFailureResponse;
import com.paylinker.api.campaign.dto.response.ViewHistoryItem;
import com.paylinker.api.campaign.dto.response.ViewHistoryResponse;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

@Service
@RequiredArgsConstructor
public class CampaignService {

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;

    // RST-002
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

        // 정렬은 DynamoDB 원본 데이터 기준으로 수행 (UNVIEWED 정렬 기준인 created_at이 응답에 미포함)
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
                        .stream()
                        .map(this::toViewHistoryItem)
                        .toList()
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

    // RST-001
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

    /** 캠페인 존재 여부 + 소유자 검증 */
    private void validateCampaignOwnership(String campaignId, String requesterId) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        String ownerId = str(campaign, "owner_id");
        if (ownerId != null && !ownerId.equals(requesterId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }
    }

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

    /**
     * filter 값에 따른 정렬 기준 반환
     * VIEWED: firstViewedAt 내림차순 / UNVIEWED: createdAt 오름차순 / ALL: name 오름차순
     */
    private Comparator<Map<String, AttributeValue>> rawViewHistoryComparator(String filter) {
        if ("VIEWED".equals(filter)) {
            return (a, b) -> nullSafeCompare(str(b, "first_viewed_at"), str(a, "first_viewed_at"));
        }
        if ("UNVIEWED".equals(filter)) {
            return (a, b) -> nullSafeCompare(str(a, "created_at"), str(b, "created_at"));
        }
        return (a, b) -> nullSafeCompare(str(a, "name"), str(b, "name"));
    }

    private int nullSafeCompare(String a, String b) {
        if (a == null && b == null) return 0;
        if (a == null) return 1;
        if (b == null) return -1;
        return a.compareTo(b);
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }

    /**
     * is_viewed 필드 타입 방어 처리: BOOL → N(0=false) → S 순으로 폴백
     */
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
