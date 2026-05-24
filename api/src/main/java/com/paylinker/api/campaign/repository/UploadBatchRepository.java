package com.paylinker.api.campaign.repository;

import java.util.Optional;
import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

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
        return Optional.of(item);}
    public void save(String uploadBatchId, String campaignId,
                     int totalRowCount, int validRowCount, int errorRowCount, int duplicateRowCount,
                     String uploadType, String fileS3Key, String validationErrorsS3Key) {
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName())
                .item(Map.of(
                        "upload_batch_id", AttributeValue.fromS(uploadBatchId),
                        "campaign_id", AttributeValue.fromS(campaignId),
                        "total_row_count", AttributeValue.fromN(String.valueOf(totalRowCount)),
                        "valid_row_count", AttributeValue.fromN(String.valueOf(validRowCount)),
                        "error_row_count", AttributeValue.fromN(String.valueOf(errorRowCount)),
                        "duplicate_row_count", AttributeValue.fromN(String.valueOf(duplicateRowCount)),
                        "upload_type", AttributeValue.fromS(uploadType),
                        "file_s3_key", AttributeValue.fromS(fileS3Key),
                        "validation_errors_s3_key", AttributeValue.fromS(validationErrorsS3Key),
                        "created_at", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
    }
}
