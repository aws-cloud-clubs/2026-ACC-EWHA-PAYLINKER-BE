package com.paylinker.worker.sesevent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * SNS 가 SQS 로 전달할 때 감싸는 표준 envelope.
 * raw message delivery 가 활성화되면 이 envelope 없이 SesNotification JSON 이 바로 들어온다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SnsEnvelope(
        @JsonProperty("Type") String type,
        @JsonProperty("MessageId") String messageId,
        @JsonProperty("Message") String message,
        @JsonProperty("Timestamp") String timestamp) {}
