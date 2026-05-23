package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.request.CampaignCreateRequest;
import com.paylinker.api.campaign.dto.response.CampaignCreateResponse;
import com.paylinker.api.campaign.dto.response.CampaignListResponse;
import com.paylinker.api.campaign.service.CampaignCreateService;
import com.paylinker.api.campaign.service.CampaignService;
import com.paylinker.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/campaigns")
@Validated
public class CampaignController {

    private final CampaignService campaignService;
    private final CampaignCreateService campaignCreateService;

    public CampaignController(CampaignService campaignService, CampaignCreateService campaignCreateService) {
        this.campaignService = campaignService;
        this.campaignCreateService = campaignCreateService;
    }

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
}