package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.LinkErrorType;
import com.paylinker.api.entity.enums.ResendRequestStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerResendRequest {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    private String pk;
    private String sk;
    private String requestId;
    private String campaignId;
    private String recipientId;
    private String secureLinkId;
    private ResendRequestStatus reqStatus;
    private LinkErrorType resendReason;
    private String requestedAt;
    private String processedBy;
    private String processedAt;
    private String newSecureLinkId;
    private String newSendJobId;
    private String gsi1Pk;
    private String gsi1Sk;
    private String gsi2Pk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String requestId) {
        return "RESEND#" + requestId;
    }

    public static String gsi1Pk(String reqStatus) {
        return "ST#" + reqStatus;
    }

    public static String gsi2Pk(String recipientId) {
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

    @DynamoDbAttribute("request_id")
    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
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

    @DynamoDbAttribute("req_status")
    public ResendRequestStatus getReqStatus() {
        return reqStatus;
    }

    public void setReqStatus(ResendRequestStatus reqStatus) {
        this.reqStatus = reqStatus;
    }

    @DynamoDbAttribute("resend_reason")
    public LinkErrorType getResendReason() {
        return resendReason;
    }

    public void setResendReason(LinkErrorType resendReason) {
        this.resendReason = resendReason;
    }

    @DynamoDbAttribute("requested_at")
    public String getRequestedAt() {
        return requestedAt;
    }

    public void setRequestedAt(String requestedAt) {
        this.requestedAt = requestedAt;
    }

    @DynamoDbAttribute("processed_by")
    public String getProcessedBy() {
        return processedBy;
    }

    public void setProcessedBy(String processedBy) {
        this.processedBy = processedBy;
    }

    @DynamoDbAttribute("processed_at")
    public String getProcessedAt() {
        return processedAt;
    }

    public void setProcessedAt(String processedAt) {
        this.processedAt = processedAt;
    }

    @DynamoDbAttribute("new_secure_link_id")
    public String getNewSecureLinkId() {
        return newSecureLinkId;
    }

    public void setNewSecureLinkId(String newSecureLinkId) {
        this.newSecureLinkId = newSecureLinkId;
    }

    @DynamoDbAttribute("new_send_job_id")
    public String getNewSendJobId() {
        return newSendJobId;
    }

    public void setNewSendJobId(String newSendJobId) {
        this.newSendJobId = newSendJobId;
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