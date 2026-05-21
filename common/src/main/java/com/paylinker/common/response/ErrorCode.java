package com.paylinker.common.response;

public enum ErrorCode {

    AUTH_INVALID_TOKEN(401, "유효하지 않은 토큰입니다."),

    CAMPAIGN_NOT_FOUND(404, "해당 캠페인을 찾을 수 없습니다."),
    CAMPAIGN_FORBIDDEN(403, "해당 캠페인 조회 권한이 없습니다."),
    CAMPAIGN_ALREADY_SENDING(409, "이미 발송 중이거나 완료된 캠페인입니다."),
    CAMPAIGN_CANNOT_SEND(422, "발송 조건을 만족하지 못합니다."),

    LINK_EXPIRED(401, "보안 링크가 만료되었습니다."),
    LINK_REUSED(401, "이미 사용된 일회용 링크입니다."),
    LINK_INVALID(400, "유효하지 않은 보안 링크입니다."),
    LINK_INVALIDATED(403, "재전송으로 무효화된 링크입니다."),
    LINK_SESSION_EXPIRED(401, "세션이 만료되었습니다."),

    DOCUMENT_NOT_FOUND(404, "명세서를 찾을 수 없습니다."),

    RECIPIENT_FILE_UNSUPPORTED(400, "지원하지 않는 파일 형식입니다."),
    RECIPIENT_FILE_TOO_LARGE(413, "업로드 가능한 최대 크기를 초과했습니다."),
    RECIPIENT_CAMPAIGN_LIMIT_EXCEEDED(409, "캠페인 최대 수신자 수를 초과했습니다."),

    RESEND_REQUEST_DUPLICATE(409, "이미 동일한 재전송 요청이 접수되어 있습니다."),
    RESEND_NOT_ALLOWED(422, "이 캠페인은 재전송 요청을 받지 않습니다."),
    RESEND_LIMIT_EXCEEDED(422, "캠페인 재전송 한도를 초과했습니다."),
    RESEND_REQUEST_NOT_FOUND(404, "해당 재전송 요청을 찾을 수 없습니다."),
    RESEND_REQUEST_ALREADY_PROCESSED(409, "이미 처리된 재전송 요청입니다."),
    ;

    private final int httpStatus;
    private final String message;

    ErrorCode(int httpStatus, String message) {
        this.httpStatus = httpStatus;
        this.message = message;
    }

    public int getHttpStatus() {
        return httpStatus;
    }

    public String getMessage() {
        return message;
    }
}
