package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerCampaign;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
public class CampaignRepository {

    private final DynamoDbTable<PaylinkerCampaign> campaignTable;

    public CampaignRepository(DynamoDbEnhancedClient enhancedClient) {
        this.campaignTable = enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));
    }

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = campaignTable.index(PaylinkerCampaign.INDEX_GSI1);

        // GSI1 PK 생성 규칙인 "ADMIN#{adminId}"를 활용하여 쿼리
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }
}