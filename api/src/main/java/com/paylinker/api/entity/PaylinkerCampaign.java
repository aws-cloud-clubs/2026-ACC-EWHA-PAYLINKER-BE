package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerCampaign {

    public static final String SK_METADATA = "METADATA";

    private String pk;
    private String sk;
    private String campaignId;
    private String adminId;
    private String campaignName;
    private String status;
    private String sendCompletedAt;
    private Integer totalRecipientCount;
    private Integer sendSuccessCount;
    private Integer sendFailedCount;
    private Integer viewedCount;
    private Integer unviewedCount;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
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

    @DynamoDbAttribute("admin_id")
    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    @DynamoDbAttribute("campaign_name")
    public String getCampaignName() {
        return campaignName;
    }

    public void setCampaignName(String campaignName) {
        this.campaignName = campaignName;
    }

    @DynamoDbAttribute("status")
    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @DynamoDbAttribute("send_completed_at")
    public String getSendCompletedAt() {
        return sendCompletedAt;
    }

    public void setSendCompletedAt(String sendCompletedAt) {
        this.sendCompletedAt = sendCompletedAt;
    }

    @DynamoDbAttribute("total_recipient_count")
    public Integer getTotalRecipientCount() {
        return totalRecipientCount;
    }

    public void setTotalRecipientCount(Integer totalRecipientCount) {
        this.totalRecipientCount = totalRecipientCount;
    }

    @DynamoDbAttribute("send_success_count")
    public Integer getSendSuccessCount() {
        return sendSuccessCount;
    }

    public void setSendSuccessCount(Integer sendSuccessCount) {
        this.sendSuccessCount = sendSuccessCount;
    }

    @DynamoDbAttribute("send_failed_count")
    public Integer getSendFailedCount() {
        return sendFailedCount;
    }

    public void setSendFailedCount(Integer sendFailedCount) {
        this.sendFailedCount = sendFailedCount;
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
}
