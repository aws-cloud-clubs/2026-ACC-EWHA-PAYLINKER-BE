package com.paylinker.api.document.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.document.dto.DocumentMatchItem;
import com.paylinker.api.document.dto.DocumentMatchResultsResponse;
import com.paylinker.api.document.dto.DocumentUploadResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignRecipient;
import com.paylinker.api.entity.PaylinkerDocument;
import com.paylinker.api.entity.PaylinkerDocumentMatch;
import com.paylinker.api.entity.PaylinkerRecipient;
import com.paylinker.api.entity.PaylinkerUploadBatch;
import com.paylinker.api.repository.CampaignRecipientRepository;
import com.paylinker.api.repository.CampaignRepository;
import com.paylinker.api.repository.DocumentMatchRepository;
import com.paylinker.api.repository.DocumentRepository;
import com.paylinker.api.repository.RecipientRepository;
import com.paylinker.api.repository.UploadBatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import com.paylinker.common.util.MaskingUtil;
import java.io.IOException;
import java.io.InputStream;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentService {

    private static final long MAX_FILE_SIZE_BYTES = 50L * 1024 * 1024;
    private static final String FILE_EXTENSION_JSON = ".json";
    private static final String MATCH_KEY_EMPLOYEE_NO = "EMPLOYEE_NO";
    private static final String MATCH_KEY_EMAIL = "EMAIL";
    private static final String COLUMN_EMPLOYEE_NO = "employeeNo";
    private static final String COLUMN_EMAIL = "email";
    private static final Set<String> ALLOWED_DOCUMENT_TYPES = Set.of("PDF", "HTML", "JSON");
    private static final Set<String> ALLOWED_MATCH_KEYS = Set.of(MATCH_KEY_EMPLOYEE_NO, MATCH_KEY_EMAIL);
    private static final Set<String> UPLOADABLE_CAMPAIGN_STATUSES = Set.of("DRAFT", "READY");

    private final CampaignRepository campaignRepository;
    private final CampaignRecipientRepository campaignRecipientRepository;
    private final RecipientRepository recipientRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final DocumentRepository documentRepository;
    private final DocumentMatchRepository documentMatchRepository;
    private final S3UploadService s3UploadService;
    private final ObjectMapper objectMapper;

    public DocumentService(CampaignRepository campaignRepository,
                           CampaignRecipientRepository campaignRecipientRepository,
                           RecipientRepository recipientRepository,
                           UploadBatchRepository uploadBatchRepository,
                           DocumentRepository documentRepository,
                           DocumentMatchRepository documentMatchRepository,
                           S3UploadService s3UploadService,
                           ObjectMapper objectMapper) {
        this.campaignRepository = campaignRepository;
        this.campaignRecipientRepository = campaignRecipientRepository;
        this.recipientRepository = recipientRepository;
        this.uploadBatchRepository = uploadBatchRepository;
        this.documentRepository = documentRepository;
        this.documentMatchRepository = documentMatchRepository;
        this.s3UploadService = s3UploadService;
        this.objectMapper = objectMapper;
    }

    public DocumentUploadResponse upload(String adminId,
                                         String campaignId,
                                         MultipartFile file,
                                         String documentType,
                                         String matchKey) {
        validateUploadInput(file, documentType, matchKey);

        PaylinkerCampaign campaign = verifyOwnership(adminId, campaignId);
        if (!UPLOADABLE_CAMPAIGN_STATUSES.contains(campaign.getStatus())) {
            throw new CustomException(ErrorCode.DOCUMENT_CAMPAIGN_INVALID_STATUS);
        }

        List<PaylinkerCampaignRecipient> recipients = campaignRecipientRepository.findAll(campaignId);
        if (recipients.isEmpty()) {
            throw new CustomException(ErrorCode.DOCUMENT_RECIPIENTS_NOT_READY);
        }

        String matchKeyColumn = MATCH_KEY_EMPLOYEE_NO.equals(matchKey) ? COLUMN_EMPLOYEE_NO : COLUMN_EMAIL;
        List<Map<String, Object>> rows = parseRows(file);
        validateRowsContainMatchKey(rows, matchKeyColumn);

        String uploadBatchId = UUID.randomUUID().toString();
        String storageKey = "documents/" + campaignId + "/" + uploadBatchId + ".json";
        try {
            s3UploadService.upload(storageKey, file);
        } catch (IOException e) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_UNSUPPORTED);
        }

        String now = OffsetDateTime.now(ZoneOffset.UTC).toString();
        List<PaylinkerDocument> documents = buildDocuments(campaignId, rows, matchKeyColumn, documentType, storageKey, file.getSize(), now);
        documentRepository.saveAll(documents);

        MatchOutcome outcome = matchDocumentsToRecipients(campaignId, recipients, documents, matchKey, now);
        documentMatchRepository.saveAll(outcome.matches);

        uploadBatchRepository.save(buildUploadBatch(campaignId, uploadBatchId, adminId, storageKey, rows.size(), now));

        return new DocumentUploadResponse(
                uploadBatchId,
                campaignId,
                documents.size(),
                outcome.matchedCount,
                outcome.unmatchedDocumentCount,
                outcome.unmatchedRecipientCount,
                outcome.duplicateMatchCount);
    }

    public DocumentMatchResultsResponse getMatchResults(String adminId, String campaignId, String filter) {
        PaylinkerCampaign campaign = verifyOwnership(adminId, campaignId);

        int matchedCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_MATCHED);
        int unmatchedRecipientCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_UNMATCHED);
        int duplicateMatchCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH);
        int totalDocumentCount = documentRepository.countByCampaign(campaignId);
        int totalRecipientCount = campaign.getTotalRecipientCount() == null ? 0 : campaign.getTotalRecipientCount();

        boolean canProceed = matchedCount > 0
                && duplicateMatchCount == 0
                && unmatchedRecipientCount == 0;

        List<PaylinkerDocumentMatch> rows = new ArrayList<>();
        for (String status : resolveFilterStatuses(filter)) {
            rows.addAll(documentMatchRepository.findByStatus(campaignId, status));
        }

        Set<String> recipientIds = rows.stream()
                .map(PaylinkerDocumentMatch::getRecipientId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<String, PaylinkerRecipient> recipientMap = recipientRepository.findAllByIds(recipientIds);

        List<DocumentMatchItem> items = rows.stream()
                .map(row -> toMatchItem(row, recipientMap))
                .toList();

        return new DocumentMatchResultsResponse(
                campaignId,
                totalRecipientCount,
                totalDocumentCount,
                matchedCount,
                unmatchedRecipientCount,
                duplicateMatchCount,
                canProceed,
                items);
    }

    private List<String> resolveFilterStatuses(String filter) {
        if (filter == null || filter.isBlank() || "ALL".equalsIgnoreCase(filter)) {
            return List.of(
                    PaylinkerDocumentMatch.STATUS_UNMATCHED,
                    PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH,
                    PaylinkerDocumentMatch.STATUS_MISMATCHED,
                    PaylinkerDocumentMatch.STATUS_MATCHED);
        }
        return switch (filter.toUpperCase()) {
            case "UNMATCHED" -> List.of(PaylinkerDocumentMatch.STATUS_UNMATCHED);
            case "DUPLICATE" -> List.of(PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH);
            case "MATCHED" -> List.of(PaylinkerDocumentMatch.STATUS_MATCHED);
            default -> List.of(
                    PaylinkerDocumentMatch.STATUS_UNMATCHED,
                    PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH,
                    PaylinkerDocumentMatch.STATUS_MISMATCHED,
                    PaylinkerDocumentMatch.STATUS_MATCHED);
        };
    }

    private DocumentMatchItem toMatchItem(PaylinkerDocumentMatch row, Map<String, PaylinkerRecipient> recipientMap) {
        PaylinkerRecipient recipient = row.getRecipientId() == null
                ? null
                : recipientMap.get(row.getRecipientId());
        return new DocumentMatchItem(
                row.getCampaignRecipientId(),
                recipient == null ? null : recipient.getName(),
                recipient == null ? null : MaskingUtil.maskEmployeeNo(recipient.getEmployeeNo()),
                recipient == null ? null : MaskingUtil.maskEmail(recipient.getEmail()),
                row.getMatchStatus(),
                row.getMatchKey(),
                row.getDocumentId());
    }

    private PaylinkerCampaign verifyOwnership(String adminId, String campaignId) {
        PaylinkerCampaign campaign = campaignRepository.findByCampaignId(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        if (!adminId.equals(campaign.getAdminId())) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }
        return campaign;
    }

    private void validateUploadInput(MultipartFile file, String documentType, String matchKey) {
        if (file == null || file.isEmpty()) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_UNSUPPORTED);
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_TOO_LARGE);
        }
        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null || !originalFilename.toLowerCase().endsWith(FILE_EXTENSION_JSON)) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_UNSUPPORTED);
        }
        if (documentType == null || !ALLOWED_DOCUMENT_TYPES.contains(documentType)) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_UNSUPPORTED);
        }
        if (matchKey == null || !ALLOWED_MATCH_KEYS.contains(matchKey)) {
            throw new CustomException(ErrorCode.DOCUMENT_MATCH_KEY_MISSING);
        }
    }

    private List<Map<String, Object>> parseRows(MultipartFile file) {
        try (InputStream is = file.getInputStream()) {
            return objectMapper.readValue(is, new TypeReference<List<Map<String, Object>>>() {});
        } catch (IOException e) {
            throw new CustomException(ErrorCode.DOCUMENT_FILE_UNSUPPORTED);
        }
    }

    private void validateRowsContainMatchKey(List<Map<String, Object>> rows, String matchKeyColumn) {
        if (rows.isEmpty()) {
            throw new CustomException(ErrorCode.DOCUMENT_MATCH_KEY_MISSING);
        }
        boolean missing = rows.stream().anyMatch(row -> {
            Object value = row.get(matchKeyColumn);
            return value == null || String.valueOf(value).isBlank();
        });
        if (missing) {
            throw new CustomException(ErrorCode.DOCUMENT_MATCH_KEY_MISSING);
        }
    }

    private List<PaylinkerDocument> buildDocuments(String campaignId,
                                                   List<Map<String, Object>> rows,
                                                   String matchKeyColumn,
                                                   String documentType,
                                                   String storageKey,
                                                   long fileSize,
                                                   String now) {
        List<PaylinkerDocument> documents = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            String ownerKey = String.valueOf(row.get(matchKeyColumn));
            String documentId = UUID.randomUUID().toString();
            PaylinkerDocument doc = new PaylinkerDocument();
            doc.setPk(PaylinkerDocument.pk(campaignId));
            doc.setSk(PaylinkerDocument.sk(documentId));
            doc.setDocumentId(documentId);
            doc.setCampaignId(campaignId);
            doc.setOwnerKey(ownerKey);
            doc.setStorageKey(storageKey);
            doc.setDocumentType(documentType);
            doc.setFileSizeBytes(fileSize);
            doc.setCreatedAt(now);
            doc.setGsi1Pk(PaylinkerDocument.gsi1Pk(campaignId, ownerKey));
            documents.add(doc);
        }
        return documents;
    }

    private MatchOutcome matchDocumentsToRecipients(String campaignId,
                                                    List<PaylinkerCampaignRecipient> recipients,
                                                    List<PaylinkerDocument> documents,
                                                    String matchKey,
                                                    String now) {
        Map<String, List<PaylinkerDocument>> documentsByOwnerKey = documents.stream()
                .collect(Collectors.groupingBy(PaylinkerDocument::getOwnerKey));
        Set<String> matchedOwnerKeys = new HashSet<>();

        int matchedCount = 0;
        int duplicateMatchCount = 0;
        int unmatchedRecipientCount = 0;

        List<PaylinkerDocumentMatch> matches = new ArrayList<>(recipients.size());
        for (PaylinkerCampaignRecipient recipient : recipients) {
            String recipientOwnerKey = MATCH_KEY_EMPLOYEE_NO.equals(matchKey)
                    ? recipient.getEmployeeNo()
                    : recipient.getEmail();
            List<PaylinkerDocument> matchedDocs = recipientOwnerKey == null
                    ? List.of()
                    : documentsByOwnerKey.getOrDefault(recipientOwnerKey, List.of());

            String status;
            String documentId = null;
            if (matchedDocs.isEmpty()) {
                status = PaylinkerDocumentMatch.STATUS_UNMATCHED;
                unmatchedRecipientCount++;
            } else if (matchedDocs.size() == 1) {
                status = PaylinkerDocumentMatch.STATUS_MATCHED;
                documentId = matchedDocs.get(0).getDocumentId();
                matchedCount++;
                matchedOwnerKeys.add(recipientOwnerKey);
            } else {
                status = PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH;
                documentId = matchedDocs.get(0).getDocumentId();
                duplicateMatchCount++;
                matchedOwnerKeys.add(recipientOwnerKey);
            }

            PaylinkerDocumentMatch match = new PaylinkerDocumentMatch();
            match.setPk(PaylinkerDocumentMatch.pk(campaignId));
            match.setSk(PaylinkerDocumentMatch.sk(recipient.getCampaignRecipientId()));
            match.setCampaignId(campaignId);
            match.setCampaignRecipientId(recipient.getCampaignRecipientId());
            match.setRecipientId(recipient.getRecipientId());
            match.setDocumentId(documentId);
            match.setMatchStatus(status);
            match.setMatchKey(matchKey);
            match.setCreatedAt(now);
            match.setGsi1Pk(PaylinkerDocumentMatch.gsi1Pk(campaignId, status));
            matches.add(match);
        }

        int unmatchedDocumentCount = (int) documentsByOwnerKey.keySet().stream()
                .filter(ownerKey -> !matchedOwnerKeys.contains(ownerKey))
                .count();

        return new MatchOutcome(matches, matchedCount, duplicateMatchCount, unmatchedRecipientCount, unmatchedDocumentCount);
    }

    private PaylinkerUploadBatch buildUploadBatch(String campaignId,
                                                  String uploadBatchId,
                                                  String adminId,
                                                  String storageKey,
                                                  int rowCount,
                                                  String now) {
        PaylinkerUploadBatch batch = new PaylinkerUploadBatch();
        batch.setPk(PaylinkerUploadBatch.pk(campaignId));
        batch.setSk(PaylinkerUploadBatch.sk(uploadBatchId));
        batch.setUploadBatchId(uploadBatchId);
        batch.setCampaignId(campaignId);
        batch.setAdminId(adminId);
        batch.setUploadType(PaylinkerUploadBatch.UPLOAD_TYPE_DOCUMENT);
        batch.setFileStorageKey(storageKey);
        batch.setTotalRowCount(rowCount);
        batch.setValidRowCount(rowCount);
        batch.setErrorRowCount(0);
        batch.setCreatedAt(now);
        return batch;
    }

    private record MatchOutcome(
            List<PaylinkerDocumentMatch> matches,
            int matchedCount,
            int duplicateMatchCount,
            int unmatchedRecipientCount,
            int unmatchedDocumentCount
    ) {
    }
}
