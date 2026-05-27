package com.paylinker.worker.sesevent;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.amazonaws.services.lambda.runtime.events.SQSBatchResponse;
import com.amazonaws.services.lambda.runtime.events.SQSEvent;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.worker.repository.CampaignRecipientRepo;
import com.paylinker.worker.repository.SendJobRepo;
import com.paylinker.worker.sesevent.repository.CheckItemRepo;
import com.paylinker.worker.sesevent.repository.SendJobByMessageRepo;
import com.paylinker.worker.sesevent.repository.SesEventRepo;
import java.util.ArrayList;
import java.util.List;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

/**
 * SES 이벤트 처리 Lambda entry.
 *
 * 별도 Lambda `dev-paylinker-ses-event-worker` 의 handler 로 등록:
 *   com.paylinker.worker.sesevent.SesEventHandler::handleRequest
 *
 * 트리거: SQS `dev-paylinker-ses-event-queue`
 * (해당 큐는 SNS `dev-paylinker-ses-events` 토픽 구독으로부터 메시지 수신)
 */
public class SesEventHandler implements RequestHandler<SQSEvent, SQSBatchResponse> {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    private static final SesEventService SERVICE = bootstrap();

    private static SesEventService bootstrap() {
        String region = env("AWS_REGION", "ap-northeast-2");
        String tablePrefix = env("DYNAMODB_TABLE_PREFIX", "dev-paylinker");

        DynamoDbClient ddb = DynamoDbClient.builder()
                .region(Region.of(region))
                .httpClient(UrlConnectionHttpClient.builder().build())
                .build();

        return new SesEventService(
                MAPPER,
                new SesEventRepo(ddb, tablePrefix),
                new SendJobByMessageRepo(ddb, tablePrefix),
                new SendJobRepo(ddb, tablePrefix),
                new CampaignRecipientRepo(ddb, tablePrefix),
                new CheckItemRepo(ddb, tablePrefix));
    }

    @Override
    public SQSBatchResponse handleRequest(SQSEvent event, Context context) {
        List<SQSBatchResponse.BatchItemFailure> failures = new ArrayList<>();
        for (SQSEvent.SQSMessage record : event.getRecords()) {
            String messageId = record.getMessageId();
            try {
                SERVICE.handle(record.getBody());
                context.getLogger().log("[OK] ses-event sqsMessageId=" + messageId);
            } catch (Exception e) {
                context.getLogger().log("[FAIL] ses-event sqsMessageId=" + messageId + " error=" + e);
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
