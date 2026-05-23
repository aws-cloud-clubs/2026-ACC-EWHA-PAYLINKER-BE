package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.request.CampaignCreateRequest;
import com.paylinker.api.campaign.dto.response.CampaignCreateResponse;
import com.paylinker.api.campaign.service.CampaignCreateService;
import com.paylinker.api.campaign.dto.request.ManualResendRequest;
import com.paylinker.api.campaign.dto.request.ReminderRequest;
import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.campaign.dto.response.ManualResendResponse;
import com.paylinker.api.campaign.dto.response.ReminderResponse;
import com.paylinker.api.campaign.service.CampaignService;
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

    @PostMapping("/{campaignId}/reminders")
    @Operation(
            summary = "미확인 수신자 리마인드 발송 (SND-001)",
            description = "미확인 수신자(전체 또는 선택)에게 리마인드 메일 발송을 요청한다. " +
                    "캠페인 status는 SENT 또는 PARTIAL_FAILED이어야 단다.")
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
}