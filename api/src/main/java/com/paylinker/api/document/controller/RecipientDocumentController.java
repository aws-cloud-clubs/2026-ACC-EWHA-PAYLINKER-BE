package com.paylinker.api.document.controller;

import com.paylinker.api.auth.LinkSessionAuthentication;
import com.paylinker.api.document.dto.RecipientDocumentResponse;
import com.paylinker.api.document.service.RecipientDocumentService;
import com.paylinker.common.response.ApiResponse;
import com.paylinker.common.response.CustomException;
import com.paylinker.common.response.ErrorCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수신자 본인 명세서 조회 (DOC-101 / A-3).
 * 인증: SecurityConfig 의 Filter Chain B (`LinkSessionAuthFilter`) 가 Bearer ls_ 토큰을 검증해
 * SecurityContext 에 LinkSessionAuthentication 을 박아둔 상태로 진입한다.
 */
@RestController
@RequestMapping("/api/documents")
public class RecipientDocumentController {

    private final RecipientDocumentService recipientDocumentService;

    public RecipientDocumentController(RecipientDocumentService recipientDocumentService) {
        this.recipientDocumentService = recipientDocumentService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<RecipientDocumentResponse>> getMyDocument(Authentication authentication) {
        if (!(authentication instanceof LinkSessionAuthentication linkAuth) || !linkAuth.isAuthenticated()) {
            throw new CustomException(ErrorCode.LINK_SESSION_EXPIRED);
        }
        RecipientDocumentResponse data = recipientDocumentService.getMyDocument(linkAuth.getSession());
        return ResponseEntity.ok(ApiResponse.ok("내 명세서 조회 성공", data));
    }
}
