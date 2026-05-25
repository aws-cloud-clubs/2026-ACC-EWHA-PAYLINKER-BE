package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerDocumentMatch;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.BatchWriteResult;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;

@Repository
public class DocumentMatchRepository {

    private static final int BATCH_CHUNK_SIZE = 25;
    private static final int MAX_RETRY_ATTEMPTS = 5;
    private static final long INITIAL_BACKOFF_MS = 100L;

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
            List<PaylinkerDocumentMatch> chunk = new ArrayList<>(Arrays.asList(array).subList(offset, end));
            writeChunkWithRetry(chunk);
        }
    }

    private void writeChunkWithRetry(List<PaylinkerDocumentMatch> chunk) {
        long backoffMs = INITIAL_BACKOFF_MS;
        for (int attempt = 0; attempt < MAX_RETRY_ATTEMPTS; attempt++) {
            WriteBatch.Builder<PaylinkerDocumentMatch> batch = WriteBatch.builder(PaylinkerDocumentMatch.class)
                    .mappedTableResource(table);
            chunk.forEach(batch::addPutItem);
            BatchWriteResult result = enhancedClient.batchWriteItem(r -> r.writeBatches(batch.build()));
            chunk = result.unprocessedPutItemsForTable(table);
            if (chunk.isEmpty()) {
                return;
            }
            try {
                Thread.sleep(backoffMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("BatchWriteItem 재시도 대기 중 중단되었습니다.", e);
            }
            backoffMs *= 2;
        }
        throw new RuntimeException(
                "BatchWriteItem 미처리 항목이 " + MAX_RETRY_ATTEMPTS + "회 재시도 후에도 " + chunk.size() + "건 남아 있습니다.");
    }
}
