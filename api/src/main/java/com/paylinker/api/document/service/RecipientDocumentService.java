package com.paylinker.api.document.service;

import com.paylinker.api.auth.LinkSession;
import com.paylinker.api.document.dto.RecipientDocumentItem;
import com.paylinker.api.document.dto.RecipientDocumentResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import com.paylinker.api.entity.PaylinkerDocument;
import com.paylinker.api.entity.PaylinkerDocumentMatch;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;

@Service
public class RecipientDocumentService {

    private static final Duration PRESIGN_TTL = Duration.ofMinutes(15);

    private final DynamoDbClient dynamoDbClient;
    private final S3Client s3Client;
    private final String tablePrefix;
    private final String documentBucket;

    public RecipientDocumentService(DynamoDbClient dynamoDbClient,
                                    S3Client s3Client,
                                    @Value("${aws.dynamodb.table-prefix}") String tablePrefix,
                                    @Value("${aws.s3.document-bucket}") String documentBucket) {
        this.dynamoDbClient = dynamoDbClient;
        this.s3Client = s3Client;
        this.tablePrefix = tablePrefix;
        this.documentBucket = documentBucket;
    }

    public RecipientDocumentResponse getMyDocument(LinkSession session) {
        String campaignId = session.campaignId();
        String campaignRecipientId = session.campaignRecipientId();

        Map<String, AttributeValue> campaign = findCampaign(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        Map<String, AttributeValue> recipient = findCampaignRecipient(campaignId, campaignRecipientId);
        if (recipient == null) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        Map<String, AttributeValue> match = findDocumentMatch(campaignId, campaignRecipientId);
        if (match == null) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        String matchStatus = str(match, "match_status");
        if (!PaylinkerDocumentMatch.STATUS_MATCHED.equals(matchStatus)) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }
        String documentId = str(match, "document_id");

        Map<String, AttributeValue> document = findDocument(campaignId, documentId);
        if (document == null) {
            throw new CustomException(ErrorCode.DOCUMENT_NOT_FOUND);
        }

        String storageKey = str(document, "storage_key");
        String documentType = str(document, "document_type");
        Long fileSize = num(document, "file_size_bytes");
        String downloadUrl = presignDownloadUrl(storageKey);
        String filename = extractFilename(storageKey);

        RecipientDocumentItem item = RecipientDocumentItem.builder()
                .documentId(documentId)
                .filename(filename)
                .documentType(documentType)
                .downloadUrl(downloadUrl)
                .fileSizeBytes(fileSize)
                .build();

        return RecipientDocumentResponse.builder()
                .campaignName(str(campaign, "campaign_name"))
                .emailSubject(str(campaign, "email_subject"))
                .emailDescription(str(campaign, "email_description"))
                .recipientName(str(recipient, "name"))
                .documents(List.of(item))
                .viewedAt(str(recipient, "first_viewed_at"))
                .expiresAt(session.expiresAt().atZone(java.time.ZoneOffset.UTC).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME))
                .allowResendRequest(bool(campaign, "allow_resend_request"))
                .build();
    }

    // ── DynamoDB lookups ────────────────────────────────────────

    private Map<String, AttributeValue> findCampaign(String campaignId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaign.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaign.sk())))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findCampaignRecipient(String campaignId, String campaignRecipientId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-campaign-recipient")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerCampaignRecipient.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerCampaignRecipient.sk(campaignRecipientId))))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findDocumentMatch(String campaignId, String campaignRecipientId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-document-match")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerDocumentMatch.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerDocumentMatch.sk(campaignRecipientId))))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    private Map<String, AttributeValue> findDocument(String campaignId, String documentId) {
        var resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tablePrefix + "-document")
                .key(Map.of(
                        "PK", AttributeValue.fromS(PaylinkerDocument.pk(campaignId)),
                        "SK", AttributeValue.fromS(PaylinkerDocument.sk(documentId))))
                .build());
        return resp.hasItem() ? resp.item() : null;
    }

    // ── S3 presigned URL ────────────────────────────────────────

    private String presignDownloadUrl(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) return null;
        try (S3Presigner presigner = S3Presigner.create()) {
            GetObjectPresignRequest req = GetObjectPresignRequest.builder()
                    .signatureDuration(PRESIGN_TTL)
                    .getObjectRequest(GetObjectRequest.builder()
                            .bucket(documentBucket)
                            .key(storageKey)
                            .build())
                    .build();
            return presigner.presignGetObject(req).url().toString();
        }
    }

    private static String extractFilename(String storageKey) {
        if (storageKey == null) return null;
        int slash = storageKey.lastIndexOf('/');
        return slash < 0 ? storageKey : storageKey.substring(slash + 1);
    }

    // ── attribute helper ────────────────────────────────────────

    private static String str(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        return v == null ? null : v.s();
    }

    private static Long num(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        if (v == null || v.n() == null) return null;
        try {
            return Long.parseLong(v.n());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static Boolean bool(Map<String, AttributeValue> item, String key) {
        if (item == null) return null;
        AttributeValue v = item.get(key);
        if (v == null) return null;
        return v.bool();
    }
}
