package com.jason54.itemrush.util;

import com.mojang.authlib.GameProfile;

import java.lang.reflect.Method;

/**
 * Resolves a player name across GameProfile API changes ({@code getName()} vs {@code name()}).
 */
public final class PlayerNames {

    private static final Method NAME_METHOD = resolveNameMethod();

    private PlayerNames() {
    }

    public static String of(GameProfile profile) {
        if (profile == null) {
            return "Unknown";
        }
        try {
            Object value = NAME_METHOD.invoke(profile);
            return value == null ? "Unknown" : value.toString();
        } catch (ReflectiveOperationException e) {
            return "Unknown";
        }
    }

    private static Method resolveNameMethod() {
        try {
            return GameProfile.class.getMethod("name");
        } catch (NoSuchMethodException ignored) {
            try {
                return GameProfile.class.getMethod("getName");
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException("GameProfile has neither name() nor getName()", e);
            }
        }
    }
}
