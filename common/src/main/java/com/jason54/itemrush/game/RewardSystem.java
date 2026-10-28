package com.jason54.itemrush.game;

import com.jason54.itemrush.Constants;
import com.jason54.itemrush.config.ItemRushConfig;
import com.jason54.itemrush.util.PlayerNames;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Weighted random reward picker and grant logic.
 */
public final class RewardSystem {

    private RewardSystem() {
    }

    public static Optional<GrantedReward> grantRandom(ServerPlayer winner) {
        ItemRushConfig config = ItemRushConfig.get();
        ItemRushConfig.RewardEntry entry = pickWeighted(config.rewards);
        if (entry == null) {
            return Optional.empty();
        }
        return Optional.of(apply(winner, entry));
    }

    private static ItemRushConfig.RewardEntry pickWeighted(List<ItemRushConfig.RewardEntry> rewards) {
        int total = 0;
        for (ItemRushConfig.RewardEntry reward : rewards) {
            total += Math.max(0, reward.weight);
        }
        if (total <= 0) {
            return null;
        }
        int roll = ThreadLocalRandom.current().nextInt(total);
        int cursor = 0;
        for (ItemRushConfig.RewardEntry reward : rewards) {
            cursor += Math.max(0, reward.weight);
            if (roll < cursor) {
                return reward;
            }
        }
        return rewards.get(rewards.size() - 1);
    }

    private static GrantedReward apply(ServerPlayer winner, ItemRushConfig.RewardEntry entry) {
        String type = entry.type == null ? "item" : entry.type.toLowerCase();
        return switch (type) {
            case "xp" -> {
                int xp = Math.max(1, entry.amount);
                winner.giveExperiencePoints(xp);
                String label = entry.display != null && !entry.display.isBlank() ? entry.display : xp + " XP";
                yield new GrantedReward("xp", label);
            }
            case "command" -> {
                String playerName = PlayerNames.of(winner.getGameProfile());
                String cmd = entry.command == null ? "" : entry.command
                        .replace("@s", playerName)
                        .replace("%player%", playerName);
                MinecraftServer server = winner.level().getServer();
                if (server != null && !cmd.isBlank()) {
                    CommandSourceStack source = server.createCommandSourceStack()
                            .withEntity(winner)
                            .withPosition(winner.position())
                            .withSuppressedOutput();
                    server.getCommands().performPrefixedCommand(source, cmd);
                }
                String label = entry.display != null && !entry.display.isBlank() ? entry.display : "Special reward";
                yield new GrantedReward("command", label);
            }
            default -> {
                Item item = resolveItem(entry.id);
                if (item == null || item == Items.AIR) {
                    Constants.LOG.warn("Invalid reward item id: {}", entry.id);
                    yield new GrantedReward("item", "invalid reward (" + entry.id + ")");
                }
                int count = Math.max(1, entry.count);
                ItemStack stack = item.getDefaultInstance();
                stack.setCount(count);
                if (!winner.getInventory().add(stack)) {
                    ItemEntity dropped = new ItemEntity(
                            winner.level(), winner.getX(), winner.getY() + 0.5, winner.getZ(), stack);
                    dropped.setPickUpDelay(40);
                    winner.level().addFreshEntity(dropped);
                }
                String label = entry.display != null && !entry.display.isBlank()
                        ? entry.display
                        : count + "x " + itemDisplayName(item);
                yield new GrantedReward("item", label);
            }
        };
    }

    private static String itemDisplayName(Item item) {
        return net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
    }

    @SuppressWarnings("unchecked")
    private static Item resolveItem(String rawId) {
        if (rawId == null || rawId.isBlank()) {
            return null;
        }
        try {
            Identifier id = Identifier.parse(rawId);
            try {
                Object optional = BuiltInRegistries.ITEM.getClass()
                        .getMethod("getOptional", Identifier.class)
                        .invoke(BuiltInRegistries.ITEM, id);
                if (optional instanceof Optional<?> opt && opt.isPresent()) {
                    Object value = opt.get();
                    if (value instanceof Item item && item != Items.AIR) {
                        return item;
                    }
                }
            } catch (ReflectiveOperationException ignored) {
                // fall through
            }
            Item value = BuiltInRegistries.ITEM.getValue(id);
            if (value != null && value != Items.AIR) {
                return value;
            }
        } catch (Exception e) {
            Constants.LOG.warn("Failed to resolve reward item '{}': {}", rawId, e.toString());
        }
        return null;
    }

    public record GrantedReward(String type, String display) {
    }
}
