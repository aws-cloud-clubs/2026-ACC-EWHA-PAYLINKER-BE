package com.paylinker.api.document.service;

import com.paylinker.api.auth.LinkSession;
import com.paylinker.api.document.dto.ViewedResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import com.paylinker.api.entity.PaylinkerSecureLink;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

/**
 * 수신자 확인 처리 (DOC-102 / A-4).
 *
 * 멱등성 보장 전략:
 * - campaign_recipient.is_viewed 에 conditional update 를 걸어 첫 회만 통과시킨다.
 * - 통과한 호출만 campaign.viewedCount/unviewedCount 와 secure_link 의 first-access 필드를 갱신.
 * - secure_link.access_count 는 매 호출마다 +1.
 * - allow_one_time_link === true && 첫 회 일 때만 link_status = USED 로 전이.
 */
@Service
public class RecipientViewedService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final DynamoDbClient dynamoDbClient;
    private final String tablePrefix;

    public RecipientViewedService(DynamoDbClient dynamoDbClient,
                                  @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.dynamoDbClient = dynamoDbClient;
        this.tablePrefix = tablePrefix;
    }

    public ViewedResponse markViewed(LinkSession session) {
        String campaignId = session.campaignId();
        String campaignRecipientId = session.campaignRecipientId();
        String tokenHash = sha256Hex(session.token());
        String nowIso = ZonedDateTime.now(KST).format(ISO_OFFSET);

        // 1. campaign 조회 (allow_one_time_link 확인)
        Map<String, AttributeValue> campaign = findCampaign(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }
        boolean allowOneTimeLink = Boolean.TRUE.equals(bool(campaign, "allow_one_time_link"));

        // 2. campaign_recipient 조건부 update — 첫 회만 통과
        boolean firstView = tryMarkRecipientViewed(campaignId, campaignRecipientId, nowIso);
        String viewedAtIso;

        if (firstView) {
            // 첫 회: campaign 카운터 + secure_link 첫 접속 정보 갱신
            incrementCampaignViewCount(campaignId);
            updateSecureLinkOnFirstView(tokenHash, allowOneTimeLink, nowIso);
            viewedAtIso = nowIso;
        } else {
            // 이미 viewed: campaign_recipient 의 기존 first_viewed_at 반환, secure_link 는 access_count 만 증가
            viewedAtIso = readFirstViewedAt(campaignId, campaignRecipientId);
            incrementSecureLinkAccessCount(tokenHash);
        }

        return new ViewedResponse(viewedAtIso);
    }

    // ── 멱등 핵심: campaign_recipient 조건부 update ────────────────────────

    private boolean tryMarkRecipientViewed(String campaignId, String campaignRecipientId, String nowIso) {
        String newGsi2Pk = "CAMPAIGN#" + campaignId + "#VW#" + PaylinkerCampaignRecipient.VIEWED_TRUE;
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":t", AttributeValue.fromBool(true));
        values.put(":now", AttributeValue.fromS(nowIso));
        values.put(":g", AttributeValue.fromS(newGsi2Pk));
        values.put(":f", AttributeValue.fromBool(false));
        try {
            dynamoDbClient.updateItem(UpdateItemRequest.builder()
                    .tableName(tablePrefix + "-campaign-recipient")
                    .key(Map.of(
                            "PK", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                            "SK", AttributeValue.fromS(PaylinkerCampaignRecipient.sk(campaignRecipientId))))
                    .updateExpression("SET is_viewed = :t, first_viewed_at = :now, GSI2PK = :g")
                    .conditionExpression("attribute_not_exists(is_viewed) OR is_viewed = :f")
                    .expressionAttributeValues(values)
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }

    private void incrementCampaignViewCount(String campaignId) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":one", AttributeValue.fromN("1"));
        values.put(":zero", AttributeValue.fromN("0"));
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tablePrefix + "-campaign")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaign.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaign.sk())))
                .updateExpression("SET viewed_count = if_not_exists(viewed_count, :zero) + :one,"
                        + " unviewed_count = if_not_exists(unviewed_count, :zero) - :one")
                .expressionAttributeValues(values)
                .build());
    }

    // ── secure_link 갱신 ─────────────────────────────────────────

    private void updateSecureLinkOnFirstView(String tokenHash, boolean allowOneTimeLink, String nowIso) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":now", AttributeValue.fromS(nowIso));
        values.put(":one", AttributeValue.fromN("1"));
        values.put(":zero", AttributeValue.fromN("0"));
        String updateExpr =
                "SET first_accessed_at = if_not_exists(first_accessed_at, :now),"
                + " access_count = if_not_exists(access_count, :zero) + :one";
        if (allowOneTimeLink) {
            updateExpr += ", link_status = :used, used_at = if_not_exists(used_at, :now)";
            values.put(":used", AttributeValue.fromS("USED"));
        }
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tablePrefix + "-secure-link")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerSecureLink.pk(tokenHash)),
                        "SK", AttributeValue.fromS(PaylinkerSecureLink.SK_METADATA)))
                .updateExpression(updateExpr)
                .expressionAttributeValues(values)
                .build());
    }

    private void incrementSecureLinkAccessCount(String tokenHash) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":one", AttributeValue.fromN("1"));
        values.put(":zero", AttributeValue.fromN("0"));
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tablePrefix + "-secure-link")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerSecureLink.pk(tokenHash)),
                        "SK", AttributeValue.fromS(PaylinkerSecureLink.SK_METADATA)))
                .updateExpression("SET access_count = if_not_exists(access_count, :zero) + :one")
                .expressionAttributeValues(values)
                .build());
    }

    // ── lookups ─────────────────────────────────────────────────

    private Map<String, AttributeValue> findCampaign(String campaignId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaign.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaign.sk())))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private String readFirstViewedAt(String campaignId, String campaignRecipientId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign-recipient")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaignRecipient.sk(campaignRecipientId))))
                .build());
        if (!resp.hasItem()) return null;
        AttributeValue v = resp.item().get("first_viewed_at");
        return v == null ? null : v.s();
    }

    // ── helpers ─────────────────────────────────────────────────

    private static Boolean bool(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        return v == null ? null : v.bool();
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
}
