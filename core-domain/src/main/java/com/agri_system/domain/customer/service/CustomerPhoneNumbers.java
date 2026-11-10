package com.agri_system.domain.customer.service;

import java.util.Optional;
import java.util.regex.Pattern;

/** 只识别明确的大陆手机号码；不猜测座机、国外号码或历史不明联系方式。 */
public final class CustomerPhoneNumbers {
    private static final Pattern MAINLAND = Pattern.compile("(?:\\+?86)?(1[3-9][0-9]{9})");
    private CustomerPhoneNumbers() {}

    public static Optional<String> mainland(String value) {
        if (value == null) return Optional.empty();
        var match = MAINLAND.matcher(value.replace(" ", "").replace("-", ""));
        return match.matches() ? Optional.of(match.group(1)) : Optional.empty();
    }
}
