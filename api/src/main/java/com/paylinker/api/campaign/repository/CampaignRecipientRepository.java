package com.paylinker.api.campaign.repository;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;

@Repository
@RequiredArgsConstructor
public class CampaignRecipientRepository {

    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-campaign-recipient";
    }

    /**
     * 캠페인 전체 수신자 조회 (열람 이력 조회용)
     * GSI: campaign-index, pk = CAMPAIGN#{campaignId}
     */
    public List<Map<String, AttributeValue>> findByCampaignId(String campaignId) {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        QueryRequest req = QueryRequest.builder()
                .tableName(tableName())
                .indexName("campaign-index")
                .keyConditionExpression("pk = :pk")
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS("CAMPAIGN#" + campaignId)))
                .build();
        QueryResponse resp;
        do {
            resp = dynamoDbClient.query(req);
            results.addAll(resp.items());
            if (resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }

    /**
     * 캠페인 발송 실패 수신자 조회 (실패 대상자 목록 조회용)
     * GSI1: gsi1-index, gsi1pk = CAMPAIGN#{campaignId}#ST#FAILED
     */
    public List<Map<String, AttributeValue>> findFailedByCampaignId(String campaignId) {
        List<Map<String, AttributeValue>> results = new ArrayList<>();
        QueryRequest req = QueryRequest.builder()
                .tableName(tableName())
                .indexName("gsi1-index")
                .keyConditionExpression("gsi1pk = :gsi1pk")
                .expressionAttributeValues(Map.of(
                        ":gsi1pk", AttributeValue.fromS("CAMPAIGN#" + campaignId + "#ST#FAILED")))
                .build();
        QueryResponse resp;
        do {
            resp = dynamoDbClient.query(req);
            results.addAll(resp.items());
            if (resp.lastEvaluatedKey().isEmpty()) break;
            req = req.toBuilder().exclusiveStartKey(resp.lastEvaluatedKey()).build();
        } while (true);
        return results;
    }
}
