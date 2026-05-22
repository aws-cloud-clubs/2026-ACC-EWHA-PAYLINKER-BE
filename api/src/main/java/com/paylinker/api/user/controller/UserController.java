package com.paylinker.api.user.controller;

import com.paylinker.api.user.dto.response.UserMeResponse;
import com.paylinker.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

@RestController
@RequestMapping("/api/users")
public class UserController {

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(@AuthenticationPrincipal Jwt jwt) {

        // 1. JWT 토큰(Cognito)에서 정보 추출
        String adminId = jwt.getSubject(); // 토큰의 sub (고유 식별자)
        String email = jwt.getClaimAsString("email");
        String name = jwt.getClaimAsString("name");

        // 2. 명세서에 따른 고정값 및 기본값 처리
        String role = "ADMIN"; // 명세서 기준 고정값

        // Cognito 기본 토큰에는 계정 생성일이 없을 수 있으므로,
        // 일단 현재 시각 또는 임시 날짜로 포맷팅하여 에러를 방지합니다.
        String createdAt = ZonedDateTime.now(ZoneId.of("Asia/Seoul"))
                .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);

        // 3. 응답 객체 생성
        UserMeResponse responseData = new UserMeResponse(
                adminId,
                email != null ? email : "unknown@example.com",
                name != null ? name : "관리자",
                role,
                createdAt
        );

        return ResponseEntity.ok(ApiResponse.ok("내 정보 조회 성공", responseData));
    }
}