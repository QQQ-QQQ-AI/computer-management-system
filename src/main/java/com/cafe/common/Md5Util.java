package com.cafe.common;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 密码摘要工具。
 *
 * 说明：系统采用 MD5 对密码做摘要存储，数据库中不保存明文。
 * MD5 本身不是为口令设计的算法，抗碰撞能力有限，用于课程设计场景可满足
 * 「密码不落盘明文」的基本要求；生产系统应改用 BCrypt / Argon2 等带盐慢哈希。
 */
public final class Md5Util {

    private Md5Util() {
    }

    /**
     * 计算 32 位小写十六进制 MD5 摘要。
     *
     * @param raw 明文密码
     * @return 摘要字符串
     */
    public static String encrypt(String raw) {
        if (raw == null) {
            return null;
        }
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] digest = md.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(32);
            for (byte b : digest) {
                sb.append(Character.forDigit((b >> 4) & 0xF, 16));
                sb.append(Character.forDigit(b & 0xF, 16));
            }
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            // JDK 必然支持 MD5，正常不会走到这里
            throw new IllegalStateException("当前 JDK 不支持 MD5 算法", e);
        }
    }

    /** 校验明文密码与摘要是否匹配 */
    public static boolean matches(String raw, String encrypted) {
        if (raw == null || encrypted == null) {
            return false;
        }
        return encrypt(raw).equalsIgnoreCase(encrypted);
    }
}
