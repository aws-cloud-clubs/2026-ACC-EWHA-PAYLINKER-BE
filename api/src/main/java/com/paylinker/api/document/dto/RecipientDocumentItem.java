package com.paylinker.api.document.dto;

import lombok.Builder;

@Builder
public record RecipientDocumentItem(
        String documentId,
        String filename,
        String documentType,
        String inlineHtml,
        String downloadUrl,
        Long fileSizeBytes) {}
