package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.request.CampaignScheduleRequest;
import com.paylinker.api.campaign.dto.response.CampaignScheduleResponse;
import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.enums.CampaignStatus;
import com.paylinker.api.campaign.repository.CampaignRepository;
// import com.paylinker.api.repository.DocumentMatchRepository; // TODO: #12 브랜치 머지 후 복구
// import com.paylinker.api.entity.PaylinkerDocumentMatch; // TODO: #12 브랜치 머지 후 복구
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

@Service
public class CampaignScheduleService {

    private final CampaignRepository campaignRepository;
    // private final DocumentMatchRepository documentMatchRepository; // TODO: #12 브랜치 머지 후 복구

    public CampaignScheduleService(CampaignRepository campaignRepository
                                   // , DocumentMatchRepository documentMatchRepository // TODO: #12 브랜치 머지 후 복구
    ) {
        this.campaignRepository = campaignRepository;
        // this.documentMatchRepository = documentMatchRepository;
    }

    public CampaignScheduleResponse scheduleCampaign(String adminId, String campaignId, CampaignScheduleRequest request) {
        // 1. 기존 데이터 조회 및 소유권 검증
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        if (!campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 2. 캠페인 상태 상태 검증 (DRAFT, READY, SCHEDULED 상태만 가능)
        CampaignStatus currentStatus = campaign.getStatus();
        if (currentStatus != CampaignStatus.DRAFT && currentStatus != CampaignStatus.READY && currentStatus != CampaignStatus.SCHEDULED) {
            throw new CustomException(ErrorCode.CAMPAIGN_LOCKED);
        }

        ZonedDateTime nowKst = ZonedDateTime.now(ZoneId.of("Asia/Seoul"));
        String createdAt = nowKst.format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        String targetStatus;
        String actionType;

        // 3. 예약 설정 또는 해제 분기 처리
        if (request.scheduledSendAt() != null && !request.scheduledSendAt().isBlank()) {
            // [예약 설정]
            // 3-1. 사전 조건 검증: 수신자/명세서 매칭 완료 여부 (SND-003 기반 검증)
            // TODO: #12 브랜치 머지 후 아래 주석 해제 및 복구
            // int unmatchedCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_UNMATCHED);
            // int duplicateCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH);
            int unmatchedCount = 0;
            int duplicateCount = 0;
            int totalRecipientCount = campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0;

            // 임시로 수신자 수가 0명일 때만 에러를 던지도록 동작 (추후 복구 필요)
            if (unmatchedCount > 0 || duplicateCount > 0 || totalRecipientCount == 0) {
                throw new CustomException(ErrorCode.CAMPAIGN_NOT_READY);
            }

            // 3-2. 시간 유효성 검증 (현재 시각 + 10분 이후)
            try {
                ZonedDateTime scheduledAt = ZonedDateTime.parse(request.scheduledSendAt());
                if (scheduledAt.isBefore(nowKst.plusMinutes(10))) {
                    throw new CustomException(ErrorCode.CAMPAIGN_SCHEDULE_INVALID);
                }
            } catch (java.time.format.DateTimeParseException e) {
                throw new CustomException(ErrorCode.CAMPAIGN_INVALID_INPUT);
            }

            campaign.setScheduledSendAt(request.scheduledSendAt());
            campaign.setStatus(CampaignStatus.SCHEDULED);
            targetStatus = CampaignStatus.SCHEDULED.name();
            actionType = "CAMPAIGN_SCHEDULE_SET";

        } else {
            // [예약 해제]
            campaign.setScheduledSendAt(null);
            campaign.setStatus(CampaignStatus.READY);
            targetStatus = CampaignStatus.READY.name();
            actionType = "CAMPAIGN_SCHEDULE_CANCEL";
        }

        // 4. Audit Log 엔티티 직접 생성
        String auditId = UUID.randomUUID().toString();
        String yyyyMmDd = nowKst.format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        String timestamp = String.valueOf(Instant.now().toEpochMilli());

        PaylinkerAuditLog auditLog = new PaylinkerAuditLog();
        auditLog.setPk(PaylinkerAuditLog.pk(yyyyMmDd));
        auditLog.setSk(PaylinkerAuditLog.sk(timestamp, auditId));
        auditLog.setAuditId(auditId);
        auditLog.setAdminId(adminId);
        auditLog.setCampaignId(campaignId);
        auditLog.setAction(actionType);
        auditLog.setTargetType("CAMPAIGN");
        auditLog.setTargetId(campaignId);
        auditLog.setCreatedAt(createdAt);
        auditLog.setGsi1Pk(PaylinkerAuditLog.gsi1Pk(adminId));
        auditLog.setGsi1Sk(createdAt);
        auditLog.setGsi2Pk(PaylinkerAuditLog.gsi2Pk(campaignId));
        auditLog.setGsi2Sk(createdAt);

        // 5. DynamoDB 트랜잭션 빌드 (ConditionExpression 주입하여 원자적 업데이트 보장)
        Expression conditionExpression = Expression.builder()
                .expression("#st = :draft OR #st = :ready OR #st = :scheduled")
                .expressionValues(Map.of(
                        ":draft", AttributeValue.fromS(CampaignStatus.DRAFT.name()),
                        ":ready", AttributeValue.fromS(CampaignStatus.READY.name()),
                        ":scheduled", AttributeValue.fromS(CampaignStatus.SCHEDULED.name())
                ))
                .expressionNames(Map.of("#st", "status"))
                .build();

        UpdateItemEnhancedRequest<PaylinkerCampaign> updateRequest = UpdateItemEnhancedRequest.builder(PaylinkerCampaign.class)
                .item(campaign)
                .conditionExpression(conditionExpression)
                .build();

        TransactWriteItemsEnhancedRequest transactionRequest = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(campaignRepository.getCampaignTable(), updateRequest)
                .addPutItem(campaignRepository.getAuditLogTable(), auditLog)
                .build();

        // 6. 트랜잭션 실행 (리팩토링 구조 적용)
        campaignRepository.executeTransaction(transactionRequest);

        return new CampaignScheduleResponse(campaignId, targetStatus, campaign.getScheduledSendAt());
    }
}