package com.paylinker.api.campaign.dto.response;

import java.util.List;
import lombok.Builder;

@Builder
public record RecipientValidationResponse(
        String uploadBatchId,
        String campaignId,
        int totalRowCount,
        int validRowCount,
        int errorRowCount,
        int duplicateRowCount,
        boolean canProceed,
        List<RecipientValidationErrorItem> errors
) {}
