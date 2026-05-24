package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.request.CampaignCreateRequest;
import com.paylinker.api.campaign.dto.response.CampaignCreateResponse;
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
public class CampaignCreateService {

    private final CampaignRepository campaignRepository;

    public CampaignCreateService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public CampaignCreateResponse createCampaign(String adminId, CampaignCreateRequest request) {
        // 1. 캠페인명 중복 검증
        if (campaignRepository.existsByAdminIdAndCampaignName(adminId, request.campaignName())) {
            throw new CustomException(ErrorCode.CAMPAIGN_NAME_DUPLICATE);
        }

        // 2. 시간 데이터 생성 (KST 기준)
        ZonedDateTime nowKst = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String createdAt = nowKst.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 예약 발송 검증
        CampaignStatus status = CampaignStatus.DRAFT;
        if (request.scheduledSendAt() != null && !request.scheduledSendAt().isBlank()) {
            try {
                ZonedDateTime scheduledAt = ZonedDateTime.parse(request.scheduledSendAt());
                if (scheduledAt.isBefore(nowKst.plusMinutes(10))) {
                    throw new CustomException(ErrorCode.CAMPAIGN_INVALID_INPUT);
                }
                status = CampaignStatus.SCHEDULED;
            } catch (java.time.format.DateTimeParseException e) {
                // ISO-8601 형식이 아닐 경우 400 에러 처리
                throw new CustomException(ErrorCode.CAMPAIGN_INVALID_INPUT);
            }
        }

        String campaignId = UUID.randomUUID().toString();

        // 3. 엔티티 생성: PaylinkerCampaign
        PaylinkerCampaign campaign = new PaylinkerCampaign();
        campaign.setPk(PaylinkerCampaign.pk(campaignId));
        campaign.setSk(PaylinkerCampaign.sk());
        campaign.setCampaignId(campaignId);
        campaign.setAdminId(adminId);
        campaign.setCampaignName(request.campaignName());
        campaign.setEmailSubject(request.emailSubject());
        campaign.setEmailDescription(request.emailDescription());
        campaign.setStatus(status);

        // 기본값 처리
        campaign.setLinkTtlHours(request.linkTtlHours() != null ? request.linkTtlHours() : 48);
        campaign.setAllowOneTimeLink(request.allowOneTimeLink() != null ? request.allowOneTimeLink() : false);
        campaign.setAllowResendRequest(request.allowResendRequest() != null ? request.allowResendRequest() : true);
        campaign.setResendRequestLimit(request.resendRequestLimit() != null ? request.resendRequestLimit() : 1);

        campaign.setScheduledSendAt(request.scheduledSendAt());
        campaign.setCreatedAt(createdAt);
        campaign.setGsi1Pk(PaylinkerCampaign.gsi1Pk(adminId));
        campaign.setGsi1Sk(createdAt);

        // 4. 엔티티 생성: PaylinkerCampaignLimit
        PaylinkerCampaignLimit limit = new PaylinkerCampaignLimit();
        limit.setPk(PaylinkerCampaignLimit.pk(campaignId));
        limit.setSk(PaylinkerCampaignLimit.SK_LIMIT);
        limit.setCampaignId(campaignId);
        limit.setMaxRecipients(request.maxRecipients());
        limit.setMaxDailyCount(request.maxDailyCount());
        limit.setCurrentDailyCount(0);
        limit.setCreatedAt(createdAt);

        // 5. 엔티티 생성: PaylinkerAuditLog
        String auditId = UUID.randomUUID().toString();
        String yyyyMmDd = nowKst.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        PaylinkerAuditLog auditLog = new PaylinkerAuditLog();
        auditLog.setPk(PaylinkerAuditLog.pk(yyyyMmDd));
        auditLog.setSk(PaylinkerAuditLog.sk(timestamp, auditId));
        auditLog.setAuditId(auditId);
        auditLog.setAdminId(adminId);
        auditLog.setCampaignId(campaignId);
        auditLog.setAction("CAMPAIGN_CREATE");
        auditLog.setTargetType("CAMPAIGN");
        auditLog.setTargetId(campaignId);
        auditLog.setCreatedAt(createdAt);
        auditLog.setGsi1Pk(PaylinkerAuditLog.gsi1Pk(adminId));
        auditLog.setGsi1Sk(createdAt);
        auditLog.setGsi2Pk(PaylinkerAuditLog.gsi2Pk(campaignId));
        auditLog.setGsi2Sk(createdAt);

        // 6. DB 트랜잭션 저장 - Service에서 조립 후 실행
        TransactWriteItemsEnhancedRequest transactionRequest = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(campaignRepository.getCampaignTable(), campaign)
                .addPutItem(campaignRepository.getLimitTable(), limit)
                .addPutItem(campaignRepository.getAuditLogTable(), auditLog)
                .build();

        campaignRepository.executeTransaction(transactionRequest);

        // 7. 응답 반환
        return new CampaignCreateResponse(campaignId, campaign.getCampaignName(), campaign.getStatus().name(), createdAt);
    }
}