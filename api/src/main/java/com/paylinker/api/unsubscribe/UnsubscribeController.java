package com.paylinker.api.unsubscribe;

import com.paylinker.common.util.UnsubscribeToken;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 수신자 공개 수신거부 엔드포인트.
 * 토큰은 email 해시에 대한 HMAC 서명이라, 검증되면 그 email 해시를 suppression 에 기록한다.
 * GET 은 확인 페이지만, POST 가 실제 처리 (이메일 클라이언트 GET 프리페치의 자동 수신거부 방지).
 */
@RestController
@RequestMapping("/unsubscribe")
public class UnsubscribeController {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter ISO_OFFSET = DateTimeFormatter.ISO_OFFSET_DATE_TIME;
    private static final MediaType HTML_UTF8 = MediaType.valueOf("text/html;charset=UTF-8");

    private final EmailSuppressionRepository suppressionRepository;
    private final String hmacSecret;

    public UnsubscribeController(EmailSuppressionRepository suppressionRepository,
                                 @Value("${paylinker.unsubscribe.hmac-secret}") String hmacSecret) {
        this.suppressionRepository = suppressionRepository;
        this.hmacSecret = hmacSecret;
    }

    @GetMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> page(@PathVariable String token) {
        if (UnsubscribeToken.verify(token, hmacSecret).isEmpty()) {
            return html(HttpStatus.BAD_REQUEST, errorPage());
        }
        return html(HttpStatus.OK, confirmPage(token));
    }

    @PostMapping(value = "/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> unsubscribe(@PathVariable String token) {
        Optional<String> emailHash = UnsubscribeToken.verify(token, hmacSecret);
        if (emailHash.isEmpty()) {
            return html(HttpStatus.BAD_REQUEST, errorPage());
        }
        // 멱등: 이미 수신거부 상태여도 putItem 으로 동일 결과.
        suppressionRepository.suppress(emailHash.get(), ZonedDateTime.now(KST).format(ISO_OFFSET));
        return html(HttpStatus.OK, donePage());
    }

    private ResponseEntity<String> html(HttpStatus status, String body) {
        return ResponseEntity.status(status).contentType(HTML_UTF8).body(body);
    }

    private String confirmPage(String token) {
        return shell("수신 거부",
                "<p>PayLinker 명세서 안내 메일 수신을 거부하시겠어요?</p>"
                + "<form method=\"post\" action=\"/unsubscribe/" + escape(token) + "\">"
                + "<button type=\"submit\" style=\"" + BTN + "\">수신 거부하기</button>"
                + "</form>");
    }

    private String donePage() {
        return shell("수신 거부 완료",
                "<p>수신 거부가 처리되었습니다.<br/>앞으로 안내 메일을 보내지 않습니다.</p>");
    }

    private String errorPage() {
        return shell("링크 오류",
                "<p>유효하지 않은 수신거부 링크입니다.<br/>문의는 운영 담당자에게 부탁드립니다.</p>");
    }

    private static final String BTN =
            "background:#00D4A8;color:#08152C;border:none;border-radius:10px;"
            + "padding:12px 28px;font-size:15px;font-weight:700;cursor:pointer;";

    private String shell(String title, String inner) {
        return "<!DOCTYPE html><html lang=\"ko\"><head><meta charset=\"UTF-8\"/>"
                + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\"/>"
                + "<title>" + title + "</title></head>"
                + "<body style=\"margin:0;background:#F6F8FB;font-family:-apple-system,BlinkMacSystemFont,"
                + "'Apple SD Gothic Neo','Malgun Gothic',sans-serif;\">"
                + "<div style=\"max-width:480px;margin:64px auto;background:#fff;border-radius:16px;"
                + "padding:40px 32px;text-align:center;color:#2A3349;line-height:1.7;"
                + "box-shadow:0 4px 16px rgba(8,21,44,0.06);\">"
                + "<div style=\"color:#1A2C4F;font-size:13px;font-weight:600;letter-spacing:1.2px;"
                + "margin-bottom:16px;\">PAYLINKER</div>"
                + "<h1 style=\"font-size:20px;margin:0 0 16px;\">" + title + "</h1>"
                + inner
                + "</div></body></html>";
    }

    /** 토큰은 HMAC base64url + 해시라 HTML 특수문자가 없지만, action 속성 삽입이라 방어적으로 escape. */
    private static String escape(String s) {
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}
