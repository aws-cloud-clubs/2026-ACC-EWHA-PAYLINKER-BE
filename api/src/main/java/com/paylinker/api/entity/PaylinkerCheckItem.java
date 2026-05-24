package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.CheckItemStatus;
import com.paylinker.api.entity.enums.CheckItemType;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerCheckItem {

    public static final String INDEX_GSI1 = "GSI1";
    public static final String INDEX_GSI2 = "GSI2";

    private String pk;
    private String sk;
    private String checkItemId;
    private String campaignId;
    private String adminId;
    private String recipientId;
    private CheckItemType itemType;
    private CheckItemStatus checkStatus;
    private String relatedRequestId;
    private String resolvedBy;
    private String createdAt;
    private String gsi1Pk;
    private String gsi1Sk;
    private String gsi2Pk;
    private String gsi2Sk;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String checkItemId) {
        return "CHECK#" + checkItemId;
    }

    public static String gsi1Pk(String checkStatus) {
        return "ST#" + checkStatus;
    }

    public static String gsi2Pk(String adminId, String checkStatus) {
        return "ADMIN#" + adminId + "#ST#" + checkStatus;
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

    @DynamoDbAttribute("check_item_id")
    public String getCheckItemId() {
        return checkItemId;
    }

    public void setCheckItemId(String checkItemId) {
        this.checkItemId = checkItemId;
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

    @DynamoDbAttribute("recipient_id")
    public String getRecipientId() {
        return recipientId;
    }

    public void setRecipientId(String recipientId) {
        this.recipientId = recipientId;
    }

    @DynamoDbAttribute("item_type")
    public CheckItemType getItemType() {
        return itemType;
    }

    public void setItemType(CheckItemType itemType) {
        this.itemType = itemType;
    }

    @DynamoDbAttribute("check_status")
    public CheckItemStatus getCheckStatus() {
        return checkStatus;
    }

    public void setCheckStatus(CheckItemStatus checkStatus) {
        this.checkStatus = checkStatus;
    }

    @DynamoDbAttribute("related_request_id")
    public String getRelatedRequestId() {
        return relatedRequestId;
    }

    public void setRelatedRequestId(String relatedRequestId) {
        this.relatedRequestId = relatedRequestId;
    }

    @DynamoDbAttribute("resolved_by")
    public String getResolvedBy() {
        return resolvedBy;
    }

    public void setResolvedBy(String resolvedBy) {
        this.resolvedBy = resolvedBy;
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