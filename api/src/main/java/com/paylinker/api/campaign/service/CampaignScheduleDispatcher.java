package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.repository.CampaignRepository;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 예약 발송 폴러.
 * SCHEDULED 상태이면서 scheduled_send_at 이 도래한 캠페인을 주기적으로 찾아
 * SENDING 으로 전이시키고 fan-out 을 트리거한다.
 * 다중 인스턴스(ECS 다중 태스크)에서 중복 디스패치를 막기 위해 Redis 락을 사용한다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignScheduleDispatcher {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String LOCK_KEY = "paylinker:scheduler:scheduled-send:lock";

    private final CampaignRepository campaignRepository;
    private final CampaignFanOutService campaignFanOutService;
    private final StringRedisTemplate redisTemplate;

    @Value("${paylinker.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${paylinker.link.scheduler-lock-ttl-seconds:50}")
    private long lockTtlSeconds;

    @Scheduled(fixedDelayString = "${paylinker.scheduler.poll-interval-ms:60000}")
    public void pollAndDispatch() {
        if (!enabled) {
            return;
        }
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(lockTtlSeconds));
        if (!Boolean.TRUE.equals(locked)) {
            return; // 다른 인스턴스가 처리 중
        }
        try {
            ZonedDateTime now = ZonedDateTime.now(KST);
            String nowIso = now.format(ISO_OFFSET);
            List<Map<String, AttributeValue>> scheduled = campaignRepository.findScheduledCampaigns();
            for (Map<String, AttributeValue> c : scheduled) {
                String campaignId = str(c, "campaign_id");
                String adminId = str(c, "admin_id");
                String scheduledAt = str(c, "scheduled_send_at");
                if (campaignId == null || scheduledAt == null || !isDue(scheduledAt, now)) {
                    continue;
                }
                try {
                    // SENDING 으로 먼저 전이해 다음 폴링에서 재선택되지 않게 한다.
                    campaignRepository.updateStatus(campaignId, "SENDING", nowIso);
                    campaignFanOutService.fanOutRecipients(campaignId, adminId);
                    log.info("[Scheduler] 예약 발송 트리거: campaignId={}", campaignId);
                } catch (Exception e) {
                    log.error("[Scheduler] 예약 발송 트리거 실패: campaignId={}", campaignId, e);
                }
            }
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }

    private boolean isDue(String scheduledAtIso, ZonedDateTime now) {
        try {
            return !ZonedDateTime.parse(scheduledAtIso).isAfter(now);
        } catch (Exception e) {
            log.warn("[Scheduler] scheduled_send_at 파싱 실패: {}", scheduledAtIso);
            return false;
        }
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }
}
