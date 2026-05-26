package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.HashMap;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

public class SendAttemptRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public SendAttemptRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-send-attempt";
    }

    /**
     * 발송 시도 기록. PK = JOB#<sendJobId>, SK = ATT#<attemptNo>.
     * attemptNo 는 호출자가 1부터 증가시켜 전달 (재시도 시 +1).
     */
    public void save(String sendJobId,
                     int attemptNo,
                     String attemptStatus,
                     String failureReason,
                     String sesMessageId,
                     String attemptedAt,
                     long ttlEpoch) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("PK", Attr.s("JOB#" + sendJobId));
        item.put("SK", Attr.s("ATT#" + attemptNo));
        item.put("send_job_id", Attr.s(sendJobId));
        item.put("attempt_no", Attr.n(attemptNo));
        item.put("attempt_status", Attr.s(attemptStatus));
        if (failureReason != null && !failureReason.isBlank()) {
            item.put("failure_reason", Attr.s(failureReason));
        }
        if (sesMessageId != null && !sesMessageId.isBlank()) {
            item.put("ses_message_id", Attr.s(sesMessageId));
        }
        item.put("attempted_at", Attr.s(attemptedAt));
        item.put("ttl_epoch", Attr.n(ttlEpoch));
        ddb.putItem(PutItemRequest.builder()
                .tableName(tableName)
                .item(item)
                .build());
    }
}
