package com.paylinker.api.dashboard.service;

import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.repository.CampaignRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int VIEW_RATE_SCALE = 4;

    private final CampaignRepository campaignRepository;

    public DashboardService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public DashboardCampaignSummaryResponse getCampaignSummary(String adminId, String campaignId) {
        PaylinkerCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (!adminId.equals(campaign.getAdminId())) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        return new DashboardCampaignSummaryResponse(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getStatus(),
                campaign.getSendCompletedAt(),
                campaign.getTotalRecipientCount(),
                campaign.getSendSuccessCount(),
                campaign.getSendFailedCount(),
                campaign.getViewedCount(),
                campaign.getUnviewedCount(),
                calculateViewRate(campaign.getViewedCount(), campaign.getTotalRecipientCount())
        );
    }

    private BigDecimal calculateViewRate(Integer viewedCount, Integer totalRecipientCount) {
        if (totalRecipientCount == null || totalRecipientCount <= 0) {
            return BigDecimal.ZERO.setScale(VIEW_RATE_SCALE, RoundingMode.HALF_UP);
        }
        int viewed = viewedCount == null ? 0 : viewedCount;
        return BigDecimal.valueOf(viewed)
                .divide(BigDecimal.valueOf(totalRecipientCount), VIEW_RATE_SCALE, RoundingMode.HALF_UP);
    }
}
