package dev.projectvisual.client.chess;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.blaze3d.vertex.VertexConsumer;
import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderEvents;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.Identifier;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Client-side, world-space chess table. The board is anchored in the world when summoned;
 * all geometry is emitted into the normal Fabric world render pass.
 */
public final class ChessWorldGame {
    private static final float CELL = 0.48F;
    private static final float BOARD_SIZE = CELL * 8.0F;
    private static final float HALF = BOARD_SIZE * 0.5F;
    private static final int INTRO_TICKS = 54;
    private static final int MOVE_TICKS = 15;

    private static final int WOOD = 0xFF302333;
    private static final int WOOD_EDGE = 0xFF9A7048;
    private static final int LIGHT_TILE = 0xFFE8D9BD;
    private static final int DARK_TILE = 0xFF51434A;
    private static final int WHITE_BODY = 0xFFEDE7D9;
    private static final int WHITE_HI = 0xFFFFF8E8;
    private static final int WHITE_SHADE = 0xFFB6A98F;
    private static final int GOLD = 0xFFD8B66A;
    private static final int BLACK_BODY = 0xFF222638;
    private static final int BLACK_HI = 0xFF69738B;
    private static final int BLACK_SHADE = 0xFF111522;
    private static final int CYAN = 0xFF63D9E8;
    private static final int SELECTED = 0xFF76E5D5;
    private static final int TARGET = 0xFF82CFA6;

    // DEBUG_QUADS is translucent and deliberately does not write to depth.
    // That is suitable for debug overlays, but it causes the rear faces of
    // closed 3D meshes to blend over their front faces. Use an opaque,
    // depth-writing pipeline for the chess board and pieces instead.
    private static final RenderPipeline CHESS_SOLID_PIPELINE =
            RenderPipeline.builder()
                    .withLocation(Identifier.fromNamespaceAndPath(
                            ProjectVisualClient.MOD_ID, "pipeline/chess_solid"))
                    .withVertexShader("core/position_color")
                    .withFragmentShader("core/position_color")
                    .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                    .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                    .withoutBlend()
                    .withDepthWrite(true)
                    .withCull(false)
                    .withVertexFormat(DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS)
                    .build();

    private static final RenderType CHESS_SOLID_RENDER_TYPE = RenderType.create(
            "project_visual_chess_solid",
            RenderSetup.builder(CHESS_SOLID_PIPELINE).createRenderSetup());

    private static final ChessEngine GAME = new ChessEngine();

    private static boolean registered;
    private static boolean active;
    private static boolean rightWasDown;
    private static boolean aiPending;
    private static int aiDelay;
    private static int introTick;
    private static int selectedSquare = -1;
    private static String status = "Белые ходят";
    private static Vec3 boardCenter = Vec3.ZERO;
    private static Vec3 right = new Vec3(1, 0, 0);
    private static Vec3 forward = new Vec3(0, 0, 1);
    private static double boardY;

    private static KeyMapping exitKey;
    private static KeyMapping restartKey;
    private static MoveAnimation moveAnimation;

    private static final int AFK_IDLE_TICKS = 20 * 30;
    private static final int AFK_THROW_TICK = 38;
    private static final int AFK_THROW_DURATION = 24;
    private static final int AFK_SCENE_DURATION = 92;
    private static final int AXE_APPROACH_TICK = 42;
    private static final int AXE_WINDUP_TICK = 52;
    private static final int AXE_IMPACT_TICK = 72;
    private static final int AXE_SCENE_DURATION = 116;
    private static final int WATCHER_SUCK_TICK = 106;
    private static final int WATCHER_SCENE_DURATION = 164;
    private static int afkIdleTicks;
    private static int afkSceneTick = -1;
    private static int afkSceneType; // 1 = Magnus, 2 = Axe, 3 = Watcher void pull
    private static boolean afkEventPlayed;
    private static boolean afkTracking;
    private static Vec3 afkLastPosition = Vec3.ZERO;
    private static float afkLastYaw;
    private static float afkLastPitch;
    private static Vec3 magnusPosition = Vec3.ZERO;
    private static Vec3 magnusRight = new Vec3(1, 0, 0);
    private static Vec3 magnusForward = new Vec3(0, 0, 1);
    private static Vec3 axeStartPosition = Vec3.ZERO;
    private static Vec3 axeEndPosition = Vec3.ZERO;
    private static Vec3 axeRight = new Vec3(1, 0, 0);
    private static Vec3 axeForward = new Vec3(0, 0, 1);
    private static Vec3 watcherStartPosition = Vec3.ZERO;
    private static Vec3 watcherEndPosition = Vec3.ZERO;
    private static Vec3 watcherRight = new Vec3(1, 0, 0);
    private static Vec3 watcherForward = new Vec3(0, 0, 1);
    private static int invokerPhraseIndex = -1;

    private ChessWorldGame() {}

    public static void register() {
        if (registered) return;
        registered = true;

        KeyMapping.Category category = KeyMapping.Category.register(
                Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID, "chess"));
        exitKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.chess_exit", GLFW.GLFW_KEY_BACKSPACE, category));
        restartKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.project_visual.chess_restart", GLFW.GLFW_KEY_R, category));

        ClientTickEvents.END_CLIENT_TICK.register(ChessWorldGame::tick);
        ClientTickEvents.END_CLIENT_TICK.register(ChessWorldGame::tickCosmetics);
        WorldRenderEvents.AFTER_ENTITIES.register(ChessWorldGame::render);
    }

    /** Starts a fresh match and summons the board a few blocks in front of the player. */
    public static void start() {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        GAME.reset();
        selectedSquare = -1;
        status = "Белые ходят";
        aiPending = false;
        aiDelay = 0;
        moveAnimation = null;
        introTick = 0;
        rightWasDown = false;
        active = true;

        double yaw = Math.toRadians(client.player.getYRot());
        forward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        right = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        boardY = client.player.getEyeY() - 0.84D;
        Vec3 anchor = client.player.position().add(forward.scale(3.75D));
        boardCenter = new Vec3(anchor.x, boardY, anchor.z);

        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal(
                        "§bPROJECT VISUAL CHESS §7— доска появляется из портала. §fПКМ: выбрать и переместить фигуру."),
                false);
        spawnPortalParticles(client, 16);
    }

    public static boolean isActive() {
        return active;
    }

    /** True only during the final third-person disappearance of the Watcher AFK scene. */
    public static boolean isWatcherSucking() {
        return afkSceneType == 3 && afkSceneTick >= WATCHER_SUCK_TICK;
    }

    /** True when a full replacement avatar is being rendered by the custom mesh pass. */
    public static boolean shouldHidePlayerModel() {
        return VisualFeatures.isEnabled("Queen Skin")
                || VisualFeatures.isEnabled("Invoker Skin")
                || VisualFeatures.isEnabled("Guts")
                || VisualFeatures.isEnabled("Akashi Seijuro");
    }

    public static void stop() {
        active = false;
        selectedSquare = -1;
        aiPending = false;
        moveAnimation = null;
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Шахматная доска закрыта."),
                    false);
        }
    }

    private static void tick(Minecraft client) {
        if (!active) return;
        if (client.player == null || client.level == null) {
            active = false;
            return;
        }

        while (exitKey != null && exitKey.consumeClick()) {
            stop();
            return;
        }
        while (restartKey != null && restartKey.consumeClick()) {
            start();
            return;
        }

        introTick = Math.min(INTRO_TICKS, introTick + 1);
        if (introTick < INTRO_TICKS && (introTick % 3) == 0) spawnPortalParticles(client, 3);

        if (moveAnimation != null) {
            moveAnimation.ticks++;
            if (moveAnimation.ticks >= MOVE_TICKS) moveAnimation = null;
        }

        if (client.screen != null) {
            rightWasDown = false;
            return;
        }

        boolean rightDown = client.mouseHandler.isRightPressed();
        if (rightDown && !rightWasDown && introTick >= INTRO_TICKS && !aiPending) {
            handleBoardClick(client);
        }
        rightWasDown = rightDown;

        if (aiPending) {
            aiDelay--;
            if (aiDelay <= 0) performAiMove(client);
        }
    }

    private static void handleBoardClick(Minecraft client) {
        int square = rayPickSquare(client);
        if (square < 0) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal("§7Наведи прицел на клетку доски и нажми ПКМ."),
                    true);
            return;
        }

        char piece = GAME.pieceAt(square);
        if (selectedSquare < 0) {
            if (piece != 0 && Character.isUpperCase(piece) && GAME.whiteToMove()) {
                selectedSquare = square;
                status = "Выбрана " + pieceName(piece) + " — укажи клетку назначения";
                actionBar(client, "§bВыбрана " + pieceName(piece) + " §7— ПКМ по клетке назначения");
            } else {
                actionBar(client, "§7Выбери белую фигуру. Сейчас ход белых.");
            }
            return;
        }

        if (square == selectedSquare) {
            selectedSquare = -1;
            actionBar(client, "§7Выбор отменён");
            return;
        }

        if (piece != 0 && Character.isUpperCase(piece)) {
            selectedSquare = square;
            actionBar(client, "§bВыбрана " + pieceName(piece) + " §7— выбери клетку");
            return;
        }

        int from = selectedSquare;
        char captured = GAME.pieceAt(square);
        char moving = GAME.pieceAt(from);
        if (GAME.play(from, square)) {
            selectedSquare = -1;
            moveAnimation = new MoveAnimation(from, square, moving, 0);
            status = "Чёрные думают…";
            if (captured != 0) spawnCaptureParticles(client, square);
            if (GAME.result() != ChessEngine.Result.ONGOING) {
                finishGame(client);
            } else {
                aiPending = true;
                aiDelay = 9;
                actionBar(client, "§7Ход чёрных — ИИ обдумывает ответ");
            }
        } else {
            actionBar(client, "§cТак ходить нельзя");
        }
    }

    private static int rayPickSquare(Minecraft client) {
        Vec3 eye = client.player.getEyePosition();
        Vec3 look = client.player.getLookAngle();
        double bob = Math.sin((client.level.getGameTime() % 100000L) * 0.055D) * 0.018D * easeOut(introProgress());
        double planeY = boardY + 0.035D + introLift() + bob;
        if (look.y >= -0.015D) return -1;
        double distance = (planeY - eye.y) / look.y;
        if (distance <= 0.0D || distance > 16.0D) return -1;

        Vec3 hit = eye.add(look.scale(distance));
        Vec3 relative = hit.subtract(boardCenter);
        double localX = relative.dot(right);
        double localZ = relative.dot(forward);
        if (Math.abs(localX) > HALF || Math.abs(localZ) > HALF) return -1;

        int file = Math.min(7, Math.max(0, (int) Math.floor((localX + HALF) / CELL)));
        int rank = Math.min(7, Math.max(0, (int) Math.floor((localZ + HALF) / CELL)));
        return rank * 8 + file;
    }

    private static void performAiMove(Minecraft client) {
        aiPending = false;
        ChessEngine.Move move = GAME.chooseAiMove(3);
        if (move == null) {
            finishGame(client);
            return;
        }
        char moving = GAME.pieceAt(move.from());
        char captured = GAME.pieceAt(move.to());
        if (GAME.play(move)) {
            moveAnimation = new MoveAnimation(move.from(), move.to(), moving, 0);
            if (captured != 0) spawnCaptureParticles(client, move.to());
            if (GAME.result() != ChessEngine.Result.ONGOING) {
                finishGame(client);
                return;
            }
            status = GAME.inCheck(true) ? "Шах белому королю!" : "Белые ходят";
            actionBar(client, GAME.inCheck(true)
                    ? "§cШАХ! §7Твой ход — защити короля"
                    : "§fТвой ход — выбирай фигуру ПКМ");
        } else {
            status = "Ошибка хода ИИ";
            actionBar(client, "§cНе удалось применить ход ИИ. Нажми R для перезапуска.");
        }
    }

    private static void finishGame(Minecraft client) {
        aiPending = false;
        ChessEngine.Result result = GAME.result();
        if (result == ChessEngine.Result.CHECKMATE) {
            boolean whiteWon = !GAME.whiteToMove();
            status = whiteWon ? "Мат — победа белых!" : "Мат — победа чёрных";
            actionBar(client, whiteWon ? "§aПОБЕДА! §fТы поставил мат." : "§cМАТ. §fИИ победил.");
            if (client.level != null) {
                for (int i = 0; i < 28; i++) {
                    double a = Math.PI * 2.0D * i / 28.0D;
                    client.level.addParticle(ParticleTypes.FIREWORK,
                            boardCenter.x + Math.cos(a) * 1.5D,
                            boardY + 0.5D + (i % 4) * 0.16D,
                            boardCenter.z + Math.sin(a) * 1.5D,
                            Math.cos(a) * 0.06D, 0.10D, Math.sin(a) * 0.06D);
                }
            }
        } else if (result == ChessEngine.Result.STALEMATE || result == ChessEngine.Result.DRAW) {
            status = "Ничья";
            actionBar(client, "§eНИЧЬЯ");
        }
    }

    private static void actionBar(Minecraft client, String text) {
        if (client.player != null) {
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(text), true);
        }
    }

    private static String pieceName(char piece) {
        return switch (Character.toUpperCase(piece)) {
            case 'K' -> "король";
            case 'Q' -> "ферзь";
            case 'R' -> "ладья";
            case 'B' -> "слон";
            case 'N' -> "конь";
            default -> "пешка";
        };
    }

    private static void spawnPortalParticles(Minecraft client, int amount) {
        if (client.level == null) return;
        ThreadLocalRandom random = ThreadLocalRandom.current();
        Vec3 portalCenter = boardCenter.add(forward.scale(0.48D)).add(0.0D, 0.72D, 0.0D);
        for (int i = 0; i < amount; i++) {
            double angle = random.nextDouble(Math.PI * 2.0D);
            double radius = 1.20D + random.nextDouble(-0.16D, 0.16D);
            double x = portalCenter.x + right.x * Math.cos(angle) * radius;
            double z = portalCenter.z + right.z * Math.cos(angle) * radius;
            double y = portalCenter.y + Math.sin(angle) * radius;
            client.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                    -forward.x * 0.025D + random.nextDouble(-0.025D, 0.025D),
                    random.nextDouble(-0.035D, 0.055D),
                    -forward.z * 0.025D + random.nextDouble(-0.025D, 0.025D));
            if ((i % 3) == 0) {
                client.level.addParticle(ParticleTypes.END_ROD, x, y, z,
                        random.nextDouble(-0.012D, 0.012D), 0.015D,
                        random.nextDouble(-0.012D, 0.012D));
            }
        }
    }

    private static void spawnCaptureParticles(Minecraft client, int square) {
        if (client.level == null) return;
        Vec3 point = squareCenter(square, 0.28D);
        for (int i = 0; i < 10; i++) {
            double a = Math.PI * 2.0D * i / 10.0D;
            client.level.addParticle(ParticleTypes.CRIT, point.x, point.y, point.z,
                    Math.cos(a) * 0.10D, 0.06D + (i % 3) * 0.02D, Math.sin(a) * 0.10D);
        }
        client.level.addParticle(ParticleTypes.POOF, point.x, point.y, point.z, 0, 0.04D, 0);
    }

    private static Vec3 squareCenter(int square, double height) {
        int file = square % 8;
        int rank = square / 8;
        double x = -HALF + (file + 0.5D) * CELL;
        double z = -HALF + (rank + 0.5D) * CELL;
        return boardCenter.add(right.scale(x)).add(forward.scale(z)).add(0.0D, height, 0.0D);
    }

    private static float introProgress() {
        return Math.min(1.0F, introTick / (float) INTRO_TICKS);
    }

    private static float easeOut(float value) {
        float inv = 1.0F - value;
        return 1.0F - inv * inv * inv;
    }

    private static double introLift() {
        return (1.0D - easeOut(introProgress())) * -0.85D;
    }

    private static void render(WorldRenderContext context) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;

        Vec3 camera = client.gameRenderer.getMainCamera().position();
        renderChessCosmetics(context, client, camera);
        if (!active) return;
        float progress = easeOut(introProgress());
        float scale = 0.12F + 0.88F * progress;
        double lift = introLift() + Math.sin((client.level.getGameTime() % 100000L) * 0.055D) * 0.018D * progress;
        Vec3 renderOrigin = new Vec3(boardCenter.x - camera.x, boardCenter.y - camera.y, boardCenter.z - camera.z);
        BoardTransform transform = new BoardTransform(renderOrigin, right, forward, scale, lift);

        // MultiBufferSource buffers are only valid until getBuffer() is called
        // for another render type. Finish all quads before switching to lines.
        VertexConsumer quads = context.consumers().getBuffer(CHESS_SOLID_RENDER_TYPE);
        renderBoard(quads, transform);
        renderPieces(quads, transform, camera);

        VertexConsumer lines = context.consumers().getBuffer(RenderTypes.lines());
        renderPortal(lines, transform, camera, progress);
        renderHighlights(lines, transform, camera);
    }

    private static void tickCosmetics(Minecraft client) {
        if (client.player == null || client.level == null) {
            resetAfkTracking();
            return;
        }

        long gameTime = client.level.getGameTime();
        boolean invokerEnabled = VisualFeatures.isEnabled("Invoker Skin");
        if (invokerEnabled && gameTime % 120L == 0L) {
            String[] phrases = {
                    "The elements answer.",
                    "Quas. Wex. Exort!",
                    "Your fate is already written.",
                    "Invoke!",
                    "The arcane is mine.",
                    "I have foreseen this."
            };
            invokerPhraseIndex = (invokerPhraseIndex + 1) % phrases.length;
            client.player.displayClientMessage(
                    net.minecraft.network.chat.Component.literal(
                            "§5INVOKER §8» §f" + phrases[invokerPhraseIndex]),
                    true);
        }

        boolean magnusEnabled = VisualFeatures.isEnabled("Magnus AFK");
        boolean axeEnabled = VisualFeatures.isEnabled("Axe AFK");
        boolean watcherEnabled = VisualFeatures.isEnabled("Watcher AFK");
        if (!magnusEnabled && !axeEnabled && !watcherEnabled) {
            resetAfkTracking();
            return;
        }

        Vec3 position = client.player.position();
        float yaw = client.player.getYRot();
        float pitch = client.player.getXRot();
        if (!afkTracking) {
            afkTracking = true;
            afkLastPosition = position;
            afkLastYaw = yaw;
            afkLastPitch = pitch;
            afkIdleTicks = 0;
            return;
        }

        boolean moved = position.distanceToSqr(afkLastPosition) > 0.0025D;
        boolean looked = angleDifference(yaw, afkLastYaw) > 0.8F
                || Math.abs(pitch - afkLastPitch) > 0.8F;
        afkLastPosition = position;
        afkLastYaw = yaw;
        afkLastPitch = pitch;

        if (moved || looked || client.player.isUsingItem() || client.player.swinging) {
            afkIdleTicks = 0;
            afkSceneTick = -1;
            afkSceneType = 0;
            afkEventPlayed = false;
            return;
        }

        if (afkSceneTick >= 0) {
            afkSceneTick++;
            if (afkSceneType == 1
                    && afkSceneTick == AFK_THROW_TICK + AFK_THROW_DURATION) {
                spawnAfkImpact(client, 0.95D);
            } else if (afkSceneType == 2 && afkSceneTick == AXE_IMPACT_TICK) {
                spawnAfkImpact(client, 1.38D);
            } else if (afkSceneType == 3 && afkSceneTick == WATCHER_SUCK_TICK) {
                spawnWatcherVoidEffect(client);
            }

            if (afkSceneType == 3 && (afkSceneTick % 4) == 0) {
                spawnWatcherSuctionParticles(client);
            }
            int duration = afkSceneType == 2 ? AXE_SCENE_DURATION
                    : afkSceneType == 3 ? WATCHER_SCENE_DURATION : AFK_SCENE_DURATION;
            if (afkSceneTick > duration) {
                afkSceneTick = -1;
                afkSceneType = 0;
            }
            return;
        }

        if (!afkEventPlayed) afkIdleTicks++;
        if (afkIdleTicks >= AFK_IDLE_TICKS && !afkEventPlayed) {
            int enabledScenes = (magnusEnabled ? 1 : 0) + (axeEnabled ? 1 : 0)
                    + (watcherEnabled ? 1 : 0);
            int choice = ThreadLocalRandom.current().nextInt(enabledScenes);
            if (magnusEnabled) {
                if (choice == 0) {
                    beginMagnusAfkScene(client);
                    return;
                }
                choice--;
            }
            if (axeEnabled) {
                if (choice == 0) {
                    beginAxeAfkScene(client);
                    return;
                }
                choice--;
            }
            beginWatcherAfkScene(client);
        }
    }

    private static void spawnAfkImpact(Minecraft client, double height) {
        if (client.level == null || client.player == null) return;
        Vec3 impact = client.player.position().add(0.0D, height, 0.0D);
        for (int i = 0; i < 18; i++) {
            double angle = Math.PI * 2.0D * i / 18.0D;
            client.level.addParticle(ParticleTypes.CRIT, impact.x, impact.y, impact.z,
                    Math.cos(angle) * 0.13D, 0.09D, Math.sin(angle) * 0.13D);
        }
        client.level.addParticle(ParticleTypes.POOF, impact.x, impact.y, impact.z,
                0.0D, 0.045D, 0.0D);
        client.level.addParticle(ParticleTypes.DAMAGE_INDICATOR, impact.x, impact.y, impact.z,
                0.0D, 0.16D, 0.0D);
    }

    private static void resetAfkTracking() {
        afkTracking = false;
        afkIdleTicks = 0;
        afkSceneTick = -1;
        afkSceneType = 0;
        afkEventPlayed = false;
    }

    private static float angleDifference(float a, float b) {
        float difference = (a - b) % 360.0F;
        if (difference > 180.0F) difference -= 360.0F;
        if (difference < -180.0F) difference += 360.0F;
        return Math.abs(difference);
    }

    private static void beginMagnusAfkScene(Minecraft client) {
        Vec3 playerPosition = client.player.position();
        double playerYaw = Math.toRadians(client.player.getYRot());
        Vec3 playerForward = new Vec3(-Math.sin(playerYaw), 0.0D, Math.cos(playerYaw)).normalize();
        Vec3 playerRight = new Vec3(Math.cos(playerYaw), 0.0D, Math.sin(playerYaw)).normalize();

        magnusPosition = playerPosition.add(playerForward.scale(2.65D)).add(playerRight.scale(-0.75D));
        Vec3 towardPlayer = playerPosition.subtract(magnusPosition).normalize();
        double facingYaw = Math.atan2(-towardPlayer.x, towardPlayer.z);
        magnusForward = new Vec3(-Math.sin(facingYaw), 0.0D, Math.cos(facingYaw)).normalize();
        magnusRight = new Vec3(Math.cos(facingYaw), 0.0D, Math.sin(facingYaw)).normalize();

        afkIdleTicks = AFK_IDLE_TICKS;
        afkEventPlayed = true;
        afkSceneType = 1;
        afkSceneTick = 0;
        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§6MAGNUS CARLSEN §7— Эй, хватит AFK! ♟"),
                false);
    }

    private static void beginWatcherAfkScene(Minecraft client) {
        Vec3 playerPosition = client.player.position();
        double yaw = Math.toRadians(client.player.getYRot());
        Vec3 playerForward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        Vec3 playerRight = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();

        watcherStartPosition = playerPosition.add(playerForward.scale(6.2D))
                .add(playerRight.scale(-0.35D));
        watcherEndPosition = playerPosition.add(playerForward.scale(1.25D))
                .add(playerRight.scale(-0.12D));
        Vec3 towardPlayer = playerPosition.subtract(watcherStartPosition).normalize();
        double facingYaw = Math.atan2(-towardPlayer.x, towardPlayer.z);
        watcherForward = new Vec3(-Math.sin(facingYaw), 0.0D, Math.cos(facingYaw)).normalize();
        watcherRight = new Vec3(Math.cos(facingYaw), 0.0D, Math.sin(facingYaw)).normalize();

        afkIdleTicks = AFK_IDLE_TICKS;
        afkEventPlayed = true;
        afkSceneType = 3;
        afkSceneTick = 0;
        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§5СМОТРЯЩИЙ §7— Пора покинуть этот мир..."),
                false);
    }

    private static void spawnWatcherSuctionParticles(Minecraft client) {
        if (client.level == null || client.player == null) return;
        Vec3 playerPosition = client.player.position();
        ThreadLocalRandom random = ThreadLocalRandom.current();
        double angle = random.nextDouble(Math.PI * 2.0D);
        double radius = 0.55D + random.nextDouble() * 0.95D;
        double x = playerPosition.x + Math.cos(angle) * radius;
        double z = playerPosition.z + Math.sin(angle) * radius;
        double y = playerPosition.y + 0.25D + random.nextDouble() * 1.55D;
        Vec3 towardVoid = playerPosition.add(0.0D, -0.15D, 0.0D)
                .subtract(x, y, z).normalize().scale(0.075D);
        client.level.addParticle(ParticleTypes.PORTAL, x, y, z,
                towardVoid.x, towardVoid.y - 0.035D, towardVoid.z);
    }

    private static void spawnWatcherVoidEffect(Minecraft client) {
        if (client.level == null || client.player == null) return;
        Vec3 position = client.player.position().add(0.0D, 0.18D, 0.0D);
        for (int i = 0; i < 40; i++) {
            double angle = Math.PI * 2.0D * i / 40.0D;
            double radius = 0.55D + (i % 3) * 0.18D;
            client.level.addParticle(ParticleTypes.PORTAL,
                    position.x + Math.cos(angle) * radius, position.y + (i % 4) * 0.16D,
                    position.z + Math.sin(angle) * radius,
                    -Math.cos(angle) * 0.11D, -0.12D, -Math.sin(angle) * 0.11D);
        }
        client.level.addParticle(ParticleTypes.POOF, position.x, position.y, position.z, 0, 0.08D, 0);
        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§5§lТЕБЯ ЗАМЕТИЛИ"),
                true);
    }

    private static void beginAxeAfkScene(Minecraft client) {
        Vec3 playerPosition = client.player.position();
        double yaw = Math.toRadians(client.player.getYRot());
        Vec3 playerForward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        Vec3 playerRight = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();

        // Axe starts six blocks away and charges toward a point just in front of the player.
        axeStartPosition = playerPosition.add(playerForward.scale(6.0D))
                .add(playerRight.scale(0.55D));
        axeEndPosition = playerPosition.add(playerForward.scale(1.18D))
                .add(playerRight.scale(0.25D));
        Vec3 towardPlayer = playerPosition.subtract(axeStartPosition).normalize();
        double facingYaw = Math.atan2(-towardPlayer.x, towardPlayer.z);
        axeForward = new Vec3(-Math.sin(facingYaw), 0.0D, Math.cos(facingYaw)).normalize();
        axeRight = new Vec3(Math.cos(facingYaw), 0.0D, Math.sin(facingYaw)).normalize();

        afkIdleTicks = AFK_IDLE_TICKS;
        afkEventPlayed = true;
        afkSceneType = 2;
        afkSceneTick = 0;
        client.player.displayClientMessage(
                net.minecraft.network.chat.Component.literal("§cAXE §7— Berserker's Call!"),
                false);
    }

    private static void renderChessCosmetics(WorldRenderContext context, Minecraft client, Vec3 camera) {
        boolean thirdPerson = !client.options.getCameraType().isFirstPerson();
        boolean alive = client.player.isAlive();
        boolean queenSkin = VisualFeatures.isEnabled("Queen Skin") && thirdPerson && alive;
        boolean invokerSkin = VisualFeatures.isEnabled("Invoker Skin") && thirdPerson && alive;
        boolean gutsSkin = VisualFeatures.isEnabled("Guts") && thirdPerson && alive;
        boolean akashiSkin = VisualFeatures.isEnabled("Akashi Seijuro") && thirdPerson && alive;
        boolean interworlderMask = VisualFeatures.isEnabled("Interworlder Mask") && thirdPerson && alive;
        boolean dualBlades = VisualFeatures.isEnabled("Dual Blades") && thirdPerson && alive;
        boolean flyingSpellbook = VisualFeatures.isEnabled("Flying Spellbook") && thirdPerson && alive;
        boolean handCosmetics = VisualFeatures.isEnabled("Hand Cosmetics") && thirdPerson && alive;
        boolean capeEffects = VisualFeatures.isEnabled("Cape Effects") && thirdPerson && alive;
        boolean magnusScene = afkSceneType == 1 && afkSceneTick >= 0;
        boolean axeScene = afkSceneType == 2 && afkSceneTick >= 0;
        boolean watcherScene = afkSceneType == 3 && afkSceneTick >= 0;
        if (!queenSkin && !invokerSkin && !gutsSkin && !akashiSkin && !interworlderMask
                && !dualBlades && !flyingSpellbook && !handCosmetics && !capeEffects
                && !magnusScene && !axeScene && !watcherScene) return;

        VertexConsumer out = context.consumers().getBuffer(CHESS_SOLID_RENDER_TYPE);

        double yaw = Math.toRadians(client.player.getYRot());
        Vec3 playerForward = new Vec3(-Math.sin(yaw), 0.0D, Math.cos(yaw)).normalize();
        Vec3 playerRight = new Vec3(Math.cos(yaw), 0.0D, Math.sin(yaw)).normalize();
        Vec3 relative = client.player.position().subtract(camera);

        // Invoker takes priority if both persisted toggles were enabled before this version.
        long gameTime = client.level.getGameTime();
        if (handCosmetics) renderHandCosmetics(out, new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D), gameTime);
        if (capeEffects) renderCapeEffect(out, new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D), gameTime);
        if (invokerSkin) {
            BoardTransform invokerTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
            renderInvoker(out, invokerTransform, gameTime);
        } else if (queenSkin) {
            BoardTransform queenTransform = new BoardTransform(relative, playerRight, playerForward, 2.85F, 0.0D);
            renderPiece(out, queenTransform, 0.0F, 0.0F, 'Q', 0.0F);
        } else if (gutsSkin) {
            BoardTransform gutsTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
            renderGuts(out, gutsTransform);
        } else if (akashiSkin) {
            BoardTransform akashiTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
            renderAkashi(out, akashiTransform);
        }
        if (dualBlades) {
            BoardTransform bladesTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
            renderDualBlades(out, bladesTransform);
        }
        if (flyingSpellbook) {
            renderFlyingSpellbook(out, relative, playerRight, playerForward, gameTime);
        }
        if (interworlderMask) {
            BoardTransform maskTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
            renderInterworlderMask(out, maskTransform);
        }

        if (magnusScene) {
            float approach = Math.min(1.0F, afkSceneTick / 14.0F);
            Vec3 animatedPosition = magnusPosition.add(magnusForward.scale(0.42D * (1.0D - approach)));
            animatedPosition = animatedPosition.subtract(camera);
            BoardTransform magnusTransform = new BoardTransform(animatedPosition, magnusRight, magnusForward, 1.0F,
                    Math.sin(afkSceneTick * 0.12D) * 0.012D);
            renderMagnus(out, magnusTransform, afkSceneTick);

            if (afkSceneTick < AFK_THROW_TICK) {
                Vec3 heldKing = magnusPosition.add(magnusRight.scale(0.38D))
                        .add(magnusForward.scale(0.20D)).add(0.0D, 1.32D, 0.0D);
                BoardTransform heldTransform = new BoardTransform(heldKing.subtract(camera),
                        magnusRight, magnusForward, 0.62F, 0.0D);
                renderPiece(out, heldTransform, 0.0F, 0.0F, 'k', 0.0F);
            } else {
                float flight = Math.min(1.0F,
                        (afkSceneTick - AFK_THROW_TICK) / (float) AFK_THROW_DURATION);
                Vec3 throwStart = magnusPosition.add(magnusRight.scale(0.38D))
                        .add(magnusForward.scale(0.36D)).add(0.0D, 1.34D, 0.0D);
                Vec3 throwEnd = client.player.position().add(0.0D, 0.95D, 0.0D);
                Vec3 kingPosition = throwStart.lerp(throwEnd, flight)
                        .add(0.0D, Math.sin(flight * Math.PI) * 0.72D, 0.0D);
                BoardTransform kingTransform = new BoardTransform(kingPosition.subtract(camera),
                        magnusRight, magnusForward, 0.62F, 0.0D);
                renderPiece(out, kingTransform, 0.0F, 0.0F, 'k', 0.0F);
            }
        }

        if (axeScene) {
            float approach = smooth01(afkSceneTick / (float) AXE_APPROACH_TICK);
            Vec3 actorPosition = mix(axeStartPosition, axeEndPosition, approach).subtract(camera);
            BoardTransform axeTransform = new BoardTransform(actorPosition, axeRight, axeForward, 1.0F,
                    Math.sin(afkSceneTick * 0.19D) * 0.012D);
            renderAxe(out, axeTransform, afkSceneTick);
        }

        if (watcherScene) {
            float approach = smooth01(afkSceneTick / 92.0F);
            Vec3 actorPosition = mix(watcherStartPosition, watcherEndPosition, approach).subtract(camera);
            float size = 1.0F + smooth01((afkSceneTick - 34.0F) / 105.0F) * 0.62F;
            BoardTransform watcherTransform = new BoardTransform(actorPosition, watcherRight, watcherForward,
                    size, Math.sin(afkSceneTick * 0.055D) * 0.018D);
            renderWatcher(out, watcherTransform, afkSceneTick);

            float pull = smooth01((afkSceneTick - 62.0F) / 82.0F);
            if (pull > 0.0F) {
                BoardTransform voidTransform = new BoardTransform(relative, playerRight, playerForward, 1.0F, 0.0D);
                cylinder(out, voidTransform, 0.0D, 0.0D, 0.015D, 0.055D,
                        0.18D + pull * 1.20D, 0xFF120D20, 32);
                cylinder(out, voidTransform, 0.0D, 0.0D, 0.056D, 0.072D,
                        0.12D + pull * 0.93D, 0xFF54318B, 32);
                cylinder(out, voidTransform, 0.0D, 0.0D, 0.073D, 0.083D,
                        0.06D + pull * 0.58D, 0xFF080610, 32);
            }
        }
    }

    private static void renderHandCosmetics(VertexConsumer out, BoardTransform t, long gameTime) {
        // Original wrist cuffs and small rune stones, attached to both forearms.
        int pulse = ((gameTime / 8L) & 1L) == 0L ? 0xFFFFD77A : 0xFFB88BFF;
        for (int side : new int[]{-1, 1}) {
            double x = side * 0.355D;
            cylinder(out, t, x, 0.145D, 0.795D, 0.865D, 0.082D, 0xFF8A6538, 16);
            cylinder(out, t, x, 0.145D, 0.805D, 0.855D, 0.086D, 0xFFD9B66E, 16);
            cylinder(out, t, x, 0.145D, 0.825D, 0.842D, 0.089D, 0xFF6F4F8F, 16);
            sphere(out, t, x, 0.84D, 0.233D, 0.035D, pulse, 10, 7);
            box(out, t, x - 0.024D, 0.73D, 0.12D, x + 0.024D, 0.78D, 0.19D, 0xFF39303F);
        }
    }

    private static void renderCapeEffect(VertexConsumer out, BoardTransform t, long gameTime) {
        // Segmented animated back-cape with a dark fabric base and luminous violet seams.
        box(out, t, -0.29D, 1.26D, -0.19D, 0.29D, 1.39D, -0.10D, 0xFF33254C);
        for (int panel = 0; panel < 7; panel++) {
            double x0 = -0.30D + panel * 0.086D;
            double x1 = x0 + 0.084D;
            double wave = Math.sin(gameTime * 0.075D + panel * 0.72D) * 0.045D;
            double bottomZ = -0.25D + wave;
            int fabric = (panel & 1) == 0 ? 0xFF241D39 : 0xFF302447;
            box(out, t, x0, 0.18D + Math.abs(wave) * 0.2D, bottomZ - 0.055D,
                    x1, 1.31D, -0.145D, fabric);
            box(out, t, x0 + 0.035D, 0.23D, bottomZ - 0.060D,
                    x0 + 0.048D, 1.27D, -0.198D, 0xFF7751B4);
            sphere(out, t, (x0 + x1) * 0.5D, 0.21D + Math.abs(wave) * 0.2D,
                    bottomZ - 0.063D, 0.022D, 0xFFB68CFF, 8, 5);
        }
        box(out, t, -0.24D, 1.27D, -0.205D, 0.24D, 1.34D, -0.16D, 0xFFD7B36D);
        sphere(out, t, 0.0D, 1.29D, -0.13D, 0.075D, 0xFF9E6CE4, 12, 8);
    }

    private static void renderInterworlderMask(VertexConsumer out, BoardTransform t) {
        // Compact face plate: its rear surface sits almost flush against the player's head.
        // The straps wrap around the temples so the cosmetic reads as worn equipment.
        box(out, t, -0.145D, 1.505D, 0.112D, 0.145D, 1.775D, 0.145D, 0xFF34323D);
        box(out, t, -0.132D, 1.520D, 0.145D, 0.132D, 1.758D, 0.166D, 0xFF55515F);

        // Angular brow and cheek pieces follow the width of the head rather than floating in front.
        box(out, t, -0.137D, 1.690D, 0.151D, -0.035D, 1.735D, 0.174D, 0xFF77717F);
        box(out, t, 0.035D, 1.690D, 0.151D, 0.137D, 1.735D, 0.174D, 0xFF77717F);
        box(out, t, -0.137D, 1.555D, 0.151D, -0.085D, 1.675D, 0.174D, 0xFF77717F);
        box(out, t, 0.085D, 1.555D, 0.151D, 0.137D, 1.675D, 0.174D, 0xFF77717F);
        box(out, t, -0.050D, 1.565D, 0.158D, 0.050D, 1.690D, 0.180D, 0xFF898391);

        // Narrow violet eye slits are inset into the plate, not perched on its surface.
        box(out, t, -0.112D, 1.650D, 0.173D, -0.035D, 1.680D, 0.181D, 0xFF8D3BE8);
        box(out, t, 0.035D, 1.650D, 0.173D, 0.112D, 1.680D, 0.181D, 0xFF8D3BE8);
        box(out, t, -0.098D, 1.660D, 0.181D, -0.055D, 1.670D, 0.184D, 0xFFE7B5FF);
        box(out, t, 0.055D, 1.660D, 0.181D, 0.098D, 1.670D, 0.184D, 0xFFE7B5FF);

        // A small nose ridge and tapered lower face give the mask a fitted silhouette.
        box(out, t, -0.023D, 1.605D, 0.174D, 0.023D, 1.655D, 0.190D, 0xFFAAA2B0);
        box(out, t, -0.095D, 1.525D, 0.151D, 0.095D, 1.565D, 0.176D, 0xFF292631);
        box(out, t, -0.060D, 1.535D, 0.177D, 0.060D, 1.545D, 0.183D, 0xFFB34BEB);

        // Side bands sit against the temples and disappear around the head.
        box(out, t, -0.175D, 1.555D, 0.015D, -0.132D, 1.710D, 0.132D, 0xFF292832);
        box(out, t, 0.132D, 1.555D, 0.015D, 0.175D, 1.710D, 0.132D, 0xFF292832);
        box(out, t, -0.151D, 1.610D, 0.112D, -0.132D, 1.655D, 0.151D, 0xFFB6AFC1);
        box(out, t, 0.132D, 1.610D, 0.112D, 0.151D, 1.655D, 0.151D, 0xFFB6AFC1);
    }

    private static void renderGuts(VertexConsumer out, BoardTransform t) {
        // Original low-poly dark swordsman: layered black armor, cape, metal arm and massive blade.
        box(out, t, -0.29D, 0.70D, -0.12D, 0.29D, 1.39D, 0.16D, 0xFF171922);
        box(out, t, -0.25D, 0.78D, 0.165D, 0.25D, 1.30D, 0.205D, 0xFF272B35);
        box(out, t, -0.27D, 1.12D, 0.205D, 0.27D, 1.34D, 0.238D, 0xFF3C414C);
        box(out, t, -0.19D, 1.00D, 0.24D, 0.19D, 1.16D, 0.258D, 0xFF0D0F16);
        box(out, t, -0.12D, 0.86D, 0.26D, 0.12D, 0.91D, 0.275D, 0xFFB18A45);
        box(out, t, -0.28D, 0.16D, -0.10D, -0.025D, 0.76D, 0.13D, 0xFF151720);
        box(out, t, 0.025D, 0.16D, -0.10D, 0.28D, 0.76D, 0.13D, 0xFF191B25);
        box(out, t, -0.31D, 0.05D, 0.04D, -0.01D, 0.20D, 0.27D, 0xFF080A10);
        box(out, t, 0.01D, 0.05D, 0.04D, 0.31D, 0.20D, 0.27D, 0xFF080A10);

        // Heavy shoulder guards and an asymmetric metal prosthetic arm.
        sphere(out, t, -0.30D, 1.30D, 0.02D, 0.19D, 0xFF343946, 10, 7);
        sphere(out, t, 0.30D, 1.30D, 0.02D, 0.18D, 0xFF454B59, 10, 7);
        renderLimb(out, t, new Vec3(-0.29D, 1.27D, 0.03D),
                new Vec3(-0.42D, 0.95D, 0.18D), 0.105D, 0xFF181B25);
        renderLimb(out, t, new Vec3(-0.42D, 0.95D, 0.18D),
                new Vec3(-0.36D, 0.78D, 0.24D), 0.073D, 0xFF292E3A);
        renderLimb(out, t, new Vec3(0.29D, 1.27D, 0.03D),
                new Vec3(0.40D, 0.98D, 0.12D), 0.105D, 0xFF6A7180);
        renderLimb(out, t, new Vec3(0.40D, 0.98D, 0.12D),
                new Vec3(0.35D, 0.77D, 0.23D), 0.073D, 0xFF9EA6B2);
        box(out, t, 0.30D, 0.78D, 0.18D, 0.40D, 0.91D, 0.30D, 0xFFD4D8E0);
        for (int i = 0; i < 3; i++) {
            box(out, t, 0.305D + i * 0.027D, 0.80D, 0.30D,
                    0.322D + i * 0.027D, 0.89D, 0.322D, 0xFF596272);
        }

        // Heavy black cape behind the torso.
        box(out, t, -0.31D, 0.47D, -0.25D, 0.31D, 1.36D, -0.13D, 0xFF0C0E15);
        box(out, t, -0.25D, 0.20D, -0.20D, 0.24D, 0.60D, -0.12D, 0xFF11131C);
        box(out, t, -0.22D, 1.31D, -0.11D, 0.22D, 1.40D, 0.08D, 0xFF555B66);

        // Face and spiky dark hair.
        sphere(out, t, 0.0D, 1.56D, 0.025D, 0.17D, 0xFFD8B49A, 14, 10);
        sphere(out, t, 0.0D, 1.68D, -0.015D, 0.19D, 0xFF171923, 14, 8);
        box(out, t, -0.18D, 1.58D, -0.02D, -0.12D, 1.82D, 0.09D, 0xFF12151E);
        box(out, t, -0.12D, 1.65D, -0.02D, -0.065D, 1.86D, 0.08D, 0xFF171A24);
        box(out, t, -0.04D, 1.67D, -0.015D, 0.025D, 1.88D, 0.08D, 0xFF10131C);
        box(out, t, 0.045D, 1.62D, -0.02D, 0.11D, 1.84D, 0.08D, 0xFF1B1E28);
        box(out, t, 0.12D, 1.55D, -0.01D, 0.18D, 1.76D, 0.08D, 0xFF131720);
        box(out, t, -0.078D, 1.56D, 0.175D, -0.02D, 1.575D, 0.19D, 0xFF30282B);
        box(out, t, 0.02D, 1.56D, 0.175D, 0.078D, 1.575D, 0.19D, 0xFF30282B);
        box(out, t, -0.035D, 1.51D, 0.175D, 0.035D, 1.525D, 0.19D, 0xFF805C50);

        // Huge worn blade carried along the back, with a visible crossguard and grip.
        renderLimb(out, t, new Vec3(0.32D, 0.62D, -0.27D),
                new Vec3(0.52D, 1.95D, -0.30D), 0.095D, 0xFF9DA5B2);
        renderLimb(out, t, new Vec3(0.30D, 0.51D, -0.25D),
                new Vec3(0.33D, 0.69D, -0.27D), 0.045D, 0xFF3A2D2B);
        box(out, t, 0.19D, 0.70D, -0.34D, 0.47D, 0.75D, -0.24D, 0xFFB5BBC3);
        box(out, t, 0.43D, 1.82D, -0.33D, 0.60D, 1.89D, -0.25D, 0xFFD5D9DF);
    }

    private static void renderAkashi(VertexConsumer out, BoardTransform t) {
        // Original low-poly basketball uniform and red-haired face inspired by Akashi's look.
        box(out, t, -0.23D, 0.79D, -0.12D, 0.23D, 1.39D, 0.15D, 0xFFF2F0EA);
        box(out, t, -0.23D, 0.81D, 0.153D, 0.23D, 1.36D, 0.179D, 0xFFEEECE6);
        box(out, t, -0.245D, 1.29D, 0.18D, -0.19D, 1.37D, 0.205D, 0xFFC82F4D);
        box(out, t, 0.19D, 1.29D, 0.18D, 0.245D, 1.37D, 0.205D, 0xFFC82F4D);
        box(out, t, -0.13D, 0.87D, 0.18D, 0.13D, 1.30D, 0.21D, 0xFFCE344D);
        box(out, t, -0.08D, 0.88D, 0.213D, 0.08D, 1.25D, 0.228D, 0xFFFFF4E9);
        box(out, t, -0.12D, 0.78D, -0.08D, 0.12D, 0.88D, 0.16D, 0xFFB62A43);
        box(out, t, -0.21D, 0.19D, -0.09D, -0.015D, 0.77D, 0.12D, 0xFF292C37);
        box(out, t, 0.015D, 0.19D, -0.09D, 0.21D, 0.77D, 0.12D, 0xFF292C37);
        box(out, t, -0.22D, 0.06D, 0.02D, -0.01D, 0.20D, 0.25D, 0xFFEEECE6);
        box(out, t, 0.01D, 0.06D, 0.02D, 0.22D, 0.20D, 0.25D, 0xFFEEECE6);

        renderLimb(out, t, new Vec3(-0.24D, 1.30D, 0.02D),
                new Vec3(-0.34D, 1.02D, 0.22D), 0.075D, 0xFFE7C1A8);
        renderLimb(out, t, new Vec3(-0.34D, 1.02D, 0.22D),
                new Vec3(-0.30D, 0.84D, 0.30D), 0.060D, 0xFFE7C1A8);
        renderLimb(out, t, new Vec3(0.24D, 1.30D, 0.02D),
                new Vec3(0.34D, 1.02D, 0.22D), 0.075D, 0xFFE7C1A8);
        renderLimb(out, t, new Vec3(0.34D, 1.02D, 0.22D),
                new Vec3(0.30D, 0.84D, 0.30D), 0.060D, 0xFFE7C1A8);
        box(out, t, -0.25D, 1.29D, 0.04D, -0.14D, 1.36D, 0.22D, 0xFFCE344D);
        box(out, t, 0.14D, 1.29D, 0.04D, 0.25D, 1.36D, 0.22D, 0xFFCE344D);

        sphere(out, t, 0.0D, 1.56D, 0.02D, 0.17D, 0xFFF0CBB3, 14, 10);
        sphere(out, t, 0.0D, 1.68D, -0.01D, 0.19D, 0xFFB72C4D, 14, 8);
        box(out, t, -0.18D, 1.56D, -0.02D, -0.12D, 1.79D, 0.09D, 0xFFB72C4D);
        box(out, t, -0.12D, 1.66D, -0.02D, -0.055D, 1.84D, 0.08D, 0xFFCC3C5B);
        box(out, t, -0.04D, 1.68D, -0.015D, 0.025D, 1.87D, 0.08D, 0xFFA92243);
        box(out, t, 0.045D, 1.64D, -0.02D, 0.11D, 1.83D, 0.08D, 0xFFCE3D5B);
        box(out, t, 0.12D, 1.56D, -0.01D, 0.18D, 1.77D, 0.08D, 0xFFB72C4D);
        box(out, t, -0.085D, 1.565D, 0.17D, -0.025D, 1.58D, 0.19D, 0xFFB11F36);
        box(out, t, 0.025D, 1.565D, 0.17D, 0.085D, 1.58D, 0.19D, 0xFFB11F36);
        box(out, t, -0.025D, 1.51D, 0.175D, 0.025D, 1.525D, 0.19D, 0xFFD1A18D);

        // White jersey number panel and a small red neck trim.
        box(out, t, -0.09D, 1.05D, 0.231D, 0.09D, 1.23D, 0.241D, 0xFFFFF8ED);
        box(out, t, -0.045D, 1.09D, 0.244D, 0.045D, 1.20D, 0.249D, 0xFFC82F4D);
    }

    private static void renderDualBlades(VertexConsumer out, BoardTransform t) {
        // Twin slim swords are attached to both hands; the vanilla avatar remains visible.
        for (int side : new int[]{-1, 1}) {
            double x = side * 0.38D;
            Vec3 hand = new Vec3(x, 0.86D, 0.27D);
            Vec3 guard = new Vec3(x + side * 0.015D, 0.97D, 0.30D);
            Vec3 tip = new Vec3(x + side * 0.13D, 1.82D, 0.39D);
            renderLimb(out, t, hand, guard, 0.035D, 0xFF3C3030);
            box(out, t, x - 0.12D, 0.94D, 0.25D, x + 0.12D, 0.99D, 0.34D, 0xFFD0AD67);
            renderLimb(out, t, guard, tip, 0.050D, 0xFFB9C5D5);
            renderLimb(out, t, guard.add(0.0D, 0.015D, 0.018D),
                    tip.add(0.0D, -0.015D, 0.018D), 0.016D, 0xFFF5F8FF);
        }
    }

    private static void renderFlyingSpellbook(VertexConsumer out, Vec3 relative,
                                               Vec3 playerRight, Vec3 playerForward, long gameTime) {
        double orbit = gameTime * 0.035D;
        double bob = Math.sin(gameTime * 0.09D) * 0.055D;
        Vec3 position = relative.add(playerRight.scale(-0.52D + Math.cos(orbit) * 0.045D))
                .add(0.0D, 1.38D + bob, 0.0D)
                .add(playerForward.scale(0.24D + Math.sin(orbit) * 0.035D));
        double angle = Math.sin(orbit * 0.42D) * 0.16D;
        Vec3 bookRight = playerRight.scale(Math.cos(angle)).add(playerForward.scale(Math.sin(angle))).normalize();
        Vec3 bookForward = playerForward.scale(Math.cos(angle)).subtract(playerRight.scale(Math.sin(angle))).normalize();
        BoardTransform book = new BoardTransform(position, bookRight, bookForward, 0.82F, 0.0D);

        // Floating grimoire with a thick leather cover, parchment pages and gold corners.
        box(out, book, -0.22D, -0.27D, -0.045D, 0.22D, 0.27D, 0.045D, 0xFF21162F);
        box(out, book, -0.195D, -0.245D, 0.047D, 0.195D, 0.245D, 0.066D, 0xFF67418E);
        box(out, book, -0.165D, -0.218D, 0.067D, 0.165D, 0.218D, 0.078D, 0xFFE8D8B2);
        box(out, book, -0.025D, -0.245D, 0.079D, 0.025D, 0.245D, 0.091D, 0xFFB78A4D);
        box(out, book, -0.20D, 0.205D, 0.069D, -0.145D, 0.25D, 0.093D, 0xFFFFD889);
        box(out, book, 0.145D, 0.205D, 0.069D, 0.20D, 0.25D, 0.093D, 0xFFFFD889);
        box(out, book, -0.20D, -0.25D, 0.069D, -0.145D, -0.205D, 0.093D, 0xFFFFD889);
        box(out, book, 0.145D, -0.25D, 0.069D, 0.20D, -0.205D, 0.093D, 0xFFFFD889);
        sphere(out, book, 0.0D, 0.0D, 0.105D, 0.085D, 0xFFB66BFF, 12, 8);
        sphere(out, book, 0.0D, 0.0D, 0.16D, 0.035D, 0xFFFFE7A8, 10, 7);
        box(out, book, -0.125D, -0.15D, 0.092D, -0.055D, -0.14D, 0.10D, 0xFF8A6E4D);
        box(out, book, 0.055D, 0.14D, 0.092D, 0.13D, 0.15D, 0.10D, 0xFF8A6E4D);
    }

    private static void renderInvoker(VertexConsumer out, BoardTransform t, long gameTime) {
        // Reworked arcane mage silhouette: layered flowing robes, shaped sleeves,
        // metallic shoulder pieces, long pale hair and a more readable face.
        // The 3 elemental orbs and six-second permutation cycle are preserved.

        // Flowing outer robe and inner blue-violet tunic.
        cone(out, t, 0.0D, -0.015D, 0.035D, 1.04D, 0.435D, 0xFF292B50, 28);
        cone(out, t, 0.0D, 0.055D, 0.105D, 0.99D, 0.325D, 0xFF48457D, 24);
        cylinder(out, t, 0.0D, -0.015D, 0.08D, 0.18D, 0.39D, 0xFF34345F, 24);

        // Decorative robe panels and gold hems.
        for (int side = -1; side <= 1; side += 2) {
            box(out, t, side * 0.19D - 0.035D, 0.13D, 0.315D,
                    side * 0.19D + 0.035D, 0.88D, 0.348D, 0xFFD0AD62);
            box(out, t, side * 0.20D - 0.018D, 0.18D, 0.352D,
                    side * 0.20D + 0.018D, 0.83D, 0.365D, 0xFFFFE6A4);
        }
        cylinder(out, t, 0.0D, 0.0D, 0.83D, 0.91D, 0.255D, 0xFFE1C078, 24);
        cylinder(out, t, 0.0D, 0.0D, 0.79D, 0.84D, 0.265D, 0xFF6A4D8C, 24);
        sphere(out, t, 0.0D, 0.86D, 0.276D, 0.075D, 0xFFFFD36A, 12, 8);

        // Upper torso, layered mantle and a high gold-trimmed collar.
        cylinder(out, t, 0.0D, 0.0D, 0.91D, 1.29D, 0.235D, 0xFF37375F, 20);
        box(out, t, -0.18D, 0.96D, 0.183D, 0.18D, 1.22D, 0.229D, 0xFF54457F);
        box(out, t, -0.045D, 0.98D, 0.231D, 0.045D, 1.20D, 0.252D, 0xFFD0A75B);
        sphere(out, t, 0.0D, 1.12D, 0.269D, 0.065D, 0xFFFFD46D, 12, 8);
        cylinder(out, t, 0.0D, 0.0D, 1.25D, 1.34D, 0.14D, 0xFFD8B765, 20);
        cylinder(out, t, 0.0D, 0.0D, 1.32D, 1.39D, 0.17D, 0xFF47365D, 20);

        // Long cape behind the shoulders, with gold shoulder clasps.
        box(out, t, -0.31D, 0.60D, -0.19D, 0.31D, 1.32D, -0.115D, 0xFF242943);
        box(out, t, -0.28D, 0.59D, -0.205D, -0.245D, 1.24D, -0.19D, 0xFFB89759);
        box(out, t, 0.245D, 0.59D, -0.205D, 0.28D, 1.24D, -0.19D, 0xFFB89759);
        sphere(out, t, -0.255D, 1.24D, -0.02D, 0.135D, 0xFFD7B56A, 12, 8);
        sphere(out, t, 0.255D, 1.24D, -0.02D, 0.135D, 0xFFD7B56A, 12, 8);
        sphere(out, t, -0.255D, 1.24D, 0.075D, 0.09D, 0xFF7862AC, 12, 8);
        sphere(out, t, 0.255D, 1.24D, 0.075D, 0.09D, 0xFF7862AC, 12, 8);

        // Neck and head.
        cylinder(out, t, 0.0D, 0.0D, 1.30D, 1.47D, 0.075D, 0xFFD9B99A, 16);
        sphere(out, t, 0.0D, 1.535D, 0.0D, 0.175D, 0xFFE4C4A4, 18, 12);
        // Hair cap and long side locks frame the face instead of hiding it.
        sphere(out, t, 0.0D, 1.665D, -0.018D, 0.185D, 0xFFE9E2D6, 18, 9);
        renderLimb(out, t, new Vec3(-0.145D, 1.61D, 0.015D),
                new Vec3(-0.18D, 1.10D, -0.015D), 0.052D, 0xFFE4DED0);
        renderLimb(out, t, new Vec3(0.145D, 1.61D, 0.015D),
                new Vec3(0.18D, 1.10D, -0.015D), 0.052D, 0xFFE4DED0);
        renderLimb(out, t, new Vec3(-0.07D, 1.69D, -0.13D),
                new Vec3(-0.12D, 1.22D, -0.17D), 0.045D, 0xFFF4EBDD);
        renderLimb(out, t, new Vec3(0.07D, 1.69D, -0.13D),
                new Vec3(0.12D, 1.22D, -0.17D), 0.045D, 0xFFF4EBDD);

        // Face features face local +Z.
        box(out, t, -0.083D, 1.555D, 0.151D, -0.028D, 1.575D, 0.166D, 0xFF4A3C43);
        box(out, t, 0.028D, 1.555D, 0.151D, 0.083D, 1.575D, 0.166D, 0xFF4A3C43);
        box(out, t, -0.084D, 1.583D, 0.145D, -0.026D, 1.596D, 0.163D, 0xFF9A795F);
        box(out, t, 0.026D, 1.583D, 0.145D, 0.084D, 1.596D, 0.163D, 0xFF9A795F);
        sphere(out, t, 0.0D, 1.525D, 0.169D, 0.025D, 0xFFD5A987, 8, 6);

        // Floating sleeves, articulated forearms and hands.
        Vec3 leftShoulder = new Vec3(-0.25D, 1.22D, 0.0D);
        Vec3 leftElbow = new Vec3(-0.40D, 1.02D, 0.20D);
        Vec3 leftHand = new Vec3(-0.34D, 0.93D, 0.34D);
        renderLimb(out, t, leftShoulder, leftElbow, 0.125D, 0xFF54457F);
        renderLimb(out, t, leftElbow, leftHand, 0.075D, 0xFFDCC0A2);
        sphere(out, t, leftHand.x, leftHand.y, leftHand.z, 0.075D, 0xFFDCC0A2, 10, 7);

        Vec3 rightShoulder = new Vec3(0.25D, 1.22D, 0.0D);
        Vec3 rightElbow = new Vec3(0.40D, 1.06D, 0.18D);
        Vec3 rightHand = new Vec3(0.39D, 1.18D, 0.32D);
        renderLimb(out, t, rightShoulder, rightElbow, 0.125D, 0xFF54457F);
        renderLimb(out, t, rightElbow, rightHand, 0.07D, 0xFFDCC0A2);
        sphere(out, t, rightHand.x, rightHand.y, rightHand.z, 0.075D, 0xFFDCC0A2, 10, 7);

        // Gold circlet and center rune.
        cylinder(out, t, 0.0D, 0.0D, 1.635D, 1.67D, 0.184D, 0xFFD6B15F, 20);
        sphere(out, t, 0.0D, 1.69D, 0.055D, 0.045D, 0xFF7BCBFF, 10, 7);

        // Quas, Wex and Exort: their order changes every six seconds.
        int[][] orders = {
                {0, 1, 2}, {1, 0, 2}, {2, 1, 0},
                {0, 2, 1}, {1, 2, 0}, {2, 0, 1}
        };
        int[] order = orders[(int) ((gameTime / 120L) % orders.length)];
        int[] orbColors = {0xFF69D9FF, 0xFFAD78FF, 0xFFFFA34E};
        double orbit = gameTime * 0.035D;
        for (int i = 0; i < 3; i++) {
            int element = order[i];
            double angle = orbit + i * (Math.PI * 2.0D / 3.0D);
            double x = Math.cos(angle) * 0.40D;
            double z = 0.27D + Math.sin(angle) * 0.22D;
            double y = 1.38D + Math.sin(angle * 1.7D) * 0.12D;
            sphere(out, t, x, y, z, 0.105D, orbColors[element], 16, 10);
            sphere(out, t, x - 0.028D, y + 0.038D, z + 0.062D,
                    0.036D, 0xFFFFF4DF, 10, 7);
        }
    }

    private static void renderWatcher(VertexConsumer out, BoardTransform t, int age) {
        // The Watcher: a tall, featureless shadow in a hooded coat with white eyes and fur trim.
        cone(out, t, 0.0D, -0.02D, 0.02D, 1.60D, 0.73D, 0xFF090B14, 28);
        box(out, t, -0.38D, 0.74D, -0.14D, 0.38D, 1.42D, 0.16D, 0xFF121521);
        box(out, t, -0.30D, 0.18D, 0.145D, 0.30D, 0.72D, 0.18D, 0xFF05070D);

        // Wide hood and completely dark face.
        sphere(out, t, 0.0D, 1.69D, -0.015D, 0.43D, 0xFF171A29, 18, 12);
        sphere(out, t, 0.0D, 1.68D, 0.245D, 0.305D, 0xFF020308, 18, 12);
        box(out, t, -0.17D, 1.665D, 0.523D, -0.055D, 1.704D, 0.542D, 0xFFF7F5FF);
        box(out, t, 0.055D, 1.665D, 0.523D, 0.17D, 1.704D, 0.542D, 0xFFF7F5FF);
        box(out, t, -0.145D, 1.675D, 0.543D, -0.082D, 1.694D, 0.548D, 0xFFFFFFFF);
        box(out, t, 0.082D, 1.675D, 0.543D, 0.145D, 1.694D, 0.548D, 0xFFFFFFFF);

        // Pale fur framing the hood.
        for (int i = 0; i <= 12; i++) {
            double angle = Math.PI * (0.12D + 0.76D * i / 12.0D);
            double x = Math.cos(angle) * 0.36D;
            double y = 1.70D + Math.sin(angle) * 0.34D;
            sphere(out, t, x, y, 0.12D + Math.sin(angle) * 0.06D,
                    0.052D, 0xFFD8D7E1, 9, 6);
        }

        // Long black sleeves with white fur cuffs and coat buttons.
        Vec3 leftShoulder = new Vec3(-0.31D, 1.30D, 0.0D);
        Vec3 leftElbow = new Vec3(-0.48D, 1.02D, 0.12D);
        Vec3 leftHand = new Vec3(-0.45D, 0.82D, 0.23D);
        renderLimb(out, t, leftShoulder, leftElbow, 0.145D, 0xFF10131E);
        renderLimb(out, t, leftElbow, leftHand, 0.09D, 0xFF0A0C15);
        sphere(out, t, leftHand.x, leftHand.y, leftHand.z, 0.075D, 0xFF020308, 9, 6);
        Vec3 rightShoulder = new Vec3(0.31D, 1.30D, 0.0D);
        Vec3 rightElbow = new Vec3(0.48D, 1.02D, 0.12D);
        Vec3 rightHand = new Vec3(0.45D, 0.82D, 0.23D);
        renderLimb(out, t, rightShoulder, rightElbow, 0.145D, 0xFF10131E);
        renderLimb(out, t, rightElbow, rightHand, 0.09D, 0xFF0A0C15);
        sphere(out, t, rightHand.x, rightHand.y, rightHand.z, 0.075D, 0xFF020308, 9, 6);

        for (int i = 0; i < 4; i++) {
            double y = 1.05D - i * 0.19D;
            sphere(out, t, 0.0D, y, 0.19D, 0.035D, 0xFFD9D8E2, 8, 5);
        }

    }

    private static void renderAxe(VertexConsumer out, BoardTransform t, int age) {
        // Original stylized berserker: broad red armor, iron helmet and heavy battle axe.
        box(out, t, -0.21D, 0.08D, -0.12D, -0.015D, 0.64D, 0.12D, 0xFF7B332B);
        box(out, t, 0.015D, 0.08D, -0.12D, 0.21D, 0.64D, 0.12D, 0xFF7B332B);
        box(out, t, -0.25D, 0.04D, -0.16D, -0.01D, 0.18D, 0.16D, 0xFF3D2626);
        box(out, t, 0.01D, 0.04D, -0.16D, 0.25D, 0.18D, 0.16D, 0xFF3D2626);
        box(out, t, -0.33D, 0.62D, -0.15D, 0.33D, 1.34D, 0.15D, 0xFF8C3029);
        box(out, t, -0.42D, 1.15D, -0.13D, -0.20D, 1.39D, 0.16D, 0xFFB34C32);
        box(out, t, 0.20D, 1.15D, -0.13D, 0.42D, 1.39D, 0.16D, 0xFFB34C32);
        box(out, t, -0.11D, 0.68D, 0.152D, 0.11D, 1.30D, 0.17D, 0xFFD9B04A);
        box(out, t, -0.075D, 1.28D, -0.08D, 0.075D, 1.44D, 0.08D, 0xFFD09C72);
        sphere(out, t, 0.0D, 1.52D, 0.0D, 0.18D, 0xFFD09C72, 12, 8);
        // Helmet, crest, and beard.
        box(out, t, -0.19D, 1.57D, -0.10D, 0.19D, 1.70D, 0.12D, 0xFF6C777E);
        box(out, t, -0.04D, 1.67D, -0.055D, 0.04D, 1.84D, 0.055D, 0xFFB94B36);
        box(out, t, -0.105D, 1.39D, 0.105D, 0.105D, 1.50D, 0.18D, 0xFF8E4B2D);
        box(out, t, -0.085D, 1.53D, 0.16D, -0.035D, 1.555D, 0.176D, 0xFF211B1A);
        box(out, t, 0.035D, 1.53D, 0.16D, 0.085D, 1.555D, 0.176D, 0xFF211B1A);

        Vec3 leftShoulder = new Vec3(-0.28D, 1.24D, 0.0D);
        Vec3 leftElbow = new Vec3(-0.36D, 1.02D, 0.15D);
        Vec3 leftHand = new Vec3(-0.32D, 0.88D, 0.22D);
        renderLimb(out, t, leftShoulder, leftElbow, 0.115D, 0xFF8C3029);
        renderLimb(out, t, leftElbow, leftHand, 0.075D, 0xFFD09C72);
        sphere(out, t, leftHand.x, leftHand.y, leftHand.z, 0.08D, 0xFFD09C72, 8, 5);

        float wind = smooth01((age - AXE_APPROACH_TICK) / 11.0F);
        float strike = smooth01((age - AXE_WINDUP_TICK) / 15.0F);
        float recover = smooth01((age - (AXE_IMPACT_TICK + 4.0F)) / 13.0F);
        Vec3 shoulder = new Vec3(0.28D, 1.24D, 0.0D);
        Vec3 elbowStart = new Vec3(0.38D, 1.08D, 0.12D);
        Vec3 handStart = new Vec3(0.39D, 0.92D, 0.20D);
        Vec3 elbowRaised = new Vec3(0.42D, 1.56D, -0.12D);
        Vec3 handRaised = new Vec3(0.48D, 1.78D, -0.15D);
        Vec3 elbowStrike = new Vec3(0.38D, 1.06D, 0.32D);
        Vec3 handStrike = new Vec3(0.34D, 0.72D, 0.60D);
        Vec3 elbow = mix(mix(elbowStart, elbowRaised, wind), elbowStrike, strike);
        Vec3 hand = mix(mix(handStart, handRaised, wind), handStrike, strike);
        elbow = mix(elbow, elbowStart, recover);
        hand = mix(hand, handStart, recover);
        renderLimb(out, t, shoulder, elbow, 0.13D, 0xFF8C3029);
        renderLimb(out, t, elbow, hand, 0.085D, 0xFFD09C72);
        sphere(out, t, hand.x, hand.y, hand.z, 0.085D, 0xFFD09C72, 8, 5);

        Vec3 axeTop = hand.add(0.0D, 0.56D, 0.025D);
        renderLimb(out, t, hand, axeTop, 0.035D, 0xFF493023);
        box(out, t, axeTop.x - 0.22D, axeTop.y - 0.11D, axeTop.z - 0.06D,
                axeTop.x + 0.06D, axeTop.y + 0.13D, axeTop.z + 0.06D, 0xFFBBC4C8);
        box(out, t, axeTop.x - 0.28D, axeTop.y - 0.04D, axeTop.z - 0.045D,
                axeTop.x - 0.20D, axeTop.y + 0.07D, axeTop.z + 0.045D, 0xFF7D8A91);
    }

    private static void renderMagnus(VertexConsumer out, BoardTransform t, int age) {
        // Low-poly chess grandmaster caricature: dark blazer, white shirt, brown hair.
        box(out, t, -0.19D, 0.05D, -0.105D, -0.025D, 0.62D, 0.105D, 0xFF202A38);
        box(out, t, 0.025D, 0.05D, -0.105D, 0.19D, 0.62D, 0.105D, 0xFF202A38);
        box(out, t, -0.235D, 0.04D, -0.15D, -0.015D, 0.16D, 0.15D, 0xFF11151E);
        box(out, t, 0.015D, 0.04D, -0.15D, 0.235D, 0.16D, 0.15D, 0xFF11151E);

        box(out, t, -0.255D, 0.62D, -0.12D, 0.255D, 1.34D, 0.12D, 0xFF273244);
        box(out, t, -0.085D, 0.72D, 0.121D, 0.085D, 1.27D, 0.145D, 0xFFF1EEE8);
        box(out, t, -0.026D, 0.86D, 0.147D, 0.026D, 1.22D, 0.16D, 0xFF60718B);
        box(out, t, -0.075D, 1.30D, -0.09D, 0.075D, 1.40D, 0.10D, 0xFFF1EEE8);

        // Neck and face.
        box(out, t, -0.075D, 1.30D, -0.075D, 0.075D, 1.48D, 0.075D, 0xFFD9B18F);
        sphere(out, t, 0.0D, 1.56D, 0.0D, 0.175D, 0xFFD9B18F, 12, 8);
        // Hairline, crown and side locks.
        sphere(out, t, 0.0D, 1.675D, -0.012D, 0.174D, 0xFF765B45, 12, 6);
        box(out, t, -0.17D, 1.55D, -0.06D, -0.105D, 1.68D, 0.075D, 0xFF765B45);
        box(out, t, 0.105D, 1.55D, -0.06D, 0.17D, 1.68D, 0.075D, 0xFF765B45);
        // Eyes and nose face the local +Z direction.
        box(out, t, -0.075D, 1.575D, 0.155D, -0.032D, 1.600D, 0.169D, 0xFF27211F);
        box(out, t, 0.032D, 1.575D, 0.155D, 0.075D, 1.600D, 0.169D, 0xFF27211F);
        sphere(out, t, 0.0D, 1.535D, 0.169D, 0.025D, 0xFFBE9071, 8, 5);

        // Left arm remains relaxed. The right arm winds up, throws, then follows through.
        Vec3 leftShoulder = new Vec3(-0.22D, 1.24D, 0.02D);
        Vec3 leftElbow = new Vec3(-0.34D, 1.02D, 0.13D);
        Vec3 leftHand = new Vec3(-0.32D, 0.88D, 0.20D);
        renderLimb(out, t, leftShoulder, leftElbow, 0.095D, 0xFF273244);
        renderLimb(out, t, leftElbow, leftHand, 0.065D, 0xFFD9B18F);
        sphere(out, t, leftHand.x, leftHand.y, leftHand.z, 0.075D, 0xFFD9B18F, 8, 5);

        float wind = smooth01((age - 7.0F) / 25.0F);
        float follow = smooth01((age - (AFK_THROW_TICK - 1.0F)) / 9.0F);
        Vec3 shoulder = new Vec3(0.22D, 1.24D, 0.02D);
        Vec3 elbowWind = new Vec3(0.36D, 1.34D, -0.12D);
        Vec3 handWind = new Vec3(0.39D, 1.60D, -0.22D);
        Vec3 elbowThrow = new Vec3(0.34D, 1.14D, 0.28D);
        Vec3 handThrow = new Vec3(0.31D, 1.02D, 0.56D);
        Vec3 elbow = mix(elbowWind, elbowThrow, follow);
        Vec3 hand = mix(handWind, handThrow, follow);
        elbow = mix(new Vec3(0.34D, 1.06D, 0.12D), elbow, wind);
        hand = mix(new Vec3(0.36D, 0.92D, 0.22D), hand, wind);
        renderLimb(out, t, shoulder, elbow, 0.105D, 0xFF273244);
        renderLimb(out, t, elbow, hand, 0.072D, 0xFFD9B18F);
        sphere(out, t, elbow.x, elbow.y, elbow.z, 0.078D, 0xFFD9B18F, 8, 5);
        sphere(out, t, hand.x, hand.y, hand.z, 0.075D, 0xFFD9B18F, 8, 5);
    }

    private static void renderLimb(VertexConsumer out, BoardTransform t, Vec3 start, Vec3 end,
                                   double radius, int color) {
        Vec3 direction = end.subtract(start);
        if (direction.lengthSqr() < 1.0E-8D) return;
        direction = direction.normalize();
        Vec3 reference = Math.abs(direction.y) > 0.92D
                ? new Vec3(1.0D, 0.0D, 0.0D) : new Vec3(0.0D, 1.0D, 0.0D);
        Vec3 u = direction.cross(reference).normalize();
        Vec3 v = direction.cross(u).normalize();
        int segments = 8;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            Vec3 p0 = start.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            Vec3 p1 = start.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            Vec3 p2 = end.add(u.scale(Math.cos(a1) * radius)).add(v.scale(Math.sin(a1) * radius));
            Vec3 p3 = end.add(u.scale(Math.cos(a0) * radius)).add(v.scale(Math.sin(a0) * radius));
            quad(out, t, color, 0.0D, 1.0D, 0.0D,
                    p0.x, p0.y, p0.z, p1.x, p1.y, p1.z,
                    p2.x, p2.y, p2.z, p3.x, p3.y, p3.z);
        }
        sphere(out, t, start.x, start.y, start.z, radius, color, 8, 5);
        sphere(out, t, end.x, end.y, end.z, radius, color, 8, 5);
    }

    private static float smooth01(float value) {
        float t = Math.max(0.0F, Math.min(1.0F, value));
        return t * t * (3.0F - 2.0F * t);
    }

    private static Vec3 mix(Vec3 from, Vec3 to, float amount) {
        return from.lerp(to, Math.max(0.0F, Math.min(1.0F, amount)));
    }

    private static void renderPortal(VertexConsumer lines, BoardTransform transform,
                                     Vec3 camera, float progress) {
        if (introTick >= INTRO_TICKS) return;
        double pulse = 1.0D + Math.sin(introTick * 0.22D) * 0.08D;
        double radius = 1.34D * pulse;
        double centerY = 0.76D + (1.0D - progress) * 0.08D;
        double centerZ = 0.58D;
        int segments = 48;
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments + introTick * 0.018D;
            double a1 = Math.PI * 2.0D * (i + 1) / segments + introTick * 0.018D;
            Vec3 p0 = transform.world(Math.cos(a0) * radius, centerY + Math.sin(a0) * radius, centerZ);
            Vec3 p1 = transform.world(Math.cos(a1) * radius, centerY + Math.sin(a1) * radius, centerZ);
            int color = (i % 4 == 0) ? 0xFFFFD68A : 0xFF59D7F0;
            emitLine(lines, p0, p1, camera, color, 2.6F);
            if ((i % 2) == 0) {
                double r2 = radius * 0.86D;
                Vec3 q0 = transform.world(Math.cos(a0 + 0.12D) * r2,
                        centerY + Math.sin(a0 + 0.12D) * r2, centerZ + 0.008D);
                Vec3 q1 = transform.world(Math.cos(a1 + 0.12D) * r2,
                        centerY + Math.sin(a1 + 0.12D) * r2, centerZ + 0.008D);
                emitLine(lines, q0, q1, camera, 0xFF8A8AFB, 1.6F);
            }
        }
    }

    private static void renderBoard(VertexConsumer out, BoardTransform t) {
        box(out, t, -HALF - 0.095F, -0.16F, -HALF - 0.095F,
                HALF + 0.095F, 0.005F, HALF + 0.095F, WOOD);
        box(out, t, -HALF - 0.08F, -0.19F, -HALF - 0.08F,
                HALF + 0.08F, -0.15F, HALF + 0.08F, WOOD_EDGE);

        for (int rank = 0; rank < 8; rank++) {
            for (int file = 0; file < 8; file++) {
                float x0 = -HALF + file * CELL + 0.003F;
                float z0 = -HALF + rank * CELL + 0.003F;
                int color = ((file + rank) & 1) == 0 ? LIGHT_TILE : DARK_TILE;
                box(out, t, x0, 0.004F, z0, x0 + CELL - 0.006F, 0.042F,
                        z0 + CELL - 0.006F, color);
            }
        }

        // Thin luminous trim around the floating table.
        box(out, t, -HALF - 0.09F, 0.006F, -HALF - 0.09F,
                HALF + 0.09F, 0.025F, -HALF - 0.055F, GOLD);
        box(out, t, -HALF - 0.09F, 0.006F, HALF + 0.055F,
                HALF + 0.09F, 0.025F, HALF + 0.09F, GOLD);
        box(out, t, -HALF - 0.09F, 0.006F, -HALF - 0.055F,
                -HALF - 0.055F, 0.025F, HALF + 0.055F, GOLD);
        box(out, t, HALF + 0.055F, 0.006F, -HALF - 0.055F,
                HALF + 0.09F, 0.025F, HALF + 0.055F, GOLD);
    }

    private static void renderHighlights(VertexConsumer lines, BoardTransform t, Vec3 camera) {
        if (selectedSquare < 0) return;
        drawSquareOutline(lines, t, camera, selectedSquare, SELECTED, 3.5F);
        for (ChessEngine.Move move : GAME.legalMoves()) {
            if (move.from() == selectedSquare) {
                drawSquareOutline(lines, t, camera, move.to(), TARGET, 2.2F);
            }
        }
    }

    private static void drawSquareOutline(VertexConsumer lines, BoardTransform t, Vec3 camera,
                                          int square, int color, float width) {
        int file = square % 8;
        int rank = square / 8;
        double x0 = -HALF + file * CELL + 0.015D;
        double z0 = -HALF + rank * CELL + 0.015D;
        double x1 = x0 + CELL - 0.03D;
        double z1 = z0 + CELL - 0.03D;
        double y = 0.055D;
        emitLine(lines, t.world(x0, y, z0), t.world(x1, y, z0), camera, color, width);
        emitLine(lines, t.world(x1, y, z0), t.world(x1, y, z1), camera, color, width);
        emitLine(lines, t.world(x1, y, z1), t.world(x0, y, z1), camera, color, width);
        emitLine(lines, t.world(x0, y, z1), t.world(x0, y, z0), camera, color, width);
    }

    private static void renderPieces(VertexConsumer out, BoardTransform t, Vec3 camera) {
        for (int square = 0; square < 64; square++) {
            char piece = GAME.pieceAt(square);
            if (piece == 0) continue;
            if (moveAnimation != null && moveAnimation.to == square) continue;
            float x = -HALF + ((square % 8) + 0.5F) * CELL;
            float z = -HALF + ((square / 8) + 0.5F) * CELL;
            renderPiece(out, t, x, z, piece, 0.0F);
        }

        if (moveAnimation != null) {
            float progress = Math.min(1.0F, moveAnimation.ticks / (float) MOVE_TICKS);
            float fromX = -HALF + ((moveAnimation.from % 8) + 0.5F) * CELL;
            float fromZ = -HALF + ((moveAnimation.from / 8) + 0.5F) * CELL;
            float toX = -HALF + ((moveAnimation.to % 8) + 0.5F) * CELL;
            float toZ = -HALF + ((moveAnimation.to / 8) + 0.5F) * CELL;
            float eased = progress * progress * (3.0F - 2.0F * progress);
            float x = fromX + (toX - fromX) * eased;
            float z = fromZ + (toZ - fromZ) * eased;
            float arc = (float) Math.sin(progress * Math.PI) * 0.24F;
            renderPiece(out, t, x, z, moveAnimation.piece, arc);
        }
    }

    /** Creates a recognizable, fully volumetric chess piece out of small shaded mesh primitives. */
    private static void renderPiece(VertexConsumer out, BoardTransform t,
                                    float x, float z, char piece, float animationLift) {
        boolean white = Character.isUpperCase(piece);
        int body = white ? WHITE_BODY : BLACK_BODY;
        int highlight = white ? WHITE_HI : BLACK_HI;
        int shade = white ? WHITE_SHADE : BLACK_SHADE;
        int trim = white ? GOLD : CYAN;
        float y = 0.045F + animationLift;

        cylinder(out, t, x, z, y, y + 0.045F, 0.155F, shade, 16);
        cylinder(out, t, x, z, y + 0.045F, y + 0.085F, 0.135F, trim, 16);
        cylinder(out, t, x, z, y + 0.085F, y + 0.115F, 0.115F, body, 16);

        switch (Character.toUpperCase(piece)) {
            case 'P' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.275F, 0.071F, body, 12);
                sphere(out, t, x, y + 0.315F, z, 0.105F, highlight, 12, 7);
                sphere(out, t, x, y + 0.315F, z, 0.047F, trim, 10, 5);
            }
            case 'N' -> {
                int facing = white ? 1 : -1;
                cylinder(out, t, x, z, y + 0.105F, y + 0.215F, 0.082F, body, 12);
                cylinder(out, t, x, z + facing * 0.018F, y + 0.18F, y + 0.30F,
                        0.078F, highlight, 12);
                sphere(out, t, x, y + 0.285F, z + facing * 0.038F,
                        0.098F, body, 12, 7);
                box(out, t, x - 0.062F, y + 0.255F, z + facing * 0.085F - 0.055F,
                        x + 0.062F, y + 0.315F, z + facing * 0.085F + 0.055F, highlight);
                box(out, t, x - 0.023F, y + 0.365F, z + facing * 0.015F - 0.025F,
                        x + 0.023F, y + 0.445F, z + facing * 0.015F + 0.025F, trim);
                sphere(out, t, x + 0.038F, y + 0.305F, z + facing * 0.075F,
                        0.018F, trim, 8, 5);
            }
            case 'B' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.32F, 0.066F, body, 12);
                sphere(out, t, x, y + 0.355F, z, 0.095F, highlight, 12, 7);
                cone(out, t, x, z, y + 0.39F, y + 0.49F, 0.052F, trim, 12);
                box(out, t, x - 0.018F, y + 0.31F, z - 0.10F,
                        x + 0.018F, y + 0.39F, z + 0.10F, shade);
            }
            case 'R' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.315F, 0.105F, body, 12);
                cylinder(out, t, x, z, y + 0.29F, y + 0.355F, 0.14F, highlight, 12);
                box(out, t, x - 0.135F, y + 0.35F, z - 0.135F,
                        x - 0.055F, y + 0.425F, z - 0.055F, trim);
                box(out, t, x + 0.055F, y + 0.35F, z - 0.135F,
                        x + 0.135F, y + 0.425F, z - 0.055F, trim);
                box(out, t, x - 0.135F, y + 0.35F, z + 0.055F,
                        x - 0.055F, y + 0.425F, z + 0.135F, trim);
                box(out, t, x + 0.055F, y + 0.35F, z + 0.055F,
                        x + 0.135F, y + 0.425F, z + 0.135F, trim);
            }
            case 'Q' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.37F, 0.064F, body, 12);
                cylinder(out, t, x, z, y + 0.34F, y + 0.39F, 0.145F, trim, 16);
                sphere(out, t, x, y + 0.435F, z, 0.068F, highlight, 10, 6);
                for (int i = 0; i < 5; i++) {
                    double a = Math.PI * 2.0D * i / 5.0D;
                    float px = x + (float) Math.cos(a) * 0.112F;
                    float pz = z + (float) Math.sin(a) * 0.112F;
                    sphere(out, t, px, y + 0.425F, pz, 0.043F, highlight, 8, 5);
                    cone(out, t, px, pz, y + 0.445F, y + 0.515F, 0.026F, trim, 7);
                }
            }
            case 'K' -> {
                cylinder(out, t, x, z, y + 0.105F, y + 0.405F, 0.071F, body, 12);
                cylinder(out, t, x, z, y + 0.37F, y + 0.425F, 0.13F, trim, 16);
                sphere(out, t, x, y + 0.44F, z, 0.071F, highlight, 10, 6);
                box(out, t, x - 0.027F, y + 0.47F, z - 0.025F,
                        x + 0.027F, y + 0.61F, z + 0.025F, highlight);
                box(out, t, x - 0.085F, y + 0.515F, z - 0.025F,
                        x + 0.085F, y + 0.565F, z + 0.025F, trim);
            }
            default -> { }
        }
    }

    private static void box(VertexConsumer out, BoardTransform t, double x0, double y0, double z0,
                            double x1, double y1, double z1, int color) {
        // Top and bottom.
        quad(out, t, color, 0, 1, 0,
                x0,y1,z0, x0,y1,z1, x1,y1,z1, x1,y1,z0);
        quad(out, t, color, 0,-1, 0,
                x0,y0,z0, x1,y0,z0, x1,y0,z1, x0,y0,z1);
        // Four vertical faces.
        quad(out, t, color, 0,0,-1,
                x0,y0,z0, x0,y1,z0, x1,y1,z0, x1,y0,z0);
        quad(out, t, color, 0,0,1,
                x0,y0,z1, x1,y0,z1, x1,y1,z1, x0,y1,z1);
        quad(out, t, color, -1,0,0,
                x0,y0,z0, x0,y0,z1, x0,y1,z1, x0,y1,z0);
        quad(out, t, color, 1,0,0,
                x1,y0,z0, x1,y1,z0, x1,y1,z1, x1,y0,z1);
    }

    private static void cylinder(VertexConsumer out, BoardTransform t, double x, double z,
                                 double y0, double y1, double radius, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = x + Math.cos(a0) * radius;
            double z0 = z + Math.sin(a0) * radius;
            double x1 = x + Math.cos(a1) * radius;
            double z1 = z + Math.sin(a1) * radius;
            double mid = (a0 + a1) * 0.5D;
            quad(out, t, color, Math.cos(mid),0,Math.sin(mid),
                    x0,y0,z0, x0,y1,z0, x1,y1,z1, x1,y0,z1);
            triangle(out, t, color, 0,1,0, x,y1,z, x1,y1,z1, x0,y1,z0);
            triangle(out, t, color, 0,-1,0, x,y0,z, x0,y0,z0, x1,y0,z1);
        }
    }

    private static void cone(VertexConsumer out, BoardTransform t, double x, double z,
                             double y0, double y1, double radius, int color, int segments) {
        for (int i = 0; i < segments; i++) {
            double a0 = Math.PI * 2.0D * i / segments;
            double a1 = Math.PI * 2.0D * (i + 1) / segments;
            double x0 = x + Math.cos(a0) * radius;
            double z0 = z + Math.sin(a0) * radius;
            double x1 = x + Math.cos(a1) * radius;
            double z1 = z + Math.sin(a1) * radius;
            double mid = (a0 + a1) * 0.5D;
            quad(out, t, color, Math.cos(mid), radius / Math.max(0.001D, y1-y0), Math.sin(mid),
                    x0,y0,z0, x,y1,z, x,y1,z, x1,y0,z1);
            triangle(out, t, color, 0,-1,0, x,y0,z, x1,y0,z1, x0,y0,z0);
        }
    }

    private static void sphere(VertexConsumer out, BoardTransform t, double x, double y, double z,
                               double radius, int color, int longitude, int latitude) {
        for (int lat = 0; lat < latitude; lat++) {
            double p0 = Math.PI * lat / latitude;
            double p1 = Math.PI * (lat + 1) / latitude;
            for (int lon = 0; lon < longitude; lon++) {
                double a0 = Math.PI * 2.0D * lon / longitude;
                double a1 = Math.PI * 2.0D * (lon + 1) / longitude;
                double x00 = x + radius * Math.sin(p0) * Math.cos(a0);
                double z00 = z + radius * Math.sin(p0) * Math.sin(a0);
                double y00 = y + radius * Math.cos(p0);
                double x01 = x + radius * Math.sin(p0) * Math.cos(a1);
                double z01 = z + radius * Math.sin(p0) * Math.sin(a1);
                double y01 = y + radius * Math.cos(p0);
                double x10 = x + radius * Math.sin(p1) * Math.cos(a0);
                double z10 = z + radius * Math.sin(p1) * Math.sin(a0);
                double y10 = y + radius * Math.cos(p1);
                double x11 = x + radius * Math.sin(p1) * Math.cos(a1);
                double z11 = z + radius * Math.sin(p1) * Math.sin(a1);
                double y11 = y + radius * Math.cos(p1);
                double nx = Math.sin((p0+p1)*0.5D) * Math.cos((a0+a1)*0.5D);
                double ny = Math.cos((p0+p1)*0.5D);
                double nz = Math.sin((p0+p1)*0.5D) * Math.sin((a0+a1)*0.5D);
                quad(out, t, color, nx,ny,nz,
                        x00,y00,z00, x10,y10,z10, x11,y11,z11, x01,y01,z01);
            }
        }
    }

    private static void triangle(VertexConsumer out, BoardTransform t, int color,
                                 double nx, double ny, double nz,
                                 double ax, double ay, double az,
                                 double bx, double by, double bz,
                                 double cx, double cy, double cz) {
        quad(out, t, color, nx,ny,nz, ax,ay,az, bx,by,bz, cx,cy,cz, cx,cy,cz);
    }

    private static void quad(VertexConsumer out, BoardTransform t, int color,
                             double nx, double ny, double nz,
                             double ax, double ay, double az,
                             double bx, double by, double bz,
                             double cx, double cy, double cz,
                             double dx, double dy, double dz) {
        Vec3 normal = t.normal(nx,ny,nz);
        emitVertex(out, t.world(ax,ay,az), color, normal);
        emitVertex(out, t.world(bx,by,bz), color, normal);
        emitVertex(out, t.world(cx,cy,cz), color, normal);
        emitVertex(out, t.world(dx,dy,dz), color, normal);
    }

    private static void emitVertex(VertexConsumer out, Vec3 point, int color, Vec3 normal) {
        // The chess solid pipeline uses POSITION_COLOR (unlit geometry),
        // so no normal attribute is part of this vertex format.
        out.addVertex((float) point.x, (float) point.y, (float) point.z)
                .setColor(color);
    }

    private static void emitLine(VertexConsumer out, Vec3 a, Vec3 b,
                                 Vec3 camera, int color, float width) {
        out.addVertex((float) a.x, (float) a.y, (float) a.z)
                .setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(width);
        out.addVertex((float) b.x, (float) b.y, (float) b.z)
                .setColor(color).setNormal(0.0F, 1.0F, 0.0F).setLineWidth(width);
    }

    private static final class BoardTransform {
        private final Vec3 center;
        private final Vec3 right;
        private final Vec3 forward;
        private final float scale;
        private final double lift;

        private BoardTransform(Vec3 center, Vec3 right, Vec3 forward, float scale, double lift) {
            this.center = center;
            this.right = right;
            this.forward = forward;
            this.scale = scale;
            this.lift = lift;
        }

        private Vec3 world(double x, double y, double z) {
            return center.add(right.scale(x * scale))
                    .add(0.0D, y * scale + lift, 0.0D)
                    .add(forward.scale(z * scale));
        }

        private Vec3 normal(double x, double y, double z) {
            return right.scale(x).add(0.0D, y, 0.0D).add(forward.scale(z)).normalize();
        }
    }

    private static final class MoveAnimation {
        private final int from;
        private final int to;
        private final char piece;
        private int ticks;

        private MoveAnimation(int from, int to, char piece, int ticks) {
            this.from = from;
            this.to = to;
            this.piece = piece;
            this.ticks = ticks;
        }
    }
}
