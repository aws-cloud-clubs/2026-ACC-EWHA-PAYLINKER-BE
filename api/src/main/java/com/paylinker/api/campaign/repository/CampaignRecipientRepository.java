package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;

@Repository
public class CampaignRecipientRepository {

    private static final int DYNAMO_BATCH_LIMIT = 25;
    private final DynamoDbClient dynamoDbClient;
    private final String tablePrefix;
    private final String gsi1Name;
    private final String gsi2Name;
    private final String tableName;
    private final DynamoDbIndex<PaylinkerCampaignRecipient> gsi1;
    private final DynamoDbIndex<PaylinkerCampaignRecipient> gsi2;

    public CampaignRecipientRepository(DynamoDbEnhancedClient enhancedClient,
                                       DynamoDbClient dynamoDbClient,
                                       @Value("${aws.dynamodb.table-prefix}") String tablePrefix,
                                       @Value("${aws.dynamodb.campaign-recipient.gsi1-name}") String gsi1Name,
                                       @Value("${aws.dynamodb.campaign-recipient.gsi2-name}") String gsi2Name) {

        this.dynamoDbClient = dynamoDbClient;
        this.tablePrefix = tablePrefix;
        this.gsi1Name = gsi1Name;
        this.gsi2Name = gsi2Name;
        this.tableName = tablePrefix + "-campaign-recipient";
        DynamoDbTable<PaylinkerCampaignRecipient> table = enhancedClient.table(
                this.tableName,
                TableSchema.fromBean(PaylinkerCampaignRecipient.class));
        this.gsi1 = table.index(PaylinkerCampaignRecipient.INDEX_GSI1);
        this.gsi2 = table.index(PaylinkerCampaignRecipient.INDEX_GSI2);
    }

    private String tableName() {
        return tablePrefix + "-campaign-recipient";
    }

    public List<Map<String, AttributeValue>> findByCampaignId(String campaignId) {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        QueryRequest req = QueryRequest.builder()
                .tableName(tableName())
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", "PK"))
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId))))
                .build();
        QueryResponse resp;
        do {
            resp = dynamoDbClient.query(req);
            results.addAll(resp.items());
            if (!resp.hasLastEvaluatedKey() || resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }

    public void deleteAllByCampaignId(String campaignId) {
        List<Map<String, AttributeValue>> keys = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            QueryRequest.Builder reqBuilder = QueryRequest.builder()
                    .tableName(tableName())
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "PK"))
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId))))
                    .projectionExpression("PK, SK");
            if (lastKey != null) reqBuilder.exclusiveStartKey(lastKey);

            QueryResponse resp = dynamoDbClient.query(reqBuilder.build());
            keys.addAll(resp.items());
            lastKey = resp.hasLastEvaluatedKey() ? resp.lastEvaluatedKey() : null;
        } while (lastKey != null);

        partitioned(keys, DYNAMO_BATCH_LIMIT).forEach(batch -> {
            List<WriteRequest> deletes = batch.stream()
                    .map(k -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder().key(Map.of(
                                    "PK", k.get("PK"),
                                    "SK", k.get("SK"))).build())
                            .build())
                    .collect(Collectors.toList());
            dynamoDbClient.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName(), deletes))
                    .build());
        });
    }

    public void saveAll(List<Map<String, AttributeValue>> items) {
        partitioned(items, DYNAMO_BATCH_LIMIT).forEach(batch -> {
            List<WriteRequest> puts = batch.stream()
                    .map(item -> WriteRequest.builder()
                            .putRequest(PutRequest.builder().item(item).build())
                            .build())
                    .collect(Collectors.toList());
            dynamoDbClient.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName(), puts))
                    .build());
        });
    }

    private <T> List<List<T>> partitioned(List<T> list, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < list.size(); i += size) {
            result.add(list.subList(i, Math.min(i + size, list.size())));
        }
        return result;
    }

    public Optional<Map<String, AttributeValue>> findById(String campaignRecipientId) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }

    /** GSI2(GSI2PK = CAMPAIGN#{id}#VW#0): 미열람 수신자 전체 조회 */
    public List<Map<String, AttributeValue>> findUnviewedByCampaignId(String campaignId) {
        return queryByGsiPk(gsi2Name, "GSI2PK", "CAMPAIGN#" + campaignId + "#VW#0");
    }

    /** GSI1(GSI1PK = CAMPAIGN#{id}#ST#FAILED): 발송 실패 수신자 전체 조회 */
    public List<Map<String, AttributeValue>> findFailedByCampaignId(String campaignId) {
        return queryByGsiPk(gsi1Name, "GSI1PK", "CAMPAIGN#" + campaignId + "#ST#FAILED");
    }

    /** reminder_count 증가 + last_reminder_sent_at 갱신 트랜잭션 아이템 */
    public TransactWriteItem incrementReminderCountTxItem(String campaignRecipientId, String lastReminderSentAt) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                        .updateExpression("SET reminder_count = if_not_exists(reminder_count, :zero) + :one," +
                                " last_reminder_sent_at = :sentAt")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.fromN("0"),
                                ":one", AttributeValue.fromN("1"),
                                ":sentAt", AttributeValue.fromS(lastReminderSentAt)))
                        .build())
                .build();
    }

    /** retry_count 증가 + send_status = RETRYING 갱신 트랜잭션 아이템 */
    public TransactWriteItem updateToRetryingTxItem(String campaignRecipientId) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                        .updateExpression("SET retry_count = if_not_exists(retry_count, :zero) + :one," +
                                " send_status = :retrying")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.fromN("0"),
                                ":one", AttributeValue.fromN("1"),
                                ":retrying", AttributeValue.fromS("RETRYING")))
                        .build())
                .build();
    }

    /**
     * campaignId + campaignRecipientId 로 단건 조회.
     * PK = CAMPAIGN#<campaignId> 로 query 후 campaign_recipient_id 필터.
     */
    public Optional<Map<String, AttributeValue>> findByCrId(String campaignId, String campaignRecipientId) {
        QueryRequest req = QueryRequest.builder()
                .tableName(tableName())
                .keyConditionExpression("#pk = :pk")
                .filterExpression("campaign_recipient_id = :crId")
                .expressionAttributeNames(Map.of("#pk", "PK"))
                .expressionAttributeValues(Map.of(
                        ":pk",   AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                        ":crId", AttributeValue.fromS(campaignRecipientId)))
                .build();
        QueryResponse resp = dynamoDbClient.query(req);
        return resp.items().stream().findFirst();
    }

    /**
     * resend_request_count 1 증가 트랜잭션 아이템.
     * PK = CAMPAIGN#<campaignId>, SK = RCP#<recipientId>
     */
    public TransactWriteItem incrementResendRequestCountTxItem(String campaignId, String recipientId) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of(
                                "PK", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                                "SK", AttributeValue.fromS(PaylinkerCampaignRecipient.sk(recipientId))))
                        .updateExpression(
                                "SET resend_request_count = if_not_exists(resend_request_count, :zero) + :one")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.fromN("0"),
                                ":one",  AttributeValue.fromN("1")))
                        .build())
                .build();
    }

    private List<Map<String, AttributeValue>> queryByGsiPk(
            String indexName, String pkAttrName, String pkValue) {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        QueryRequest req = QueryRequest.builder()
                .tableName(tableName())
                .indexName(indexName)
                .keyConditionExpression("#pk = :pkVal")
                .expressionAttributeNames(Map.of("#pk", pkAttrName))
                .expressionAttributeValues(Map.of(":pkVal", AttributeValue.fromS(pkValue)))
                .build();
        QueryResponse resp;
        do {
            resp = dynamoDbClient.query(req);
            results.addAll(resp.items());
            if (!resp.hasLastEvaluatedKey() || resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }

    public List<PaylinkerCampaignRecipient> findUnviewedPreviews(String campaignId, int limit) {
        return queryIndex(gsi2,
                PaylinkerCampaignRecipient.gsi2Pk(campaignId, PaylinkerCampaignRecipient.VIEWED_FALSE),
                limit);
    }

    public int countUnviewed(String campaignId) {
        return countByGsiPk("GSI2PK",
                PaylinkerCampaignRecipient.gsi2Pk(campaignId, PaylinkerCampaignRecipient.VIEWED_FALSE),
                PaylinkerCampaignRecipient.INDEX_GSI2);
    }

    public List<PaylinkerCampaignRecipient> findFailedPreviews(String campaignId, int limit) {
        return queryIndex(gsi1,
                PaylinkerCampaignRecipient.gsi1Pk(campaignId, PaylinkerCampaignRecipient.SEND_STATUS_FAILED),
                limit);
    }

    public int countFailed(String campaignId) {
        return countByGsiPk("GSI1PK",
                PaylinkerCampaignRecipient.gsi1Pk(campaignId, PaylinkerCampaignRecipient.SEND_STATUS_FAILED),
                PaylinkerCampaignRecipient.INDEX_GSI1);
    }

    private List<PaylinkerCampaignRecipient> queryIndex(DynamoDbIndex<PaylinkerCampaignRecipient> index,
                                                        String partitionValue,
                                                        int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(partitionValue).build()))
                .scanIndexForward(true)
                .limit(limit)
                .build();
        return StreamSupport.stream(index.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int countByGsiPk(String pkAttribute, String partitionValue, String indexName) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(indexName)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", pkAttribute))
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(partitionValue)))
                    .select(Select.COUNT);
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            QueryResponse response = dynamoDbClient.query(builder.build());
            total += response.count();
            exclusiveStartKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
        return total;
    }
}