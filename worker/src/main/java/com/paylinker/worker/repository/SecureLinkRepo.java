package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class SecureLinkRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public SecureLinkRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-secure-link";
    }

    /**
     * 신규 secure_link 행 저장.
     * PK = TOKEN#<tokenHash>, SK = METADATA, GSI1PK = CR#<campaignRecipientId>.
     */
    public void save(String secureLinkId,
                     String tokenHash,
                     String campaignRecipientId,
                     String campaignId,
                     String expiresAt,
                     long ttlEpoch,
                     String createdAt) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", Attr.s("TOKEN#" + tokenHash));
        item.put("SK", Attr.s("METADATA"));
        item.put("secure_link_id", Attr.s(secureLinkId));
        item.put("campaign_id", Attr.s(campaignId));
        item.put("campaign_recipient_id", Attr.s(campaignRecipientId));
        item.put("token_hash", Attr.s(tokenHash));
        item.put("link_status", Attr.s("ACTIVE"));
        item.put("expires_at", Attr.s(expiresAt));
        item.put("access_count", Attr.n(0));
        item.put("created_at", Attr.s(createdAt));
        item.put("ttl_epoch", Attr.n(ttlEpoch));
        item.put("GSI1PK", Attr.s("CR#" + campaignRecipientId));
        item.put("GSI1SK", Attr.s(createdAt));
        ddb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }
}
