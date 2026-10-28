package com.jason54.itemrush.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.annotations.SerializedName;
import com.jason54.itemrush.Constants;
import com.jason54.itemrush.platform.Services;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * JSON configuration loaded from config/itemrush.json.
 */
public class ItemRushConfig {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static ItemRushConfig INSTANCE;

    /** 0 = unlimited round time (default). */
    @SerializedName("default_duration_seconds")
    public int defaultDurationSeconds = 0;

    @SerializedName("command_permission_level")
    public int commandPermissionLevel = 2;

    @SerializedName("roulette_duration_ticks")
    public int rouletteDurationTicks = 80;

    @SerializedName("roulette_speed")
    public float rouletteSpeed = 1.0f;

    /** After a win (or timed-out round if timed), start a new round automatically. */
    @SerializedName("auto_restart")
    public boolean autoRestart = true;

    /** Delay before auto-restart so players can read the winner banner. */
    @SerializedName("auto_restart_delay_ticks")
    public int autoRestartDelayTicks = 100;

    /** When true, any registered item can be selected as the target. */
    @SerializedName("use_all_items")
    public boolean useAllItems = false;

    /** Candidate target item IDs when use_all_items is false. */
    @SerializedName("target_items")
    public List<String> targetItems = defaultTargets();

    @SerializedName("rewards")
    public List<RewardEntry> rewards = defaultRewards();

    @SerializedName("messages")
    public Messages messages = new Messages();

    @SerializedName("sounds_enabled")
    public boolean soundsEnabled = true;

    @SerializedName("particles_enabled")
    public boolean particlesEnabled = true;

    /** Ignore creative /give-style inventory mutations; only ItemEntity pickups count. */
    @SerializedName("strict_pickup_only")
    public boolean strictPickupOnly = true;

    public static ItemRushConfig get() {
        if (INSTANCE == null) {
            INSTANCE = load();
        }
        return INSTANCE;
    }

    public static void reload() {
        INSTANCE = load();
    }

    private static ItemRushConfig load() {
        Path path = Services.PLATFORM.getConfigDirectory().resolve(Constants.MOD_ID + ".json");
        try {
            Files.createDirectories(path.getParent());
            if (Files.exists(path)) {
                String raw = Files.readString(path);
                ItemRushConfig cfg = GSON.fromJson(raw, ItemRushConfig.class);
                if (cfg != null) {
                    boolean migrated = migrateLegacy(cfg, raw);
                    cfg.validate();
                    if (migrated) {
                        cfg.save(path);
                        Constants.LOG.info("Migrated ItemRush config to unlimited time + auto-restart");
                    }
                    return cfg;
                }
            }
            ItemRushConfig defaults = new ItemRushConfig();
            defaults.save(path);
            return defaults;
        } catch (Exception e) {
            Constants.LOG.error("Failed to load ItemRush config, using defaults", e);
            return new ItemRushConfig();
        }
    }

    /**
     * Old configs had a 120s timer and no auto_restart keys (Gson would force autoRestart=false).
     */
    private static boolean migrateLegacy(ItemRushConfig cfg, String rawJson) {
        JsonObject root;
        try {
            root = JsonParser.parseString(rawJson).getAsJsonObject();
        } catch (Exception e) {
            return false;
        }
        boolean changed = false;
        if (!root.has("auto_restart")) {
            cfg.autoRestart = true;
            changed = true;
        }
        if (!root.has("auto_restart_delay_ticks")) {
            cfg.autoRestartDelayTicks = 100;
            changed = true;
        }
        // Previous built-in default was 120s; switch to unlimited when migrating.
        if (!root.has("auto_restart") && cfg.defaultDurationSeconds == 120) {
            cfg.defaultDurationSeconds = 0;
            changed = true;
        }
        return changed;
    }

    public void save() {
        Path path = Services.PLATFORM.getConfigDirectory().resolve(Constants.MOD_ID + ".json");
        save(path);
    }

    private void save(Path path) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer writer = Files.newBufferedWriter(path)) {
                GSON.toJson(this, writer);
            }
        } catch (IOException e) {
            Constants.LOG.error("Failed to save ItemRush config", e);
        }
    }

    private void validate() {
        if (defaultDurationSeconds < 0) {
            defaultDurationSeconds = 0;
        }
        if (rouletteDurationTicks < 20) {
            rouletteDurationTicks = 20;
        }
        if (rouletteSpeed <= 0) {
            rouletteSpeed = 1.0f;
        }
        if (autoRestartDelayTicks < 20) {
            autoRestartDelayTicks = 20;
        }
        if (commandPermissionLevel < 0) {
            commandPermissionLevel = 0;
        }
        if (targetItems == null || targetItems.isEmpty()) {
            targetItems = defaultTargets();
        }
        if (rewards == null || rewards.isEmpty()) {
            rewards = defaultRewards();
        }
        if (messages == null) {
            messages = new Messages();
        }
    }

    private static List<String> defaultTargets() {
        List<String> list = new ArrayList<>();
        list.add("minecraft:diamond");
        list.add("minecraft:emerald");
        list.add("minecraft:gold_ingot");
        list.add("minecraft:iron_ingot");
        list.add("minecraft:apple");
        list.add("minecraft:bread");
        list.add("minecraft:coal");
        list.add("minecraft:redstone");
        list.add("minecraft:lapis_lazuli");
        list.add("minecraft:oak_log");
        list.add("minecraft:cobblestone");
        list.add("minecraft:string");
        list.add("minecraft:feather");
        list.add("minecraft:bone");
        list.add("minecraft:rotten_flesh");
        list.add("minecraft:arrow");
        list.add("minecraft:stick");
        list.add("minecraft:wheat");
        list.add("minecraft:carrot");
        list.add("minecraft:potato");
        return list;
    }

    private static List<RewardEntry> defaultRewards() {
        List<RewardEntry> list = new ArrayList<>();
        list.add(RewardEntry.item("minecraft:diamond", 3, 10));
        list.add(RewardEntry.item("minecraft:emerald", 5, 12));
        list.add(RewardEntry.item("minecraft:golden_apple", 1, 6));
        list.add(RewardEntry.item("minecraft:experience_bottle", 8, 8));
        list.add(RewardEntry.xp(100, 10));
        list.add(RewardEntry.xp(250, 4));
        list.add(RewardEntry.command("give @s minecraft:enchanted_golden_apple 1", 2));
        return list;
    }

    public static class Messages {
        public String roundStarted = "ItemRush started! Find: %item%";
        public String roundStopped = "ItemRush stopped.";
        public String timeUp = "Time's up! Nobody found the item.";
        public String winnerChat = "%player% won ItemRush and received: %reward%";
        public String winnerTitle = "Winner!";
        public String winnerSubtitle = "%player% — %reward%";
        public String alreadyRunning = "An ItemRush round is already running.";
        public String notRunning = "No ItemRush round is running.";
        public String statusIdle = "ItemRush is idle.";
        public String statusRunning = "ItemRush running — target: %item% — time left: %time%s";
        public String statusRunningUnlimited = "ItemRush running — target: %item% — time: unlimited";
        public String statusRoulette = "ItemRush roulette spinning…";
    }

    public static class RewardEntry {
        /** item | xp | command */
        public String type = "item";
        public String id = "minecraft:diamond";
        public int count = 1;
        public int amount = 0;
        public String command = "";
        public int weight = 1;
        public String display = "";

        public static RewardEntry item(String id, int count, int weight) {
            RewardEntry e = new RewardEntry();
            e.type = "item";
            e.id = id;
            e.count = count;
            e.weight = weight;
            return e;
        }

        public static RewardEntry xp(int amount, int weight) {
            RewardEntry e = new RewardEntry();
            e.type = "xp";
            e.amount = amount;
            e.weight = weight;
            e.display = amount + " XP";
            return e;
        }

        public static RewardEntry command(String command, int weight) {
            RewardEntry e = new RewardEntry();
            e.type = "command";
            e.command = command;
            e.weight = weight;
            e.display = "Special reward";
            return e;
        }
    }
}
