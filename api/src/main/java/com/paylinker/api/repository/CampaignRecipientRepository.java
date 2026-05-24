package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

@Repository
public class CampaignRecipientRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;
    private final DynamoDbIndex<PaylinkerCampaignRecipient> gsi1;
    private final DynamoDbIndex<PaylinkerCampaignRecipient> gsi2;

    public CampaignRecipientRepository(DynamoDbEnhancedClient enhancedClient,
                                       DynamoDbClient dynamoDbClient,
                                       @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tablePrefix + "-campaign-recipient";
        DynamoDbTable<PaylinkerCampaignRecipient> table = enhancedClient.table(
                tableName,
                TableSchema.fromBean(PaylinkerCampaignRecipient.class));
        this.gsi1 = table.index(PaylinkerCampaignRecipient.INDEX_GSI1);
        this.gsi2 = table.index(PaylinkerCampaignRecipient.INDEX_GSI2);
    }

    public List<PaylinkerCampaignRecipient> findUnviewedPreviews(String campaignId, int limit) {
        return queryIndex(gsi2,
                PaylinkerCampaignRecipient.gsi2Pk(campaignId, PaylinkerCampaignRecipient.VIEWED_FALSE),
                limit);
    }

    public int countUnviewed(String campaignId) {
        return countByGsiPk("GSI2PK",
                PaylinkerCampaignRecipient.gsi2Pk(campaignId, PaylinkerCampaignRecipient.VIEWED_FALSE),
                PaylinkerCampaignRecipient.INDEX_GSI2);
    }

    public List<PaylinkerCampaignRecipient> findFailedPreviews(String campaignId, int limit) {
        return queryIndex(gsi1,
                PaylinkerCampaignRecipient.gsi1Pk(campaignId, PaylinkerCampaignRecipient.SEND_STATUS_FAILED),
                limit);
    }

    public int countFailed(String campaignId) {
        return countByGsiPk("GSI1PK",
                PaylinkerCampaignRecipient.gsi1Pk(campaignId, PaylinkerCampaignRecipient.SEND_STATUS_FAILED),
                PaylinkerCampaignRecipient.INDEX_GSI1);
    }

    private List<PaylinkerCampaignRecipient> queryIndex(DynamoDbIndex<PaylinkerCampaignRecipient> index,
                                                        String partitionValue,
                                                        int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(partitionValue).build()))
                .scanIndexForward(true)
                .limit(limit)
                .build();
        return StreamSupport.stream(index.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    private int countByGsiPk(String pkAttribute, String partitionValue, String indexName) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(indexName)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", pkAttribute))
                    .expressionAttributeValues(Map.of(":pk", AttributeValue.fromS(partitionValue)))
                    .select(Select.COUNT);
            if (exclusiveStartKey != null && !exclusiveStartKey.isEmpty()) {
                builder.exclusiveStartKey(exclusiveStartKey);
            }
            QueryResponse response = dynamoDbClient.query(builder.build());
            total += response.count();
            exclusiveStartKey = response.hasLastEvaluatedKey() ? response.lastEvaluatedKey() : null;
        } while (exclusiveStartKey != null && !exclusiveStartKey.isEmpty());
        return total;
    }
}
