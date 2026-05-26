package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerDocument;
import java.util.Collection;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.WriteBatch;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

@Repository
public class DocumentRepository {

    private static final int BATCH_CHUNK_SIZE = 25;

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbTable<PaylinkerDocument> table;

    public DocumentRepository(DynamoDbEnhancedClient enhancedClient,
                              DynamoDbClient dynamoDbClient,
                              @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.enhancedClient = enhancedClient;
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tablePrefix + "-document";
        this.table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(PaylinkerDocument.class));
    }

    public int countByCampaign(String campaignId) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "PK"))
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(PaylinkerDocument.pk(campaignId))))
                    .select(Select.COUNT);
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            QueryResponse response = dynamoDbClient.query(builder.build());
            total += response.count();
            exclusiveStartKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
        return total;
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
