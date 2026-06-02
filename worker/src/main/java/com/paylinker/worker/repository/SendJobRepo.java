package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

public class SendJobRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public SendJobRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-send-job";
    }

    /** PK = CAMPAIGN#<campaignId>, SK = JOB#<sendJobId> 단건 조회. */
    public Map<String, AttributeValue> findById(String campaignId, String sendJobId) {
        var resp = ddb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("JOB#" + sendJobId)))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    /** job_status = "SUCCESS", ses_message_id, secure_link_id 갱신. */
    public void markSuccess(String campaignId, String sendJobId, String sesMessageId, String secureLinkId) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":s", Attr.s("SUCCESS"));
        values.put(":g", Attr.s("SES#" + sesMessageId));
        values.put(":mid", Attr.s(sesMessageId));
        values.put(":sl", Attr.s(secureLinkId));
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("JOB#" + sendJobId)))
                .updateExpression("SET job_status = :s, ses_message_id = :mid, secure_link_id = :sl, GSI2PK = :g REMOVE failure_reason")
                .expressionAttributeValues(values)
                .build());
    }

    /** job_status = "FAILED", failure_reason 갱신. */
    public void markFailed(String campaignId, String sendJobId, String failureReason) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":s", Attr.s("FAILED"));
        values.put(":r", Attr.s(failureReason));
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("JOB#" + sendJobId)))
                .updateExpression("SET job_status = :s, failure_reason = :r")
                .expressionAttributeValues(values)
                .build());
    }

    /** job_status = "SKIPPED" + failure_reason 갱신 (수신거부 등 비-실패 스킵). */
    public void markSkipped(String campaignId, String sendJobId, String reason) {
        Map<String, AttributeValue> values = new HashMap<>();
        values.put(":s", Attr.s("SKIPPED"));
        values.put(":r", Attr.s(reason));
        ddb.updateItem(UpdateItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("CAMPAIGN#" + campaignId),
                        "SK", Attr.s("JOB#" + sendJobId)))
                .updateExpression("SET job_status = :s, failure_reason = :r")
                .expressionAttributeValues(values)
                .build());
    }
}
