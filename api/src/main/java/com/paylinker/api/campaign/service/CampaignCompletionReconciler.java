package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;

/**
 * 캠페인 완료 집계 폴러.
 * SENDING 상태 캠페인의 모든 수신자가 종료 상태에 도달하면 SENT(실패 0) 또는
 * PARTIAL_FAILED(실패 1건 이상)로 전이하고, 성공/실패 카운트와 완료 시각을 기록한다.
 * SKIPPED(수신거부)는 성공/실패 어느 쪽에도 넣지 않는다.
 * 다중 인스턴스 중복 집계는 Redis 락으로 방지.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CampaignCompletionReconciler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final String LOCK_KEY = "paylinker:scheduler:completion:lock";
    // 하나라도 있으면 캠페인 미완료로 간주하는 진행 중 수신자 상태
    private static final Set<String> IN_PROGRESS = Set.of("QUEUED", "SENDING", "RETRYING", "REQUESTED");

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final StringRedisTemplate redisTemplate;

    @Value("${paylinker.scheduler.enabled:true}")
    private boolean enabled;

    @Value("${paylinker.link.scheduler-lock-ttl-seconds:50}")
    private long lockTtlSeconds;

    @Scheduled(fixedDelayString = "${paylinker.scheduler.completion-poll-interval-ms:60000}")
    public void reconcile() {
        if (!enabled) {
            return;
        }
        Boolean locked = redisTemplate.opsForValue()
                .setIfAbsent(LOCK_KEY, "1", Duration.ofSeconds(lockTtlSeconds));
        if (!Boolean.TRUE.equals(locked)) {
            return; // 다른 인스턴스가 처리 중
        }
        try {
            String now = ZonedDateTime.now(KST).format(ISO_OFFSET);
            for (Map<String, AttributeValue> campaign : campaignRepository.findByStatus("SENDING")) {
                String campaignId = str(campaign, "campaign_id");
                if (campaignId != null) {
                    reconcileOne(campaignId, now);
                }
            }
        } finally {
            redisTemplate.delete(LOCK_KEY);
        }
    }

    private void reconcileOne(String campaignId, String now) {
        try {
            List<Map<String, AttributeValue>> recipients =
                    campaignRecipientRepository.findByCampaignId(campaignId);
            if (recipients.isEmpty()) {
                return;
            }
            int success = 0;
            int skipped = 0;
            for (Map<String, AttributeValue> r : recipients) {
                String st = str(r, "send_status");
                if (st == null || IN_PROGRESS.contains(st)) {
                    return; // 아직 진행 중인 수신자가 있으므로 완료 아님
                }
                if ("SUCCESS".equals(st)) {
                    success++;
                } else if ("SKIPPED".equals(st)) {
                    skipped++;
                }
            }
            int total = recipients.size();
            int failed = total - success - skipped;
            String status = failed > 0 ? "PARTIAL_FAILED" : "SENT";
            campaignRepository.completeCampaign(campaignId, status, now, success, failed);
            log.info("[Completion] campaignId={} -> {} (total={}, success={}, failed={}, skipped={})",
                    campaignId, status, total, success, failed, skipped);
        } catch (Exception e) {
            log.error("[Completion] 집계 실패: campaignId={}", campaignId, e);
        }
    }

    private String str(Map<String, AttributeValue> item, String key) {
        AttributeValue v = item.get(key);
        return v != null ? v.s() : null;
    }
}
