package com.paylinker.api.notification.repository;

import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.Put;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;

@Repository
@RequiredArgsConstructor
public class SendJobRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-send-job";
    }

    public void save(String sendJobId, String campaignRecipientId, String campaignId,
                     String jobType, String secureLinkId, String createdAt) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName())
                .item(Map.of(
                        "send_job_id", AttributeValue.fromS(sendJobId),
                        "campaign_recipient_id", AttributeValue.fromS(campaignRecipientId),
                        "campaign_id", AttributeValue.fromS(campaignId),
                        "job_type", AttributeValue.fromS(jobType),
                        "secure_link_id", AttributeValue.fromS(secureLinkId),
                        "status", AttributeValue.fromS("QUEUED"),
                        "created_at", AttributeValue.fromS(createdAt)))
                .build());
    }

    public TransactWriteItem saveTxItem(String sendJobId, String campaignRecipientId, String campaignId,
                                        String jobType, String secureLinkId, String createdAt) {
        return TransactWriteItem.builder()
                .put(Put.builder()
                        .tableName(tableName())
                        .item(Map.of(
                                "send_job_id", AttributeValue.fromS(sendJobId),
                                "campaign_recipient_id", AttributeValue.fromS(campaignRecipientId),
                                "campaign_id", AttributeValue.fromS(campaignId),
                                "job_type", AttributeValue.fromS(jobType),
                                "secure_link_id", AttributeValue.fromS(secureLinkId),
                                "status", AttributeValue.fromS("QUEUED"),
                                "created_at", AttributeValue.fromS(createdAt)))
                        .build())
                .build();
    }
}
