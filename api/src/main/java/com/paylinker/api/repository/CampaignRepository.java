package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerCampaign;
import java.util.List;
import java.util.Optional;
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

@Repository
public class CampaignRepository {

    private final DynamoDbTable<PaylinkerCampaign> table;
    private final DynamoDbIndex<PaylinkerCampaign> gsi1;

    public CampaignRepository(DynamoDbEnhancedClient enhancedClient,
                              @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.table = enhancedClient.table(
                tablePrefix + "-campaign",
                TableSchema.fromBean(PaylinkerCampaign.class));
        this.gsi1 = table.index(PaylinkerCampaign.INDEX_GSI1);
    }

    public Optional<PaylinkerCampaign> findByCampaignId(String campaignId) {
        Key key = Key.builder()
                .partitionValue(PaylinkerCampaign.pk(campaignId))
                .sortValue(PaylinkerCampaign.SK_METADATA)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<PaylinkerCampaign> findRecentByAdminId(String adminId, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(PaylinkerCampaign.gsi1Pk(adminId)).build()))
                .scanIndexForward(false)
                .limit(limit)
                .build();
        return StreamSupport.stream(gsi1.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());
    }
}
