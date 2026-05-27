package com.paylinker.worker.dto;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public record EmailJobMessage(
        String sendJobId,
        String campaignRecipientId,
        String campaignId,
        JobType jobType) {

    @JsonCreator
    public EmailJobMessage(
            @JsonProperty("sendJobId") String sendJobId,
            @JsonProperty("campaignRecipientId") String campaignRecipientId,
            @JsonProperty("campaignId") String campaignId,
            @JsonProperty("jobType") JobType jobType) {
        this.sendJobId = sendJobId;
        this.campaignRecipientId = campaignRecipientId;
        this.campaignId = campaignId;
        this.jobType = jobType == null ? JobType.INITIAL : jobType;
    }
}
