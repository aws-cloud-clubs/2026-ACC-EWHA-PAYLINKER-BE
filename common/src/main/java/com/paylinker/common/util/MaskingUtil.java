package com.paylinker.common.util;

public final class MaskingUtil {

    private static final int EMAIL_PREFIX_LENGTH = 3;
    private static final String EMAIL_MASK = "***";
    private static final int EMPLOYEE_NO_PREFIX_LENGTH = 2;

    private MaskingUtil() {
    }

    public static String maskEmail(String email) {
        if (email == null) {
            return null;
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return email;
        }
        String local = email.substring(0, at);
        String domain = email.substring(at);
        String prefix = local.length() <= EMAIL_PREFIX_LENGTH ? local : local.substring(0, EMAIL_PREFIX_LENGTH);
        return prefix + EMAIL_MASK + domain;
    }

    public static String maskEmployeeNo(String employeeNo) {
        if (employeeNo == null) {
            return null;
        }
        if (employeeNo.length() <= EMPLOYEE_NO_PREFIX_LENGTH) {
            return employeeNo;
        }
        String prefix = employeeNo.substring(0, EMPLOYEE_NO_PREFIX_LENGTH);
        return prefix + "*".repeat(employeeNo.length() - EMPLOYEE_NO_PREFIX_LENGTH);
    }
}
