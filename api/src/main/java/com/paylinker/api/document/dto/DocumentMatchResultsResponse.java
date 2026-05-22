package com.paylinker.api.document.dto;

import java.util.List;

public record DocumentMatchResultsResponse(
        String campaignId,
        Integer totalRecipientCount,
        Integer totalDocumentCount,
        Integer matchedCount,
        Integer unmatchedRecipientCount,
        Integer duplicateMatchCount,
        Boolean canProceed,
        List<DocumentMatchItem> items
) {
}
