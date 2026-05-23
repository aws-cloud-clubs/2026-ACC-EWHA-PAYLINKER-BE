package com.paylinker.api.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ResendRequestActionRequest {

    @NotBlank(message = "action은 필수입니다.")
    @Pattern(regexp = "APPROVE|REJECT", message = "action은 APPROVE 또는 REJECT여야 합니다.")
    private String action;

    @Size(max = 200, message = "반려 사유는 최대 200자입니다.")
    private String reason;
}
