package com.paylinker.api.unsubscribe;

import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.PutItemRequest;

/**
 * 이메일 단위 수신거부(suppression) 저장소.
 * 마스터 recipient 프로필이 없는 현재 데이터 모델에 맞춰, email 해시를 키로 수신거부를 보관한다.
 * 기존 {@code <prefix>-recipient} 테이블을 재사용하되 PK 네임스페이스(EMAILSUB#)로 분리한다.
 */
@Repository
public class EmailSuppressionRepository {

    private final DynamoDbClient ddb;
    private final String tableName;

    public EmailSuppressionRepository(DynamoDbClient ddb,
                                      @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-recipient";
    }

    /** email 해시를 수신거부로 기록. putItem 이라 멱등(재호출해도 동일). */
    public void suppress(String emailHash, String unsubscribedAt) {
        Map<String, AttributeValue> item = Map.of(
                "PK", AttributeValue.fromS("EMAILSUB#" + emailHash),
                "SK", AttributeValue.fromS("SUPPRESSION"),
                "email_hash", AttributeValue.fromS(emailHash),
                "unsubscribed_at", AttributeValue.fromS(unsubscribedAt));
        ddb.putItem(PutItemRequest.builder().tableName(tableName).item(item).build());
    }
}
