package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.DocumentMatchStatus;
import com.paylinker.api.entity.enums.RecipientValidationStatus;
import com.paylinker.api.entity.enums.SendFailureReason;
import com.paylinker.api.entity.enums.SendJobStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerCampaignRecipient {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    public static final String SEND_STATUS_FAILED = "FAILED";
    public static final String VIEWED_FALSE = "0";
    public static final String VIEWED_TRUE = "1";

    private String pk;
    private String sk;
    private String campaignRecipientId;
    private String campaignId;
    private String recipientId;
    private String email;
    private String employeeNo;
    private RecipientValidationStatus validationStatus;
    private DocumentMatchStatus documentMatchStatus;
    private SendJobStatus sendStatus;
    private String isViewed;
    private String firstViewedAt;
    private Integer retryCount;
    private SendFailureReason failureReason;
    private Integer reminderCount;
    private String lastReminderSentAt;
    private Integer resendRequestCount;
    private String createdAt;
    private String gsi1Pk;
    private String gsi1Sk;
    private String gsi2Pk;
    private String gsi2Sk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String recipientId) {
        return "RCP#" + recipientId;
    }

    public static String gsi1Pk(String campaignId, String sendStatus) {
        return "CAMPAIGN#" + campaignId + "#ST#" + sendStatus;
    }

    public static String gsi2Pk(String campaignId, String viewedFlag) {
        return "CAMPAIGN#" + campaignId + "#VW#" + viewedFlag;
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

    @DynamoDbAttribute("campaign_recipient_id")
    public String getCampaignRecipientId() {
        return campaignRecipientId;
    }

    public void setCampaignRecipientId(String campaignRecipientId) {
        this.campaignRecipientId = campaignRecipientId;
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

    @DynamoDbAttribute("email")
    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    @DynamoDbAttribute("employee_no")
    public String getEmployeeNo() {
        return employeeNo;
    }

    public void setEmployeeNo(String employeeNo) {
        this.employeeNo = employeeNo;
    }

    @DynamoDbAttribute("validation_status")
    public RecipientValidationStatus getValidationStatus() {
        return validationStatus;
    }

    public void setValidationStatus(RecipientValidationStatus validationStatus) {
        this.validationStatus = validationStatus;
    }

    @DynamoDbAttribute("document_match_status")
    public DocumentMatchStatus getDocumentMatchStatus() {
        return documentMatchStatus;
    }

    public void setDocumentMatchStatus(DocumentMatchStatus documentMatchStatus) {
        this.documentMatchStatus = documentMatchStatus;
    }

    @DynamoDbAttribute("send_status")
    public SendJobStatus  getSendStatus() {
        return sendStatus;
    }

    public void setSendStatus(SendJobStatus  sendStatus) {
        this.sendStatus = sendStatus;
    }

    @DynamoDbAttribute("is_viewed")
    public String getIsViewed() {
        return isViewed;
    }

    public void setIsViewed(String isViewed) {
        this.isViewed = isViewed;
    }

    @DynamoDbAttribute("first_viewed_at")
    public String getFirstViewedAt() {
        return firstViewedAt;
    }

    public void setFirstViewedAt(String firstViewedAt) {
        this.firstViewedAt = firstViewedAt;
    }

    @DynamoDbAttribute("retry_count")
    public Integer getRetryCount() {
        return retryCount;
    }

    public void setRetryCount(Integer retryCount) {
        this.retryCount = retryCount;
    }

    @DynamoDbAttribute("failure_reason")
    public SendFailureReason  getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(SendFailureReason  failureReason) {
        this.failureReason = failureReason;
    }

    @DynamoDbAttribute("reminder_count")
    public Integer getReminderCount() {
        return reminderCount;
    }

    public void setReminderCount(Integer reminderCount) {
        this.reminderCount = reminderCount;
    }

    @DynamoDbAttribute("last_reminder_sent_at")
    public String getLastReminderSentAt() {
        return lastReminderSentAt;
    }

    public void setLastReminderSentAt(String lastReminderSentAt) {
        this.lastReminderSentAt = lastReminderSentAt;
    }

    @DynamoDbAttribute("resend_request_count")
    public Integer getResendRequestCount() {
        return resendRequestCount;
    }

    public void setResendRequestCount(Integer resendRequestCount) {
        this.resendRequestCount = resendRequestCount;
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

    @DynamoDbAttribute("gsi1_sk")
    @DynamoDbSecondarySortKey(indexNames = INDEX_GSI1)
    public String getGsi1Sk() {
        return gsi1Sk;
    }

    public void setGsi1Sk(String gsi1Sk) {
        this.gsi1Sk = gsi1Sk;
    }

    @DynamoDbAttribute("gsi2_pk")
    @DynamoDbSecondaryPartitionKey(indexNames = INDEX_GSI2)
    public String getGsi2Pk() {
        return gsi2Pk;
    }

    public void setGsi2Pk(String gsi2Pk) {
        this.gsi2Pk = gsi2Pk;
    }

    @DynamoDbAttribute("gsi2_sk")
    @DynamoDbSecondarySortKey(indexNames = INDEX_GSI2)
    public String getGsi2Sk() {
        return gsi2Sk;
    }

    public void setGsi2Sk(String gsi2Sk) {
        this.gsi2Sk = gsi2Sk;
    }
}
