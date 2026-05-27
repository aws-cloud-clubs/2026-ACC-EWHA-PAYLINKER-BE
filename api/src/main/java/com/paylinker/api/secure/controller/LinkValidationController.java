package com.paylinker.api.secure.controller;

import com.paylinker.api.secure.dto.LinkSessionResponse;
import com.paylinker.api.secure.dto.LinkValidateRequest;
import com.paylinker.api.secure.service.LinkValidationService;
import com.paylinker.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수신자 보안 링크 검증 (LNK-001 / A-2).
 * SecurityConfig Filter Chain B 에서 permitAll 로 설정되어 인증 없이 호출 가능하다.
 */
@RestController
@RequestMapping("/api/secure-links")
public class LinkValidationController {

    private final LinkValidationService linkValidationService;

    public LinkValidationController(LinkValidationService linkValidationService) {
        this.linkValidationService = linkValidationService;
    }

    @PostMapping("/validate")
    public ResponseEntity<ApiResponse<LinkSessionResponse>> validate(
            @Valid @RequestBody LinkValidateRequest request) {
        LinkSessionResponse data = linkValidationService.validateAndIssue(request.token());
        return ResponseEntity.ok(ApiResponse.ok("보안 링크 검증 성공", data));
    }
}
