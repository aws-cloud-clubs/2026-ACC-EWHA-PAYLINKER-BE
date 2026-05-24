package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.request.CampaignUpdateRequest;
import com.paylinker.api.campaign.dto.response.CampaignDetailResponse;
import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import com.paylinker.api.entity.enums.CampaignStatus;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class CampaignUpdateService {

    private final CampaignRepository campaignRepository;

    public CampaignUpdateService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public CampaignDetailResponse updateCampaign(String adminId, String campaignId, CampaignUpdateRequest request) {
        // 1. 기존 데이터 조회
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);
        if (campaign == null || !campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        PaylinkerCampaignLimit limit = campaignRepository.findLimitById(campaignId);
        if (limit == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        // 2. 상태 검증 (수정 가능 상태: DRAFT, READY, SCHEDULED)
        CampaignStatus status = campaign.getStatus();
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.READY && status != CampaignStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.CAMPAIGN_LOCKED);
        }

        // 3. 중복 이름 검증 (이름을 변경하려는 경우에만)
        if (request.campaignName() != null && !request.campaignName().isBlank() && !request.campaignName().equals(campaign.getCampaignName())) {
            if (campaignRepository.existsByAdminIdAndCampaignName(adminId, request.campaignName())) {
                throw new CustomException(ErrorCode.CAMPAIGN_NAME_DUPLICATE);
            }
            campaign.setCampaignName(request.campaignName());
        }

        // 4. 필드 부분 업데이트 (PATCH)
        if (request.emailSubject() != null) campaign.setEmailSubject(request.emailSubject());
        if (request.emailDescription() != null) campaign.setEmailDescription(request.emailDescription());
        if (request.linkTtlHours() != null) campaign.setLinkTtlHours(request.linkTtlHours());
        if (request.allowOneTimeLink() != null) campaign.setAllowOneTimeLink(request.allowOneTimeLink());
        if (request.allowResendRequest() != null) campaign.setAllowResendRequest(request.allowResendRequest());
        if (request.resendRequestLimit() != null) campaign.setResendRequestLimit(request.resendRequestLimit());

        if (request.maxRecipients() != null) limit.setMaxRecipients(request.maxRecipients());
        if (request.maxDailyCount() != null) limit.setMaxDailyCount(request.maxDailyCount());

        // 5. Audit Log 생성
        ZonedDateTime nowKst = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String createdAt = nowKst.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String auditId = UUID.randomUUID().toString();
        String yyyyMmDd = nowKst.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        PaylinkerAuditLog auditLog = new PaylinkerAuditLog();
        auditLog.setPk(PaylinkerAuditLog.pk(yyyyMmDd));
        auditLog.setSk(PaylinkerAuditLog.sk(timestamp, auditId));
        auditLog.setAuditId(auditId);
        auditLog.setAdminId(adminId);
        auditLog.setCampaignId(campaignId);
        auditLog.setAction("CAMPAIGN_UPDATE");
        auditLog.setTargetType("CAMPAIGN");
        auditLog.setTargetId(campaignId);
        auditLog.setCreatedAt(createdAt);
        auditLog.setGsi1Pk(PaylinkerAuditLog.gsi1Pk(adminId));
        auditLog.setGsi1Sk(createdAt);
        auditLog.setGsi2Pk(PaylinkerAuditLog.gsi2Pk(campaignId));
        auditLog.setGsi2Sk(createdAt);

        // 6. DB 트랜잭션 업데이트 - Service에서 조립 후 실행
        TransactWriteItemsEnhancedRequest transactionRequest = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(campaignRepository.getCampaignTable(), campaign)
                .addUpdateItem(campaignRepository.getLimitTable(), limit)
                .addPutItem(campaignRepository.getAuditLogTable(), auditLog)
                .build();

        campaignRepository.executeTransaction(transactionRequest);

        // 7. 응답 반환 (상세 정보)
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