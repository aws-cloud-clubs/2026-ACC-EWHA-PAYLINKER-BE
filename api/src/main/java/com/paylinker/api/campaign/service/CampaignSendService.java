package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignSendRequestResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerDocumentMatch;
import com.paylinker.api.entity.enums.CampaignStatus;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.campaign.repository.AuditLogRepository;
import com.paylinker.api.repository.DocumentMatchRepository;
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
    private final DocumentMatchRepository documentMatchRepository;

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
        int unmatchedCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_UNMATCHED);
        int totalRecipientCount = campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0;

        // 방어 로직
        if (unmatchedCount > 0 || totalRecipientCount == 0) {
            throw new CustomException(ErrorCode.CAMPAIGN_CANNOT_SEND);
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
            try {
                campaignFanOutService.fanOutRecipients(campaignId, adminId);
            } catch (Exception e) {
                // 스레드 풀 고갈(TaskRejectedException) 등 비동기 큐잉 자체가 실패했을 때의 상태 롤백 처리
                log.error("비동기 Fan-out 작업 큐 등록 실패. 캠페인 상태를 PARTIAL_FAILED로 롤백합니다. CampaignId: {}", campaignId, e);
                rollbackCampaignStatusToFailed(campaign);

                // 클라이언트에게 에러 응답을 반환하기 위해 RuntimeException 발생
                throw new RuntimeException("이메일 발송 작업 초기화에 실패했습니다. 잠시 후 다시 시도해주세요.");
            }
        }

        return CampaignSendRequestResponse.builder()
                .campaignId(campaignId)
                .status(targetStatus)
                .sendStartedAt(sendStartedAt)
                .queuedJobCount(isScheduled ? 0 : totalRecipientCount)
                .build();
    }

    // 롤백 전용 헬퍼 메서드
    private void rollbackCampaignStatusToFailed(PaylinkerCampaign campaign) {
        try {
            campaign.setStatus(CampaignStatus.PARTIAL_FAILED);
            campaignRepository.getCampaignTable().updateItem(campaign);
        } catch (Exception ex) {
            log.error("캠페인 상태 롤백 중 2차 실패 발생. CampaignId: {}", campaign.getCampaignId(), ex);
        }
    }
}