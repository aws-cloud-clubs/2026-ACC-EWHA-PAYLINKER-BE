package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@Repository
@RequiredArgsConstructor
public class CampaignRepository {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;

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

    public DynamoDbTable<PaylinkerCampaign> getCampaignTable() {
        return enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));
    }

    public DynamoDbTable<PaylinkerCampaignLimit> getLimitTable() {
        return enhancedClient.table("paylinker_campaign_limit", TableSchema.fromBean(PaylinkerCampaignLimit.class));
    }

    public DynamoDbTable<PaylinkerAuditLog> getAuditLogTable() {
        return enhancedClient.table("paylinker_audit_log", TableSchema.fromBean(PaylinkerAuditLog.class));
    }

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = getCampaignTable().index(PaylinkerCampaign.INDEX_GSI1);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    public boolean existsByAdminIdAndCampaignName(String adminId, String campaignName) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = getCampaignTable().index(PaylinkerCampaign.INDEX_GSI1);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        Expression filterExpression = Expression.builder()
                .expression("campaign_name = :name")
                .expressionValues(Map.of(":name", AttributeValue.fromS(campaignName)))
                .build();

        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(1)
                .build();

        return gsi1.query(request).stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .isPresent();
    }

    public void saveCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(getCampaignTable(), campaign)
                .addPutItem(getLimitTable(), limit)
                .addPutItem(getAuditLogTable(), auditLog)
                .build();

        executeTransaction(request);
    }

    public PaylinkerCampaign findCampaignById(String campaignId) {
        return getCampaignTable().getItem(r -> r.key(k ->
                k.partitionValue(PaylinkerCampaign.pk(campaignId)).sortValue(PaylinkerCampaign.sk())));
    }

    public PaylinkerCampaignLimit findLimitById(String campaignId) {
        return getLimitTable().getItem(r -> r.key(k ->
                k.partitionValue(PaylinkerCampaignLimit.pk(campaignId)).sortValue(PaylinkerCampaignLimit.SK_LIMIT)));
    }

    public void executeTransaction(TransactWriteItemsEnhancedRequest request) {
        enhancedClient.transactWriteItems(request);
    }
}