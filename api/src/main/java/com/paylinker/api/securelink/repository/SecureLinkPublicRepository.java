package com.paylinker.api.securelink.repository;

import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

/**
 * 수신자(public) 측에서 사용하는 SecureLink 조회 전용 레포지토리.
 * 관리자 측 notification.repository.SecureLinkRepository 와 역할을 분리.
 */
@Repository
@RequiredArgsConstructor
public class SecureLinkPublicRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-secure-link";
    }

    /**
     * token hash 로 SecureLink 단건 조회.
     * PK = TOKEN#<tokenHash>, SK = METADATA
     */
    public Optional<Map<String, AttributeValue>> findByTokenHash(String tokenHash) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of(
                        "PK", AttributeValue.fromS("TOKEN#" + tokenHash),
                        "SK", AttributeValue.fromS("METADATA")))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }
}
