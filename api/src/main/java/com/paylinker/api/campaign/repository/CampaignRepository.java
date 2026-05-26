package com.paylinker.api.campaign.repository;

import com.paylinker.api.entity.PaylinkerAuditLog;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerCampaignLimit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbIndex;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Expression;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;
import software.amazon.awssdk.enhanced.dynamodb.model.TransactWriteItemsEnhancedRequest;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.GetItemRequest;
import software.amazon.awssdk.services.dynamodb.model.GetItemResponse;

@Repository
public class CampaignRepository {

    private final DynamoDbClient dynamoDbClient;
    private final DynamoDbEnhancedClient enhancedClient;
    private final String tablePrefix;
    private final DynamoDbTable<PaylinkerCampaign> table;
    private final DynamoDbIndex<PaylinkerCampaign> gsi1;

    // 생성자 방식을 겨레님 방식으로 변경, RequiredArgsConstructor 삭제
    public CampaignRepository(DynamoDbEnhancedClient enhancedClient,
                              DynamoDbClient dynamoDbClient,
                              @Value("${aws.dynamodb.table-prefix}") String tablePrefix) {
        this.dynamoDbClient = dynamoDbClient;
        this.enhancedClient = enhancedClient;
        this.tablePrefix = tablePrefix;
        this.table = enhancedClient.table(
                tablePrefix + "-campaign",
                TableSchema.fromBean(PaylinkerCampaign.class));
        this.gsi1 = table.index(PaylinkerCampaign.INDEX_GSI1);
    }

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

    public Optional<PaylinkerCampaign> findByCampaignId(String campaignId) {
        Key key = Key.builder()
                .partitionValue(PaylinkerCampaign.pk(campaignId))
                .sortValue(PaylinkerCampaign.SK_METADATA)
                .build();
        return Optional.ofNullable(table.getItem(key));
    }

    public List<PaylinkerCampaign> findRecentByAdminId(String adminId, int limit) {
        QueryEnhancedRequest request = QueryEnhancedRequest.builder()
                .queryConditional(QueryConditional.keyEqualTo(
                        Key.builder().partitionValue(PaylinkerCampaign.gsi1Pk(adminId)).build()))
                .scanIndexForward(false)
                .limit(limit)
                .build();
        return StreamSupport.stream(gsi1.query(request).spliterator(), false)
                .flatMap(page -> page.items().stream())
                .limit(limit)
                .collect(Collectors.toList());
    }

    // 테이블 객체를 메서드 내부에서 동적으로 가져오는 Getter 메서드들
    public DynamoDbTable<PaylinkerCampaign> getCampaignTable() {
        return enhancedClient.table(tablePrefix + "-campaign", TableSchema.fromBean(PaylinkerCampaign.class));
    }

    public DynamoDbTable<PaylinkerCampaignLimit> getLimitTable() {
        return enhancedClient.table(tablePrefix + "-campaign-limit", TableSchema.fromBean(PaylinkerCampaignLimit.class));
    }

    public DynamoDbTable<PaylinkerAuditLog> getAuditLogTable() {
        return enhancedClient.table(tablePrefix + "-audit-log", TableSchema.fromBean(PaylinkerAuditLog.class));
    }

    public List<PaylinkerCampaign> findAllByAdminId(String adminId) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = getCampaignTable().index(PaylinkerCampaign.INDEX_GSI1);

        QueryConditional queryConditional = QueryConditional.keyEqualTo(k ->
                k.partitionValue(PaylinkerCampaign.gsi1Pk(adminId)));

        return gsi1.query(queryConditional).stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // 이름 중복 검사를 위한 메서드 (필터링 최적화 적용된 최신 버전 유지)
    public boolean existsByAdminIdAndCampaignName(String adminId, String campaignName) {
        DynamoDbIndex<PaylinkerCampaign> gsi1 = getCampaignTable().index(PaylinkerCampaign.INDEX_GSI1);

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

        return gsi1.query(request).stream()
                .flatMap(page -> page.items().stream())
                .findFirst()
                .isPresent();
    }

    // 트랜잭션을 이용한 3개 테이블 동시 저장
    public void saveCampaignWithTransaction(PaylinkerCampaign campaign, PaylinkerCampaignLimit limit, PaylinkerAuditLog auditLog) {
        TransactWriteItemsEnhancedRequest request = TransactWriteItemsEnhancedRequest.builder()
                .addPutItem(getCampaignTable(), campaign)
                .addPutItem(getLimitTable(), limit)
                .addPutItem(getAuditLogTable(), auditLog)
                .build();

        executeTransaction(request);
    }

    // 캠페인 단건 조회 (PK 기준)
    public PaylinkerCampaign findCampaignById(String campaignId) {
        return getCampaignTable().getItem(r -> r.key(k ->
                k.partitionValue(PaylinkerCampaign.pk(campaignId)).sortValue(PaylinkerCampaign.sk())));
    }

    public PaylinkerCampaignLimit findLimitById(String campaignId) {
        return getLimitTable().getItem(r -> r.key(k ->
                k.partitionValue(PaylinkerCampaignLimit.pk(campaignId)).sortValue(PaylinkerCampaignLimit.SK_LIMIT)));
    }

    //캠페인 생성, 수정, 삭제에서 공통으로 사용하는 트랜잭션 메서드
    public void executeTransaction(TransactWriteItemsEnhancedRequest request) {
        enhancedClient.transactWriteItems(request);
    }
}