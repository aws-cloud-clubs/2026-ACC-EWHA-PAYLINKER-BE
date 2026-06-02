package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public class CampaignRecipientRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public CampaignRecipientRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-campaign-recipient";
    }

    /** PK = CAMPAIGN#<campaignId>, SK = RCP#<recipientId> 로 단건 조회. */
    public Map<String, AttributeValue> findByCampaignAndRecipient(String campaignId, String recipientId) {
        var resp = ddb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("RCP#" + recipientId)))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    /** send_status = "SENDING" 으로 갱신하고 GSI1PK 도 함께 갱신. */
    public void markSending(String campaignId, String recipientId) {
        String newStatus = "SENDING";
        String gsi1Pk = "CAMPAIGN#" + campaignId + "#ST#" + newStatus;
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("RCP#" + recipientId)))
                .updateExpression("SET send_status = :s, GSI1PK = :g")
                .expressionAttributeValues(Map.of(
                        ":s", Attr.s(newStatus),
                        ":g", Attr.s(gsi1Pk)))
                .build());
    }

    /** send_status = "SUCCESS" 로 갱신. */
    public void markSuccess(String campaignId, String recipientId) {
        String newStatus = "SUCCESS";
        String gsi1Pk = "CAMPAIGN#" + campaignId + "#ST#" + newStatus;
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("RCP#" + recipientId)))
                .updateExpression("SET send_status = :s, GSI1PK = :g REMOVE failure_reason")
                .expressionAttributeValues(Map.of(
                        ":s", Attr.s(newStatus),
                        ":g", Attr.s(gsi1Pk)))
                .build());
    }

    /** send_status = "FAILED" + failure_reason 갱신. */
    public void markFailed(String campaignId, String recipientId, String failureReason) {
        String newStatus = "FAILED";
        String gsi1Pk = "CAMPAIGN#" + campaignId + "#ST#" + newStatus;
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("RCP#" + recipientId)))
                .updateExpression("SET send_status = :s, GSI1PK = :g, failure_reason = :r, retry_count = if_not_exists(retry_count, :zero) + :one")
                .expressionAttributeValues(Map.of(
                        ":s", Attr.s(newStatus),
                        ":g", Attr.s(gsi1Pk),
                        ":r", Attr.s(failureReason),
                        ":zero", Attr.n(0),
                        ":one", Attr.n(1)))
                .build());
    }

    /** send_status = "SKIPPED" + failure_reason 갱신 (수신거부 등 비-실패 스킵). */
    public void markSkipped(String campaignId, String recipientId, String reason) {
        String newStatus = "SKIPPED";
        String gsi1Pk = "CAMPAIGN#" + campaignId + "#ST#" + newStatus;
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("RCP#" + recipientId)))
                .updateExpression("SET send_status = :s, GSI1PK = :g, failure_reason = :r")
                .expressionAttributeValues(Map.of(
                        ":s", Attr.s(newStatus),
                        ":g", Attr.s(gsi1Pk),
                        ":r", Attr.s(reason)))
                .build());
    }
}
