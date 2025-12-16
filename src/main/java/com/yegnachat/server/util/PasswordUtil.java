package com.yegnachat.server.util;

import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {
    private PasswordUtil() {}

    public static String hashPassword(String plain) {
        return BCrypt.hashpw(plain, BCrypt.gensalt(12));
    }

    public static boolean checkPassword(String plain, String hashed) {
        if (plain == null || hashed == null) return false;
        return BCrypt.checkpw(plain, hashed);
    }
}
