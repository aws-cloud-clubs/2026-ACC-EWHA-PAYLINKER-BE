package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.LinkErrorType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerLinkAccessLog {

    public static final String RESULT_SUCCESS = "SUCCESS";
    public static final String RESULT_FAIL = "FAIL";

    private String pk;
    private String sk;
    private String secureLinkId;
    private String campaignId;
    private String accessResult;
    private LinkErrorType errorType;
    private String accessedAt;
    private Integer ttlEpoch;

    public static String pk(String secureLinkId) {
        return "LINK#" + secureLinkId;
    }

    public static String sk(String accessedAt, String ulid) {
        return "ACC#" + accessedAt + "#" + ulid;
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

    @DynamoDbAttribute("secure_link_id")
    public String getSecureLinkId() {
        return secureLinkId;
    }

    public void setSecureLinkId(String secureLinkId) {
        this.secureLinkId = secureLinkId;
    }

    @DynamoDbAttribute("campaign_id")
    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    @DynamoDbAttribute("access_result")
    public String getAccessResult() {
        return accessResult;
    }

    public void setAccessResult(String accessResult) {
        this.accessResult = accessResult;
    }

    @DynamoDbAttribute("error_type")
    public LinkErrorType getErrorType() {
        return errorType;
    }

    public void setErrorType(LinkErrorType errorType) {
        this.errorType = errorType;
    }

    @DynamoDbAttribute("accessed_at")
    public String getAccessedAt() {
        return accessedAt;
    }

    public void setAccessedAt(String accessedAt) {
        this.accessedAt = accessedAt;
    }

    @DynamoDbAttribute("ttl_epoch")
    public Integer getTtlEpoch() {
        return ttlEpoch;
    }

    public void setTtlEpoch(Integer ttlEpoch) {
        this.ttlEpoch = ttlEpoch;
    }
}