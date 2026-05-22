package com.paylinker.api.campaign.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;
import software.amazon.awssdk.services.dynamodb.model.ScanRequest;
import software.amazon.awssdk.services.dynamodb.model.ScanResponse;
import software.amazon.awssdk.services.dynamodb.model.TransactWriteItem;
import software.amazon.awssdk.services.dynamodb.model.Update;

@Repository
@RequiredArgsConstructor
public class CampaignRecipientRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-campaign-recipient";
    }

    public Optional<Map<String, AttributeValue>> findById(String campaignRecipientId) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }

    /** GSI2(PK=CAMPAIGN#{id}#VW#0) 대응: 미열람 수신자 조회 */
    public List<Map<String, AttributeValue>> findUnviewedByCampaignId(String campaignId) {
        return scanWithFilter(
                "campaign_id = :campaignId AND viewed = :notViewed",
                Map.of(
                        ":campaignId", AttributeValue.fromS(campaignId),
                        ":notViewed", AttributeValue.fromN("0")));
    }

    /** GSI1(PK=CAMPAIGN#{id}#ST#FAILED) 대응: 발송 실패 수신자 조회 */
    public List<Map<String, AttributeValue>> findFailedByCampaignId(String campaignId) {
        return scanWithFilter(
                "campaign_id = :campaignId AND send_status = :failed",
                Map.of(
                        ":campaignId", AttributeValue.fromS(campaignId),
                        ":failed", AttributeValue.fromS("FAILED")));
    }

    /** reminder_count 증가 + last_reminder_sent_at 갱신 트랜잭션 아이템 */
    public TransactWriteItem incrementReminderCountTxItem(String campaignRecipientId, String lastReminderSentAt) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                        .updateExpression("SET reminder_count = if_not_exists(reminder_count, :zero) + :one," +
                                " last_reminder_sent_at = :sentAt")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.fromN("0"),
                                ":one", AttributeValue.fromN("1"),
                                ":sentAt", AttributeValue.fromS(lastReminderSentAt)))
                        .build())
                .build();
    }

    /** retry_count 증가 + send_status = RETRYING 갱신 트랜잭션 아이템 */
    public TransactWriteItem updateToRetryingTxItem(String campaignRecipientId) {
        return TransactWriteItem.builder()
                .update(Update.builder()
                        .tableName(tableName())
                        .key(Map.of("campaign_recipient_id", AttributeValue.fromS(campaignRecipientId)))
                        .updateExpression("SET retry_count = if_not_exists(retry_count, :zero) + :one," +
                                " send_status = :retrying")
                        .expressionAttributeValues(Map.of(
                                ":zero", AttributeValue.fromN("0"),
                                ":one", AttributeValue.fromN("1"),
                                ":retrying", AttributeValue.fromS("RETRYING")))
                        .build())
                .build();
    }

    private List<Map<String, AttributeValue>> scanWithFilter(
            String filterExpression, Map<String, AttributeValue> expressionValues) {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        ScanRequest req = ScanRequest.builder()
                .tableName(tableName())
                .filterExpression(filterExpression)
                .expressionAttributeValues(expressionValues)
                .build();
        ScanResponse resp;
        do {
            resp = dynamoDbClient.scan(req);
            results.addAll(resp.items());
            if (resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }
}
