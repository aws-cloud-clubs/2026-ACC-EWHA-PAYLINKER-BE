package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.SesEventType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerSesEvent {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    private String pk;
    private String sk;
    private String campaignId;
    private String sendJobId;
    private String sesMessageId;
    private SesEventType sesEventType;
    private String eventPayload;
    private String receivedAt;
    private Integer ttlEpoch;
    private String gsi1Pk;
    private String gsi2Pk;
    private String gsi2Sk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String sesMessageId, String receivedAt) {
        return "SES#" + sesMessageId + "#" + receivedAt;
    }

    public static String gsi1Pk(String sesMessageId) {
        return "SES#" + sesMessageId;
    }

    public static String gsi2Pk(String campaignId, String eventType) {
        return "CAMPAIGN#" + campaignId + "#ET#" + eventType;
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

    @DynamoDbAttribute("send_job_id")
    public String getSendJobId() {
        return sendJobId;
    }

    public void setSendJobId(String sendJobId) {
        this.sendJobId = sendJobId;
    }

    @DynamoDbAttribute("ses_message_id")
    public String getSesMessageId() {
        return sesMessageId;
    }

    public void setSesMessageId(String sesMessageId) {
        this.sesMessageId = sesMessageId;
    }

    @DynamoDbAttribute("ses_event_type")
    public SesEventType getSesEventType() {
        return sesEventType;
    }

    public void setSesEventType(SesEventType sesEventType) {
        this.sesEventType = sesEventType;
    }

    @DynamoDbAttribute("event_payload")
    public String getEventPayload() {
        return eventPayload;
    }

    public void setEventPayload(String eventPayload) {
        this.eventPayload = eventPayload;
    }

    @DynamoDbAttribute("received_at")
    public String getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(String receivedAt) {
        this.receivedAt = receivedAt;
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

    @DynamoDbAttribute("GSI2PK")
    @DynamoDbSecondaryPartitionKey(indexNames = INDEX_GSI2)
    public String getGsi2Pk() {
        return gsi2Pk;
    }

    public void setGsi2Pk(String gsi2Pk) {
        this.gsi2Pk = gsi2Pk;
    }

    @DynamoDbAttribute("GSI2SK")
    @DynamoDbSecondarySortKey(indexNames = INDEX_GSI2)
    public String getGsi2Sk() {
        return gsi2Sk;
    }

    public void setGsi2Sk(String gsi2Sk) {
        this.gsi2Sk = gsi2Sk;
    }
}