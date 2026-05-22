package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerDocument;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
public class DocumentRepository {

    private static final int BATCH_CHUNK_SIZE = 25;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<PaylinkerDocument> table;

    public DocumentRepository(DynamoDbEnhancedClient enhancedClient,
                              @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(
                tablePrefix + "-document",
                TableSchema.fromBean(PaylinkerDocument.class));
    }

    public void saveAll(Collection<PaylinkerDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        PaylinkerDocument[] array = documents.toArray(PaylinkerDocument[]::new);
        for (int offset = 0; offset < array.length; offset += BATCH_CHUNK_SIZE) {
            int end = Math.min(offset + BATCH_CHUNK_SIZE, array.length);
            WriteBatch.Builder<PaylinkerDocument> batch = WriteBatch.builder(PaylinkerDocument.class)
                    .mappedTableResource(table);
            for (int i = offset; i < end; i++) {
                batch.addPutItem(array[i]);
            }
            enhancedClient.batchWriteItem(r -> r.writeBatches(batch.build()));
        }
    }
}
