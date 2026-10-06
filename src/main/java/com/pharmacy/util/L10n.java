package com.pharmacy.util;

import org.springframework.context.i18n.LocaleContextHolder;

public final class L10n {
    private L10n() {}

    public static boolean isArabic() {
        return "ar".equals(LocaleContextHolder.getLocale().getLanguage());
    }

    /** Returns the value for the current language, falling back to the other one when empty. */
    public static String pick(String ar, String en) {
        boolean arabic = isArabic();
        String first = arabic ? ar : en;
        String second = arabic ? en : ar;
        return first != null && !first.isBlank() ? first : (second == null ? "" : second);
    }
}
