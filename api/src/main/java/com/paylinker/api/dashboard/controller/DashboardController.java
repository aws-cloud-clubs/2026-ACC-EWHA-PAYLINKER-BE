package com.paylinker.api.dashboard.controller;

import com.paylinker.api.dashboard.dto.DashboardCampaignSummaryResponse;
import com.paylinker.api.dashboard.dto.DashboardFailureResponse;
import com.paylinker.api.dashboard.dto.DashboardSummaryResponse;
import com.paylinker.api.dashboard.dto.DashboardUnviewedResponse;
import com.paylinker.api.dashboard.dto.DashboardViewTrendResponse;
import com.paylinker.api.dashboard.service.DashboardService;
import com.paylinker.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;

    public DashboardController(DashboardService dashboardService) {
        this.dashboardService = dashboardService;
    }

    @GetMapping("/summary")
    public ResponseEntity<ApiResponse<DashboardSummaryResponse>> getSummary(Authentication authentication) {
        String adminId = authentication.getName();
        DashboardSummaryResponse data = dashboardService.getSummary(adminId);
        return ResponseEntity.ok(ApiResponse.ok("대시보드 요약 조회 성공", data));
    }

    @GetMapping("/campaigns/{campaignId}/summary")
    public ResponseEntity<ApiResponse<DashboardCampaignSummaryResponse>> getCampaignSummary(
            @PathVariable String campaignId,
            Authentication authentication) {
        String adminId = authentication.getName();
        DashboardCampaignSummaryResponse data = dashboardService.getCampaignSummary(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("캠페인 요약 조회 성공", data));
    }

    @GetMapping("/campaigns/{campaignId}/unviewed-recipients")
    public ResponseEntity<ApiResponse<DashboardUnviewedResponse>> getUnviewedRecipients(
            @PathVariable String campaignId,
            Authentication authentication) {
        String adminId = authentication.getName();
        DashboardUnviewedResponse data = dashboardService.getUnviewedRecipients(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("미확인 수신자 현황 조회 성공", data));
    }

    @GetMapping("/campaigns/{campaignId}/send-failures")
    public ResponseEntity<ApiResponse<DashboardFailureResponse>> getSendFailures(
            @PathVariable String campaignId,
            Authentication authentication) {
        String adminId = authentication.getName();
        DashboardFailureResponse data = dashboardService.getSendFailures(adminId, campaignId);
        return ResponseEntity.ok(ApiResponse.ok("발송 실패 대상자 현황 조회 성공", data));
    }

    @GetMapping("/campaigns/{campaignId}/view-trend")
    public ResponseEntity<ApiResponse<DashboardViewTrendResponse>> getViewTrend(
            @PathVariable String campaignId,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            Authentication authentication) {
        String adminId = authentication.getName();
        DashboardViewTrendResponse data = dashboardService.getViewTrend(adminId, campaignId, from, to);
        return ResponseEntity.ok(ApiResponse.ok("명세서 확인 추이 조회 성공", data));
    }
}
