package com.paylinker.worker.sesevent;

import static com.paylinker.worker.util.Attr.str;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.worker.repository.CampaignRecipientRepo;
import com.paylinker.worker.repository.SendJobRepo;
import com.paylinker.worker.sesevent.dto.SesNotification;
import com.paylinker.worker.sesevent.dto.SnsEnvelope;
import com.paylinker.worker.sesevent.repository.CheckItemRepo;
import com.paylinker.worker.sesevent.repository.SendJobByMessageRepo;
import com.paylinker.worker.sesevent.repository.SesEventRepo;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * SES 이벤트 처리 오케스트레이션.
 *
 * 흐름:
 * 1. SNS envelope 여부 판별 → SesNotification 추출
 * 2. eventType (BOUNCE / COMPLAINT / REJECT) 와 SES messageId 추출
 * 3. send-job 역조회 (GSI2: SES#<messageId>) → campaignId / campaignRecipientId 확보
 * 4. ses-event 적재 (조건부 PutItem 으로 중복 처리 방지)
 * 5. send-job.job_status = FAILED, failure_reason 매핑
 * 6. campaign-recipient.send_status = FAILED, failure_reason 매핑 (D-1 룰 입력)
 * 7. check-item 생성 (운영자 알림 큐)
 */
public class SesEventService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final long EVENT_TTL_DAYS = 180L;

    private final ObjectMapper mapper;
    private final SesEventRepo sesEventRepo;
    private final SendJobByMessageRepo sendJobByMessageRepo;
    private final SendJobRepo sendJobRepo;
    private final CampaignRecipientRepo recipientRepo;
    private final CheckItemRepo checkItemRepo;

    public SesEventService(ObjectMapper mapper,
                           SesEventRepo sesEventRepo,
                           SendJobByMessageRepo sendJobByMessageRepo,
                           SendJobRepo sendJobRepo,
                           CampaignRecipientRepo recipientRepo,
                           CheckItemRepo checkItemRepo) {
        this.mapper = mapper;
        this.sesEventRepo = sesEventRepo;
        this.sendJobByMessageRepo = sendJobByMessageRepo;
        this.sendJobRepo = sendJobRepo;
        this.recipientRepo = recipientRepo;
        this.checkItemRepo = checkItemRepo;
    }

    /** SQS message body 1건 처리. 예외 시 호출자가 SQS 부분 배치 실패로 표시한다. */
    public void handle(String body) throws Exception {
        SesNotification event = unwrap(body);
        String eventType = normalizeEventType(event.eventType());
        if (eventType == null) {
            // 우리가 처리하는 타입이 아니면 멱등 종료
            return;
        }
        if (event.mail() == null || event.mail().messageId() == null) {
            throw new IllegalStateException("SES event mail.messageId 누락");
        }

        String sesMessageId = event.mail().messageId();
        String receivedAt = ZonedDateTime.now(KST).format(ISO_OFFSET);
        long ttlEpoch = ZonedDateTime.now(KST).plusDays(EVENT_TTL_DAYS).toEpochSecond();

        // send-job 역조회로 campaignId / recipientId / jobId 확보
        Map<String, AttributeValue> sendJob = sendJobByMessageRepo.findBySesMessageId(sesMessageId);
        String campaignId = sendJob == null ? "UNKNOWN" : str(sendJob, "campaign_id");
        String campaignRecipientId = sendJob == null ? null : str(sendJob, "campaign_recipient_id");
        String sendJobId = sendJob == null ? null : str(sendJob, "send_job_id");
        String adminId = sendJob == null ? null : str(sendJob, "requested_by_admin_id");

        // ses-event 적재 (조건부 — 같은 messageId+eventType 두 번 처리되면 false)
        boolean inserted = sesEventRepo.putIfAbsent(
                campaignId, sesMessageId, eventType, receivedAt,
                sendJobId, mapper.writeValueAsString(event), ttlEpoch);
        if (!inserted) {
            // 멱등: 이미 처리된 이벤트
            return;
        }

        // send-job 이 없으면 send-job/recipient/check-item 갱신은 스킵 (감사용 ses-event 만 남음)
        if (sendJob == null || campaignRecipientId == null) {
            return;
        }

        String failureReason = mapFailureReason(eventType);

        // send-job 상태 갱신
        sendJobRepo.markFailed(campaignId, sendJobId, failureReason);
        // campaign-recipient 상태 갱신 (D-1 룰 입력)
        recipientRepo.markFailed(campaignId, campaignRecipientId, failureReason);
        // 운영자 알림
        String checkItemId = "chk_" + sesMessageId + "_" + eventType;
        checkItemRepo.save(checkItemId, campaignId, adminId, campaignRecipientId,
                "SEND_FAILURE_" + eventType, receivedAt);
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    /**
     * SQS body 가 SNS envelope 형식이면 unwrap, raw delivery 면 그대로 SesNotification 으로 파싱.
     */
    private SesNotification unwrap(String body) throws Exception {
        String trimmed = body == null ? "" : body.trim();
        if (trimmed.contains("\"Type\"") && trimmed.contains("\"Message\"")) {
            SnsEnvelope envelope = mapper.readValue(trimmed, SnsEnvelope.class);
            return mapper.readValue(envelope.message(), SesNotification.class);
        }
        return mapper.readValue(trimmed, SesNotification.class);
    }

    private static String normalizeEventType(String raw) {
        if (raw == null) return null;
        String upper = raw.toUpperCase();
        return switch (upper) {
            case "BOUNCE", "COMPLAINT", "REJECT" -> upper;
            default -> null;
        };
    }

    /** SES eventType → SendFailureReason enum 매핑. */
    private static String mapFailureReason(String eventType) {
        return switch (eventType) {
            case "BOUNCE" -> "BOUNCED";
            case "COMPLAINT" -> "COMPLAINT";
            case "REJECT" -> "BLOCKED";
            default -> "UNKNOWN";
        };
    }
}
