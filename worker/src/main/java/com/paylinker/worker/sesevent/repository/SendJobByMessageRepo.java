package com.paylinker.worker.sesevent.repository;

import com.paylinker.worker.util.Attr;
import java.util.List;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

/**
 * send-job 테이블을 SES messageId 로 역조회하는 전용 리포지토리.
 * 본 테이블의 GSI2 PK 가 SES#<messageId> 로 설정되어 있다.
 */
public class SendJobByMessageRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public SendJobByMessageRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-send-job";
    }

    public Map<String, AttributeValue> findBySesMessageId(String sesMessageId) {
        QueryResponse resp = ddb.query(QueryRequest.builder()
                .tableName(tableName)
                .indexName("GSI2")
                .keyConditionExpression("GSI2PK = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", Attr.s("SES#" + sesMessageId)))
                .limit(1)
                .build());
        List<Map<String, AttributeValue>> items = resp.items();
        return items.isEmpty() ? null : items.get(0);
    }
}
