package com.cafe.common;

import java.math.BigDecimal;
import java.util.regex.Pattern;

/**
 * 业务数据格式校验工具。
 *
 * 集中放置的原因：手机号、身份证号在注册、开卡、查询等多个入口都要校验，
 * 规则散落各处容易出现「这个入口校验、那个入口不校验」的不一致。
 */
public final class ValidateUtil {

    private ValidateUtil() {
    }

    /** 中国大陆手机号：1 开头，第二位 3-9，共 11 位 */
    private static final Pattern PHONE = Pattern.compile("^1[3-9]\\d{9}$");

    /** 身份证号：17 位数字 + 1 位数字或 X */
    private static final Pattern ID_CARD = Pattern.compile("^\\d{17}[\\dXx]$");

    /** 身份证校验码加权因子（GB 11643-1999） */
    private static final int[] WEIGHT = {7, 9, 10, 5, 8, 4, 2, 1, 6, 3, 7, 9, 10, 5, 8, 4, 2};

    /** 校验码对应表，索引为前 17 位加权和模 11 的结果 */
    private static final char[] CHECK_CODE = {'1', '0', 'X', '9', '8', '7', '6', '5', '4', '3', '2'};

    public static boolean isPhone(String phone) {
        return phone != null && PHONE.matcher(phone).matches();
    }

    /**
     * 校验身份证号：先校验格式，再校验最后一位校验码。
     * 只校验格式不校验校验码，等于放过了任何 17 位数字加任意末位，
     * 开卡环节录入错误无法拦截。
     */
    public static boolean isIdCard(String idCard) {
        if (idCard == null || !ID_CARD.matcher(idCard).matches()) {
            return false;
        }
        int sum = 0;
        for (int i = 0; i < 17; i++) {
            sum += (idCard.charAt(i) - '0') * WEIGHT[i];
        }
        char expect = CHECK_CODE[sum % 11];
        return Character.toUpperCase(idCard.charAt(17)) == expect;
    }

    /** 金额必须为正数 */
    public static boolean isPositiveAmount(BigDecimal amount) {
        return amount != null && amount.signum() > 0;
    }

    /** 金额小数位不超过 2 位 */
    public static boolean isMoneyScaleValid(BigDecimal amount) {
        return amount != null && amount.scale() <= 2;
    }
}
