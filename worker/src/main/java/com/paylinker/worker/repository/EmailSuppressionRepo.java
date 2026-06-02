package com.paylinker.worker.repository;

import com.paylinker.worker.util.Attr;
import java.util.Map;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;

/**
 * 이메일 해시 기반 수신거부(suppression) 조회.
 * api 의 EmailSuppressionRepository 와 동일 테이블/키 규칙({@code <prefix>-recipient}, PK=EMAILSUB#).
 */
public class EmailSuppressionRepo {

    private final DynamoDbClient ddb;
    private final String tableName;

    public EmailSuppressionRepo(DynamoDbClient ddb, String tablePrefix) {
        this.ddb = ddb;
        this.tableName = tablePrefix + "-recipient";
    }

    /** 해당 email 해시가 수신거부 목록에 있으면 true. */
    public boolean isSuppressed(String emailHash) {
        var resp = ddb.getItem(GetItemRequest.builder()
                .tableName(tableName)
                .key(Map.of(
                        "PK", Attr.s("EMAILSUB#" + emailHash),
                        "SK", Attr.s("SUPPRESSION")))
                .build());
        return resp.hasItem();
    }
}
