package com.cafe.common;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

/** New credentials use BCrypt; historical MD5 credentials migrate after successful login. */
public final class PasswordUtil {
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder(10);
    private PasswordUtil() {}
    public static String encrypt(String raw) {
        if (raw == null || raw.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new BizException("密码不能为空且UTF-8长度不能超过72字节");
        return ENCODER.encode(raw);
    }
    public static boolean isLegacy(String encoded) {
        return encoded != null && encoded.matches("[0-9a-fA-F]{32}");
    }
    public static boolean matches(String raw, String encoded) {
        if (raw == null || encoded == null || raw.getBytes(StandardCharsets.UTF_8).length > 72) return false;
        if (isLegacy(encoded)) return Md5Util.matches(raw, encoded);
        return ENCODER.matches(raw, encoded);
    }
    public static String stamp(String encoded) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(encoded.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception e) { throw new IllegalStateException(e); }
    }
}
