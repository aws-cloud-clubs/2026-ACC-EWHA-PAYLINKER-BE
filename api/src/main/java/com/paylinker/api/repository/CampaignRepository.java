package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerCampaign;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;

@Repository
public class CampaignRepository {

    private final DynamoDbTable<PaylinkerCampaign> table;

    public CampaignRepository(DynamoDbEnhancedClient enhancedClient,
                              @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.table = enhancedClient.table(
                tablePrefix + "-campaign",
                TableSchema.fromBean(PaylinkerCampaign.class));
    }

    public Optional<PaylinkerCampaign> findByCampaignId(String campaignId) {
        Key key = Key.builder()
                .partitionValue(PaylinkerCampaign.pk(campaignId))
                .sortValue(PaylinkerCampaign.SK_METADATA)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }
}
