package com.paylinker.api.notification.repository;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

@Repository
@RequiredArgsConstructor
public class SecureLinkRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-secure-link";
    }

    // campaign_recipient_id 기반 스캔 (GSI 없을 시 풀스캔 + 필터)
    public Optional<Map<String, AttributeValue>> findActiveByRecipientId(String campaignRecipientId) {
        ScanRequest req = ScanRequest.builder()
                .tableName(tableName())
                .filterExpression("campaign_recipient_id = :recipientId AND link_status = :active")
                .expressionAttributeValues(Map.of(
                        ":recipientId", AttributeValue.fromS(campaignRecipientId),
                        ":active", AttributeValue.fromS("ACTIVE")))
                .build();
        ScanResponse resp = dynamoDbClient.scan(req);
        return resp.items().stream().findFirst();
    }

    public void invalidate(String secureLinkId) {
        dynamoDbClient.updateItem(UpdateItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("secure_link_id", AttributeValue.fromS(secureLinkId)))
                .updateExpression("SET link_status = :invalidated")
                .expressionAttributeValues(Map.of(":invalidated", AttributeValue.fromS("INVALIDATED")))
                .build());
    }

    public void save(String secureLinkId, String campaignRecipientId, String campaignId,
                     String tokenHash, String expiresAt) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName())
                .item(Map.of(
                        "secure_link_id", AttributeValue.fromS(secureLinkId),
                        "campaign_recipient_id", AttributeValue.fromS(campaignRecipientId),
                        "campaign_id", AttributeValue.fromS(campaignId),
                        "token_hash", AttributeValue.fromS(tokenHash),
                        "link_status", AttributeValue.fromS("ACTIVE"),
                        "expires_at", AttributeValue.fromS(expiresAt)))
                .build());
    }
}
