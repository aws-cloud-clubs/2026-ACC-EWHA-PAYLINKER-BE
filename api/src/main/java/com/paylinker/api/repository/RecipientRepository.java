package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerRecipient;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.ReadBatch;

@Repository
public class RecipientRepository {

    private final DynamoDbEnhancedClient enhancedClient;
    private final DynamoDbTable<PaylinkerRecipient> table;

    public RecipientRepository(DynamoDbEnhancedClient enhancedClient,
                               @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.enhancedClient = enhancedClient;
        this.table = enhancedClient.table(
                tablePrefix + "-recipient",
                TableSchema.fromBean(PaylinkerRecipient.class));
    }

    public Map<String, PaylinkerRecipient> findAllByIds(Collection<String> recipientIds) {
        Map<String, PaylinkerRecipient> result = new LinkedHashMap<>();
        if (recipientIds == null || recipientIds.isEmpty()) {
            return result;
        }

        ReadBatch.Builder<PaylinkerRecipient> batch = ReadBatch.builder(PaylinkerRecipient.class)
                .mappedTableResource(table);
        for (String recipientId : recipientIds) {
            batch.addGetItem(Key.builder()
                    .partitionValue(PaylinkerRecipient.pk(recipientId))
                    .sortValue(PaylinkerRecipient.SK_PROFILE)
                    .build());
        }

        enhancedClient.batchGetItem(r -> r.readBatches(batch.build()))
                .resultsForTable(table)
                .forEach(item -> result.put(item.getRecipientId(), item));
        return result;
    }
}
