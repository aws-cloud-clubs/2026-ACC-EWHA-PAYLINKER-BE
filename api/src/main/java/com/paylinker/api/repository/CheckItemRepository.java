package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerCheckItem;
import com.paylinker.api.entity.enums.CheckItemStatus;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
import software.amazon.awssdk.services.dynamodb.model.QueryResponse;
import software.amazon.awssdk.services.dynamodb.model.Select;

@Repository
public class CheckItemRepository {

    private final DynamoDbClient dynamoDbClient;
    private final String tableName;

    public CheckItemRepository(DynamoDbClient dynamoDbClient,
                               @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.dynamoDbClient = dynamoDbClient;
        this.tableName = tablePrefix + "-check-item";
    }

    public int countUnresolved(String adminId) {
        return countByAdminAndStatus(adminId, CheckItemStatus.OPEN.name())
                + countByAdminAndStatus(adminId, CheckItemStatus.IN_PROGRESS.name());
    }

    private int countByAdminAndStatus(String adminId, String checkStatus) {
        int total = 0;
        Map<String, AttributeValue> exclusiveStartKey = null;
        do {
            QueryRequest.Builder builder = QueryRequest.builder()
                    .tableName(tableName)
                    .indexName(PaylinkerCheckItem.INDEX_GSI2)
                    .keyConditionExpression("#pk = :pk")
                    .expressionAttributeNames(Map.of("#pk", "GSI2PK"))
                    .expressionAttributeValues(Map.of(
                            ":pk", AttributeValue.fromS(PaylinkerCheckItem.gsi2Pk(adminId, checkStatus))))
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
