package com.jason54.itemrush.client;

import com.jason54.itemrush.Constants;

import java.lang.reflect.Method;

/**
 * Registers Fabric HUD rendering across API renames (HudElementRegistry vs HudRenderCallback).
 */
public final class FabricHudBootstrap {

    private FabricHudBootstrap() {
    }

    public static void register() {
        if (tryHudElementRegistry()) {
            return;
        }
        if (tryHudRenderCallback()) {
            return;
        }
        throw new IllegalStateException("No Fabric HUD registration API found");
    }

    private static boolean tryHudElementRegistry() {
        try {
            Class<?> registry = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry");
            Class<?> vanilla = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements");
            Object chat = vanilla.getField("CHAT").get(null);
            Method attach = null;
            for (Method method : registry.getMethods()) {
                if ("attachElementBefore".equals(method.getName()) && method.getParameterCount() == 3) {
                    attach = method;
                    break;
                }
            }
            if (attach == null) {
                return false;
            }
            Object layerId = Constants.id("hud");
            // HudElement is typically a functional interface (graphics, delta) -> void
            Object element = java.lang.reflect.Proxy.newProxyInstance(
                    registry.getClassLoader(),
                    new Class<?>[]{attach.getParameterTypes()[2]},
                    (proxy, method, args) -> {
                        if (args != null && args.length >= 2) {
                            Object graphics = args[0];
                            float partial = 0f;
                            Object delta = args[1];
                            try {
                                Method partialMethod = delta.getClass()
                                        .getMethod("getGameTimeDeltaPartialTick", boolean.class);
                                partial = (Float) partialMethod.invoke(delta, false);
                            } catch (ReflectiveOperationException ignored) {
                                // ignore
                            }
                            HudBridge.render(graphics, partial);
                        }
                        return null;
                    }
            );
            attach.invoke(null, chat, layerId, element);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }

    private static boolean tryHudRenderCallback() {
        try {
            Class<?> callback = Class.forName("net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback");
            Object event = callback.getField("EVENT").get(null);
            Method register = event.getClass().getMethod("register", Object.class);
            Object listener = java.lang.reflect.Proxy.newProxyInstance(
                    callback.getClassLoader(),
                    new Class<?>[]{callback},
                    (proxy, method, args) -> {
                        if (args != null && args.length >= 1) {
                            float partial = args.length > 1 && args[1] instanceof Number n ? n.floatValue() : 0f;
                            HudBridge.render(args[0], partial);
                        }
                        return null;
                    }
            );
            register.invoke(event, listener);
            return true;
        } catch (ReflectiveOperationException e) {
            return false;
        }
    }
}
