package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.request.CampaignCreateRequest;
import com.paylinker.api.campaign.dto.request.CampaignScheduleRequest;
import com.paylinker.api.campaign.dto.request.CampaignUpdateRequest;
import com.paylinker.api.campaign.dto.request.ManualResendRequest;
import com.paylinker.api.campaign.dto.request.ReminderRequest;
import com.paylinker.api.campaign.dto.response.CampaignCancelResponse;
import com.paylinker.api.campaign.dto.response.CampaignCreateResponse;
import com.paylinker.api.campaign.dto.response.CampaignDetailResponse;
import com.paylinker.api.campaign.dto.response.CampaignFinalReviewResponse;
import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.campaign.dto.response.CampaignScheduleResponse;
import com.paylinker.api.campaign.dto.response.CampaignSendResponse;
import com.paylinker.api.campaign.dto.response.ManualResendResponse;
import com.paylinker.api.campaign.dto.response.ReminderResponse;
import com.paylinker.api.campaign.dto.response.SendFailureResponse;
import com.paylinker.api.campaign.dto.response.ViewHistoryResponse;
import com.paylinker.api.campaign.service.CampaignCancelService;
import com.paylinker.api.campaign.service.CampaignCreateService;
import com.paylinker.api.campaign.service.CampaignDetailService;
import com.paylinker.api.campaign.service.CampaignDispatchService;
import com.paylinker.api.campaign.service.CampaignFinalReviewService;
import com.paylinker.api.campaign.service.CampaignScheduleService;
import com.paylinker.api.campaign.service.CampaignService;
import com.paylinker.api.campaign.service.CampaignUpdateService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Validated
@Tag(name = "Campaign", description = "캠페인 관리 API")
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignCreateService campaignCreateService;
    private final CampaignUpdateService campaignUpdateService;
    private final CampaignCancelService campaignCancelService;
    private final CampaignDetailService campaignDetailService;
    private final CampaignFinalReviewService campaignFinalReviewService;
    private final CampaignScheduleService campaignScheduleService;
    private final CampaignDispatchService campaignDispatchService;

    @GetMapping
    public ResponseEntity<ApiResponse<CampaignListResponse>> getCampaigns(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "페이지 번호는 1 이상이어야 합니다.") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "페이지 크기는 1 이상이어야 합니다.") @Max(value = 50, message = "페이지 크기는 50 이하이어야 합니다.") int pageSize,
            @RequestParam(defaultValue = "createdAt:desc") String sort,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignListResponse responseData = campaignService.getCampaigns(
                adminId, status, keyword, page, pageSize, sort
        );
        return ResponseEntity.ok(ApiResponse.ok("캠페인 목록 조회 성공", responseData));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CampaignCreateResponse>> createCampaign(
            @Valid @RequestBody CampaignCreateRequest request,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignCreateResponse responseData = campaignCreateService.createCampaign(adminId, request);
        return ResponseEntity.status(201).body(ApiResponse.created("캠페인 생성 완료", responseData));
    }

    @PatchMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> updateCampaign(
            @PathVariable String campaignId,
            @Valid @RequestBody CampaignUpdateRequest request,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignDetailResponse responseData = campaignUpdateService.updateCampaign(adminId, campaignId, request);
        return ResponseEntity.ok(ApiResponse.ok("캠페인 수정 완료", responseData));
    }

    @PatchMapping("/{campaignId}/cancel")
    public ResponseEntity<ApiResponse<CampaignCancelResponse>> cancelCampaign(
            @PathVariable String campaignId,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignCancelResponse responseData = campaignCancelService.cancelCampaign(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("캠페인 취소 완료", responseData));
    }

    @GetMapping("/{campaignId}")
    public ResponseEntity<ApiResponse<CampaignDetailResponse>> getCampaignDetails(
            @PathVariable String campaignId,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignDetailResponse responseData = campaignDetailService.getCampaignDetails(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("캠페인 상세 조회 성공", responseData));
    }

    @GetMapping("/{campaignId}/final-review")
    public ResponseEntity<ApiResponse<CampaignFinalReviewResponse>> getFinalReviewInfo(
            @PathVariable String campaignId,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignFinalReviewResponse responseData = campaignFinalReviewService.getFinalReviewInfo(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("발송 전 최종 확인 정보 조회 성공", responseData));
    }

    @PatchMapping("/{campaignId}/schedule")
    public ResponseEntity<ApiResponse<CampaignScheduleResponse>> scheduleCampaign(
            @PathVariable String campaignId,
            @RequestBody CampaignScheduleRequest request,
            Authentication authentication) {

        String adminId = authentication.getName();
        CampaignScheduleResponse responseData = campaignScheduleService.scheduleCampaign(adminId, campaignId, request);
        String msg = request.scheduledSendAt() != null ? "예약 발송 설정 완료" : "예약 발송 해제 완료";
        return ResponseEntity.ok(ApiResponse.ok(msg, responseData));
    }

    @PostMapping("/{campaignId}/send")
    @Operation(
            summary = "캠페인 즉시 발송 (초기 대량발송)",
            description = "READY/SCHEDULED 상태 캠페인의 전체 수신자에게 INITIAL 발송잡을 생성하고 SQS 에 적재한다. " +
                    "캠페인 상태는 SENDING 으로 전이된다.")
    public ResponseEntity<ApiResponse<CampaignSendResponse>> sendCampaign(
            @Parameter(description = "캠페인 ID", required = true)
            @PathVariable String campaignId,
            @AuthenticationPrincipal Jwt jwt) {

        CampaignSendResponse data = campaignDispatchService.dispatch(campaignId, jwt.getSubject());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("발송 요청이 접수되었습니다.", data));
    }

    @PostMapping("/{campaignId}/reminders")
    @Operation(
            summary = "미확인 수신자 리마인드 발송 (SND-001)",
            description = "미확인 수신자(전체 또는 선택)에게 리마인드 메일 발송을 요청한다. " +
                    "캠페인 status는 SENT 또는 PARTIAL_FAILED이어야 한다.")
    public ResponseEntity<ApiResponse<ReminderResponse>> sendReminder(
            @Parameter(description = "캠페인 ID (UUID)", required = true)
            @PathVariable String campaignId,
            @Valid @RequestBody ReminderRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String requesterId = jwt.getSubject();
        ReminderResponse data = campaignService.sendReminder(campaignId, request, requesterId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("리마인드 발송 요청이 접수되었습니다.", data));
    }

    @PostMapping("/{campaignId}/resends")
    @Operation(
            summary = "실패 대상자 수동 재발송 (SND-002)",
            description = "실패 대상자(전체 또는 선택)에게 새 링크 발급 후 재발송을 요청한다. " +
                    "영구 실패(INVALID_EMAIL/BLOCKED/COMPLAINT)는 자동 제외된다. " +
                    "캠페인 status는 SENT 또는 PARTIAL_FAILED이어야 한다.")
    public ResponseEntity<ApiResponse<ManualResendResponse>> manualResend(
            @Parameter(description = "캠페인 ID (UUID)", required = true)
            @PathVariable String campaignId,
            @Valid @RequestBody ManualResendRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        String requesterId = jwt.getSubject();
        ManualResendResponse data = campaignService.manualResend(campaignId, request, requesterId);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.accepted("실패 대상자 재발송 요청이 접수되었습니다.", data));
    }

    @GetMapping("/{campaignId}/view-history")
    @Operation(
            summary = "명세서 열람 이력 조회 (RST-002)",
            description = "캠페인별 수신자 명세서 열람 여부, 최초 열람 시각, 링크 상태를 목록으로 반환한다.")
    public ResponseEntity<ApiResponse<ViewHistoryResponse>> getViewHistory(
            @Parameter(description = "캠페인 ID", required = true)
            @PathVariable String campaignId,
            @Parameter(description = "열람 상태 필터 (ALL | VIEWED | UNVIEWED)")
            @RequestParam(defaultValue = "ALL") String filter,
            @Parameter(description = "페이지 번호 (1부터 시작)")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {

        ViewHistoryResponse data = campaignService.getViewHistory(
                campaignId, filter, page, pageSize, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok("명세서 열람 이력 조회 성공", data));
    }

    @GetMapping("/{campaignId}/send-failures")
    @Operation(
            summary = "실패 대상자 목록 조회 (RST-001)",
            description = "대시보드 미리보기 후 '실패 건 보기'로 진입한 운영자에게 실패 대상자의 상세 목록을 반환한다.")
    public ResponseEntity<ApiResponse<SendFailureResponse>> getSendFailures(
            @Parameter(description = "캠페인 ID", required = true)
            @PathVariable String campaignId,
            @Parameter(description = "실패 사유 필터 (예: BOUNCED, INVALID_EMAIL 등)")
            @RequestParam(required = false) String failureReason,
            @Parameter(description = "페이지 번호 (1부터 시작)")
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @Parameter(description = "페이지 크기 (1~50)")
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize,
            @AuthenticationPrincipal Jwt jwt) {

        SendFailureResponse data = campaignService.getSendFailures(
                campaignId, failureReason, page, pageSize, jwt.getSubject());
        return ResponseEntity.ok(ApiResponse.ok("실패 대상자 목록 조회 성공", data));
    }
}