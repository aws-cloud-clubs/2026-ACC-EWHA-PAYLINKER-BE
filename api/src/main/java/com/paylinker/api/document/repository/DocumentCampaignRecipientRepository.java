package com.paylinker.api.document.repository;

import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

@Repository
public class DocumentCampaignRecipientRepository {

    private final DynamoDbTable<PaylinkerCampaignRecipient> table;

    public DocumentCampaignRecipientRepository(DynamoDbEnhancedClient enhancedClient,
                                               @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.table = enhancedClient.table(
                tablePrefix + "-campaign-recipient",
                TableSchema.fromBean(PaylinkerCampaignRecipient.class));
    }

    public List<PaylinkerCampaignRecipient> findAll(String campaignId) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(PaylinkerCampaignRecipient.pk(campaignId)).build()))
                .build();
        return StreamSupport.stream(table.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .collect(Collectors.toList());
    }
}
