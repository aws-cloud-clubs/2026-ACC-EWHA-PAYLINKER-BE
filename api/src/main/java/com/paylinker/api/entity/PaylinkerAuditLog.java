package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerAuditLog {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    private String pk;
    private String sk;
    private String auditId;
    private String adminId;
    private String campaignId;
    private String action;
    private String targetType;
    private String targetId;
    private String createdAt;
    private Integer ttlEpoch;
    private String gsi1Pk;
    private String gsi1Sk;
    private String gsi2Pk;
    private String gsi2Sk;

    public static String pk(String yyyyMmDd) {
        return "AUDIT#" + yyyyMmDd;
    }

    public static String sk(String timestamp, String auditId) {
        return timestamp + "#" + auditId;
    }

    public static String gsi1Pk(String adminId) {
        return "ADMIN#" + adminId;
    }

    public static String gsi2Pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
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

    @DynamoDbAttribute("audit_id")
    public String getAuditId() {
        return auditId;
    }

    public void setAuditId(String auditId) {
        this.auditId = auditId;
    }

    @DynamoDbAttribute("admin_id")
    public String getAdminId() {
        return adminId;
    }

    public void setAdminId(String adminId) {
        this.adminId = adminId;
    }

    @DynamoDbAttribute("campaign_id")
    public String getCampaignId() {
        return campaignId;
    }

    public void setCampaignId(String campaignId) {
        this.campaignId = campaignId;
    }

    @DynamoDbAttribute("action")
    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    @DynamoDbAttribute("target_type")
    public String getTargetType() {
        return targetType;
    }

    public void setTargetType(String targetType) {
        this.targetType = targetType;
    }

    @DynamoDbAttribute("target_id")
    public String getTargetId() {
        return targetId;
    }

    public void setTargetId(String targetId) {
        this.targetId = targetId;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
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

    @DynamoDbAttribute("GSI2SK")
    @DynamoDbSecondarySortKey(indexNames = INDEX_GSI2)
    public String getGsi2Sk() {
        return gsi2Sk;
    }

    public void setGsi2Sk(String gsi2Sk) {
        this.gsi2Sk = gsi2Sk;
    }
}