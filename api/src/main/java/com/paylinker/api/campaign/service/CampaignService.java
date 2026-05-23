package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

@Service
public class CampaignService {

    private final CampaignRepository campaignRepository;

    public CampaignService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

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
}