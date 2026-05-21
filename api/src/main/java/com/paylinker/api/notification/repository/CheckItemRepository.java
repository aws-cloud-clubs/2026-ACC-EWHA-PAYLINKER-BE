package com.paylinker.api.notification.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
@RequiredArgsConstructor
public class CheckItemRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

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
}
