package org.example.springboot.security;

public final class AuthContext {

    private static final ThreadLocal<UserPrincipal> HOLDER = new ThreadLocal<>();

    private AuthContext() {
    }

    public static void set(UserPrincipal principal) {
        HOLDER.set(principal);
    }

    public static UserPrincipal get() {
        return HOLDER.get();
    }

    public static void clear() {
        HOLDER.remove();
    }
}
