package com.paylinker.api.campaign.dto.response;

import java.util.List;

public record CampaignFinalReviewResponse(
        String campaignId,
        String campaignName,
        String emailSubject,
        String emailDescription,
        Integer linkTtlHours,
        int totalRecipientCount,
        int matchedDocumentCount,
        int unmatchedRecipientCount,
        int duplicateMatchCount,
        String scheduledSendAt,
        boolean canSend,
        List<String> blockingIssues
) {}