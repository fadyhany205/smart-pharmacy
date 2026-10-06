package com.pharmacy.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class Money {
    private Money() {}

    public static BigDecimal r(BigDecimal v) {
        return v == null ? BigDecimal.ZERO.setScale(2) : v.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /** A discount percentage must be between 0 and 100; returns null for "no percentage". */
    public static BigDecimal validPercent(BigDecimal p) {
        if (p == null) return null;
        if (p.signum() < 0 || p.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new com.pharmacy.service.BusinessException("error.invalidPercent");
        }
        return p.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal percent(BigDecimal amount, BigDecimal rate) {
        return r(amount.multiply(rate).divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP));
    }
}
