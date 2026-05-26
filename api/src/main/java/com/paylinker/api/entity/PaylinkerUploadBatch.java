package com.paylinker.api.entity;

import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerUploadBatch {

    public static final String UPLOAD_TYPE_DOCUMENT = "DOCUMENT";
    public static final String UPLOAD_TYPE_RECIPIENT = "RECIPIENT";

    private String pk;
    private String sk;
    private String uploadBatchId;
    private String campaignId;
    private String adminId;
    private String uploadType;
    private String fileStorageKey;
    private Integer totalRowCount;
    private Integer validRowCount;
    private Integer errorRowCount;
    private String createdAt;

    public static String pk(String campaignId) {
        return "CAMPAIGN#" + campaignId;
    }

    public static String sk(String uploadBatchId) {
        return "UPLOAD#" + uploadBatchId;
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

    @DynamoDbAttribute("upload_batch_id")
    public String getUploadBatchId() {
        return uploadBatchId;
    }

    public void setUploadBatchId(String uploadBatchId) {
        this.uploadBatchId = uploadBatchId;
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

    @DynamoDbAttribute("upload_type")
    public String getUploadType() {
        return uploadType;
    }

    public void setUploadType(String uploadType) {
        this.uploadType = uploadType;
    }

    @DynamoDbAttribute("file_storage_key")
    public String getFileStorageKey() {
        return fileStorageKey;
    }

    public void setFileStorageKey(String fileStorageKey) {
        this.fileStorageKey = fileStorageKey;
    }

    @DynamoDbAttribute("total_row_count")
    public Integer getTotalRowCount() {
        return totalRowCount;
    }

    public void setTotalRowCount(Integer totalRowCount) {
        this.totalRowCount = totalRowCount;
    }

    @DynamoDbAttribute("valid_row_count")
    public Integer getValidRowCount() {
        return validRowCount;
    }

    public void setValidRowCount(Integer validRowCount) {
        this.validRowCount = validRowCount;
    }

    @DynamoDbAttribute("error_row_count")
    public Integer getErrorRowCount() {
        return errorRowCount;
    }

    public void setErrorRowCount(Integer errorRowCount) {
        this.errorRowCount = errorRowCount;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }
}
