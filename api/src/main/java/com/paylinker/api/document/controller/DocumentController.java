package com.paylinker.api.document.controller;

import com.paylinker.api.document.dto.DocumentMatchResultsResponse;
import com.paylinker.api.document.dto.DocumentUploadResponse;
import com.paylinker.api.document.service.DocumentService;
import com.paylinker.common.response.ApiResponse;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/campaigns/{campaignId}/documents")
public class DocumentController {

    private final DocumentService documentService;

    public DocumentController(DocumentService documentService) {
        this.documentService = documentService;
    }

    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<DocumentUploadResponse>> upload(
            @PathVariable String campaignId,
            @RequestParam("file") MultipartFile file,
            @RequestParam("documentType") String documentType,
            @RequestParam("matchKey") String matchKey,
            Authentication authentication) {
        String adminId = authentication.getName();
        DocumentUploadResponse data = documentService.upload(adminId, campaignId, file, documentType, matchKey);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.created("명세서 업로드 완료", data));
    }

    @GetMapping("/match-results")
    public ResponseEntity<ApiResponse<DocumentMatchResultsResponse>> getMatchResults(
            @PathVariable String campaignId,
            @RequestParam(required = false) String filter,
            Authentication authentication) {
        String adminId = authentication.getName();
        DocumentMatchResultsResponse data = documentService.getMatchResults(adminId, campaignId, filter);
        return ResponseEntity.ok(ApiResponse.ok("명세서 매칭 결과 조회 성공", data));
    }
}
