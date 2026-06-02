package com.paylinker.api.campaign.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.campaign.dto.response.CampaignSendResponse;
import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.notification.repository.SendJobRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

/**
 * 캠페인 초기 대량발송 디스패처.
 * READY(또는 SCHEDULED) 캠페인의 전체 수신자에게 INITIAL 발송잡(send_job)을 생성하고
 * SQS 에 적재한다. 워커가 큐 메시지를 받아 보안링크 발급 + SES 발송을 수행한다.
 * 캠페인 상태는 SENDING 으로 전이시켜 중복 디스패치를 막는다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CampaignDispatchService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final Set<String> SENDABLE = Set.of("READY", "SCHEDULED");

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final SendJobRepository sendJobRepository;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.sqs.email-queue-url}")
    private String sqsQueueUrl;

    public CampaignSendResponse dispatch(String campaignId, String requesterId) {
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));

        String ownerId = str(campaign, "admin_id");
        if (ownerId != null && !ownerId.equals(requesterId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        String status = str(campaign, "status");
        if ("SENDING".equals(status) || "SENT".equals(status) || "PARTIAL_FAILED".equals(status)) {
            throw new CustomException(ErrorCode.CAMPAIGN_ALREADY_SENDING);
        }
        if (!SENDABLE.contains(status)) {
            throw new CustomException(ErrorCode.CAMPAIGN_CANNOT_SEND);
        }

        List<Map<String, AttributeValue>> recipients =
                campaignRecipientRepository.findByCampaignId(campaignId);
        if (recipients.isEmpty()) {
            throw new CustomException(ErrorCode.CAMPAIGN_CANNOT_SEND);
        }

        String now = ZonedDateTime.now(KST).format(ISO_OFFSET);
        // 발송 시작 표시를 먼저 해 중복 디스패치(연타/스케줄러 중복)를 막는다.
        campaignRepository.updateStatus(campaignId, "SENDING", now);

        int queued = 0;
        for (Map<String, AttributeValue> r : recipients) {
            String campaignRecipientId = str(r, "campaign_recipient_id");
            if (campaignRecipientId == null) {
                continue;
            }
            String sendJobId = "sj_" + UUID.randomUUID().toString().replace("-", "");
            // 보안 링크는 워커가 발급하므로 여기서는 placeholder id 만 둔다(성공 시 워커가 실제 id 로 갱신).
            String secureLinkId = "sl_" + UUID.randomUUID().toString().replace("-", "");
            try {
                sendJobRepository.save(sendJobId, campaignRecipientId, campaignId, "INITIAL", secureLinkId, now);
                enqueue(sendJobId, campaignId, campaignRecipientId);
                queued++;
            } catch (Exception e) {
                log.error("INITIAL 발송잡 적재 실패: campaignId={}, campaignRecipientId={}",
                        campaignId, campaignRecipientId, e);
                try {
                    sendJobRepository.updateToFailed(campaignId, sendJobId);
                } catch (Exception ignore) {
                    // 상태 갱신 실패는 무시(원 예외 우선)
                }
            }
        }

        return CampaignSendResponse.builder()
                .campaignId(campaignId)
                .queuedCount(queued)
                .requestedAt(now)
                .build();
    }

    private void enqueue(String sendJobId, String campaignId, String campaignRecipientId) {
        try {
            String body = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "campaignId", campaignId,
                    "campaignRecipientId", campaignRecipientId));
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .messageBody(body)
                    .build());
        } catch (Exception e) {
            throw new RuntimeException("SQS 큐잉 실패: " + sendJobId, e);
        }
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }
}
