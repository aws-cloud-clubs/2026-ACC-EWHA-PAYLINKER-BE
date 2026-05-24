package com.paylinker.api.campaign.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.paylinker.api.campaign.dto.response.RecipientValidationErrorItem;
import com.paylinker.api.campaign.dto.response.RecipientValidationResponse;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.campaign.repository.UploadBatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

@Service
@RequiredArgsConstructor
public class RecipientValidationService {

    private final CampaignRepository campaignRepository;
    private final UploadBatchRepository uploadBatchRepository;
    private final S3Client s3Client;
    private final ObjectMapper objectMapper;

    @Value("${aws.s3.upload-bucket}")
    private String uploadBucket;

    public RecipientValidationResponse getValidationResult(String campaignId, String uploadBatchId, String adminId) {
        // 캠페인 존재 및 소유자 검증
        Map<String, AttributeValue> campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND));
        String createdBy = campaign.getOrDefault("created_by", AttributeValue.fromS("")).s();
        if (!adminId.equals(createdBy)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 업로드 배치 조회 (campaign_id 소유권 이중 검증 포함)
        Map<String, AttributeValue> item = uploadBatchRepository
                .findByBatchIdAndCampaignId(uploadBatchId, campaignId)
                .orElseThrow(() -> new CustomException(ErrorCode.RECIPIENT_UPLOAD_BATCH_NOT_FOUND));

        int totalRowCount = Integer.parseInt(item.get("total_row_count").n());
        int validRowCount = Integer.parseInt(item.get("valid_row_count").n());
        int errorRowCount = Integer.parseInt(item.get("error_row_count").n());
        int duplicateRowCount = Integer.parseInt(item.get("duplicate_row_count").n());
        String errorsS3Key = item.getOrDefault("validation_errors_s3_key", AttributeValue.fromS("")).s();

        // 업로드 시점에 저장해둔 errors.json 조회 (재파싱 없음)
        List<RecipientValidationErrorItem> errors = readErrorsFromS3(errorsS3Key);

        return RecipientValidationResponse.builder()
                .uploadBatchId(uploadBatchId)
                .campaignId(campaignId)
                .totalRowCount(totalRowCount)
                .validRowCount(validRowCount)
                .errorRowCount(errorRowCount)
                .duplicateRowCount(duplicateRowCount)
                .canProceed(validRowCount > 0 && errorRowCount == 0)
                .errors(errors)
                .build();
    }

    private List<RecipientValidationErrorItem> readErrorsFromS3(String s3Key) {
        if (s3Key == null || s3Key.isBlank()) {
            return List.of();
        }
        try {
            ResponseBytes<GetObjectResponse> response = s3Client.getObjectAsBytes(
                    GetObjectRequest.builder().bucket(uploadBucket).key(s3Key).build());
            return objectMapper.readValue(response.asByteArray(),
                    new TypeReference<List<RecipientValidationErrorItem>>() {});
        } catch (NoSuchKeyException e) {
            return List.of();
        } catch (Exception e) {
            throw new CustomException(ErrorCode.RECIPIENT_S3_DOWNLOAD_FAILED);
        }
    }
}
