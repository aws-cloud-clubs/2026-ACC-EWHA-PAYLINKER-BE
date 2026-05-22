package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerDocumentMatch;
import java.util.Collection;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
public class DocumentMatchRepository {

    private static final int BATCH_CHUNK_SIZE = 25;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<PaylinkerDocumentMatch> table;

    public DocumentMatchRepository(DynamoDbEnhancedClient enhancedClient,
                                   @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(
                tablePrefix + "-document-match",
                TableSchema.fromBean(PaylinkerDocumentMatch.class));
    }

    public void saveAll(Collection<PaylinkerDocumentMatch> matches) {
        if (matches == null || matches.isEmpty()) {
            return;
        }
        PaylinkerDocumentMatch[] array = matches.toArray(PaylinkerDocumentMatch[]::new);
        for (int offset = 0; offset < array.length; offset += BATCH_CHUNK_SIZE) {
            int end = Math.min(offset + BATCH_CHUNK_SIZE, array.length);
            WriteBatch.Builder<PaylinkerDocumentMatch> batch = WriteBatch.builder(PaylinkerDocumentMatch.class)
                    .mappedTableResource(table);
            for (int i = offset; i < end; i++) {
                batch.addPutItem(array[i]);
            }
            enhancedClient.batchWriteItem(r -> r.writeBatches(batch.build()));
        }
    }
}
