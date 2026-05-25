package com.paylinker.api.campaign.service;

import com.paylinker.api.campaign.dto.response.CampaignFinalReviewResponse;
import com.paylinker.api.entity.PaylinkerCampaign;
import com.paylinker.api.entity.PaylinkerDocumentMatch;
import com.paylinker.api.entity.enums.CampaignStatus;
import com.paylinker.api.campaign.repository.CampaignRepository;
import com.paylinker.api.repository.DocumentMatchRepository;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class CampaignFinalReviewService {

    private final CampaignRepository campaignRepository;
    private final DocumentMatchRepository documentMatchRepository;

    public CampaignFinalReviewService(CampaignRepository campaignRepository,
                                      DocumentMatchRepository documentMatchRepository) {
        this.campaignRepository = campaignRepository;
        this.documentMatchRepository = documentMatchRepository;
    }

    public CampaignFinalReviewResponse getFinalReviewInfo(String adminId, String campaignId) {
        // 1. 캠페인 정보 조회 및 유효성 검증
        PaylinkerCampaign campaign = campaignRepository.findCampaignById(campaignId);
        if (campaign == null) {
            throw new CustomException(ErrorCode.CAMPAIGN_NOT_FOUND);
        }

        // 2. 권한 검증
        if (!campaign.getAdminId().equals(adminId)) {
            throw new CustomException(ErrorCode.CAMPAIGN_FORBIDDEN);
        }

        // 3. DocumentMatchRepository를 이용해 실제 상태별 카운트 조회
        int matchedCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_MATCHED);
        int unmatchedRecipientCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_UNMATCHED);
        int duplicateMatchCount = documentMatchRepository.countByStatus(campaignId, PaylinkerDocumentMatch.STATUS_DUPLICATE_MATCH);

        int totalRecipientCount = campaign.getTotalRecipientCount() != null ? campaign.getTotalRecipientCount() : 0;
        CampaignStatus status = campaign.getStatus();

        // 4. 발송 가능 여부(canSend) 조건식 판별
        boolean isStatusValid = (status == CampaignStatus.READY || status == CampaignStatus.SCHEDULED);
        boolean hasRecipients = totalRecipientCount > 0;

        boolean canSend = (unmatchedRecipientCount == 0)
                && (duplicateMatchCount == 0)
                && hasRecipients
                && isStatusValid;

        // 5. 차단 사유(blockingIssues) 메시지 빌드
        List<String> blockingIssues = new ArrayList<>();
        if (!canSend) {
            if (unmatchedRecipientCount > 0) {
                blockingIssues.add("매칭되지 않은 수신자 " + unmatchedRecipientCount + "명이 있습니다. 명세서를 재업로드하거나 수신자를 보정해 주세요.");
            }
            if (duplicateMatchCount > 0) {
                blockingIssues.add("중복 매칭된 수신자가 " + duplicateMatchCount + "건 있습니다. 데이터를 확인해 주세요.");
            }
            if (!hasRecipients) {
                blockingIssues.add("발송 대상 수신자가 없습니다. 수신자 데이터를 먼저 업로드해 주세요.");
            }
            if (!isStatusValid) {
                blockingIssues.add("현재 캠페인 상태(" + status.name() + ")는 발송할 수 없는 상태입니다.");
            }
        }

        // 6. 결과 반환
        return new CampaignFinalReviewResponse(
                campaign.getCampaignId(),
                campaign.getCampaignName(),
                campaign.getEmailSubject(),
                campaign.getEmailDescription(),
                campaign.getLinkTtlHours(),
                totalRecipientCount,
                matchedCount,
                unmatchedRecipientCount,
                duplicateMatchCount,
                campaign.getScheduledSendAt(),
                canSend,
                blockingIssues
        );
    }
}