package com.paylinker.worker.sesevent.repository;

import com.paylinker.worker.util.Attr;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.ConditionalCheckFailedException;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class SesEventRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public SesEventRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-ses-event";
    }

    /**
     * SES 이벤트 1건 적재. 조건부 PutItem 으로 (messageId, eventType) 중복 처리 방지.
     * 이미 같은 키가 존재하면 ConditionalCheckFailedException 을 던지고 false 반환.
     */
    public boolean putIfAbsent(String campaignId,
                               String sesMessageId,
                               String eventType,
                               String receivedAt,
                               String sendJobId,
                               String eventPayloadJson,
                               long ttlEpoch) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", Attr.s("CAMPAIGN#" + campaignId));
        item.put("SK", Attr.s("SES#" + sesMessageId + "#" + receivedAt));
        item.put("campaign_id", Attr.s(campaignId));
        item.put("ses_message_id", Attr.s(sesMessageId));
        item.put("ses_event_type", Attr.s(eventType));
        item.put("event_payload", Attr.s(eventPayloadJson));
        item.put("received_at", Attr.s(receivedAt));
        item.put("ttl_epoch", Attr.n(ttlEpoch));
        item.put("GSI1PK", Attr.s("SES#" + sesMessageId));
        item.put("GSI2PK", Attr.s(campaignId + "#" + eventType));
        item.put("GSI2SK", Attr.s(receivedAt));
        if (sendJobId != null && !sendJobId.isBlank()) {
            item.put("send_job_id", Attr.s(sendJobId));
        }
        try {
            ddb.putItem(PutItemRequest.builder()
                    .tableName(tableName)
                    .item(item)
                    .conditionExpression("attribute_not_exists(PK)")
                    .build());
            return true;
        } catch (ConditionalCheckFailedException e) {
            return false;
        }
    }
}
