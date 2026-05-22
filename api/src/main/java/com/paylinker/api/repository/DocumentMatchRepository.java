package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerDocumentMatch;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.Select;

@Repository
public class DocumentMatchRepository {

    private static final int BATCH_CHUNK_SIZE = 25;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbTable<PaylinkerDocumentMatch> table;
    private final DynamoDbIndex<PaylinkerDocumentMatch> gsi1;

    public DocumentMatchRepository(DynamoDbEnhancedClient enhancedClient,
                                   DynamoDbClient dynamoDbClient,
                                   @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tablePrefix + "-document-match";
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(PaylinkerDocumentMatch.class));
        this.gsi1 = table.index(PaylinkerDocumentMatch.INDEX_GSI1);
    }

    public List<PaylinkerDocumentMatch> findByStatus(String campaignId, String matchStatus) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder()
                                .partitionValue(PaylinkerDocumentMatch.gsi1Pk(campaignId, matchStatus))
                                .build()))
                .build();
        return StreamSupport.stream(gsi1.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public int countByStatus(String campaignId, String matchStatus) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(PaylinkerDocumentMatch.INDEX_GSI1)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", "gsi1_pk"))
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(PaylinkerDocumentMatch.gsi1Pk(campaignId, matchStatus))))
                .select(Select.COUNT)
                .build();
        return dynamoDbClient.query(request).count();
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
