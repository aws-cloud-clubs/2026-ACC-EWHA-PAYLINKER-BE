package com.paylinker.api.user.controller;

import com.paylinker.api.user.dto.response.UserMeResponse;
import com.paylinker.api.user.service.UserService;
import com.paylinker.common.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserMeResponse>> getMe(Authentication authentication) {
        // 컨트롤러는 오직 서비스 호출과 응답 포맷팅만 담당
        UserMeResponse responseData = userService.getMyProfile(authentication);
        return ResponseEntity.ok(ApiResponse.ok("내 정보 조회 성공", responseData));
    }
}