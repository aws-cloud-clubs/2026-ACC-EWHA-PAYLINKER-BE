package com.paylinker.api.secure.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.auth.LinkSession;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import com.paylinker.api.entity.PaylinkerDocumentMatch;
import com.paylinker.api.entity.PaylinkerSecureLink;
import com.paylinker.api.secure.dto.LinkSessionResponse;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * 수신자 보안 링크 검증 + LinkSession 발급 (LNK-001 / A-2).
 *
 * 입력 plain token 을 sha256 으로 변환해 secure_link 테이블의 PK 와 매칭한다.
 * 유효한 경우 ls_ 접두사 Bearer 토큰을 Redis 에 TTL 과 함께 저장하고 응답한다.
 */
@Service
public class LinkValidationService {

    private static final String LS_PREFIX = "ls_";
    private static final String REDIS_KEY_PREFIX = "paylinker:linksession:";
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final DynamoDbClient dynamoDbClient;
    private final StringRedisTemplate redis;
    private final ObjectMapper mapper;
    private final String tablePrefix;
    private final long sessionTtlSeconds;

    public LinkValidationService(DynamoDbClient dynamoDbClient,
                                 StringRedisTemplate redis,
                                 ObjectMapper mapper,
                                 @Value("${aws.dynamodb.table-prefix}") String tablePrefix,
                                 @Value("${paylinker.link.session-ttl-seconds}") long sessionTtlSeconds) {
        this.dynamoDbClient = dynamoDbClient;
        this.redis = redis;
        this.mapper = mapper;
        this.tablePrefix = tablePrefix;
        this.sessionTtlSeconds = sessionTtlSeconds;
    }

    public LinkSessionResponse validateAndIssue(String plainToken) {
        if (plainToken == null || plainToken.isBlank()) {
            throw new CustomException(ErrorCode.LINK_INVALID);
        }
        String tokenHash = sha256Hex(plainToken);

        // 1. secure_link 조회 (PK = TOKEN#<hash>, SK = METADATA)
        Map<String, AttributeValue> secureLink = findSecureLink(tokenHash);
        if (secureLink == null) {
            throw new CustomException(ErrorCode.LINK_INVALID);
        }

        // 2. INVALIDATED 상태 검사
        String linkStatus = str(secureLink, "link_status");
        if ("INVALIDATED".equals(linkStatus)) {
            throw new CustomException(ErrorCode.LINK_INVALIDATED);
        }

        // 3. expires_at 코드 비교 검증
        String expiresAtStr = str(secureLink, "expires_at");
        Instant expiresAt = parseInstantOrNull(expiresAtStr);
        if (expiresAt == null || Instant.now().isAfter(expiresAt)) {
            throw new CustomException(ErrorCode.LINK_EXPIRED);
        }

        String campaignId = str(secureLink, "campaign_id");
        String campaignRecipientId = str(secureLink, "campaign_recipient_id");
        if (campaignId == null || campaignRecipientId == null) {
            throw new CustomException(ErrorCode.LINK_INVALID);
        }

        // 4. campaign 조회 (allow_one_time_link 확인)
        Map<String, AttributeValue> campaign = findCampaign(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.LINK_INVALID);
        }
        boolean allowOneTimeLink = Boolean.TRUE.equals(bool(campaign, "allow_one_time_link"));

        // 5. allow_one_time_link 인 캠페인은 USED 재사용 차단
        if (allowOneTimeLink && "USED".equals(linkStatus)) {
            throw new CustomException(ErrorCode.LINK_REUSED);
        }

        // 6. campaign_recipient 조회 (recipientName)
        Map<String, AttributeValue> recipient = findCampaignRecipient(campaignId, campaignRecipientId);
        if (recipient == null) {
            throw new CustomException(ErrorCode.LINK_INVALID);
        }

        // 7. document_match 단건 — 매칭된 명세서가 있어야 의미 있는 세션
        Map<String, AttributeValue> match = findDocumentMatch(campaignId, campaignRecipientId);
        int documentCount = (match != null
                && PaylinkerDocumentMatch.STATUS_MATCHED.equals(str(match, "match_status")))
                ? 1 : 0;

        // 8. LinkSession 생성 + Redis 저장
        String sessionToken = generateSessionToken();
        Instant issuedAt = Instant.now();
        Instant sessionExpiresAt = issuedAt.plusSeconds(sessionTtlSeconds);
        LinkSession session = new LinkSession(
                sessionToken, campaignRecipientId, campaignId,
                str(recipient, "email"), sessionExpiresAt);
        persistSession(session);

        // 9. 응답
        return LinkSessionResponse.builder()
                .linkSessionToken(sessionToken)
                .campaignRecipientId(campaignRecipientId)
                .campaignId(campaignId)
                .campaignName(str(campaign, "campaign_name"))
                .recipientName(str(recipient, "name"))
                .documentCount(documentCount)
                .issuedAt(formatIso(issuedAt))
                .expiresAt(formatIso(sessionExpiresAt))
                .build();
    }

    // ── lookups ────────────────────────────────────────────────

    private Map<String, AttributeValue> findSecureLink(String tokenHash) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-secure-link")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerSecureLink.pk(tokenHash)),
                        "SK", AttributeValue.fromS(PaylinkerSecureLink.SK_METADATA)))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findCampaign(String campaignId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaign.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaign.sk())))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findCampaignRecipient(String campaignId, String campaignRecipientId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign-recipient")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaignRecipient.sk(campaignRecipientId))))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findDocumentMatch(String campaignId, String campaignRecipientId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-document-match")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerDocumentMatch.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerDocumentMatch.sk(campaignRecipientId))))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    // ── Redis persistence ──────────────────────────────────────

    private void persistSession(LinkSession session) {
        try {
            String json = mapper.writeValueAsString(session);
            redis.opsForValue().set(
                    REDIS_KEY_PREFIX + session.token(),
                    json,
                    Duration.ofSeconds(sessionTtlSeconds));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("LinkSession 직렬화 실패", e);
        }
    }

    // ── helpers ────────────────────────────────────────────────

    private static String generateSessionToken() {
        byte[] bytes = new byte[24];
        SECURE_RANDOM.nextBytes(bytes);
        return LS_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String sha256Hex(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private static Instant parseInstantOrNull(String iso) {
        if (iso == null || iso.isBlank()) return null;
        try {
            return OffsetDateTime.parse(iso, DateTimeFormatter.ISO_OFFSET_DATE_TIME).toInstant();
        } catch (Exception e) {
            return null;
        }
    }

    private static String formatIso(Instant instant) {
        return instant.atOffset(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
    }

    private static String str(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        return v == null ? null : v.s();
    }

    private static Boolean bool(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        return v == null ? null : v.bool();
    }
}
