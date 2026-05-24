package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignSendRequestResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.enums.CampaignStatus;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.campaign.repository.AuditLogRepository;
// import com.paylinker.api.repository.DocumentMatchRepository; // TODO: 머지 후 주석 해제
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.model.UpdateItemEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignSendService {

    private final CampaignRepository campaignRepository;
    private final CampaignFanOutService campaignFanOutService;
    private final AuditLogRepository auditLogRepository;
    // private final DocumentMatchRepository documentMatchRepository; // TODO: 머지 후 주석 해제

    public CampaignSendRequestResponse sendCampaign(String adminId, String campaignId) {
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);

        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        if (!campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 1. 이미 발송 중이거나 완료된 경우 차단 (409)
        CampaignStatus status = campaign.getStatus();
        if (status == CampaignStatus.SENDING || status == CampaignStatus.SENT || status == CampaignStatus.PARTIAL_FAILED) {
            throw new CustomException(ErrorCode.CAMPAIGN_ALREADY_SENDING);
        }

        // 2. SND-003 발송 조건 불충족 시 차단 (422)
        // TODO: #12 브랜치 DocumentMatchRepository 병합 완료 시 실제 로직으로 변경
        // int unmatchedCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_UNMATCHED);
        int unmatchedCount = 0;
        int totalRecipientCount = campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0;

        // 예시 방어 로직 (현재는 통과되도록 세팅, 추후 원상복구)
        if (unmatchedCount > 0 || totalRecipientCount == 0) {
            // throw new CustomException(ErrorCode.CAMPAIGN_CANNOT_SEND);
        }

        String now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        boolean isScheduled = campaign.getScheduledSendAt() != null && !campaign.getScheduledSendAt().isBlank();

        // 3. 상태 변경 트랜잭션 업데이트 (ConditionExpression으로 멱등성 및 TOCTOU 방어)
        String targetStatus = isScheduled ? CampaignStatus.SCHEDULED.name() : CampaignStatus.SENDING.name();
        String sendStartedAt = isScheduled ? null : now;

        campaign.setStatus(CampaignStatus.valueOf(targetStatus));
        campaign.setSendStartedAt(sendStartedAt);

        Expression conditionExpression = Expression.builder()
                .expression("#st = :ready OR #st = :scheduled")
                .expressionValues(Map.of(
                        ":ready", AttributeValue.fromS(CampaignStatus.READY.name()),
                        ":scheduled", AttributeValue.fromS(CampaignStatus.SCHEDULED.name())
                ))
                .expressionNames(Map.of("#st", "status"))
                .build();

        try {
            // 조건부 업데이트로 상태 변경 시도
            campaignRepository.getCampaignTable().updateItem(UpdateItemEnhancedRequest.builder(PaylinkerCampaign.class)
                    .item(campaign)
                    .conditionExpression(conditionExpression)
                    .build());

            // SEND_REQUEST Audit Log 기록
            String auditLogId = "al_" + UUID.randomUUID().toString().replace("-", "");
            auditLogRepository.save(auditLogId, campaignId, "SEND_REQUEST", now);

        } catch (ConditionalCheckFailedException e) {
            // 상태 충돌 예외만 명확하게 잡아서 409 처리 (나머지 500 에러는 던짐)
            log.warn("캠페인 발송 상태 업데이트 실패 (Race Condition 또는 이미 처리됨): {}", campaignId);
            throw new CustomException(ErrorCode.CAMPAIGN_ALREADY_SENDING);
        }

        // 4. 즉시 발송일 경우에만 Fan-out 시작
        if (!isScheduled) {
            campaignFanOutService.fanOutRecipients(campaignId, adminId);
        }

        return CampaignSendRequestResponse.builder()
                .campaignId(campaignId)
                .status(targetStatus)
                .sendStartedAt(sendStartedAt)
                .queuedJobCount(isScheduled ? 0 : totalRecipientCount)
                .build();
    }
}