package com.paylinker.api.securelink.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class ResendSubmitRequest {

    @NotBlank(message = "token은 필수입니다.")
    private String token;

    @NotBlank(message = "reason은 필수입니다.")
    @Pattern(
            regexp = "EXPIRED|REUSED|INVALID_LINK|UNAUTHORIZED|UNKNOWN",
            message = "reason은 EXPIRED|REUSED|INVALID_LINK|UNAUTHORIZED|UNKNOWN 중 하나여야 합니다.")
    private String reason;
}
