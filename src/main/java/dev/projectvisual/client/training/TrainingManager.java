package dev.projectvisual.client.training;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Local PvP training progression. Progress is intentionally client-side and stored
 * in the player's config directory; it does not claim to modify a multiplayer server.
 */
public final class TrainingManager {
    private enum ProgressType { MACE_DAMAGE, PVP_DAMAGE, COBWEB }

    private record Quest(String id, String title, int goal, ProgressType type) {}

    private static final Quest[] QUESTS = {
            new Quest("mace_damage", "Нанеси 15 000 урона булавой игрокам", 15_000, ProgressType.MACE_DAMAGE),
            new Quest("cobwebs", "Поставь 4 000 блоков паутины", 4_000, ProgressType.COBWEB),
            new Quest("pvp_damage", "Нанеси 6 000 урона игрокам в PvP", 6_000, ProgressType.PVP_DAMAGE)
    };
    private static final String[] CASE_SKINS = {
            "Guts", "Akashi Seijuro", "Dual Blades", "Flying Spellbook"
    };
    private static final Set<String> UNLOCKED = new LinkedHashSet<>();

    private static Quest activeQuest = QUESTS[0];
    private static double progress;
    private static int completedQuests;
    private static int cases;
    private static LivingEntity trackedTarget;
    private static float targetHealth;
    private static boolean trackedAttackWasMace;
    private static int trackedAttackTicks;
    private static boolean trackedHitConfirmed;
    private static int confirmedHealthWaitTicks;

    private TrainingManager() {}

    static {
        load();
    }

    public static void tick(Minecraft client) {
        if (client.player == null || client.level == null || trackedTarget == null) {
            clearTrackedTarget();
            return;
        }
        if (trackedTarget.isRemoved() || trackedTarget.level() != client.level || trackedAttackTicks <= 0) {
            clearTrackedTarget();
            return;
        }
        trackedAttackTicks--;
        if (!trackedHitConfirmed) {
            if (trackedAttackTicks <= 0) clearTrackedTarget();
            return;
        }
        // The server hurt event confirms the hit; health synchronization can arrive a few ticks later.
        float currentHealth = trackedTarget.getHealth();
        double dealt = targetHealth - currentHealth;
        if (dealt > 0.001D) {
            recordDamage(dealt, trackedAttackWasMace);
            clearTrackedTarget();
            return;
        }
        confirmedHealthWaitTicks--;
        if (confirmedHealthWaitTicks <= 0 || trackedAttackTicks <= 0) clearTrackedTarget();
    }

    /** Called when the exact entity previously attacked receives its server hurt animation. */
    public static void onHurtAnimation(LivingEntity victim) {
        if (victim == trackedTarget && trackedAttackTicks > 0) {
            trackedHitConfirmed = true;
            confirmedHealthWaitTicks = 24;
        }
    }

    /** Called at the head of the local player's attack packet path. */
    public static void onAttack(Player attacker, Entity target) {
        Minecraft client = Minecraft.getInstance();
        if (attacker != client.player || !(target instanceof Player victim) || victim == attacker
                || !victim.isAlive()) {
            return;
        }

        trackedTarget = victim;
        targetHealth = victim.getHealth();
        trackedAttackWasMace = attacker.getMainHandItem().is(Items.MACE)
                || attacker.getOffhandItem().is(Items.MACE);
        trackedAttackTicks = 40;
        trackedHitConfirmed = false;
        confirmedHealthWaitTicks = 0;
    }

    /** Called only when the client accepted a block-use action with a cobweb stack. */
    public static void recordCobwebPlacement() {
        addProgress(ProgressType.COBWEB, 1.0D);
    }

    private static void recordDamage(double amount, boolean mace) {
        ProgressType current = activeQuest.type();
        if (current == ProgressType.MACE_DAMAGE && mace) {
            addProgress(ProgressType.MACE_DAMAGE, amount);
        } else if (current == ProgressType.PVP_DAMAGE) {
            addProgress(ProgressType.PVP_DAMAGE, amount);
        }
    }

    private static void addProgress(ProgressType type, double amount) {
        if (activeQuest.type() != type || amount <= 0.0D) return;
        progress = Math.min(activeQuest.goal(), progress + amount);
        if (progress >= activeQuest.goal()) completeQuest();
        else save();
    }

    private static void completeQuest() {
        completedQuests++;
        boolean caseEarned = completedQuests % 2 == 0;
        if (caseEarned) cases++;

        String previousId = activeQuest.id();
        Quest next = activeQuest;
        if (QUESTS.length > 1) {
            while (next.id().equals(previousId)) {
                next = QUESTS[ThreadLocalRandom.current().nextInt(QUESTS.length)];
            }
        }
        activeQuest = next;
        progress = 0.0D;

        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(Component.literal(caseEarned
                    ? "§6ТРЕНЕР §fКвест выполнен! Получен кейс со скином — открой его в меню «Скин»."
                    : "§aТРЕНЕР §fКвест выполнен! Ещё один квест до следующего кейса."), false);
        }
        save();
    }

    public static String getQuestTitle() {
        return activeQuest.title();
    }

    public static String getProgressLabel() {
        return String.format(java.util.Locale.ROOT, "%,.0f / %,d", Math.min(progress, activeQuest.goal()),
                activeQuest.goal());
    }

    public static double getProgressRatio() {
        return Math.max(0.0D, Math.min(1.0D, progress / activeQuest.goal()));
    }

    public static int getCompletedQuests() {
        return completedQuests;
    }

    public static int getCaseCount() {
        return cases;
    }

    public static boolean isUnlocked(String skinId) {
        if (!isCaseSkin(skinId)) return true;
        return UNLOCKED.contains(skinId);
    }

    public static boolean isCaseSkin(String skinId) {
        for (String id : CASE_SKINS) if (id.equals(skinId)) return true;
        return false;
    }

    public static String[] getCaseSkinIds() {
        return CASE_SKINS.clone();
    }

    /**
     * Consumes one earned case and returns its predetermined reward. The caller
     * reveals and persists the reward after the opening animation finishes.
     */
    public static String beginCaseOpening() {
        if (cases <= 0) return null;
        cases--;

        String[] locked = java.util.Arrays.stream(CASE_SKINS)
                .filter(id -> !UNLOCKED.contains(id))
                .toArray(String[]::new);
        String[] pool = locked.length > 0 ? locked : CASE_SKINS;
        String reward = pool[ThreadLocalRandom.current().nextInt(pool.length)];
        save();
        return reward;
    }

    public static void unlockSkin(String skinId) {
        if (isCaseSkin(skinId)) {
            UNLOCKED.add(skinId);
            save();
        }
    }

    public static String getSkinDisplayName(String id) {
        return switch (id) {
            case "Guts" -> "Гатс";
            case "Akashi Seijuro" -> "Акаши Сейджуро";
            case "Dual Blades" -> "Клинки в обеих руках";
            case "Flying Spellbook" -> "Летающая книга заклинаний";
            default -> id;
        };
    }

    private static void clearTrackedTarget() {
        trackedTarget = null;
        trackedAttackTicks = 0;
        trackedAttackWasMace = false;
        trackedHitConfirmed = false;
        confirmedHealthWaitTicks = 0;
    }

    private static Path savePath() {
        return FabricLoader.getInstance().getConfigDir().resolve("project_visual_training.properties");
    }

    private static void load() {
        Properties properties = new Properties();
        Path file = savePath();
        if (!Files.exists(file)) return;

        try (InputStream input = Files.newInputStream(file)) {
            properties.load(input);
            completedQuests = Math.max(0, Integer.parseInt(properties.getProperty("completed", "0")));
            cases = Math.max(0, Integer.parseInt(properties.getProperty("cases", "0")));
            progress = Math.max(0.0D, Double.parseDouble(properties.getProperty("progress", "0")));
            String questId = properties.getProperty("quest", QUESTS[0].id());
            for (Quest quest : QUESTS) {
                if (quest.id().equals(questId)) {
                    activeQuest = quest;
                    break;
                }
            }
            progress = Math.min(progress, activeQuest.goal());
            String unlocked = properties.getProperty("unlocked", "");
            for (String id : unlocked.split(",")) {
                String trimmed = id.trim();
                if (isCaseSkin(trimmed)) UNLOCKED.add(trimmed);
            }
        } catch (IOException | NumberFormatException ignored) {
            activeQuest = QUESTS[0];
            progress = 0.0D;
            completedQuests = 0;
            cases = 0;
            UNLOCKED.clear();
        }
    }

    private static void save() {
        Properties properties = new Properties();
        properties.setProperty("quest", activeQuest.id());
        properties.setProperty("progress", Double.toString(progress));
        properties.setProperty("completed", Integer.toString(completedQuests));
        properties.setProperty("cases", Integer.toString(cases));
        properties.setProperty("unlocked", String.join(",", UNLOCKED));

        Path file = savePath();
        try {
            Files.createDirectories(file.getParent());
            try (OutputStream output = Files.newOutputStream(file)) {
                properties.store(output, "PROJECT VISUAL local training progress");
            }
        } catch (IOException ignored) {
            // Gameplay should continue if the config folder is temporarily unavailable.
        }
    }
}
