package com.paylinker.worker.sesevent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

/**
 * SES 가 SNS 토픽에 던지는 이벤트 페이로드.
 *
 * SNS subscription 이 raw message delivery 가 아니면 다음 envelope 안에 JSON 문자열로 들어온다:
 * { "Type":"Notification", "Message":"<SES JSON>", ... }
 *
 * raw delivery 이면 이 record 형식이 직접 SQS body 로 들어온다.
 *
 * BOUNCE / COMPLAINT / REJECT 만 처리한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record SesNotification(
        @JsonProperty("eventType") String eventType,
        @JsonProperty("mail") Mail mail,
        @JsonProperty("bounce") Bounce bounce,
        @JsonProperty("complaint") Complaint complaint) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Mail(
            @JsonProperty("messageId") String messageId,
            @JsonProperty("destination") List<String> destination,
            @JsonProperty("timestamp") String timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Bounce(
            @JsonProperty("bounceType") String bounceType,
            @JsonProperty("bounceSubType") String bounceSubType,
            @JsonProperty("bouncedRecipients") List<RecipientInfo> bouncedRecipients,
            @JsonProperty("timestamp") String timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Complaint(
            @JsonProperty("complaintFeedbackType") String complaintFeedbackType,
            @JsonProperty("complainedRecipients") List<RecipientInfo> complainedRecipients,
            @JsonProperty("timestamp") String timestamp) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record RecipientInfo(
            @JsonProperty("emailAddress") String emailAddress) {}
}
