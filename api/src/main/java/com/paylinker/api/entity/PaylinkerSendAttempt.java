package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.SendFailureReason;
import com.paylinker.api.entity.enums.SendJobStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerSendAttempt {

    private String pk;
    private String sk;
    private String sendJobId;
    private Integer attemptNo;
    private SendJobStatus attemptStatus;
    private SendFailureReason failureReason;
    private String sesMessageId;
    private String attemptedAt;
    private Integer ttlEpoch;

    public static String pk(String sendJobId) {
        return "JOB#" + sendJobId;
    }

    public static String sk(Integer attemptNo) {
        return "ATT#" + attemptNo;
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

    @DynamoDbAttribute("attempt_no")
    public Integer getAttemptNo() {
        return attemptNo;
    }

    public void setAttemptNo(Integer attemptNo) {
        this.attemptNo = attemptNo;
    }

    @DynamoDbAttribute("attempt_status")
    public SendJobStatus getAttemptStatus() {
        return attemptStatus;
    }

    public void setAttemptStatus(SendJobStatus attemptStatus) {
        this.attemptStatus = attemptStatus;
    }

    @DynamoDbAttribute("failure_reason")
    public SendFailureReason getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(SendFailureReason failureReason) {
        this.failureReason = failureReason;
    }

    @DynamoDbAttribute("ses_message_id")
    public String getSesMessageId() {
        return sesMessageId;
    }

    public void setSesMessageId(String sesMessageId) {
        this.sesMessageId = sesMessageId;
    }

    @DynamoDbAttribute("attempted_at")
    public String getAttemptedAt() {
        return attemptedAt;
    }

    public void setAttemptedAt(String attemptedAt) {
        this.attemptedAt = attemptedAt;
    }

    @DynamoDbAttribute("ttl_epoch")
    public Integer getTtlEpoch() {
        return ttlEpoch;
    }

    public void setTtlEpoch(Integer ttlEpoch) {
        this.ttlEpoch = ttlEpoch;
    }
}