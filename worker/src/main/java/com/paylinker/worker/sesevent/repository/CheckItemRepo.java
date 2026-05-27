package com.paylinker.worker.sesevent.repository;

import com.paylinker.worker.util.Attr;
import com.paylinker.worker.util.IdUtil;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class CheckItemRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public CheckItemRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-check-item";
    }

    /**
     * 운영자 확인 큐에 행 추가. SES 이벤트 처리 결과로 발생한 알림.
     *
     * 멱등성을 위해 호출자가 checkItemId 를 명시적으로 줄 수 있다 (sesMessageId 기반 등).
     * 단순 PutItem 이라 동일 checkItemId 중복 호출 시 덮어씌워진다.
     */
    public void save(String checkItemId,
                     String campaignId,
                     String adminId,
                     String recipientId,
                     String itemType,
                     String createdAt) {
        String resolvedId = (checkItemId == null || checkItemId.isBlank())
                ? IdUtil.newId("chk") : checkItemId;
        String checkStatus = "OPEN";

        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", Attr.s("CAMPAIGN#" + campaignId));
        item.put("SK", Attr.s("CHECK#" + resolvedId));
        item.put("check_item_id", Attr.s(resolvedId));
        item.put("campaign_id", Attr.s(campaignId));
        item.put("item_type", Attr.s(itemType));
        item.put("check_status", Attr.s(checkStatus));
        item.put("created_at", Attr.s(createdAt));
        item.put("GSI1PK", Attr.s(checkStatus));
        item.put("GSI1SK", Attr.s(createdAt));
        if (adminId != null && !adminId.isBlank()) {
            item.put("admin_id", Attr.s(adminId));
            item.put("GSI2PK", Attr.s("ADMIN#" + adminId + "#" + checkStatus));
            item.put("GSI2SK", Attr.s(createdAt));
        }
        if (recipientId != null && !recipientId.isBlank()) {
            item.put("recipient_id", Attr.s(recipientId));
        }
        ddb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }
}
