package com.paylinker.api.notification.repository;

import com.paylinker.api.entity.PaylinkerCheckItem;
import com.paylinker.api.entity.enums.CheckItemStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
public class CheckItemRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tablePrefix;
    private final String tableName;

    // 생성자 방식을 겨레님 방식으로 변경, RequiredArgsConstructor 삭제
    public CheckItemRepository(DynamoDbClient dynamoDbClient,
                               @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {

        this.dynamoDbClient = dynamoDbClient;
        this.tablePrefix = tablePrefix;
        this.tableName = tablePrefix + "-check-item";
    }

    private String tableName() {
        return tablePrefix + "-check-item";
    }

    public List<Map<String, AttributeValue>> findAll() {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        ScanRequest req = ScanRequest.builder().tableName(tableName()).build();
        ScanResponse resp;
        do {
            resp = dynamoDbClient.scan(req);
            results.addAll(resp.items());
            if (resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }

    // related_request_id로 단일 check-item 조회 (승인/반려 처리 시 status 갱신용)
    public Optional<Map<String, AttributeValue>> findByRelatedRequestId(String requestId) {
        ScanRequest req = ScanRequest.builder()
                .tableName(tableName())
                .filterExpression("related_request_id = :requestId")
                .expressionAttributeValues(Map.of(":requestId", AttributeValue.fromS(requestId)))
                .build();
        ScanResponse resp = dynamoDbClient.scan(req);
        return resp.items().stream().findFirst();
    }

    public void updateCheckStatus(String checkItemId, String checkStatus) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("check_item_id", AttributeValue.fromS(checkItemId)))
                .updateExpression("SET check_status = :checkStatus")
                .expressionAttributeValues(Map.of(":checkStatus", AttributeValue.fromS(checkStatus)))
                .build());
    }

    public TransactWriteItem updateCheckStatusTxItem(String checkItemId, String checkStatus) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("check_item_id", AttributeValue.fromS(checkItemId)))
                        .updateExpression("SET check_status = :checkStatus")
                        .expressionAttributeValues(Map.of(":checkStatus", AttributeValue.fromS(checkStatus)))
                        .build())
                .build();
    }

    public int countUnresolved(String adminId) {
        return countByAdminAndStatus(adminId, CheckItemStatus.OPEN.name())
                + countByAdminAndStatus(adminId, CheckItemStatus.IN_PROGRESS.name());
    }

    private int countByAdminAndStatus(String adminId, String checkStatus) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(PaylinkerCheckItem.INDEX_GSI2)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "GSI2PK"))
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS(PaylinkerCheckItem.gsi2Pk(adminId, checkStatus))))
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