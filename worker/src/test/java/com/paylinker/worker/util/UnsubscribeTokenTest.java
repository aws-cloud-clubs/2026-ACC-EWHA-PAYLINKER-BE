package com.paylinker.worker.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnsubscribeTokenTest {

    private static final String SECRET = "test-secret-1234567890";

    @Test
    void roundTripReturnsSubject() {
        String token = UnsubscribeToken.generate("h_email", SECRET);
        assertEquals(Optional.of("h_email"), UnsubscribeToken.verify(token, SECRET));
    }

    @Test
    void tamperedTokenRejected() {
        String token = UnsubscribeToken.generate("h_email", SECRET);
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'A' ? 'B' : 'A');
        assertTrue(UnsubscribeToken.verify(tampered, SECRET).isEmpty());
    }

    @Test
    void blankSecretRejected() {
        String token = UnsubscribeToken.generate("h_email", SECRET);
        assertTrue(UnsubscribeToken.verify(token, "").isEmpty());
        assertTrue(UnsubscribeToken.verify(token, null).isEmpty());
    }

    @Test
    void goldenVectorMatchesApiModule() {
        // api(common) 모듈 UnsubscribeTokenTest 와 동일 벡터. 두 복제 구현의 서명 호환을 고정.
        String token = UnsubscribeToken.generate("h_golden_email", "golden-secret");
        assertEquals("h_golden_email.kLuTExotplEVeEq1j-juFi3Pp_-xKfDDl0VBvbtBPK4", token);
    }

    @Test
    void wrongSecretRejected() {
        String token = UnsubscribeToken.generate("h_email", SECRET);
        assertTrue(UnsubscribeToken.verify(token, "different-secret").isEmpty());
    }

    @Test
    void malformedTokenRejected() {
        assertTrue(UnsubscribeToken.verify(null, SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify("no-dot-here", SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify("h_x.", SECRET).isEmpty());
        assertTrue(UnsubscribeToken.verify(".sig", SECRET).isEmpty());
    }
}
