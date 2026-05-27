package com.paylinker.api.securelink.controller;

import com.paylinker.api.securelink.dto.request.ResendSubmitRequest;
import com.paylinker.api.securelink.dto.response.ResendSubmitResponse;
import com.paylinker.api.securelink.service.SecureLinkService;
import com.paylinker.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/secure-links")
@RequiredArgsConstructor
@Tag(name = "SecureLink", description = "수신자용 보안 링크 API (인증 불필요)")
public class SecureLinkController {

    private final SecureLinkService secureLinkService;

    @PostMapping("/resend-request")
    @Operation(
            summary = "재전송 요청 제출 (A-5 / ERR-003)",
            description = """
                    수신자가 링크 에러 페이지에서 재전송을 요청한다.
                    - 인증 불필요 (public) — token 유효성만 검증
                    - 만료/재사용 링크여도 요청 접수 가능
                    - 운영자 /api/notifications/resend-requests 큐에 새 항목 추가
                    """)
    public ResponseEntity<ApiResponse<ResendSubmitResponse>> submitResendRequest(
            @Valid @RequestBody ResendSubmitRequest request) {

        ResendSubmitResponse data = secureLinkService.submitResendRequest(request);
        return ResponseEntity.ok(ApiResponse.ok("재전송 요청이 접수되었습니다.", data));
    }
}
