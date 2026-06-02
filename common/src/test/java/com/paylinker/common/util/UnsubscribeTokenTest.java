package com.paylinker.common.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnsubscribeTokenTest {

    private static final String SECRET = "test-secret-1234567890";

    @Test
    void roundTripReturnsRecipientId() {
        String token = UnsubscribeToken.generate("rcp_abc123", SECRET);
        assertEquals(Optional.of("rcp_abc123"), UnsubscribeToken.verify(token, SECRET));
    }

    @Test
    void tamperedTokenRejected() {
        String token = UnsubscribeToken.generate("rcp_abc123", SECRET);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertTrue(UnsubscribeToken.verify(tampered, SECRET).isEmpty());
    }

    @Test
    void wrongSecretRejected() {
        String token = UnsubscribeToken.generate("rcp_abc123", SECRET);
        assertTrue(UnsubscribeToken.verify(token, "different-secret").isEmpty());
    }

    @Test
    void malformedTokenRejected() {
        assertTrue(UnsubscribeToken.verify(null, SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify("no-dot-here", SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify("rcp_x.", SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify(".sig", SECRET).isEmpty());
    }

    @Test
    void blankSecretRejected() {
        String token = UnsubscribeToken.generate("rcp_abc123", SECRET);
        assertTrue(UnsubscribeToken.verify(token, "").isEmpty());
        assertTrue(UnsubscribeToken.verify(token, null).isEmpty());
    }

    @Test
    void goldenVectorPinsCrossModuleCompatibility() {
        // worker(발급)·api(검증)가 같은 토큰을 만들어야 하므로 고정 벡터로 알고리즘 드리프트를 잡는다.
        // worker 모듈의 동일 테스트와 같은 (subject, secret) -> token 을 공유한다.
        String token = UnsubscribeToken.generate("h_golden_email", "golden-secret");
        assertEquals("h_golden_email.kLuTExotplEVeEq1j-juFi3Pp_-xKfDDl0VBvbtBPK4", token);
        assertEquals(Optional.of("h_golden_email"), UnsubscribeToken.verify(token, "golden-secret"));
    }
}
