package com.paylinker.worker.email;

import com.samskivert.mustache.Mustache;
import com.samskivert.mustache.Template;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/**
 * 수신자에게 발송할 이메일 HTML 템플릿.
 * 디자인 토큰(navy/mint/gray)을 따르는 .html 리소스를 Mustache 엔진으로 렌더링.
 * placeholder 는 기본적으로 HTML escape 되며, 빈 값에는 기본값을 채워 넣는다.
 */
public final class EmailTemplate {

    private static final String TEMPLATE_PATH = "templates/email.html";

    private static final Template TEMPLATE = loadTemplate();

    private EmailTemplate() {}

    public static String render(String recipientName,
                                String campaignName,
                                String emailSubject,
                                String emailDescription,
                                String linkUrl,
                                String expiresAtKst) {
        String campaign = blankToDefault(campaignName, "명세서 안내");
        String subject = blankToDefault(emailSubject, campaign);
        String expires = blankToDefault(expiresAtKst, "");

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("recipientName", blankToDefault(recipientName, "고객"));
        ctx.put("campaignName", campaign);
        ctx.put("subject", subject);
        ctx.put("description", blankToDefault(emailDescription, "아래 버튼을 눌러 본인 명세서를 확인해 주세요."));
        ctx.put("linkUrl", linkUrl == null ? "" : linkUrl);
        ctx.put("expiresAtKst", expires);
        ctx.put("hasExpires", !expires.isEmpty());

        return TEMPLATE.execute(ctx);
    }

    private static Template loadTemplate() {
        ClassLoader cl = EmailTemplate.class.getClassLoader();
        try (InputStream in = cl.getResourceAsStream(TEMPLATE_PATH)) {
            if (in == null) {
                throw new IllegalStateException("email template not found: " + TEMPLATE_PATH);
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                return Mustache.compiler().escapeHTML(true).compile(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("failed to load email template", e);
        }
    }

    private static String blankToDefault(String s, String defaultValue) {
        return (s == null || s.isBlank()) ? defaultValue : s;
    }
}
