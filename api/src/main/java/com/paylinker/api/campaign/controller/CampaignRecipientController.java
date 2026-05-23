package com.paylinker.api.campaign.controller;

import com.paylinker.api.campaign.dto.response.RecipientUploadResponse;
import com.paylinker.api.campaign.service.CampaignRecipientService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@Tag(name = "Campaign", description = "캠페인 관련 API")
public class CampaignRecipientController {

    private final CampaignRecipientService campaignRecipientService;

    @PostMapping(value = "/{campaignId}/recipients/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "수신자 데이터 업로드 (RCP-001)",
            description = "수신자 파일(.csv/.xlsx)을 업로드하여 캠페인 대상자를 등록하고 요약 카운트를 반환한다.")
    public ResponseEntity<ApiResponse<RecipientUploadResponse>> uploadRecipients(
            @Parameter(description = "캠페인 식별자 (UUID)", required = true)
            @PathVariable String campaignId,
            @Parameter(description = "수신자 데이터 파일 (.csv 또는 .xlsx, 최대 10MB)", required = true)
            @RequestPart("file") MultipartFile file,
            @Parameter(description = "업로드 모드 (FULL_REPLACE | APPEND, 기본값 FULL_REPLACE)")
            @RequestParam(value = "uploadType", required = false) String uploadType) {

        RecipientUploadResponse data = campaignRecipientService.uploadRecipients(campaignId, file, uploadType);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("수신자 파일 업로드 완료", data));
    }
}
