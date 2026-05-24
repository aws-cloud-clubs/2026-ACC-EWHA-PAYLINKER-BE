package com.paylinker.api.campaign.dto.response;

import lombok.Builder;

@Builder
public record RecipientValidationErrorItem(
        int rowNumber,
        String column,
        String errorType,
        String rawValue,
        String message
) {}
