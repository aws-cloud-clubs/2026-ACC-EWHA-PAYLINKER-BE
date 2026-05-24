package com.paylinker.api.campaign.dto.request;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record CampaignUpdateRequest(
        @Size(max = 20, message = "캠페인명은 최대 20자여야 합니다.")
        String campaignName,

        @Size(max = 80, message = "이메일 제목은 최대 80자여야 합니다.")
        String emailSubject,

        @Size(max = 500, message = "이메일 본문은 최대 500자여야 합니다.")
        String emailDescription,

        @Min(value = 1, message = "보안 링크 유효 시간은 1시간 이상이어야 합니다.")
        @Max(value = 720, message = "보안 링크 유효 시간은 720시간 이하여야 합니다.")
        Integer linkTtlHours,

        Boolean allowOneTimeLink,
        Boolean allowResendRequest,

        @Min(value = 1, message = "재전송 요청 허용 횟수는 1회 이상이어야 합니다.")
        @Max(value = 5, message = "재전송 요청 허용 횟수는 5회 이하여야 합니다.")
        Integer resendRequestLimit,

        @Min(value = 1, message = "최대 수신자 수는 1명 이상이어야 합니다.")
        @Max(value = 100000, message = "최대 수신자 수는 100,000명 이하여야 합니다.")
        Integer maxRecipients,

        @Min(value = 1, message = "1일 발송 한도는 1명 이상이어야 합니다.")
        @Max(value = 100000, message = "1일 발송 한도는 100,000명 이하여야 합니다.")
        Integer maxDailyCount
) {}