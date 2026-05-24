package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerCampaignLimit {

    public static final String SK_LIMIT = "LIMIT";

    private String pk;
    private String sk;
    private String campaignId;
    private Integer maxRecipients;
    private Integer maxDailyCount;
    private Integer currentDailyCount;
    private String createdAt;

    public static String pk(String campaignId) { return "CAMPAIGN#" + campaignId; }

    @DynamoDbPartitionKey
    public String getPk() { return pk; }

    public void setPk(String pk) { this.pk = pk; }

    @DynamoDbSortKey
    public String getSk() { return sk; }

    public void setSk(String sk) { this.sk = sk; }

    @DynamoDbAttribute("campaign_id")
    public String getCampaignId() { return campaignId; }

    public void setCampaignId(String campaignId) { this.campaignId = campaignId; }

    @DynamoDbAttribute("max_recipients")
    public Integer getMaxRecipients() { return maxRecipients; }

    public void setMaxRecipients(Integer maxRecipients) { this.maxRecipients = maxRecipients; }

    @DynamoDbAttribute("max_daily_count")
    public Integer getMaxDailyCount() { return maxDailyCount; }

    public void setMaxDailyCount(Integer maxDailyCount) { this.maxDailyCount = maxDailyCount; }

    @DynamoDbAttribute("current_daily_count")
    public Integer getCurrentDailyCount() { return currentDailyCount; }

    public void setCurrentDailyCount(Integer currentDailyCount) { this.currentDailyCount = currentDailyCount; }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() { return createdAt; }

    public void setCreatedAt(String createdAt) { this.createdAt = createdAt; }
}