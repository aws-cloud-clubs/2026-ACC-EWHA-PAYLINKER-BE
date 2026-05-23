package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.DocumentMatchStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerDocumentMatch {

    public static final String INDEX_GSI1 = "GSI1";

    public static final String STATUS_MATCHED = "MATCHED";
    public static final String STATUS_UNMATCHED = "UNMATCHED";
    public static final String STATUS_DUPLICATE_MATCH = "DUPLICATE_MATCH";
    public static final String STATUS_MISMATCHED = "MISMATCHED";

    private String pk;
    private String sk;
    private String campaignId;
    private String campaignRecipientId;
    private String recipientId;
    private String documentId;
    private DocumentMatchStatus matchStatus;
    private String matchKey;
    private String createdAt;
    private String gsi1Pk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String campaignRecipientId) {
        return "MATCH#" + campaignRecipientId;
    }

    public static String gsi1Pk(String campaignId, String matchStatus) {
        return "CAMPAIGN#" + campaignId + "#MST#" + matchStatus;
    }

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbSortKey
    public String getSk() {
        return sk;
    }

    public void setSk(String sk) {
        this.sk = sk;
    }

    @DynamoDbAttribute("campaign_id")
    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    @DynamoDbAttribute("campaign_recipient_id")
    public String getCampaignRecipientId() {
        return campaignRecipientId;
    }

    public void setCampaignRecipientId(String campaignRecipientId) {
        this.campaignRecipientId = campaignRecipientId;
    }

    @DynamoDbAttribute("recipient_id")
    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    @DynamoDbAttribute("document_id")
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    @DynamoDbAttribute("match_status")
    public DocumentMatchStatus getMatchStatus() {
        return matchStatus;
    }

    public void setMatchStatus(DocumentMatchStatus matchStatus) {
        this.matchStatus = matchStatus;
    }

    @DynamoDbAttribute("match_key")
    public String getMatchKey() {
        return matchKey;
    }

    public void setMatchKey(String matchKey) {
        this.matchKey = matchKey;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("gsi1_pk")
    @DynamoDbSecondaryPartitionKey(indexNames = INDEX_GSI1)
    public String getGsi1Pk() {
        return gsi1Pk;
    }

    public void setGsi1Pk(String gsi1Pk) {
        this.gsi1Pk = gsi1Pk;
    }
}
