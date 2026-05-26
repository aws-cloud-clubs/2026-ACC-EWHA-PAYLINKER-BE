package com.paylinker.worker.email;

import software.amazon.awssdk.services.ses.SesClient;
import software.amazon.awssdk.services.ses.model.Body;
import software.amazon.awssdk.services.ses.model.Content;
import software.amazon.awssdk.services.ses.model.Destination;
import software.amazon.awssdk.services.ses.model.Message;
import software.amazon.awssdk.services.ses.model.SendEmailRequest;
import software.amazon.awssdk.services.ses.model.SendEmailResponse;

public class EmailSender {

    private final SesClient ses;
    private final String fromEmail;
    private final String configurationSetName;

    public EmailSender(SesClient ses, String fromEmail, String configurationSetName) {
        this.ses = ses;
        this.fromEmail = fromEmail;
        this.configurationSetName = configurationSetName;
    }

    /** SES SendEmail. 성공 시 SES messageId 반환. */
    public String send(String to, String subject, String htmlBody) {
        SendEmailRequest.Builder builder = SendEmailRequest.builder()
                .source(fromEmail)
                .destination(Destination.builder().toAddresses(to).build())
                .message(Message.builder()
                        .subject(Content.builder().data(subject).charset("UTF-8").build())
                        .body(Body.builder()
                                .html(Content.builder().data(htmlBody).charset("UTF-8").build())
                                .build())
                        .build());
        if (configurationSetName != null && !configurationSetName.isBlank()) {
            builder.configurationSetName(configurationSetName);
        }
        SendEmailResponse resp = ses.sendEmail(builder.build());
        return resp.messageId();
    }
}
