package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@Repository
@RequiredArgsConstructor
public class CampaignRepository {
    private final DynamoDbClient dynamoDbClient;

    @Value("${aws.dynamodb.table-prefix}")
    private String tablePrefix;

    private String tableName() {
        return tablePrefix + "-campaign";
    }

    public Optional<Map<String, AttributeValue>> findById(String campaignId) {
        GetItemResponse resp = dynamoDbClient.getItem(GetItemRequest.builder()
                .tableName(tableName())
                .key(Map.of("campaign_id", AttributeValue.fromS(campaignId)))
                .build());
        return resp.hasItem() ? Optional.of(resp.item()) : Optional.empty();
    }

    private final DynamoDbEnhancedClient enhancedClient;

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        // 테이블 객체를 전역 변수로 두지 않고 메서드 내부에서 동적 생성하여 구조 충돌 방지
        DynamoDbTable<PaylinkerCampaign> campaignTable =
                enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));

        DynamoDbIndex<PaylinkerCampaign> gsi1 = campaignTable.index(PaylinkerCampaign.INDEX_GSI1);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // 이름 중복 검사를 위한 메서드 (필터링 최적화 적용된 최신 버전 유지)
    public boolean existsByAdminIdAndCampaignName(String adminId, String campaignName) {
        DynamoDbTable<PaylinkerCampaign> campaignTable =
                enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));

        DynamoDbIndex<PaylinkerCampaign> gsi1 = campaignTable.index(PaylinkerCampaign.INDEX_GSI1);

        // 파티션 키: 해당 Admin의 데이터만 탐색
        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        // 필터 조건: 캠페인 이름 일치 여부를 DB 서버 단에서 필터링
        Expression filterExpression = Expression.builder()
                .expression("campaign_name = :name")
                .expressionValues(Map.of(":name", AttributeValue.fromS(campaignName)))
                .build();

        // limit(1)을 설정하여 중복되는 첫 번째 건을 찾으면 즉시 검색 종료
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(queryConditional)
                .filterExpression(filterExpression)
                .limit(1)
                .build();

        // 1건이라도 존재하면 중복(true)으로 반환
        return gsi1.query(request).stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .isPresent();
    }

    // 트랜잭션을 이용한 3개 테이블 동시 저장
    public void saveCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        // 테이블 객체를 메서드 내부에서 동적 생성
        DynamoDbTable<PaylinkerCampaign> campaignTable = enhancedClient.table("paylinker_campaign", TableSchema.fromBean(PaylinkerCampaign.class));
        DynamoDbTable<PaylinkerCampaignLimit> limitTable = enhancedClient.table("paylinker_campaign_limit", TableSchema.fromBean(PaylinkerCampaignLimit.class));
        DynamoDbTable<PaylinkerAuditLog> auditLogTable = enhancedClient.table("paylinker_audit_log", TableSchema.fromBean(PaylinkerAuditLog.class));

        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(campaignTable, campaign)
                .addPutItem(limitTable, limit)
                .addPutItem(auditLogTable, auditLog)
                .build();

        enhancedClient.transactWriteItems(request);
    }

    // 캠페인 단건 조회 (PK 기준)
    public PaylinkerCampaign findById(String campaignId) {
        return campaignTable.getItem(r -> r.key(k -> k.partitionValue(PaylinkerCampaign.pk(campaignId)).sortValue(PaylinkerCampaign.sk())));
    }

    // 캠페인 제한 단건 조회 (PK 기준)
    public PaylinkerCampaignLimit findLimitById(String campaignId) {
        return limitTable.getItem(r -> r.key(k -> k.partitionValue(PaylinkerCampaignLimit.pk(campaignId)).sortValue(PaylinkerCampaignLimit.SK_LIMIT)));
    }

    // 캠페인 수정 트랜잭션 (AuditLog 포함)
    public void updateCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addUpdateItem(campaignTable, campaign)
                .addUpdateItem(limitTable, limit)
                .addPutItem(auditLogTable, auditLog)
                .build();

        enhancedClient.transactWriteItems(request);
    }
}