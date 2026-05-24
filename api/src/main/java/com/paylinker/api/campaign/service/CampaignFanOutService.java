package com.paylinker.api.campaign.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.BatchWriteItemRequest;
import software.amazon.awssdk.services.dynamodb.model.PutRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.WriteRequest;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageBatchRequestEntry;
import com.fasterxml.jackson.databind.ObjectMapper;

@Slf4j
@Service
@RequiredArgsConstructor
public class CampaignFanOutService {

    private final DynamoDbClient dynamoDbClient;
    private final SqsClient sqsClient;
    private final ObjectMapper objectMapper;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    @Value("${aws.sqs.email-queue-url}")
    private String sqsQueueUrl;

    @Async
    public void fanOutRecipients(String campaignId, String adminId) {
        log.info("[FanOut Start] CampaignId: {}", campaignId);
        String recipientTable = tablePrefix + "-campaign-recipient";
        String secureLinkTable = tablePrefix + "-secure-link";
        String sendJobTable = tablePrefix + "-send-job";
        String auditLogTable = tablePrefix + "-audit-log";

        String now = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
        String expiresAt = ZonedDateTime.now(ZoneId.of("Asia/Seoul")).plusDays(7).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        Map<String, AttributeValue> lastKey = null;
        int totalProcessed = 0;

        try {
            do {
                // 1. 수신자 페이징 조회 (100건 단위)
                QueryRequest.Builder queryBuilder = QueryRequest.builder()
                        .tableName(recipientTable)
                        .keyConditionExpression("campaign_id = :cid")
                        .expressionAttributeValues(Map.of(":cid", AttributeValue.fromS(campaignId)))
                        .limit(100);

                if (lastKey != null && !lastKey.isEmpty()) {
                    queryBuilder.exclusiveStartKey(lastKey);
                }

                QueryResponse queryResponse = dynamoDbClient.query(queryBuilder.build());
                List<Map<String, AttributeValue>> recipients = queryResponse.items();

                if (!recipients.isEmpty()) {
                    processBatch(recipients, campaignId, secureLinkTable, sendJobTable, now, expiresAt);
                    totalProcessed += recipients.size();
                }

                lastKey = queryResponse.hasLastEvaluatedKey() ? queryResponse.lastEvaluatedKey() : null;

            } while (lastKey != null && !lastKey.isEmpty());

            // 2. 발송 지시 완료 Audit Log 기록
            writeAuditLog(auditLogTable, campaignId, adminId, "SEND_DISPATCHED", now);
            log.info("[FanOut Complete] CampaignId: {}, Total Processed: {}", campaignId, totalProcessed);

        } catch (Exception e) {
            log.error("[FanOut Failed] CampaignId: {}", campaignId, e);
            // TODO: 실패 시 별도의 알림(DLQ 등) 처리 로직 추가 가능
        }
    }

    private void processBatch(List<Map<String, AttributeValue>> recipients, String campaignId,
                              String secureLinkTable, String sendJobTable, String now, String expiresAt) throws Exception {

        List<WriteRequest> secureLinkWrites = new ArrayList<>();
        List<WriteRequest> sendJobWrites = new ArrayList<>();
        List<SendMessageBatchRequestEntry> sqsEntries = new ArrayList<>();

        for (Map<String, AttributeValue> recipient : recipients) {
            String recipientId = recipient.get("campaign_recipient_id").s();
            String plainToken = UUID.randomUUID().toString().replace("-", "");
            String hashedToken = sha256(plainToken);

            // Deterministic ID 생성: 캠페인ID + 수신자ID 조합으로 해시 생성 (멱등성 보장)
            String combinedKey = campaignId + "_" + recipientId;
            String deterministicHash = sha256(combinedKey).substring(0, 16);
            String secureLinkId = "sl_" + deterministicHash;
            String sendJobId = "sj_" + deterministicHash;

            // Secure Link PutRequest 생성
            secureLinkWrites.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(Map.of(
                            "secure_link_id", AttributeValue.fromS(secureLinkId),
                            "campaign_recipient_id", AttributeValue.fromS(recipientId),
                            "campaign_id", AttributeValue.fromS(campaignId),
                            "token_hash", AttributeValue.fromS(hashedToken),
                            "expires_at", AttributeValue.fromS(expiresAt),
                            "is_valid", AttributeValue.fromBool(true)
                    )).build()).build());

            // Send Job PutRequest 생성
            sendJobWrites.add(WriteRequest.builder()
                    .putRequest(PutRequest.builder().item(Map.of(
                            "send_job_id", AttributeValue.fromS(sendJobId),
                            "campaign_recipient_id", AttributeValue.fromS(recipientId),
                            "campaign_id", AttributeValue.fromS(campaignId),
                            "job_type", AttributeValue.fromS("SEND"),
                            "secure_link_id", AttributeValue.fromS(secureLinkId),
                            "job_status", AttributeValue.fromS("PENDING"),
                            "created_at", AttributeValue.fromS(now)
                    )).build()).build());

            // SQS 메시지 Entry 생성
            String messageBody = objectMapper.writeValueAsString(Map.of(
                    "sendJobId", sendJobId,
                    "secureLinkRawToken", plainToken
            ));
            sqsEntries.add(SendMessageBatchRequestEntry.builder()
                    .id(UUID.randomUUID().toString()) // Batch 내 고유 ID
                    .messageBody(messageBody)
                    .build());
        }

        // DynamoDB BatchWrite (제한: 최대 25개)
        executeDynamoBatchWrite(secureLinkTable, secureLinkWrites, 25);
        executeDynamoBatchWrite(sendJobTable, sendJobWrites, 25);

        // SQS SendMessageBatch (제한: 최대 10개)
        executeSqsBatchSend(sqsEntries, 10);
    }

    private void executeDynamoBatchWrite(String tableName, List<WriteRequest> writes, int batchSize) {
        for (int i = 0; i < writes.size(); i += batchSize) {
            List<WriteRequest> batch = writes.subList(i, Math.min(i + batchSize, writes.size()));
            dynamoDbClient.batchWriteItem(BatchWriteItemRequest.builder()
                    .requestItems(Map.of(tableName, batch))
                    .build());
        }
    }

    private void executeSqsBatchSend(List<SendMessageBatchRequestEntry> entries, int batchSize) {
        for (int i = 0; i < entries.size(); i += batchSize) {
            List<SendMessageBatchRequestEntry> batch = entries.subList(i, Math.min(i + batchSize, entries.size()));
            sqsClient.sendMessageBatch(SendMessageBatchRequest.builder()
                    .queueUrl(sqsQueueUrl)
                    .entries(batch)
                    .build());
        }
    }

    private void writeAuditLog(String tableName, String campaignId, String adminId, String action, String now) {
        String auditId = "al_" + UUID.randomUUID().toString().replace("-", "");
        String datePk = now.substring(0, 10).replace("-", ""); // yyyyMMdd
        dynamoDbClient.putItem(software.amazon.awssdk.services.dynamodb.model.PutItemRequest.builder()
                .tableName(tableName)
                .item(Map.of(
                        "pk", AttributeValue.fromS("AUDIT#" + datePk),
                        "sk", AttributeValue.fromS(System.currentTimeMillis() + "#" + auditId),
                        "audit_id", AttributeValue.fromS(auditId),
                        "admin_id", AttributeValue.fromS(adminId),
                        "campaign_id", AttributeValue.fromS(campaignId),
                        "action", AttributeValue.fromS(action),
                        "created_at", AttributeValue.fromS(now)
                )).build());
    }

    private String sha256(String input) throws NoSuchAlgorithmException {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(md.digest(input.getBytes(StandardCharsets.UTF_8)));
    }
}