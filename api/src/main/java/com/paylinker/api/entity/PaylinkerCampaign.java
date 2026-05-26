package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.CampaignStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerCampaign {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String SK_METADATA = "METADATA";

    private String pk;
    private String sk;
    private String campaignId;
    private String adminId;
    private String campaignName;
    private String emailSubject;
    private String emailDescription;
    private CampaignStatus status;
    private Integer linkTtlHours;
    private Boolean allowOneTimeLink;
    private Boolean allowResendRequest;
    private Integer resendRequestLimit;
    private String scheduledSendAt;
    private String sendStartedAt;
    private String sendCompletedAt;
    private String cancelledAt;
    private Integer sendSuccessCount;
    private Integer sendFailedCount;
    private Integer viewedCount;
    private Integer unviewedCount;
    private Integer totalRecipientCount;
    private String createdAt;
    private String gsi1Pk;
    private String gsi1Sk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk() {
        return SK_METADATA;
    }

    public static String gsi1Pk(String adminId) {
        return "ADMIN#" + adminId;
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

    @DynamoDbAttribute("email_subject")
    public String getEmailSubject() {
        return emailSubject;
    }

    public void setEmailSubject(String emailSubject) {
        this.emailSubject = emailSubject;
    }

    @DynamoDbAttribute("email_description")
    public String getEmailDescription() {
        return emailDescription;
    }

    public void setEmailDescription(String emailDescription) {
        this.emailDescription = emailDescription;
    }

    @DynamoDbAttribute("status")
    public CampaignStatus getStatus() {
        return status;
    }

    public void setStatus(CampaignStatus status) {
        this.status = status;
    }

    @DynamoDbAttribute("link_ttl_hours")
    public Integer getLinkTtlHours() {
        return linkTtlHours;
    }

    public void setLinkTtlHours(Integer linkTtlHours) {
        this.linkTtlHours = linkTtlHours;
    }

    @DynamoDbAttribute("allow_one_time_link")
    public Boolean getAllowOneTimeLink() {
        return allowOneTimeLink;
    }

    public void setAllowOneTimeLink(Boolean allowOneTimeLink) {
        this.allowOneTimeLink = allowOneTimeLink;
    }

    @DynamoDbAttribute("allow_resend_request")
    public Boolean getAllowResendRequest() {
        return allowResendRequest;
    }

    public void setAllowResendRequest(Boolean allowResendRequest) {
        this.allowResendRequest = allowResendRequest;
    }

    @DynamoDbAttribute("resend_request_limit")
    public Integer getResendRequestLimit() {
        return resendRequestLimit;
    }

    public void setResendRequestLimit(Integer resendRequestLimit) {
        this.resendRequestLimit = resendRequestLimit;
    }

    @DynamoDbAttribute("scheduled_send_at")
    public String getScheduledSendAt() {
        return scheduledSendAt;
    }

    public void setScheduledSendAt(String scheduledSendAt) {
        this.scheduledSendAt = scheduledSendAt;
    }

    @DynamoDbAttribute("send_started_at")
    public String getSendStartedAt() {
        return sendStartedAt;
    }

    public void setSendStartedAt(String sendStartedAt) {
        this.sendStartedAt = sendStartedAt;
    }

    @DynamoDbAttribute("send_completed_at")
    public String getSendCompletedAt() {
        return sendCompletedAt;
    }

    public void setSendCompletedAt(String sendCompletedAt) {
        this.sendCompletedAt = sendCompletedAt;
    }

    @DynamoDbAttribute("cancelled_at")
    public String getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(String cancelledAt) {
        this.cancelledAt = cancelledAt;
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

    @DynamoDbAttribute("total_recipient_count")
    public Integer getTotalRecipientCount() {
        return totalRecipientCount;
    }

    public void setTotalRecipientCount(Integer totalRecipientCount) {
        this.totalRecipientCount = totalRecipientCount;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
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