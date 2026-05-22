package com.paylinker.api.document.dto;

public record DocumentUploadResponse(
        String uploadBatchId,
        String campaignId,
        Integer totalDocumentCount,
        Integer matchedCount,
        Integer unmatchedDocumentCount,
        Integer unmatchedRecipientCount,
        Integer duplicateMatchCount
) {
}
