package com.paylinker.api.repository;

import com.paylinker.api.entity.PaylinkerCheckItem;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.QueryRequest;
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

    public int countUnresolved() {
        return countByStatus(PaylinkerCheckItem.STATUS_OPEN)
                + countByStatus(PaylinkerCheckItem.STATUS_IN_PROGRESS);
    }

    private int countByStatus(String checkStatus) {
        QueryRequest request = QueryRequest.builder()
                .tableName(tableName)
                .indexName(PaylinkerCheckItem.INDEX_GSI1)
                .keyConditionExpression("#pk = :pk")
                .expressionAttributeNames(Map.of("#pk", "gsi1_pk"))
                .expressionAttributeValues(Map.of(
                        ":pk", AttributeValue.fromS(PaylinkerCheckItem.gsi1Pk(checkStatus))))
                .select(Select.COUNT)
                .build();
        return dynamoDbClient.query(request).count();
    }
}
