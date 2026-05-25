package com.paylinker.api.document.repository;

import com.paylinker.api.entity.PaylinkerUploadBatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class DocumentUploadBatchRepository {

    private final DynamoDbTable<PaylinkerUploadBatch> table;

    public DocumentUploadBatchRepository(DynamoDbEnhancedClient enhancedClient,
                                         @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.table = enhancedClient.table(
                tablePrefix + "-upload-batch",
                TableSchema.fromBean(PaylinkerUploadBatch.class));
    }

    public void save(PaylinkerUploadBatch batch) {
        table.putItem(batch);
    }
}
