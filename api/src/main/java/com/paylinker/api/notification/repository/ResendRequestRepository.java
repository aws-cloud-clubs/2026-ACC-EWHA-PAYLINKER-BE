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
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
@RequiredArgsConstructor
public class ResendRequestRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-resend-request";
    }

    public Optional<Map<String, AttributeValue>> findById(String requestId) {
        QueryResponse resp = dynamoDbClient.query(QueryRequest.builder()
                .tableName(tableName())
                .indexName("GSI3")
                .keyConditionExpression("request_id = :requestId")
                .expressionAttributeValues(Map.of(":requestId", AttributeValue.fromS(requestId)))
                .limit(1)
                .build());
        return resp.items().stream().findFirst();
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

    // status는 DynamoDB 예약어이므로 expressionAttributeNames 사용
    // ConditionExpression으로 레이스 컨디션 방지: REQUESTED 상태일 때만 갱신
    public TransactWriteItem updateToCompletedTxItem(String requestId, String processedBy, String processedAt) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("request_id", AttributeValue.fromS(requestId)))
                        .updateExpression("SET #s = :status, processed_by = :processedBy, processed_at = :processedAt")
                        .conditionExpression("#s = :requested")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(
                                ":status", AttributeValue.fromS("COMPLETED"),
                                ":processedBy", AttributeValue.fromS(processedBy),
                                ":processedAt", AttributeValue.fromS(processedAt),
                                ":requested", AttributeValue.fromS("REQUESTED")))
                        .build())
                .build();
    }

    public TransactWriteItem updateToRejectedTxItem(String requestId, String processedBy, String processedAt) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("request_id", AttributeValue.fromS(requestId)))
                        .updateExpression("SET #s = :status, processed_by = :processedBy, processed_at = :processedAt")
                        .conditionExpression("#s = :requested")
                        .expressionAttributeNames(Map.of("#s", "status"))
                        .expressionAttributeValues(Map.of(
                                ":status", AttributeValue.fromS("REJECTED"),
                                ":processedBy", AttributeValue.fromS(processedBy),
                                ":processedAt", AttributeValue.fromS(processedAt),
                                ":requested", AttributeValue.fromS("REQUESTED")))
                        .build())
                .build();
    }
}
