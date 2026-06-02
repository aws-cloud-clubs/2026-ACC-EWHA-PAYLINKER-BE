package com.paylinker.worker;

import static com.paylinker.worker.util.Attr.num;
import static com.paylinker.worker.util.Attr.str;

import com.paylinker.worker.dto.EmailJobMessage;
import com.paylinker.worker.email.EmailSender;
import com.paylinker.worker.email.EmailTemplate;
import com.paylinker.worker.repository.CampaignRecipientRepo;
import com.paylinker.worker.repository.CampaignRepo;
import com.paylinker.worker.repository.EmailSuppressionRepo;
import com.paylinker.worker.repository.SecureLinkRepo;
import com.paylinker.worker.repository.SendAttemptRepo;
import com.paylinker.worker.repository.SendJobRepo;
import com.paylinker.worker.util.HashUtil;
import com.paylinker.worker.util.IdUtil;
import com.paylinker.worker.util.UnsubscribeToken;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.ses.model.SesException;

public class EmailWorkerService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final DateTimeFormatter HUMAN_KST = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final int DEFAULT_LINK_TTL_HOURS = 48;
    private static final long ATTEMPT_TTL_DAYS = 90L;

    private final CampaignRepo campaignRepo;
    private final CampaignRecipientRepo recipientRepo;
    private final SecureLinkRepo secureLinkRepo;
    private final SendJobRepo sendJobRepo;
    private final SendAttemptRepo sendAttemptRepo;
    private final EmailSender emailSender;
    private final String recipientLinkBaseUrl;
    private final EmailSuppressionRepo emailSuppressionRepo;
    private final String unsubscribeBaseUrl;
    private final String unsubscribeSecret;

    public EmailWorkerService(CampaignRepo campaignRepo,
                              CampaignRecipientRepo recipientRepo,
                              SecureLinkRepo secureLinkRepo,
                              SendJobRepo sendJobRepo,
                              SendAttemptRepo sendAttemptRepo,
                              EmailSender emailSender,
                              String recipientLinkBaseUrl,
                              EmailSuppressionRepo emailSuppressionRepo,
                              String unsubscribeBaseUrl,
                              String unsubscribeSecret) {
        this.campaignRepo = campaignRepo;
        this.recipientRepo = recipientRepo;
        this.secureLinkRepo = secureLinkRepo;
        this.sendJobRepo = sendJobRepo;
        this.sendAttemptRepo = sendAttemptRepo;
        this.emailSender = emailSender;
        this.recipientLinkBaseUrl = trimTrailingSlash(recipientLinkBaseUrl);
        this.emailSuppressionRepo = emailSuppressionRepo;
        this.unsubscribeBaseUrl = trimTrailingSlash(unsubscribeBaseUrl);
        this.unsubscribeSecret = unsubscribeSecret;
    }

    /**
     * SQS 메시지 1건 처리. 호출자는 예외 발생 시 SQS 부분 배치 실패로 표시한다.
     */
    public void handle(EmailJobMessage msg) {
        // 1. send_job 조회 + 멱등성 가드
        Map<String, AttributeValue> sendJob = sendJobRepo.findById(msg.campaignId(), msg.sendJobId());
        if (sendJob == null) {
            throw new IllegalStateException(
                    "send_job not found: " + msg.sendJobId() + " (campaign " + msg.campaignId() + ")");
        }
        String existingStatus = str(sendJob, "job_status");
        if ("SUCCESS".equals(existingStatus) || "SKIPPED".equals(existingStatus)) {
            // 이미 처리(발송 또는 스킵)된 메시지. 멱등 종료.
            return;
        }

        // 2. campaign + recipient 조회
        Map<String, AttributeValue> campaign = campaignRepo.findById(msg.campaignId());
        if (campaign == null) {
            failJob(msg, "SYSTEM_ERROR");
            throw new IllegalStateException("campaign not found: " + msg.campaignId());
        }
        Map<String, AttributeValue> recipient =
                recipientRepo.findByCampaignAndRecipient(msg.campaignId(), msg.campaignRecipientId());
        if (recipient == null) {
            failJob(msg, "SYSTEM_ERROR");
            throw new IllegalStateException(
                    "campaign_recipient not found: " + msg.campaignRecipientId());
        }
        String toEmail = str(recipient, "email");
        if (toEmail == null || toEmail.isBlank()) {
            failJob(msg, "INVALID_EMAIL");
            recipientRepo.markFailed(msg.campaignId(), msg.campaignRecipientId(), "INVALID_EMAIL");
            return;
        }

        // 2-1. 수신거부 가드: email 해시가 suppression 에 있으면 발송하지 않고 SKIPPED 처리.
        String emailHash = HashUtil.sha256Hex(toEmail.toLowerCase(Locale.ROOT));
        if (emailSuppressionRepo.isSuppressed(emailHash)) {
            recipientRepo.markSkipped(msg.campaignId(), msg.campaignRecipientId(), "UNSUBSCRIBED");
            sendJobRepo.markSkipped(msg.campaignId(), msg.sendJobId(), "UNSUBSCRIBED");
            return;
        }

        // 3. 상태 SENDING 으로 전이
        recipientRepo.markSending(msg.campaignId(), msg.campaignRecipientId());

        // 4. secure_link 발급
        String plainToken = IdUtil.newToken();
        String tokenHash = HashUtil.sha256Hex(plainToken);
        String secureLinkId = IdUtil.newId("sl");
        int linkTtlHours = parseLinkTtlHours(campaign);
        ZonedDateTime nowKst = ZonedDateTime.now(KST);
        ZonedDateTime expiresAtKst = nowKst.plusHours(linkTtlHours);
        String expiresAtIso = expiresAtKst.format(ISO_OFFSET);
        String createdAtIso = nowKst.format(ISO_OFFSET);
        long ttlEpoch = expiresAtKst.plusDays(7).toEpochSecond();

        secureLinkRepo.save(secureLinkId, tokenHash, msg.campaignRecipientId(),
                msg.campaignId(), expiresAtIso, ttlEpoch, createdAtIso);

        // 5. 이메일 본문 구성 + SES 발송
        String html = EmailTemplate.render(
                str(recipient, "name"),
                str(campaign, "campaign_name"),
                str(campaign, "email_subject"),
                str(campaign, "email_description"),
                buildLinkUrl(plainToken),
                expiresAtKst.format(HUMAN_KST) + " KST",
                buildUnsubscribeUrl(emailHash));
        String subject = orDefault(str(campaign, "email_subject"),
                orDefault(str(campaign, "campaign_name"), "PayLinker 명세서 안내"));

        int attemptNo = computeNextAttemptNo(sendJob);
        long attemptTtl = ZonedDateTime.now(KST).plusDays(ATTEMPT_TTL_DAYS).toEpochSecond();
        String sesMessageId;
        try {
            sesMessageId = emailSender.send(toEmail, subject, html);
        } catch (SesException e) {
            String reason = mapSesError(e);
            sendAttemptRepo.save(msg.sendJobId(), attemptNo, "FAILED",
                    reason, null, createdAtIso, attemptTtl);
            failJob(msg, reason);
            recipientRepo.markFailed(msg.campaignId(), msg.campaignRecipientId(), reason);
            throw e;
        } catch (Exception e) {
            sendAttemptRepo.save(msg.sendJobId(), attemptNo, "FAILED",
                    "SYSTEM_ERROR", null, createdAtIso, attemptTtl);
            failJob(msg, "SYSTEM_ERROR");
            recipientRepo.markFailed(msg.campaignId(), msg.campaignRecipientId(), "SYSTEM_ERROR");
            throw new RuntimeException("SES send failed", e);
        }

        // 6. 성공 기록
        sendAttemptRepo.save(msg.sendJobId(), attemptNo, "SUCCESS",
                null, sesMessageId, createdAtIso, attemptTtl);
        sendJobRepo.markSuccess(msg.campaignId(), msg.sendJobId(), sesMessageId, secureLinkId);
        recipientRepo.markSuccess(msg.campaignId(), msg.campaignRecipientId());
    }

    // ── 헬퍼 ────────────────────────────────────────────────────────────

    private void failJob(EmailJobMessage msg, String reason) {
        try {
            sendJobRepo.markFailed(msg.campaignId(), msg.sendJobId(), reason);
        } catch (Exception ignore) {
            // 상태 갱신 실패해도 본 예외 우선
        }
    }

    private int parseLinkTtlHours(Map<String, AttributeValue> campaign) {
        Integer v = num(campaign, "link_ttl_hours");
        if (v == null || v <= 0) return DEFAULT_LINK_TTL_HOURS;
        return v;
    }

    private int computeNextAttemptNo(Map<String, AttributeValue> sendJob) {
        Integer current = num(sendJob, "attempt_count");
        return current == null ? 1 : current + 1;
    }

    private String buildLinkUrl(String plainToken) {
        return recipientLinkBaseUrl + "/" + plainToken;
    }

    private String buildUnsubscribeUrl(String emailHash) {
        if (emailHash == null || unsubscribeSecret == null || unsubscribeSecret.isBlank()) {
            return null;
        }
        return unsubscribeBaseUrl + "/" + UnsubscribeToken.generate(emailHash, unsubscribeSecret);
    }

    private static String orDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

    private static String trimTrailingSlash(String url) {
        if (url == null) return "";
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    /**
     * SES SDK 예외를 SendFailureReason enum 값으로 매핑.
     * BE 의 SendFailureReason: INVALID_EMAIL, BLOCKED, BOUNCED, COMPLAINT,
     *                           TEMPORARY_FAILURE, SYSTEM_ERROR, RATE_LIMITED, UNKNOWN.
     */
    private static String mapSesError(SesException e) {
        String code = e.awsErrorDetails() != null ? e.awsErrorDetails().errorCode() : null;
        if (code == null) return "SYSTEM_ERROR";
        return switch (code) {
            case "InvalidParameterValue", "InvalidParameter" -> "INVALID_EMAIL";
            case "MessageRejected" -> "BLOCKED";
            case "Throttling", "TooManyRequestsException" -> "RATE_LIMITED";
            case "AccountSendingPausedException", "ConfigurationSetSendingPausedException" -> "BLOCKED";
            case "MailFromDomainNotVerifiedException" -> "SYSTEM_ERROR";
            default -> "UNKNOWN";
        };
    }
}
