package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.response.RecipientValidationResponse;
import com.paylinker.api.campaign.service.RecipientValidationService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign", description = "캠페인 관련 API")
public class CampaignRecipientController {

    private final RecipientValidationService recipientValidationService;

    @GetMapping("/{campaignId}/recipients/upload/{uploadBatchId}/validation")
    @Operation(
            summary = "수신자 검증 결과 확인 (RCP-002)",
            description = "업로드된 수신자 데이터의 정상/오류/중복 행과 다음 단계 진행 가능 여부를 반환한다.")
    public ResponseEntity<ApiResponse<RecipientValidationResponse>> getValidationResult(
            @Parameter(description = "캠페인 식별자 (UUID)", required = true)
            @PathVariable String campaignId,
            @Parameter(description = "업로드 배치 식별자", required = true)
            @PathVariable String uploadBatchId,
            @AuthenticationPrincipal Jwt jwt) {

        String adminId = jwt.getSubject();
        RecipientValidationResponse data =
                recipientValidationService.getValidationResult(campaignId, uploadBatchId, adminId);
        return ResponseEntity.ok(ApiResponse.ok("수신자 검증 결과 조회 성공", data));
    }
}
