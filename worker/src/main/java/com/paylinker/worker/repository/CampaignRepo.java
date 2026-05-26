package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

public class CampaignRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public CampaignRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-campaign";
    }

    public Map<String, AttributeValue> findById(String campaignId) {
        var resp = ddb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("METADATA")))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }
}
