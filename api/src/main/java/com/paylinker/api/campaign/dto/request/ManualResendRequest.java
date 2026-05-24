package com.paylinker.api.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ManualResendRequest {

    @NotBlank(message = "target은 필수입니다.")
    @Pattern(regexp = "ALL_FAILED|SELECTED", message = "target은 ALL_FAILED 또는 SELECTED여야 합니다.")
    private String target;

    @Size(max = 500, message = "campaignRecipientIds는 최대 500개까지 허용됩니다.")
    private List<String> campaignRecipientIds;

    /** null이면 서비스에서 기본값(영구 실패 3종)이 적용됩니다. */
    private List<String> excludeFailureReasons;
}
