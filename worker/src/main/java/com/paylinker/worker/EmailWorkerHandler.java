package com.paylinker.worker;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.paylinker.worker.dto.EmailJobMessage;
import com.paylinker.worker.email.EmailSender;
import com.paylinker.worker.repository.CampaignRecipientRepo;
import com.paylinker.worker.repository.CampaignRepo;
import com.paylinker.worker.repository.EmailSuppressionRepo;
import com.paylinker.worker.repository.SecureLinkRepo;
import com.paylinker.worker.repository.SendAttemptRepo;
import com.paylinker.worker.repository.SendJobRepo;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.ses.SesClient;

/**
 * SQS dev-paylinker-email-send-queue 의 message 를 받아
 * secure_link 발급 + SES 발송 + DynamoDB 상태 갱신을 수행한다.
 *
 * Lambda handler entry: com.paylinker.worker.EmailWorkerHandler::handleRequest
 *
 * 처리 단위는 단일 메시지. SQS event 의 records 를 순회하며 실패한 messageId 만
 * SQSBatchResponse 에 담아 반환하면 해당 메시지만 재처리된다 (partial batch failure).
 */
public class EmailWorkerHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .setPropertyNamingStrategy(PropertyNamingStrategies.LOWER_CAMEL_CASE)
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final EmailWorkerService SERVICE = bootstrap();

    private static EmailWorkerService bootstrap() {
        String region = env("AWS_REGION", "ap-northeast-2");
        String tablePrefix = env("DYNAMODB_TABLE_PREFIX", "dev-paylinker");
        String fromEmail = env("SES_FROM_EMAIL", "");
        String configurationSet = env("SES_CONFIGURATION_SET", "");
        String linkBase = env("RECIPIENT_LINK_BASE_URL", "https://paylinker.kr/link");
        String unsubscribeBase = env("UNSUBSCRIBE_BASE_URL", "https://paylinker.kr/unsubscribe");
        String unsubscribeSecret = env("UNSUBSCRIBE_HMAC_SECRET", "");

        DynamoDbClient ddb = DynamoDbClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();
        SesClient ses = SesClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();

        return new EmailWorkerService(
                new CampaignRepo(ddb, tablePrefix),
                new CampaignRecipientRepo(ddb, tablePrefix),
                new SecureLinkRepo(ddb, tablePrefix),
                new SendJobRepo(ddb, tablePrefix),
                new SendAttemptRepo(ddb, tablePrefix),
                new EmailSender(ses, fromEmail, configurationSet),
                linkBase,
                new EmailSuppressionRepo(ddb, tablePrefix),
                unsubscribeBase,
                unsubscribeSecret);
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            String messageId = record.getMessageId();
            try {
                EmailJobMessage msg = MAPPER.readValue(record.getBody(), EmailJobMessage.class);
                if (msg.sendJobId() == null || msg.campaignId() == null || msg.campaignRecipientId() == null) {
                    context.getLogger().log("[SKIP] missing required field. messageId=" + messageId);
                    // 페이로드 자체가 잘못된 경우 재시도해도 같은 결과. 실패 표시 안 하고 drop.
                    continue;
                }
                SERVICE.handle(msg);
                context.getLogger().log("[OK] sendJobId=" + msg.sendJobId() + " messageId=" + messageId);
            } catch (Exception e) {
                context.getLogger().log("[FAIL] messageId=" + messageId + " error=" + e);
                failures.add(SQSBatchResponse.BatchItemFailure.builder()
                        .withItemIdentifier(messageId)
                        .build());
            }
        }
        return SQSBatchResponse.builder().withBatchItemFailures(failures).build();
    }

    private static String env(String key, String defaultValue) {
        String v = System.getenv(key);
        return (v == null || v.isBlank()) ? defaultValue : v;
    }
}
