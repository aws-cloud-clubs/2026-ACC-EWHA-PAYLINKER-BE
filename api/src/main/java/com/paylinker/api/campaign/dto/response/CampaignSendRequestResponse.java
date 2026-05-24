package com.paylinker.api.campaign.dto.response;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class CampaignSendRequestResponse {
    private String campaignId;
    private String status;
    private String sendStartedAt;
    private int queuedJobCount;
}