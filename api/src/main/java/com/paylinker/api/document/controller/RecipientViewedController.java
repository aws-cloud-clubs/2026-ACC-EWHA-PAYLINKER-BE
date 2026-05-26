package com.paylinker.api.document.controller;

import com.paylinker.api.auth.LinkSessionAuthentication;
import com.paylinker.api.document.dto.ViewedResponse;
import com.paylinker.api.document.service.RecipientViewedService;
import com.paylinker.common.response.ApiResponse;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수신자 확인 처리 (DOC-102 / A-4).
 * 인증: SecurityConfig 의 Filter Chain B 가 Bearer ls_ 토큰을 검증한 상태로 진입.
 */
@RestController
@RequestMapping("/api/documents/me")
public class RecipientViewedController {

    private final RecipientViewedService recipientViewedService;

    public RecipientViewedController(RecipientViewedService recipientViewedService) {
        this.recipientViewedService = recipientViewedService;
    }

    @PostMapping("/viewed")
    public ResponseEntity<ApiResponse<ViewedResponse>> markViewed(Authentication authentication) {
        if (!(authentication instanceof LinkSessionAuthentication linkAuth) || !linkAuth.isAuthenticated()) {
            throw new CustomException(ErrorCode.LINK_SESSION_EXPIRED);
        }
        ViewedResponse data = recipientViewedService.markViewed(linkAuth.getSession());
        return ResponseEntity.ok(ApiResponse.ok("확인 처리 완료", data));
    }
}
