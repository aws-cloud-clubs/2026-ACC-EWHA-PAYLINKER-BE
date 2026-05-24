package com.paylinker.api.dashboard.service;

import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryCard;
import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryResponse;
import com.paylinker.api.dashboard.dto.DashboardFailedRecipientPreview;
import com.paylinker.api.dashboard.dto.DashboardFailureResponse;
import com.paylinker.api.dashboard.dto.DashboardSummaryResponse;
import com.paylinker.api.dashboard.dto.DashboardUnviewedRecipientPreview;
import com.paylinker.api.dashboard.dto.DashboardUnviewedResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import com.paylinker.api.entity.PaylinkerRecipient;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.notification.repository.CheckItemRepository;
import com.paylinker.api.repository.RecipientRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import com.paylinker.common.util.MaskingUtil;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private static final int VIEW_RATE_SCALE = 4;
    private static final int RECENT_CAMPAIGN_LIMIT = 5;
    private static final int RECIPIENT_PREVIEW_LIMIT = 5;

    private final CampaignRepository campaignRepository;
    private final CheckItemRepository checkItemRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final RecipientRepository recipientRepository;

    public DashboardService(CampaignRepository campaignRepository,
                            CheckItemRepository checkItemRepository,
                            CampaignRecipientRepository campaignRecipientRepository,
                            RecipientRepository recipientRepository) {
        this.campaignRepository = campaignRepository;
        this.checkItemRepository = checkItemRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.recipientRepository = recipientRepository;
    }

    public DashboardCampaignSummaryResponse getCampaignSummary(String adminId, String campaignId) {
        Objects.requireNonNull(adminId, "adminId");
        PaylinkerCampaign campaign = verifyOwnership(adminId, campaignId);

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
        int attentionRequiredCount = checkItemRepository.countUnresolved(adminId);

        return new DashboardSummaryResponse(
                totalUnviewedCount,
                totalFailedCount,
                attentionRequiredCount,
                recentCampaigns
        );
    }

    public DashboardUnviewedResponse getUnviewedRecipients(String adminId, String campaignId) {
        PaylinkerCampaign campaign = verifyOwnership(adminId, campaignId);

        int totalUnviewedCount = campaignRecipientRepository.countUnviewed(campaignId);
        List<PaylinkerCampaignRecipient> previewRows =
                campaignRecipientRepository.findUnviewedPreviews(campaignId, RECIPIENT_PREVIEW_LIMIT);
        int elapsedHours = elapsedHoursSince(campaign.getSendCompletedAt());

        Map<String, PaylinkerRecipient> recipientMap = recipientRepository.findAllByIds(
                previewRows.stream().map(PaylinkerCampaignRecipient::getRecipientId).toList());

        List<DashboardUnviewedRecipientPreview> previews = previewRows.stream()
                .map(row -> {
                    PaylinkerRecipient profile = recipientMap.get(row.getRecipientId());
                    String name = profile == null ? null : profile.getName();
                    return new DashboardUnviewedRecipientPreview(
                            row.getRecipientId(),
                            name,
                            MaskingUtil.maskEmployeeNo(row.getEmployeeNo()),
                            elapsedHours);
                })
                .toList();

        return new DashboardUnviewedResponse(campaignId, totalUnviewedCount, elapsedHours, previews);
    }

    public DashboardFailureResponse getSendFailures(String adminId, String campaignId) {
        verifyOwnership(adminId, campaignId);

        int totalFailedCount = campaignRecipientRepository.countFailed(campaignId);
        List<PaylinkerCampaignRecipient> previewRows =
                campaignRecipientRepository.findFailedPreviews(campaignId, RECIPIENT_PREVIEW_LIMIT);

        Map<String, PaylinkerRecipient> recipientMap = recipientRepository.findAllByIds(
                previewRows.stream().map(PaylinkerCampaignRecipient::getRecipientId).toList());

        List<DashboardFailedRecipientPreview> previews = previewRows.stream()
                .map(row -> {
                    PaylinkerRecipient profile = recipientMap.get(row.getRecipientId());
                    String name = profile == null ? null : profile.getName();
                    String department = profile == null ? null : profile.getDepartment();
                    return new DashboardFailedRecipientPreview(
                            row.getCampaignRecipientId(),
                            name,
                            department,
                            MaskingUtil.maskEmail(row.getEmail()),
                            row.getFailureReason() != null ? row.getFailureReason().name() : null);
                })
                .toList();

        return new DashboardFailureResponse(campaignId, totalFailedCount, previews);
    }

    private DashboardCampaignSummaryCard toCard(PaylinkerCampaign campaign) {
        return new DashboardCampaignSummaryCard(
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

    private int elapsedHoursSince(String iso8601Timestamp) {
        if (iso8601Timestamp == null || iso8601Timestamp.isBlank()) {
            return 0;
        }
        OffsetDateTime start = OffsetDateTime.parse(iso8601Timestamp);
        OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
        long hours = Duration.between(start, now).toHours();
        return hours < 0 ? 0 : (int) hours;
    }

    private PaylinkerCampaign verifyOwnership(String adminId, String campaignId) {
        PaylinkerCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!adminId.equals(campaign.getAdminId())) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }
        return campaign;
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
