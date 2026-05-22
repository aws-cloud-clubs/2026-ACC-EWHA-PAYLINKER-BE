package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.response.SendFailureResponse;
import com.paylinker.api.campaign.dto.response.ViewHistoryResponse;
import com.paylinker.api.campaign.service.CampaignService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign", description = "캠페인 관련 API")
public class CampaignController {

    private final CampaignService campaignService;

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
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {

        ViewHistoryResponse data = campaignService.getViewHistory(campaignId, filter, page, pageSize);
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
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int pageSize) {

        SendFailureResponse data = campaignService.getSendFailures(campaignId, failureReason, page, pageSize);
        return ResponseEntity.ok(ApiResponse.ok("실패 대상자 목록 조회 성공", data));
    }
}
