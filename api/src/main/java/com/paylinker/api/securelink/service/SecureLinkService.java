package com.paylinker.api.securelink.service;

import com.paylinker.api.campaign.repository.CampaignRecipientRepository;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCheckItem;
import com.paylinker.api.entity.PaylinkerResendRequest;
import com.paylinker.api.notification.repository.CheckItemRepository;
import com.paylinker.api.notification.repository.ResendRequestRepository;
import com.paylinker.api.securelink.dto.request.ResendSubmitRequest;
import com.paylinker.api.securelink.dto.response.ResendSubmitResponse;
import com.paylinker.api.securelink.repository.SecureLinkPublicRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItemsRequest;

@Service
@RequiredArgsConstructor
@Slf4j
public class SecureLinkService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;

    private final SecureLinkPublicRepository secureLinkPublicRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final ResendRequestRepository resendRequestRepository;
    private final CheckItemRepository checkItemRepository;
    private final DynamoDbClient dynamoDbClient;

    /**
     * A-5: 수신자가 에러 페이지에서 재전송 요청 제출.
     * <p>
     * 흐름:
     * 1. token → sha256 → SecureLink 조회 (없으면 LINK_INVALID)
     * 2. Campaign 조회 → allowResendRequest 체크 (RESEND_NOT_ALLOWED)
     * 3. CampaignRecipient 조회 → resendRequestLimit 초과 체크 (RESEND_LIMIT_EXCEEDED)
     * 4. 중복 REQUESTED 체크 (RESEND_REQUEST_DUPLICATE)
     * 5. TransactWrite: ResendRequest 저장 + resend_request_count++ + CheckItem 저장
     */
    public ResendSubmitResponse submitResendRequest(ResendSubmitRequest request) {
        String tokenHash = sha256(request.getToken());

        // 1. SecureLink 조회 — 만료/재사용 링크여도 요청은 수리 (mock 동작과 동일)
        Map<String, AttributeValue> link = secureLinkPublicRepository.findByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(ErrorCode.LINK_INVALID));

        String campaignId          = s(link, "campaign_id");
        String campaignRecipientId = s(link, "campaign_recipient_id");
        String secureLinkId        = s(link, "secure_link_id");

        // 2. Campaign 조회 → allowResendRequest 체크
        PaylinkerCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));

        if (Boolean.FALSE.equals(campaign.getAllowResendRequest())) {
            throw new CustomException(ErrorCode.RESEND_NOT_ALLOWED);
        }

        // 3. CampaignRecipient 조회 → resend count 및 recipientId 확보
        Map<String, AttributeValue> cr = campaignRecipientRepository
                .findByCrId(campaignId, campaignRecipientId)
                .orElseThrow(() -> new CustomException(ErrorCode.LINK_INVALID));

        // SK = RCP#<recipientId> → recipientId 파싱
        String sk          = s(cr, "SK");
        String recipientId = sk != null && sk.startsWith("RCP#") ? sk.substring(4) : s(cr, "recipient_id");

        int resendCount   = num(cr, "resend_request_count");
        int resendLimit   = campaign.getResendRequestLimit() != null ? campaign.getResendRequestLimit() : Integer.MAX_VALUE;

        if (resendCount >= resendLimit) {
            throw new CustomException(ErrorCode.RESEND_LIMIT_EXCEEDED);
        }

        // 4. 중복 REQUESTED 체크
        if (resendRequestRepository.existsPendingByRecipientId(recipientId, campaignId)) {
            throw new CustomException(ErrorCode.RESEND_REQUEST_DUPLICATE);
        }

        // 5. 저장
        String requestId   = "rr_" + UUID.randomUUID().toString().replace("-", "");
        String checkItemId = "chk_" + UUID.randomUUID().toString().replace("-", "");
        String now         = ZonedDateTime.now(KST).format(ISO_OFFSET);
        String adminId     = campaign.getAdminId();

        List<TransactWriteItem> txItems = new ArrayList<>();
        txItems.add(resendRequestRepository.saveTxItem(buildResendRequestItem(
                requestId, campaignId, campaignRecipientId, recipientId, secureLinkId, request.getReason(), now)));
        txItems.add(campaignRecipientRepository.incrementResendRequestCountTxItem(campaignId, recipientId));
        txItems.add(checkItemRepository.saveTxItem(buildCheckItem(
                checkItemId, campaignId, adminId, recipientId, requestId, now)));

        dynamoDbClient.transactWriteItems(TransactWriteItemsRequest.builder()
                .transactItems(txItems)
                .build());

        log.info("재전송 요청 접수 완료: requestId={}, campaignId={}, recipientId={}", requestId, campaignId, recipientId);

        return ResendSubmitResponse.builder()
                .requestId(requestId)
                .status("REQUESTED")
                .build();
    }

    // ── 항목 빌더 ──────────────────────────────────────────────────────────

    private Map<String, AttributeValue> buildResendRequestItem(
            String requestId, String campaignId, String campaignRecipientId,
            String recipientId, String secureLinkId, String reason, String now) {
        return Map.ofEntries(
                Map.entry("PK",                    AttributeValue.fromS(PaylinkerResendRequest.pk(campaignId))),
                Map.entry("SK",                    AttributeValue.fromS(PaylinkerResendRequest.sk(requestId))),
                Map.entry("request_id",            AttributeValue.fromS(requestId)),
                Map.entry("campaign_id",           AttributeValue.fromS(campaignId)),
                Map.entry("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)), // approve() 에서 필요
                Map.entry("recipient_id",          AttributeValue.fromS(recipientId)),
                Map.entry("secure_link_id",        AttributeValue.fromS(secureLinkId)),
                Map.entry("req_status",            AttributeValue.fromS("REQUESTED")),
                Map.entry("resend_reason",         AttributeValue.fromS(reason)),
                Map.entry("requested_at",          AttributeValue.fromS(now)),
                Map.entry("GSI1PK",                AttributeValue.fromS(PaylinkerResendRequest.gsi1Pk("REQUESTED"))),
                Map.entry("GSI1SK",                AttributeValue.fromS(now)),
                Map.entry("GSI2PK",                AttributeValue.fromS(PaylinkerResendRequest.gsi2Pk(recipientId)))
        );
    }

    private Map<String, AttributeValue> buildCheckItem(
            String checkItemId, String campaignId, String adminId,
            String recipientId, String requestId, String now) {
        return Map.ofEntries(
                Map.entry("PK",                 AttributeValue.fromS(PaylinkerCheckItem.pk(campaignId))),
                Map.entry("SK",                 AttributeValue.fromS(PaylinkerCheckItem.sk(checkItemId))),
                Map.entry("check_item_id",      AttributeValue.fromS(checkItemId)),
                Map.entry("campaign_id",        AttributeValue.fromS(campaignId)),
                Map.entry("admin_id",           AttributeValue.fromS(adminId)),
                Map.entry("recipient_id",       AttributeValue.fromS(recipientId)),
                Map.entry("item_type",          AttributeValue.fromS("RESEND_REQUEST")),
                Map.entry("check_status",       AttributeValue.fromS("OPEN")),
                Map.entry("related_request_id", AttributeValue.fromS(requestId)),
                Map.entry("created_at",         AttributeValue.fromS(now)),
                Map.entry("GSI1PK",             AttributeValue.fromS(PaylinkerCheckItem.gsi1Pk("OPEN"))),
                Map.entry("GSI1SK",             AttributeValue.fromS(now)),
                Map.entry("GSI2PK",             AttributeValue.fromS(PaylinkerCheckItem.gsi2Pk(adminId, "OPEN"))),
                Map.entry("GSI2SK",             AttributeValue.fromS(now))
        );
    }

    // ── 유틸 ───────────────────────────────────────────────────────────────

    private String sha256(String input) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 unavailable", e);
        }
    }

    private String s(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        return val != null ? val.s() : null;
    }

    private int num(Map<String, AttributeValue> item, String key) {
        AttributeValue val = item.get(key);
        if (val == null || val.n() == null) return 0;
        return Integer.parseInt(val.n());
    }
}
