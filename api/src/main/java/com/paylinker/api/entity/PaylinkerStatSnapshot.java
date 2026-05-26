package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerStatSnapshot {

    private String pk;
    private String sk;
    private String campaignId;
    private String snapshotAt;
    private Integer successCount;
    private Integer failedCount;
    private Integer viewedCount;
    private Integer unviewedCount;
    private Double viewRate;
    private Integer ttlEpoch;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String snapshotAt) {
        return "STAT#" + snapshotAt;
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

    @DynamoDbAttribute("snapshot_at")
    public String getSnapshotAt() {
        return snapshotAt;
    }

    public void setSnapshotAt(String snapshotAt) {
        this.snapshotAt = snapshotAt;
    }

    @DynamoDbAttribute("success_count")
    public Integer getSuccessCount() {
        return successCount;
    }

    public void setSuccessCount(Integer successCount) {
        this.successCount = successCount;
    }

    @DynamoDbAttribute("failed_count")
    public Integer getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Integer failedCount) {
        this.failedCount = failedCount;
    }

    @DynamoDbAttribute("viewed_count")
    public Integer getViewedCount() {
        return viewedCount;
    }

    public void setViewedCount(Integer viewedCount) {
        this.viewedCount = viewedCount;
    }

    @DynamoDbAttribute("unviewed_count")
    public Integer getUnviewedCount() {
        return unviewedCount;
    }

    public void setUnviewedCount(Integer unviewedCount) {
        this.unviewedCount = unviewedCount;
    }

    @DynamoDbAttribute("view_rate")
    public Double getViewRate() {
        return viewRate;
    }

    public void setViewRate(Double viewRate) {
        this.viewRate = viewRate;
    }

    @DynamoDbAttribute("ttl_epoch")
    public Integer getTtlEpoch() {
        return ttlEpoch;
    }

    public void setTtlEpoch(Integer ttlEpoch) {
        this.ttlEpoch = ttlEpoch;
    }
}