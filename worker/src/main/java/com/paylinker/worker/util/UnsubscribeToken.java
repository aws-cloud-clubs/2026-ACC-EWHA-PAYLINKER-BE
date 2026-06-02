package com.paylinker.worker.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * 수신거부 링크용 HMAC 서명 토큰.
 * 형식: {@code subject + "." + base64url(HMAC_SHA256(subject, secret))}.
 * common 모듈의 com.paylinker.common.util.UnsubscribeToken(api 가 사용) 과 동일 로직.
 * worker 는 common 을 의존하지 않으므로 같은 구현을 복제해 둔다(서명 시크릿이 같아야 검증 통과).
 */
public final class UnsubscribeToken {

    private static final String HMAC_ALGO = "HmacSHA256";

    private UnsubscribeToken() {}

    /** subject 에 HMAC 서명을 붙여 토큰을 만든다. */
    public static String generate(String subject, String secret) {
        return subject + "." + sign(subject, secret);
    }

    /** 유효하면 subject, 위변조·형식오류·서명불일치면 empty. */
    public static Optional<String> verify(String token, String secret) {
        // 시크릿 미설정(빈 값)이면 HMAC 키 생성이 불가하므로 검증을 무효 처리(공개 엔드포인트 500 방지).
        if (token == null || secret == null || secret.isEmpty()) {
            return Optional.empty();
        }
        int dot = token.lastIndexOf('.');
        if (dot <= 0 || dot == token.length() - 1) {
            return Optional.empty();
        }
        String subject = token.substring(0, dot);
        String sig = token.substring(dot + 1);
        byte[] given = sig.getBytes(StandardCharsets.UTF_8);
        byte[] expected = sign(subject, secret).getBytes(StandardCharsets.UTF_8);
        if (!MessageDigest.isEqual(given, expected)) {
            return Optional.empty();
        }
        return Optional.of(subject);
    }

    private static String sign(String data, String secret) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGO);
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), HMAC_ALGO));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC 서명 실패", e);
        }
    }
}
