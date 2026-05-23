package com.paylinker.api.notification.controller;

import com.paylinker.api.notification.dto.request.ResendRequestActionRequest;
import com.paylinker.api.notification.dto.response.CheckItemListResponse;
import com.paylinker.api.notification.dto.response.ResendRequestActionResponse;
import com.paylinker.api.notification.dto.response.ResendRequestListResponse;
import com.paylinker.api.notification.service.NotificationService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notification", description = "알림 관련 API")
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping("/check-items")
    @Operation(
            summary = "확인 필요 알림 조회 (REQ-003)",
            description = "처리 필요 항목(미확인 수신자/발송 실패/재전송 요청) 목록을 반환한다.")
    public ResponseEntity<ApiResponse<CheckItemListResponse>> getCheckItems(
            @Parameter(description = "알림 유형 (RESEND_REQUEST, FINAL_FAILED, UNVIEWED_RECIPIENT)")
            @RequestParam(required = false) String itemType,
            @Parameter(description = "처리 상태 (OPEN, IN_PROGRESS, RESOLVED, REJECTED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "캠페인 ID")
            @RequestParam(required = false) String campaignId,
            @Parameter(description = "페이지 번호 (1부터 시작)")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {

        CheckItemListResponse data = notificationService.getCheckItems(
                itemType, status, campaignId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("확인 필요 알림 조회 성공", data));
    }

    @GetMapping("/resend-requests")
    @Operation(
            summary = "재전송 요청 목록 조회 (REQ-001)",
            description = "수신자로부터 접수된 재전송 요청 목록을 필터/페이지네이션과 함께 조회한다.")
    public ResponseEntity<ApiResponse<ResendRequestListResponse>> getResendRequests(
            @Parameter(description = "상태 필터 (REQUESTED, COMPLETED, REJECTED)")
            @RequestParam(required = false) String status,
            @Parameter(description = "캠페인 ID")
            @RequestParam(required = false) String campaignId,
            @Parameter(description = "페이지 번호 (1부터 시작)")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {

        ResendRequestListResponse data = notificationService.getResendRequests(
                status, campaignId, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("재전송 요청 목록 조회 성공", data));
    }

    @PatchMapping("/resend-requests/{requestId}")
    @Operation(
            summary = "재전송 요청 처리 (REQ-002)",
            description = "재전송 요청을 승인(새 링크 발급 + 재발송) 또는 반려 처리한다.")
    public ResponseEntity<ApiResponse<ResendRequestActionResponse>> processResendRequest(
            @Parameter(description = "재전송 요청 ID", required = true)
            @PathVariable String requestId,
            @Valid @RequestBody ResendRequestActionRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String processedBy = jwt != null ? jwt.getSubject() : "unknown";
        ResendRequestActionResponse data = notificationService.processResendRequest(
                requestId, request, processedBy);

        String message = "APPROVE".equals(request.getAction()) ? "재전송 요청 승인 완료" : "재전송 요청 반려 완료";
        return ResponseEntity.ok(ApiResponse.ok(message, data));
    }
}
