package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.SendFailureReason;
import com.paylinker.api.entity.enums.SendJobStatus;
import com.paylinker.api.entity.enums.SendJobType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerSendJob {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    private String pk;
    private String sk;
    private String sendJobId;
    private String campaignId;
    private String campaignRecipientId;
    private String secureLinkId;
    private SendJobType jobType;
    private SendJobStatus jobStatus;
    private String requestedByAdminId;
    private String sesMessageId;
    private SendFailureReason failureReason;
    private String requestedAt;
    private Integer ttlEpoch;
    private String gsi1Pk;
    private String gsi1Sk;
    private String gsi2Pk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String sendJobId) {
        return "JOB#" + sendJobId;
    }

    public static String gsi1Pk(String campaignRecipientId) {
        return "CR#" + campaignRecipientId;
    }

    public static String gsi2Pk(String sesMessageId) {
        return "SES#" + sesMessageId;
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

    @DynamoDbAttribute("send_job_id")
    public String getSendJobId() {
        return sendJobId;
    }

    public void setSendJobId(String sendJobId) {
        this.sendJobId = sendJobId;
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

    @DynamoDbAttribute("secure_link_id")
    public String getSecureLinkId() {
        return secureLinkId;
    }

    public void setSecureLinkId(String secureLinkId) {
        this.secureLinkId = secureLinkId;
    }

    @DynamoDbAttribute("job_type")
    public SendJobType getJobType() {
        return jobType;
    }

    public void setJobType(SendJobType jobType) {
        this.jobType = jobType;
    }

    @DynamoDbAttribute("job_status")
    public SendJobStatus getJobStatus() {
        return jobStatus;
    }

    public void setJobStatus(SendJobStatus jobStatus) {
        this.jobStatus = jobStatus;
    }

    @DynamoDbAttribute("requested_by_admin_id")
    public String getRequestedByAdminId() {
        return requestedByAdminId;
    }

    public void setRequestedByAdminId(String requestedByAdminId) {
        this.requestedByAdminId = requestedByAdminId;
    }

    @DynamoDbAttribute("ses_message_id")
    public String getSesMessageId() {
        return sesMessageId;
    }

    public void setSesMessageId(String sesMessageId) {
        this.sesMessageId = sesMessageId;
    }

    @DynamoDbAttribute("failure_reason")
    public SendFailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(SendFailureReason failureReason) {
        this.failureReason = failureReason;
    }

    @DynamoDbAttribute("requested_at")
    public String getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(String requestedAt) {
        this.requestedAt = requestedAt;
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

    @DynamoDbAttribute("GSI2PK")
    @DynamoDbSecondaryPartitionKey(indexNames = INDEX_GSI2)
    public String getGsi2Pk() {
        return gsi2Pk;
    }

    public void setGsi2Pk(String gsi2Pk) {
        this.gsi2Pk = gsi2Pk;
    }
}