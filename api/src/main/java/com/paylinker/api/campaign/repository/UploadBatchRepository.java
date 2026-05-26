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
import com.paylinker.api.entity.PaylinkerUploadBatch;

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
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerUploadBatch.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerUploadBatch.sk(uploadBatchId))))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }
    public void save(String uploadBatchId, String campaignId,
                     int totalRowCount, int validRowCount, int errorRowCount, int duplicateRowCount,
                     String uploadType, String fileS3Key, String validationErrorsS3Key) {
        Map<String, AttributeValue> item = new java.util.HashMap<>();
        item.put("PK", AttributeValue.fromS(PaylinkerUploadBatch.pk(campaignId)));
        item.put("SK", AttributeValue.fromS(PaylinkerUploadBatch.sk(uploadBatchId)));
        item.put("upload_batch_id", AttributeValue.fromS(uploadBatchId));
        item.put("campaign_id", AttributeValue.fromS(campaignId));
        item.put("total_row_count", AttributeValue.fromN(String.valueOf(totalRowCount)));
        item.put("valid_row_count", AttributeValue.fromN(String.valueOf(validRowCount)));
        item.put("error_row_count", AttributeValue.fromN(String.valueOf(errorRowCount)));
        item.put("duplicate_row_count", AttributeValue.fromN(String.valueOf(duplicateRowCount)));
        item.put("upload_type", AttributeValue.fromS(uploadType));
        item.put("file_s3_key", AttributeValue.fromS(fileS3Key));
        item.put("validation_errors_s3_key", AttributeValue.fromS(validationErrorsS3Key));
        item.put("created_at", AttributeValue.fromS(Instant.now().toString()));
        dynamoDbClient.putItem(PutItemRequest.builder()
                .tableName(tableName())
                .item(item)
                .build());
    }
}
