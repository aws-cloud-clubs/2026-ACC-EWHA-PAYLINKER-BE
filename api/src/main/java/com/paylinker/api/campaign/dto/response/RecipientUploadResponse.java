package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record RecipientUploadResponse(
        String uploadBatchId,
        String campaignId,
        int totalRowCount,
        int validRowCount,
        int errorRowCount,
        int duplicateRowCount
) {}
