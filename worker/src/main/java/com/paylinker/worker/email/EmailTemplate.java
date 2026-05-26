package com.paylinker.worker.email;

/**
 * 수신자에게 발송할 이메일 HTML 템플릿.
 * 디자인 토큰: navy/mint/gray (라이트 모드).
 * inline CSS + table 기반 레이아웃 (Gmail, Outlook, Naver, Daum 호환).
 */
public final class EmailTemplate {

    private static final String NAVY_900 = "#08152C";
    private static final String NAVY_700 = "#1A2C4F";
    private static final String NAVY_600 = "#2E4267";
    private static final String NAVY_400 = "#6E80A0";
    private static final String MINT_500 = "#00D4A8";
    private static final String MINT_600 = "#00B894";
    private static final String GRAY_50 = "#F6F8FB";
    private static final String GRAY_100 = "#ECEFF4";
    private static final String GRAY_500 = "#67748D";
    private static final String GRAY_700 = "#2A3349";
    private static final String WHITE = "#FFFFFF";

    private EmailTemplate() {}

    public static String render(String recipientName,
                                String campaignName,
                                String emailSubject,
                                String emailDescription,
                                String linkUrl,
                                String expiresAtKst) {
        String safeRecipientName = esc(blankToDefault(recipientName, "고객"));
        String safeCampaignName = esc(blankToDefault(campaignName, "명세서 안내"));
        String safeSubject = esc(blankToDefault(emailSubject, safeCampaignName));
        String safeDescription = esc(blankToDefault(emailDescription, "아래 버튼을 눌러 본인 명세서를 확인해 주세요."));
        String safeLinkUrl = esc(linkUrl);
        String safeExpires = esc(blankToDefault(expiresAtKst, ""));

        return ""
                + "<!DOCTYPE html>\n"
                + "<html lang=\"ko\">\n"
                + "<head>\n"
                + "  <meta charset=\"UTF-8\"/>\n"
                + "  <meta name=\"color-scheme\" content=\"light only\"/>\n"
                + "  <meta name=\"supported-color-schemes\" content=\"light\"/>\n"
                + "  <title>" + safeSubject + "</title>\n"
                + "</head>\n"
                + "<body style=\"margin:0;padding:0;background-color:" + GRAY_50 + ";"
                +    "font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,'Apple SD Gothic Neo','Malgun Gothic',sans-serif;\">\n"
                + "  <table role=\"presentation\" width=\"100%\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                +    " style=\"background-color:" + GRAY_50 + ";padding:40px 16px;\">\n"
                + "    <tr><td align=\"center\">\n"
                + "      <table role=\"presentation\" width=\"560\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                +        " style=\"max-width:560px;width:100%;background-color:" + WHITE + ";"
                +        "border-radius:16px;overflow:hidden;box-shadow:0 4px 16px rgba(8,21,44,0.06);\">\n"
                // 헤더 (navy 그라데이션 느낌은 단색으로 fallback)
                + "        <tr><td style=\"background-color:" + NAVY_700 + ";padding:32px 36px 28px;\">\n"
                + "          <div style=\"color:" + MINT_500 + ";font-size:13px;font-weight:600;letter-spacing:1.2px;"
                +              "text-transform:uppercase;margin-bottom:8px;\">PAYLINKER</div>\n"
                + "          <div style=\"color:" + WHITE + ";font-size:22px;font-weight:700;line-height:1.4;\">"
                +              safeSubject + "</div>\n"
                + "          <div style=\"color:#C8D1E0;font-size:13px;margin-top:6px;\">" + safeCampaignName + "</div>\n"
                + "        </td></tr>\n"
                // 본문
                + "        <tr><td style=\"padding:36px 36px 28px;color:" + GRAY_700 + ";font-size:15px;line-height:1.7;\">\n"
                + "          <div style=\"margin-bottom:16px;\">" + safeRecipientName + " 님, 안녕하세요.</div>\n"
                + "          <div style=\"margin-bottom:24px;white-space:pre-line;\">" + safeDescription + "</div>\n"
                // CTA 버튼
                + "          <table role=\"presentation\" cellpadding=\"0\" cellspacing=\"0\" border=\"0\""
                +              " style=\"margin:8px 0 24px;\">\n"
                + "            <tr><td style=\"background-color:" + MINT_500 + ";border-radius:10px;\">\n"
                + "              <a href=\"" + safeLinkUrl + "\" target=\"_blank\""
                +                " style=\"display:inline-block;padding:14px 32px;color:" + NAVY_900 + ";"
                +                "font-weight:700;font-size:15px;text-decoration:none;letter-spacing:0.2px;\">명세서 확인하기</a>\n"
                + "            </td></tr>\n"
                + "          </table>\n"
                // 만료 안내
                + (safeExpires.isEmpty() ? ""
                    : "          <div style=\"font-size:13px;color:" + GRAY_500 + ";margin-bottom:8px;\">"
                      + "확인 가능 기한: " + safeExpires + "</div>\n")
                + "          <div style=\"font-size:13px;color:" + GRAY_500 + ";\">"
                +              "버튼이 동작하지 않으면 아래 주소를 브라우저에 직접 붙여넣어 주세요."
                +            "</div>\n"
                + "          <div style=\"font-size:12px;color:" + NAVY_400 + ";margin-top:4px;word-break:break-all;\">"
                +              safeLinkUrl + "</div>\n"
                + "        </td></tr>\n"
                // 푸터
                + "        <tr><td style=\"background-color:" + GRAY_100 + ";padding:20px 36px;"
                +              "font-size:12px;color:" + GRAY_500 + ";text-align:center;line-height:1.6;\">\n"
                + "          본 메일은 발신 전용입니다. 문의는 운영 담당자에게 부탁드립니다.<br/>\n"
                + "          ⓒ PayLinker\n"
                + "        </td></tr>\n"
                + "      </table>\n"
                + "    </td></tr>\n"
                + "  </table>\n"
                + "</body>\n"
                + "</html>\n";
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    private static String blankToDefault(String s, String defaultValue) {
        return (s == null || s.isBlank()) ? defaultValue : s;
    }
}
