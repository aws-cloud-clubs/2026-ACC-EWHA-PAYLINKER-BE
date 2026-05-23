package com.paylinker.api.campaign.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.DeleteRequest;
import software.amazon.awssdk.services.dynamodb.model.KeysAndAttributes;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;

import java.util.stream.Collectors;

@Repository
@RequiredArgsConstructor
public class CampaignRecipientRepository {

    private static final int DYNAMO_BATCH_LIMIT = 25;

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

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
                            .deleteRequest(DeleteRequest.builder().key(Map.of(
                                    "campaign_id", k.get("campaign_id"),
                                    "employee_no", k.get("employee_no"))).build())
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
}
