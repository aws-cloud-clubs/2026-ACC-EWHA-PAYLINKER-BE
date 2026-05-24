package com.paylinker.api.campaign.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

@Repository
@RequiredArgsConstructor
public class CampaignRecipientRepository {

    private static final int DYNAMO_BATCH_LIMIT = 25;

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    /**
     * GSI1: gsi1_pk = CAMPAIGN#{campaignId}#ST#{send_status}
     * 인덱스 이름은 aws.dynamodb.campaign-recipient.gsi1-name 으로 주입
     */
    @Value("${aws.dynamodb.campaign-recipient.gsi1-name}")
    private String gsi1Name;

    /**
     * GSI2: gsi2_pk = CAMPAIGN#{campaignId}#VW#{viewed}
     * 인덱스 이름은 aws.dynamodb.campaign-recipient.gsi2-name 으로 주입
     */
    @Value("${aws.dynamodb.campaign-recipient.gsi2-name}")
    private String gsi2Name;

    private String tableName() {
        return tablePrefix + "-campaign-recipient";
    }

    /**
     * 캠페인의 기존 수신자를 전부 삭제한다 (FULL_REPLACE 모드).
     */
    public void deleteAllByCampaignId(String campaignId) {
        List<Map<String, AttributeValue>> keys = new ArrayList<>();
        Map<String, AttributeValue> lastKey = null;

        do {
            QueryRequest.Builder reqBuilder = QueryRequest.builder()
                    .tableName(tableName())
                    .keyConditionExpression("campaign_id = :cid")
                    .expressionAttributeValues(Map.of(":cid", AttributeValue.fromS(campaignId)))
                    .projectionExpression("campaign_id, employee_no");
            if (lastKey != null) reqBuilder.exclusiveStartKey(lastKey);

            QueryResponse resp = dynamoDbClient.query(reqBuilder.build());
            keys.addAll(resp.items());
            lastKey = resp.hasLastEvaluatedKey() ? resp.lastEvaluatedKey() : null;
        } while (lastKey != null);

        partitioned(keys, DYNAMO_BATCH_LIMIT).forEach(batch -> {
            List<WriteRequest> deletes = batch.stream()
                    .map(k -> WriteRequest.builder()
                            .deleteRequest(DeleteRequest.builder()
                                    .key(Map.of(
                                            "campaign_id", k.get("campaign_id"),
                                            "employee_no", k.get("employee_no")))
                                    .build())
                            .build())
                    .collect(Collectors.toList());
            dynamoDbClient.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName(), deletes))
                    .build());
        });
    }

    /**
     * 수신자 행 목록을 DynamoDB에 일괄 저장한다.
     */
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

    /** GSI2(gsi2_pk = CAMPAIGN#{id}#VW#0): 미열람 수신자 전체 조회 */
    public List<Map<String, AttributeValue>> findUnviewedByCampaignId(String campaignId) {
        return queryByGsiPk(gsi2Name, "gsi2_pk", "CAMPAIGN#" + campaignId + "#VW#0");
    }

    /** GSI1(gsi1_pk = CAMPAIGN#{id}#ST#FAILED): 발송 실패 수신자 전체 조회 */
    public List<Map<String, AttributeValue>> findFailedByCampaignId(String campaignId) {
        return queryByGsiPk(gsi1Name, "gsi1_pk", "CAMPAIGN#" + campaignId + "#ST#FAILED");
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
}
