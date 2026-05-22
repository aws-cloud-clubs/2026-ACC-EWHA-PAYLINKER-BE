package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerStatSnapshot;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class StatSnapshotRepository {

    private final DynamoDbTable<PaylinkerStatSnapshot> table;

    public StatSnapshotRepository(DynamoDbEnhancedClient enhancedClient,
                                  @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.table = enhancedClient.table(
                tablePrefix + "-stat-snapshot",
                TableSchema.fromBean(PaylinkerStatSnapshot.class));
    }

    public List<PaylinkerStatSnapshot> findInRange(String campaignId, String fromIso8601, String toIso8601) {
        Key fromKey = Key.builder()
                .partitionValue(PaylinkerStatSnapshot.pk(campaignId))
                .sortValue(PaylinkerStatSnapshot.sk(fromIso8601))
                .build();
        Key toKey = Key.builder()
                .partitionValue(PaylinkerStatSnapshot.pk(campaignId))
                .sortValue(PaylinkerStatSnapshot.sk(toIso8601))
                .build();
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.sortBetween(fromKey, toKey))
                .scanIndexForward(true)
                .build();
        return StreamSupport.stream(table.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }

    public Optional<PaylinkerStatSnapshot> findLatest(String campaignId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(PaylinkerStatSnapshot.pk(campaignId)).build()))
                .scanIndexForward(false)
                .limit(1)
                .build();
        return StreamSupport.stream(table.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .findFirst();
    }
}
