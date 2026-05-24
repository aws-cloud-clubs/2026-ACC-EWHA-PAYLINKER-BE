package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignCancelResponse;
import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
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
public class CampaignCancelService {

    private final CampaignRepository campaignRepository;

    public CampaignCancelService(CampaignRepository campaignRepository) {
        this.campaignRepository = campaignRepository;
    }

    public CampaignCancelResponse cancelCampaign(String adminId, String campaignId) {
        // 1. 기존 데이터 조회
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);

        // 캠페인이 없거나, 권한이 없는(본인 캠페인이 아닌) 경우 404 처리
        if (campaign == null || !campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        // 2. 상태 검증 (취소 가능 상태: DRAFT, READY, SCHEDULED)
        CampaignStatus status = campaign.getStatus();
        if (status != CampaignStatus.DRAFT && status != CampaignStatus.READY && status != CampaignStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.CAMPAIGN_CANNOT_CANCEL);
        }

        // 3. 상태 변경 및 취소 시각 기록 (KST 기준)
        ZonedDateTime nowKst = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String cancelledAt = nowKst.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        campaign.setStatus(CampaignStatus.CANCELLED);
        campaign.setCancelledAt(cancelledAt);

        // 4. Audit Log 직접 생성
        String auditId = UUID.randomUUID().toString();
        String yyyyMmDd = nowKst.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        PaylinkerAuditLog auditLog = new PaylinkerAuditLog();
        auditLog.setPk(PaylinkerAuditLog.pk(yyyyMmDd));
        auditLog.setSk(PaylinkerAuditLog.sk(timestamp, auditId));
        auditLog.setAuditId(auditId);
        auditLog.setAdminId(adminId);
        auditLog.setCampaignId(campaignId);
        auditLog.setAction("CAMPAIGN_CANCEL");
        auditLog.setTargetType("CAMPAIGN");
        auditLog.setTargetId(campaignId);
        auditLog.setCreatedAt(cancelledAt);
        auditLog.setGsi1Pk(PaylinkerAuditLog.gsi1Pk(adminId));
        auditLog.setGsi1Sk(cancelledAt);
        auditLog.setGsi2Pk(PaylinkerAuditLog.gsi2Pk(campaignId));
        auditLog.setGsi2Sk(cancelledAt);

        // 5. DB 트랜잭션 요청 객체 조립 및 실행
        TransactWriteItemsEnhancedRequest transactionRequest = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(campaignRepository.getCampaignTable(), campaign)
                .addPutItem(campaignRepository.getAuditLogTable(), auditLog)
                .build();

        campaignRepository.executeTransaction(transactionRequest);

        // 6. 응답 반환
        return new CampaignCancelResponse(campaignId, CampaignStatus.CANCELLED.name(), cancelledAt);
    }
}