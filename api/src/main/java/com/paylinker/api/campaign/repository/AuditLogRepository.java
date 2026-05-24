package com.paylinker.api.campaign.repository;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

@Repository
@RequiredArgsConstructor
public class AuditLogRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-audit-log";
    }

    public void save(String auditLogId, String campaignId, String actionType, String createdAt) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName())
                .item(Map.of(
                        "audit_log_id", AttributeValue.fromS(auditLogId),
                        "campaign_id", AttributeValue.fromS(campaignId),
                        "action_type", AttributeValue.fromS(actionType),
                        "created_at", AttributeValue.fromS(createdAt)))
                .build());
    }
}
