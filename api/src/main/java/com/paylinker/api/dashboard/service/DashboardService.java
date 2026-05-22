package com.paylinker.api.dashboard.service;

import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryCard;
import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryResponse;
import com.paylinker.api.dashboard.dto.DashboardSummaryResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.repository.CampaignRepository;
import com.paylinker.api.repository.CheckItemRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int VIEW_RATE_SCALE = 4;
    private static final int RECENT_CAMPAIGN_LIMIT = 5;

    private final CampaignRepository campaignRepository;
    private final CheckItemRepository checkItemRepository;

    public DashboardService(CampaignRepository campaignRepository,
                            CheckItemRepository checkItemRepository) {
        this.campaignRepository = campaignRepository;
        this.checkItemRepository = checkItemRepository;
    }

    public DashboardCampaignSummaryResponse getCampaignSummary(String adminId, String campaignId) {
        Objects.requireNonNull(adminId, "adminId");

        PaylinkerCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (!adminId.equals(campaign.getAdminId())) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        return new DashboardCampaignSummaryResponse(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getStatus() != null ? campaign.getStatus().name() : null,
                campaign.getSendCompletedAt(),
                campaign.getTotalRecipientCount(),
                campaign.getSendSuccessCount(),
                campaign.getSendFailedCount(),
                campaign.getViewedCount(),
                campaign.getUnviewedCount(),
                calculateViewRate(campaign.getViewedCount(), campaign.getTotalRecipientCount())
        );
    }

    public DashboardSummaryResponse getSummary(String adminId) {
        List<PaylinkerCampaign> campaigns =
                campaignRepository.findRecentByAdminId(adminId, RECENT_CAMPAIGN_LIMIT);

        List<DashboardCampaignSummaryCard> recentCampaigns = campaigns.stream()
                .map(this::toCard)
                .toList();

        int totalUnviewedCount = campaigns.stream()
                .mapToInt(c -> nullToZero(c.getUnviewedCount()))
                .sum();
        int totalFailedCount = campaigns.stream()
                .mapToInt(c -> nullToZero(c.getSendFailedCount()))
                .sum();
        int attentionRequiredCount = checkItemRepository.countUnresolved();

        return new DashboardSummaryResponse(
                totalUnviewedCount,
                totalFailedCount,
                attentionRequiredCount,
                recentCampaigns
        );
    }

    private DashboardCampaignSummaryCard toCard(PaylinkerCampaign campaign) {
        return new DashboardCampaignSummaryCard(
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
        int viewed = nullToZero(viewedCount);
        return BigDecimal.valueOf(viewed)
                .divide(BigDecimal.valueOf(totalRecipientCount), VIEW_RATE_SCALE, RoundingMode.HALF_UP);
    }

    private int nullToZero(Integer value) {
        return value == null ? 0 : value;
    }
}
