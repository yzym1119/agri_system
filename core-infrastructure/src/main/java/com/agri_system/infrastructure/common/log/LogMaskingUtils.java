package com.agri_system.infrastructure.common.log;

import java.util.regex.Pattern;

public final class LogMaskingUtils {

    private static final Pattern PASSWORD_PATTERN = Pattern.compile("\"(password|payPassword|oldPassword|newPassword)\"\\s*:\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern TOKEN_PATTERN = Pattern.compile("\"(token|Authorization)\"\\s*:\\s*\"(.*?)\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern ID_CARD_PATTERN = Pattern.compile("\"(idCard)\"\\s*:\\s*\"([0-9A-Za-z]{6})([^\"-]{6,})([0-9A-Za-z]{4})\"", Pattern.CASE_INSENSITIVE);
    private static final Pattern MOBILE_PATTERN = Pattern.compile("\"(mobile|phone)\"\\s*:\\s*\"(\\d{3})(\\d{4})(\\d{4})\"", Pattern.CASE_INSENSITIVE);

    private LogMaskingUtils() {
    }

    public static String mask(Object arg) {
        if (arg == null) {
            return null;
        }
        String json = arg.toString();
        json = PASSWORD_PATTERN.matcher(json).replaceAll("\"$1\":\"******\"");
        json = TOKEN_PATTERN.matcher(json).replaceAll("\"$1\":\"******\"");
        json = ID_CARD_PATTERN.matcher(json).replaceAll("\"$1\":\"$2******$4\"");
        json = MOBILE_PATTERN.matcher(json).replaceAll("\"$1\":\"$2****$4\"");
        return json;
    }
}






