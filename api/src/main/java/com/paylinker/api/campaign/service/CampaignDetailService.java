package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignDetailResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.stereotype.Service;

@Service
public class CampaignDetailService {

    private final CampaignRepository campaignRepository;

    public CampaignDetailService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public CampaignDetailResponse getCampaignDetails(String adminId, String campaignId) {
        // 1. 캠페인 메타데이터 조회 및 검증
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        // 2. 권한 검증 (본인 캠페인이 아닌 경우 403 Forbidden)
        if (!campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 3. 캠페인 제한 정보 조회
        PaylinkerCampaignLimit limit = campaignRepository.findLimitById(campaignId);
        if (limit == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        // 4. 응답 DTO 매핑 및 반환
        return new CampaignDetailResponse(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getStatus(),
                campaign.getEmailSubject(),
                campaign.getEmailDescription(),
                campaign.getLinkTtlHours(),
                campaign.getAllowOneTimeLink(),
                campaign.getAllowResendRequest(),
                campaign.getResendRequestLimit(),
                limit.getMaxRecipients(),
                limit.getMaxDailyCount(),
                campaign.getScheduledSendAt(),
                campaign.getSendStartedAt(),
                campaign.getSendCompletedAt(),
                campaign.getCancelledAt(),
                campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0,
                campaign.getSendSuccessCount() != null ? campaign.getSendSuccessCount() : 0,
                campaign.getSendFailedCount() != null ? campaign.getSendFailedCount() : 0,
                campaign.getViewedCount() != null ? campaign.getViewedCount() : 0,
                campaign.getUnviewedCount() != null ? campaign.getUnviewedCount() : 0,
                campaign.getCreatedAt()
        );
    }
}