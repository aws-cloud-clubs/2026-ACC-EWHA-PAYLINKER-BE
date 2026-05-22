package com.paylinker.api.entity;

import com.paylinker.api.entity.enums.LinkErrorType;
import com.paylinker.api.entity.enums.LinkStatus;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbAttribute;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondarySortKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSortKey;

@DynamoDbBean
public class PaylinkerSecureLink {

    public static final String INDEX_GSI1 = "gsi1";
    public static final String SK_METADATA = "METADATA";

    private String pk;
    private String sk;
    private String secureLinkId;
    private String campaignId;
    private String campaignRecipientId;
    private String documentId;
    private String tokenHash;
    private LinkStatus linkStatus;
    private String expiresAt;
    private String firstAccessedAt;
    private String usedAt;
    private Integer accessCount;
    private LinkErrorType lastErrorType;
    private String createdAt;
    private Long ttlEpoch;
    private String gsi1Pk;
    private String gsi1Sk;

    public static String pk(String tokenHash) {
        return "TOKEN#" + tokenHash;
    }

    public static String gsi1Pk(String campaignRecipientId) {
        return "CR#" + campaignRecipientId;
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

    @DynamoDbAttribute("campaign_recipient_id")
    public String getCampaignRecipientId() {
        return campaignRecipientId;
    }

    public void setCampaignRecipientId(String campaignRecipientId) {
        this.campaignRecipientId = campaignRecipientId;
    }

    @DynamoDbAttribute("document_id")
    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    @DynamoDbAttribute("token_hash")
    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    @DynamoDbAttribute("link_status")
    public LinkStatus getLinkStatus() {
        return linkStatus;
    }

    public void setLinkStatus(LinkStatus linkStatus) {
        this.linkStatus = linkStatus;
    }

    @DynamoDbAttribute("expires_at")
    public String getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(String expiresAt) {
        this.expiresAt = expiresAt;
    }

    @DynamoDbAttribute("first_accessed_at")
    public String getFirstAccessedAt() {
        return firstAccessedAt;
    }

    public void setFirstAccessedAt(String firstAccessedAt) {
        this.firstAccessedAt = firstAccessedAt;
    }

    @DynamoDbAttribute("used_at")
    public String getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(String usedAt) {
        this.usedAt = usedAt;
    }

    @DynamoDbAttribute("access_count")
    public Integer getAccessCount() {
        return accessCount;
    }

    public void setAccessCount(Integer accessCount) {
        this.accessCount = accessCount;
    }

    @DynamoDbAttribute("last_error_type")
    public LinkErrorType getLastErrorType() {
        return lastErrorType;
    }

    public void setLastErrorType(LinkErrorType lastErrorType) {
        this.lastErrorType = lastErrorType;
    }

    @DynamoDbAttribute("created_at")
    public String getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(String createdAt) {
        this.createdAt = createdAt;
    }

    @DynamoDbAttribute("ttl_epoch")
    public Long getTtlEpoch() {
        return ttlEpoch;
    }

    public void setTtlEpoch(Long ttlEpoch) {
        this.ttlEpoch = ttlEpoch;
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
}