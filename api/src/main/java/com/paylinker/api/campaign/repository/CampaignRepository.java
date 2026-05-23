package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@Repository
@RequiredArgsConstructor
public class CampaignRepository {
    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-campaign";
    }

    public Optional<Map<String, AttributeValue>> findById(String campaignId) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("campaign_id", AttributeValue.fromS(campaignId)))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }
    
    private final DynamoDbEnhancedClient enhancedClient;

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        // 테이블 객체를 전역 변수로 두지 않고 메서드 내부에서 동적 생성하여 구조 충돌 방지
        DynamoDbTable<PaylinkerCampaign> campaignTable =
                enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));

        DynamoDbIndex<PaylinkerCampaign> gsi1 = campaignTable.index(PaylinkerCampaign.INDEX_GSI1);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // 이름 중복 검사를 위한 메서드
    public boolean existsByAdminIdAndCampaignName(String adminId, String campaignName) {
        return findAllByAdminId(adminId).stream()
                .anyMatch(c -> c.getCampaignName() != null && c.getCampaignName().equals(campaignName));
    }

    // 트랜잭션을 이용한 3개 테이블 동시 저장
    public void saveCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        // 테이블 객체를 메서드 내부에서 동적 생성
        DynamoDbTable<PaylinkerCampaign> campaignTable = enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));
        DynamoDbTable<PaylinkerCampaignLimit> limitTable = enhancedClient.table("paylinker_campaign_limit", TableSchema.fromBean(PaylinkerCampaignLimit.class));
        DynamoDbTable<PaylinkerAuditLog> auditLogTable = enhancedClient.table("paylinker_audit_log", TableSchema.fromBean(PaylinkerAuditLog.class));

        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(campaignTable, campaign)
                .addPutItem(limitTable, limit)
                .addPutItem(auditLogTable, auditLog)
                .build();

        enhancedClient.transactWriteItems(request);
    }
}