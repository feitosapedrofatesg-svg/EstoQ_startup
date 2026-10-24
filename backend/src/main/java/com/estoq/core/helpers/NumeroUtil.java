package com.estoq.core.helpers;

import java.math.BigDecimal;
import java.math.RoundingMode;

public final class NumeroUtil {

    public static final int MONEY_SCALE = 2;

    private NumeroUtil() {
    }

    public static BigDecimal zero() {
        return BigDecimal.ZERO.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static BigDecimal s(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    public static BigDecimal money(BigDecimal v) {
        return s(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

}
