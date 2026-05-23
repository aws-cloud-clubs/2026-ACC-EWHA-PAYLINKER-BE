package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;

import java.util.List;

@Repository
public class CampaignRepository {

    private final DynamoDbTable<PaylinkerCampaign> campaignTable;
    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<PaylinkerCampaignLimit> limitTable;
    private final DynamoDbTable<PaylinkerAuditLog> auditLogTable;

    public CampaignRepository(DynamoDbEnhancedClient enhancedClient) {
        this.campaignTable = enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));
        this.enhancedClient = enhancedClient;
        this.limitTable = enhancedClient.table("paylinker_campaign_limit", TableSchema.fromBean(PaylinkerCampaignLimit.class));
        this.auditLogTable = enhancedClient.table("paylinker_audit_log", TableSchema.fromBean(PaylinkerAuditLog.class));
    }

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = campaignTable.index(PaylinkerCampaign.INDEX_GSI1);

        // GSI1 PK 생성 규칙인 "ADMIN#{adminId}"를 활용하여 쿼리
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // 이름 중복 검사를 위한 메서드 (GSI1에서 해당 어드민의 캠페인 중 이름이 같은 것이 있는지 확인)
    public boolean existsByAdminIdAndCampaignName(String adminId, String campaignName) {
        return findAllByAdminId(adminId).stream()
                .anyMatch(c -> c.getCampaignName() != null && c.getCampaignName().equals(campaignName));
    }

    // 트랜잭션을 이용한 3개 테이블 동시 저장
    public void saveCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(campaignTable, campaign)
                .addPutItem(limitTable, limit)
                .addPutItem(auditLogTable, auditLog)
                .build();

        enhancedClient.transactWriteItems(request);
    }

    // 캠페인 단건 조회 (PK 기준)
    public PaylinkerCampaign findById(String campaignId) {
        return campaignTable.getItem(r -> r.key(k -> k.partitionValue(PaylinkerCampaign.pk(campaignId)).sortValue(PaylinkerCampaign.sk())));
    }

    // 캠페인 제한 단건 조회 (PK 기준)
    public PaylinkerCampaignLimit findLimitById(String campaignId) {
        return limitTable.getItem(r -> r.key(k -> k.partitionValue(PaylinkerCampaignLimit.pk(campaignId)).sortValue(PaylinkerCampaignLimit.SK_LIMIT)));
    }

    // 캠페인 수정 트랜잭션 (AuditLog 포함)
    public void updateCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(campaignTable, campaign)
                .addUpdateItem(limitTable, limit)
                .addPutItem(auditLogTable, auditLog)
                .build();

        enhancedClient.transactWriteItems(request);
    }
}