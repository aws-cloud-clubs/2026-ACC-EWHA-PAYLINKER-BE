package com.paylinker.api.campaign.repository;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@Repository
@RequiredArgsConstructor
public class UploadBatchRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-upload-batch";
    }

    public Optional<Map<String, AttributeValue>> findByBatchIdAndCampaignId(String uploadBatchId, String campaignId) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("upload_batch_id", AttributeValue.fromS(uploadBatchId)))
                .build());

        if (!resp.hasItem()) {
            return Optional.empty();
        }
        Map<String, AttributeValue> item = resp.item();
        String storedCampaignId = item.getOrDefault("campaign_id", AttributeValue.fromS("")).s();
        if (!campaignId.equals(storedCampaignId)) {
            return Optional.empty();
        }
        return Optional.of(item);
    }
}
