package com.paylinker.worker.email;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmailTemplateTest {

    @Test
    void rendersPlaceholders() {
        String html = EmailTemplate.render(
                "민수정",
                "2026년 5월 급여 명세서",
                "5월 명세서 안내",
                "급여 명세서를 확인해 주세요.",
                "https://pay.example/abc",
                "2026-06-10 18:00 KST"
        );

        assertTrue(html.contains("민수정 님, 안녕하세요."));
        assertTrue(html.contains("2026년 5월 급여 명세서"));
        assertTrue(html.contains("5월 명세서 안내"));
        assertTrue(html.contains("급여 명세서를 확인해 주세요."));
        assertTrue(html.contains("https://pay.example/abc"));
        assertTrue(html.contains("확인 가능 기한: 2026-06-10 18:00 KST"));
    }

    @Test
    void appliesDefaultsForBlankValues() {
        String html = EmailTemplate.render(null, null, null, null, "https://pay.example/x", "");

        assertTrue(html.contains("고객 님, 안녕하세요."));
        assertTrue(html.contains("명세서 안내"));
        assertTrue(html.contains("아래 버튼을 눌러 본인 명세서를 확인해 주세요."));
        assertFalse(html.contains("확인 가능 기한:"));
    }

    @Test
    void escapesXssInUserSuppliedFields() {
        String html = EmailTemplate.render(
                "<script>alert('xss')</script>",
                "C & D <Inc>",
                "subj \"q\"",
                "desc < > & \"",
                "https://pay.example/?a=1&b=2",
                "2026-06-10"
        );

        assertFalse(html.contains("<script>alert"));
        assertFalse(html.contains("alert('xss')"));
        assertTrue(html.contains("&lt;script&gt;alert"));
        assertTrue(html.contains("C &amp; D &lt;Inc&gt;"));
        assertTrue(html.contains("subj &quot;q&quot;"));
        assertTrue(html.contains("desc &lt; &gt; &amp; &quot;"));
        // Mustache 는 & 를 &amp; 로 escape — 브라우저가 디코드 후 정상 URL 로 해석.
        assertTrue(html.contains("&amp;b"));
    }

    @Test
    void omitsExpiryBlockWhenBlank() {
        String html = EmailTemplate.render("이름", "캠페인", "제목", "설명", "https://x", null);

        assertFalse(html.contains("확인 가능 기한"));
    }
}
