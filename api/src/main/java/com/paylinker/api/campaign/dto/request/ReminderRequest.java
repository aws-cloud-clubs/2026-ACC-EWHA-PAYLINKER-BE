package com.paylinker.api.campaign.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ReminderRequest {

    @NotBlank(message = "target은 필수입니다.")
    @Pattern(regexp = "ALL_UNVIEWED|SELECTED", message = "target은 ALL_UNVIEWED 또는 SELECTED여야 합니다.")
    private String target;

    @Size(max = 1000, message = "campaignRecipientIds는 최대 1000개까지 허용됩니다.")
    private List<String> campaignRecipientIds;
}
