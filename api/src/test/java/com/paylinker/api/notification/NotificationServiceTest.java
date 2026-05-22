package com.paylinker.api.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.notification.dto.request.ResendRequestActionRequest;
import com.paylinker.api.notification.dto.response.CheckItemListResponse;
import com.paylinker.api.notification.dto.response.ResendRequestActionResponse;
import com.paylinker.api.notification.dto.response.ResendRequestListResponse;
import com.paylinker.api.notification.repository.CheckItemRepository;
import com.paylinker.api.notification.repository.ResendRequestRepository;
import com.paylinker.api.notification.repository.SecureLinkRepository;
import com.paylinker.api.notification.repository.SendJobRepository;
import com.paylinker.api.notification.service.NotificationService;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock CheckItemRepository checkItemRepository;
    @Mock ResendRequestRepository resendRequestRepository;
    @Mock SecureLinkRepository secureLinkRepository;
    @Mock SendJobRepository sendJobRepository;
    @Mock SqsClient sqsClient;
    @Mock ObjectMapper objectMapper;

    @InjectMocks
    NotificationService service;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(service, "sqsQueueUrl", "https://sqs.test/queue");
    }

    @Test
    @DisplayName("확인 필요 알림 조회 - 전체 조회 성공")
    void getCheckItems_success() {
        when(checkItemRepository.findAll()).thenReturn(List.of(
                item("check_item_id", "ci_1", "check_status", "OPEN",
                        "item_type", "RESEND_REQUEST", "campaign_id", "c1",
                        "campaign_name", "2026-02 급여", "recipient_name", "홍길동",
                        "created_at", "2026-03-01T10:00:00+09:00"),
                item("check_item_id", "ci_2", "check_status", "IN_PROGRESS",
                        "item_type", "FINAL_FAILED", "campaign_id", "c1",
                        "campaign_name", "2026-02 급여", "recipient_name", "김철수",
                        "created_at", "2026-03-02T10:00:00+09:00"),
                item("check_item_id", "ci_3", "check_status", "RESOLVED",
                        "item_type", "RESEND_REQUEST", "campaign_id", "c2",
                        "campaign_name", "2026-01 급여", "recipient_name", "이영희",
                        "created_at", "2026-02-28T09:00:00+09:00")
        ));

        CheckItemListResponse result = service.getCheckItems(null, null, null, 1, 20);

        assertThat(result.totalCount()).isEqualTo(3);
        assertThat(result.openCount()).isEqualTo(1);
        assertThat(result.inProgressCount()).isEqualTo(1);
        assertThat(result.items()).hasSize(3);
        // OPEN이 첫 번째
        assertThat(result.items().get(0).checkStatus()).isEqualTo("OPEN");
        assertThat(result.items().get(1).checkStatus()).isEqualTo("IN_PROGRESS");
    }

    @Test
    @DisplayName("확인 필요 알림 조회 - itemType 필터 적용")
    void getCheckItems_withItemTypeFilter() {
        when(checkItemRepository.findAll()).thenReturn(List.of(
                item("check_item_id", "ci_1", "check_status", "OPEN",
                        "item_type", "RESEND_REQUEST", "campaign_id", "c1",
                        "campaign_name", "캠페인", "recipient_name", "홍길동",
                        "created_at", "2026-03-01T10:00:00+09:00"),
                item("check_item_id", "ci_2", "check_status", "OPEN",
                        "item_type", "FINAL_FAILED", "campaign_id", "c1",
                        "campaign_name", "캠페인", "recipient_name", "김철수",
                        "created_at", "2026-03-02T10:00:00+09:00")
        ));

        CheckItemListResponse result = service.getCheckItems("RESEND_REQUEST", null, null, 1, 20);

        assertThat(result.totalCount()).isEqualTo(1);
        assertThat(result.items().get(0).itemType()).isEqualTo("RESEND_REQUEST");
        // openCount는 필터 무관한 전체 건수
        assertThat(result.openCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("재전송 요청 목록 조회 - 성공")
    void getResendRequests_success() {
        when(resendRequestRepository.findAll()).thenReturn(List.of(
                item("request_id", "rr_1", "campaign_id", "c1",
                        "campaign_name", "2026-02 급여", "recipient_name", "홍길동",
                        "status", "REQUESTED", "requested_at", "2026-03-01T10:00:00+09:00"),
                item("request_id", "rr_2", "campaign_id", "c1",
                        "campaign_name", "2026-02 급여", "recipient_name", "김철수",
                        "status", "COMPLETED", "requested_at", "2026-03-02T10:00:00+09:00")
        ));

        ResendRequestListResponse result = service.getResendRequests(null, null, 1, 20);

        assertThat(result.totalCount()).isEqualTo(2);
        assertThat(result.pendingCount()).isEqualTo(1);
        assertThat(result.items()).hasSize(2);
        // 최신순 정렬
        assertThat(result.items().get(0).requestedAt()).isGreaterThan(result.items().get(1).requestedAt());
    }

    @Test
    @DisplayName("재전송 요청 처리 - 승인 성공")
    void processResendRequest_approve_success() throws Exception {
        when(resendRequestRepository.findById("rr_1")).thenReturn(Optional.of(
                item("request_id", "rr_1", "status", "REQUESTED",
                        "campaign_recipient_id", "cr_1", "campaign_id", "camp_1")));
        when(secureLinkRepository.findActiveByRecipientId("cr_1")).thenReturn(Optional.empty());
        when(checkItemRepository.findByRelatedRequestId("rr_1")).thenReturn(Optional.empty());
        doReturn("{\"sendJobId\":\"sj_test\"}").when(objectMapper).writeValueAsString(any());
        when(sqsClient.sendMessage(any(software.amazon.awssdk.services.sqs.model.SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("msg_1").build());

        ResendRequestActionResponse result = service.processResendRequest(
                "rr_1", actionRequest("APPROVE", null), "admin_sub");

        assertThat(result.status()).isEqualTo("COMPLETED");
        assertThat(result.newSendJobId()).isNotNull();
        assertThat(result.newLinkExpiresAt()).isNotNull();

        verify(secureLinkRepository).save(anyString(), eq("cr_1"), eq("camp_1"), anyString(), anyString());
        verify(sendJobRepository).save(anyString(), eq("cr_1"), eq("camp_1"), eq("RESEND"), anyString(), anyString());
        verify(resendRequestRepository).updateToCompleted(eq("rr_1"), eq("admin_sub"), anyString());
    }

    @Test
    @DisplayName("재전송 요청 처리 - 기존 활성 링크 무효화 후 승인")
    void processResendRequest_approve_invalidatesExistingLink() throws Exception {
        when(resendRequestRepository.findById("rr_1")).thenReturn(Optional.of(
                item("request_id", "rr_1", "status", "REQUESTED",
                        "campaign_recipient_id", "cr_1", "campaign_id", "camp_1")));
        when(secureLinkRepository.findActiveByRecipientId("cr_1")).thenReturn(
                Optional.of(item("secure_link_id", "sl_old")));
        when(checkItemRepository.findByRelatedRequestId("rr_1")).thenReturn(Optional.empty());
        doReturn("{\"sendJobId\":\"sj_test\"}").when(objectMapper).writeValueAsString(any());
        when(sqsClient.sendMessage(any(software.amazon.awssdk.services.sqs.model.SendMessageRequest.class)))
                .thenReturn(SendMessageResponse.builder().messageId("msg_1").build());

        service.processResendRequest("rr_1", actionRequest("APPROVE", null), "admin_sub");

        verify(secureLinkRepository).invalidate("sl_old");
    }

    @Test
    @DisplayName("재전송 요청 처리 - 반려 성공")
    void processResendRequest_reject_success() {
        when(resendRequestRepository.findById("rr_1")).thenReturn(Optional.of(
                item("request_id", "rr_1", "status", "REQUESTED",
                        "campaign_recipient_id", "cr_1", "campaign_id", "camp_1")));
        when(checkItemRepository.findByRelatedRequestId("rr_1")).thenReturn(Optional.of(
                item("check_item_id", "ci_1")));

        ResendRequestActionResponse result = service.processResendRequest(
                "rr_1", actionRequest("REJECT", "중복 요청"), "admin_sub");

        assertThat(result.status()).isEqualTo("REJECTED");
        assertThat(result.newSendJobId()).isNull();
        assertThat(result.newLinkExpiresAt()).isNull();

        verify(resendRequestRepository).updateToRejected(eq("rr_1"), eq("admin_sub"), anyString());
        verify(checkItemRepository).updateCheckStatus("ci_1", "REJECTED");
        verify(sqsClient, never()).sendMessage(any(software.amazon.awssdk.services.sqs.model.SendMessageRequest.class));
    }

    @Test
    @DisplayName("재전송 요청 처리 - 요청 없음 (404)")
    void processResendRequest_notFound() {
        when(resendRequestRepository.findById("rr_notexist")).thenReturn(Optional.empty());

        assertThatThrownBy(() ->
                service.processResendRequest("rr_notexist", actionRequest("APPROVE", null), "admin"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESEND_REQUEST_NOT_FOUND));
    }

    @Test
    @DisplayName("재전송 요청 처리 - 이미 처리된 요청 (409)")
    void processResendRequest_alreadyProcessed() {
        when(resendRequestRepository.findById("rr_1")).thenReturn(Optional.of(
                item("request_id", "rr_1", "status", "COMPLETED")));

        assertThatThrownBy(() ->
                service.processResendRequest("rr_1", actionRequest("APPROVE", null), "admin"))
                .isInstanceOf(CustomException.class)
                .satisfies(e -> assertThat(((CustomException) e).getErrorCode())
                        .isEqualTo(ErrorCode.RESEND_REQUEST_ALREADY_PROCESSED));
    }

    // --- helpers ---

    private ResendRequestActionRequest actionRequest(String action, String reason) {
        ResendRequestActionRequest req = new ResendRequestActionRequest();
        ReflectionTestUtils.setField(req, "action", action);
        ReflectionTestUtils.setField(req, "reason", reason);
        return req;
    }

    private Map<String, AttributeValue> item(String... keyValues) {
        if (keyValues.length % 2 != 0) throw new IllegalArgumentException();
        var map = new java.util.HashMap<String, AttributeValue>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], AttributeValue.fromS(keyValues[i + 1]));
        }
        return map;
    }
}
