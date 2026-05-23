package com.paylinker.api.campaign.repository;

import java.time.Instant;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
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

    public void save(String uploadBatchId, String campaignId,
                     int totalRowCount, int validRowCount, int errorRowCount, int duplicateRowCount,
                     String uploadType, String s3Key) {
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
                        "s3_key", AttributeValue.fromS(s3Key),
                        "created_at", AttributeValue.fromS(Instant.now().toString())
                ))
                .build());
    }
}
