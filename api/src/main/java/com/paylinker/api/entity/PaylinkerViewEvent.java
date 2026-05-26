package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerViewEvent {

    public static final String INDEX_GSI1 = "GSI1";

    private String pk;
    private String sk;
    private String campaignId;
    private String recipientId;
    private String secureLinkId;
    private String documentId;
    private Boolean isFirstView;
    private String viewedAt;
    private Integer ttlEpoch;
    private String gsi1Pk;
    private String gsi1Sk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String recipientId, String viewedAt, String ulid) {
        return "VIEW#" + recipientId + "#" + viewedAt + "#" + ulid;
    }

    public static String gsi1Pk(String recipientId) {
        return "RCP#" + recipientId;
    }

    @DynamoDbAttribute("PK")

    @DynamoDbPartitionKey
    public String getPk() {
        return pk;
    }

    public void setPk(String pk) {
        this.pk = pk;
    }

    @DynamoDbAttribute("SK")

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

    @DynamoDbAttribute("recipient_id")
    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    @DynamoDbAttribute("secure_link_id")
    public String getSecureLinkId() {
        return secureLinkId;
    }

    public void setSecureLinkId(String secureLinkId) {
        this.secureLinkId = secureLinkId;
    }

    @DynamoDbAttribute("document_id")
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    @DynamoDbAttribute("is_first_view")
    public Boolean getIsFirstView() {
        return isFirstView;
    }

    public void setIsFirstView(Boolean isFirstView) {
        this.isFirstView = isFirstView;
    }

    @DynamoDbAttribute("viewed_at")
    public String getViewedAt() {
        return viewedAt;
    }

    public void setViewedAt(String viewedAt) {
        this.viewedAt = viewedAt;
    }

    @DynamoDbAttribute("ttl_epoch")
    public Integer getTtlEpoch() {
        return ttlEpoch;
    }

    public void setTtlEpoch(Integer ttlEpoch) {
        this.ttlEpoch = ttlEpoch;
    }

    @DynamoDbAttribute("GSI1PK")
    @DynamoDbSecondaryPartitionKey(indexNames = INDEX_GSI1)
    public String getGsi1Pk() {
        return gsi1Pk;
    }

    public void setGsi1Pk(String gsi1Pk) {
        this.gsi1Pk = gsi1Pk;
    }

    @DynamoDbAttribute("GSI1SK")
    @DynamoDbSecondarySortKey(indexNames = INDEX_GSI1)
    public String getGsi1Sk() {
        return gsi1Sk;
    }

    public void setGsi1Sk(String gsi1Sk) {
        this.gsi1Sk = gsi1Sk;
    }
}