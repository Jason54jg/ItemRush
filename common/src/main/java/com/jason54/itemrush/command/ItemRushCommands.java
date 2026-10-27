package com.jason54.itemrush.command;

import com.jason54.itemrush.config.ItemRushConfig;
import com.jason54.itemrush.game.GameManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;

import java.lang.reflect.Method;
import java.util.function.Predicate;

/**
 * /itemrush start [seconds] | stop | status | reload
 */
public final class ItemRushCommands {

    private ItemRushCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        Predicate<CommandSourceStack> permission = permissionPredicate(ItemRushConfig.get().commandPermissionLevel);

        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("itemrush")
                .then(Commands.literal("start")
                        .requires(permission)
                        .executes(ctx -> start(ctx.getSource(), ItemRushConfig.get().defaultDurationSeconds))
                        .then(Commands.argument("duration_in_seconds", IntegerArgumentType.integer(5, 3600))
                                .executes(ctx -> start(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "duration_in_seconds")))))
                .then(Commands.literal("stop")
                        .requires(permission)
                        .executes(ctx -> stop(ctx.getSource())))
                .then(Commands.literal("status")
                        .executes(ctx -> {
                            ctx.getSource().sendSuccess(GameManager::status, false);
                            return 1;
                        }))
                .then(Commands.literal("reload")
                        .requires(permission)
                        .executes(ctx -> {
                            ItemRushConfig.reload();
                            ctx.getSource().sendSuccess(
                                    () -> Component.literal("ItemRush config reloaded."), false);
                            return 1;
                        }));

        dispatcher.register(root);
    }

    /**
     * Supports legacy {@code hasPermission(int)} (1.21.x) and PermissionCheck API (26.x).
     */
    @SuppressWarnings("unchecked")
    private static Predicate<CommandSourceStack> permissionPredicate(int level) {
        int clamped = Math.max(0, Math.min(4, level));

        // 1.21.x: CommandSourceStack#hasPermission(int)
        try {
            Method legacy = CommandSourceStack.class.getMethod("hasPermission", int.class);
            return src -> {
                try {
                    return Boolean.TRUE.equals(legacy.invoke(src, clamped));
                } catch (ReflectiveOperationException e) {
                    return false;
                }
            };
        } catch (NoSuchMethodException ignored) {
            // continue to 26.x path
        }

        // 26.x: Commands.hasPermission(Commands.LEVEL_*)
        try {
            String field = switch (clamped) {
                case 0 -> "LEVEL_ALL";
                case 1 -> "LEVEL_MODERATORS";
                case 3 -> "LEVEL_ADMINS";
                case 4 -> "LEVEL_OWNERS";
                default -> "LEVEL_GAMEMASTERS";
            };
            Object check = Commands.class.getField(field).get(null);
            for (Method method : Commands.class.getMethods()) {
                if ("hasPermission".equals(method.getName()) && method.getParameterCount() == 1) {
                    Object predicate = method.invoke(null, check);
                    if (predicate instanceof Predicate<?> p) {
                        return (Predicate<CommandSourceStack>) p;
                    }
                }
            }
        } catch (ReflectiveOperationException ignored) {
            // fall through
        }

        return src -> true;
    }

    private static int start(CommandSourceStack source, int duration) {
        MinecraftServer server = source.getServer();
        Component result = GameManager.start(server, duration);
        source.sendSuccess(() -> result, true);
        return 1;
    }

    private static int stop(CommandSourceStack source) {
        Component result = GameManager.stop(source.getServer());
        source.sendSuccess(() -> result, true);
        return 1;
    }
}
