package dev.projectvisual.client.chess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** A lightweight playable chess screen with an asynchronous medium-strength AI. */
public final class ChessScreen extends Screen {
    private static final int PANEL = 0xF0141824;
    private static final int LIGHT = 0xFFE7D8BC;
    private static final int DARK = 0xFF80634D;
    private static final int ACCENT = 0xFF9C7CF5;
    private static final int TEXT = 0xFFF4F0FA;
    private static final int MUTED = 0xFFB4AEC4;
    private static final String[] SYMBOLS = {"♔","♕","♖","♗","♘","♙","♚","♛","♜","♝","♞","♟"};

    private ChessGame game = new ChessGame();
    private int selectedSquare = -1;
    private List<ChessMove> selectedMoves = List.of();
    private int lastFrom = -1;
    private int lastTo = -1;
    private boolean againstAi = true;
    private CompletableFuture<ChessMove> aiFuture;
    private String notice = "You play White";

    public ChessScreen() {
        super(Component.literal("PROJECT VISUAL — Chess"));
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        updateAi();
        graphics.fill(0, 0, width, height, 0xF0090B12);
        int tile = Math.max(25, Math.min(52, Math.min((height - 106) / 8, (width - 270) / 8)));
        int boardSize = tile * 8;
        int boardX = Math.max(12, (width - boardSize - 230) / 2);
        int boardY = Math.max(54, (height - boardSize) / 2 + 10);
        int panelX = boardX + boardSize + 20;

        graphics.drawCenteredString(font, "PROJECT VISUAL  /  CHESS", width / 2, 18, TEXT);
        graphics.fill(boardX - 5, boardY - 5, boardX + boardSize + 5,
                boardY + boardSize + 5, 0xFF342A26);

        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 8; col++) {
                int square = row * 8 + col;
                int x = boardX + col * tile;
                int y = boardY + row * tile;
                int color = ((row + col) & 1) == 0 ? LIGHT : DARK;
                if (square == lastFrom || square == lastTo) color = blend(color, 0xFFB9A4E8, 0.42f);
                if (square == selectedSquare) color = blend(color, ACCENT, 0.62f);
                graphics.fill(x, y, x + tile, y + tile, color);

                if (row == 7) graphics.drawString(font, String.valueOf((char) ('a' + col)),
                        x + tile - 8, y + tile - 10, (col % 2 == 0) ? DARK : LIGHT, false);
                if (col == 0) graphics.drawString(font, String.valueOf(8 - row),
                        x + 3, y + 2, (row % 2 == 0) ? DARK : LIGHT, false);

                char piece = game.pieceAt(square);
                if (piece != 0) {
                    String symbol = symbol(piece);
                    int pieceColor = Character.isUpperCase(piece) ? 0xFFFFFDF5 : 0xFF24212B;
                    graphics.drawCenteredString(font, symbol, x + tile / 2,
                            y + Math.max(2, tile / 2 - 5), pieceColor);
                }
            }
        }

        for (ChessMove move : selectedMoves) {
            if (move.from() != selectedSquare) continue;
            int x = boardX + (move.to() % 8) * tile;
            int y = boardY + (move.to() / 8) * tile;
            boolean capture = game.pieceAt(move.to()) != 0;
            int center = x + tile / 2;
            int cy = y + tile / 2;
            int radius = Math.max(3, tile / 9);
            graphics.fill(center - radius, cy - radius, center + radius,
                    cy + radius, capture ? 0xA0E85D69 : 0x88433A4F);
        }

        graphics.fill(panelX, boardY, panelX + 210, boardY + boardSize, PANEL);
        graphics.drawString(font, "MATCH", panelX + 12, boardY + 12, ACCENT, false);
        graphics.drawString(font, againstAi ? "You  vs  Computer" : "Local 2 players",
                panelX + 12, boardY + 34, TEXT, false);
        graphics.drawString(font, aiFuture != null ? "Computer is thinking..." : game.statusText(),
                panelX + 12, boardY + 61, aiFuture != null ? ACCENT : MUTED, false);
        graphics.drawString(font, notice, panelX + 12, boardY + 83, MUTED, false);
        graphics.drawString(font, "FEN", panelX + 12, boardY + 116, ACCENT, false);
        String fen = game.fen();
        graphics.drawWordWrap(font, Component.literal(fen), panelX + 12,
                boardY + 132, 184, MUTED);

        int buttonY = boardY + boardSize - 126;
        drawButton(graphics, panelX + 10, buttonY, 190, 28, "New game", mouseX, mouseY);
        drawButton(graphics, panelX + 10, buttonY + 36, 190, 28, "Undo move", mouseX, mouseY);
        drawButton(graphics, panelX + 10, buttonY + 72, 190, 28,
                againstAi ? "Mode: vs computer" : "Mode: 2 players", mouseX, mouseY);
        graphics.drawCenteredString(font, "ESC  ·  Back", panelX + 105,
                boardY + boardSize - 10, MUTED);

        super.render(graphics, mouseX, mouseY, partialTick);
    }

    private void updateAi() {
        if (aiFuture == null || !aiFuture.isDone()) return;
        try {
            ChessMove move = aiFuture.join();
            if (move != null && game.isAutomaticDraw() == false) {
                if (game.play(move)) {
                    lastFrom = move.from();
                    lastTo = move.to();
                    notice = "Computer played " + move.toUci();
                }
            }
        } catch (RuntimeException ignored) {
            notice = "Computer move failed. Start a new game.";
        } finally {
            aiFuture = null;
            selectedSquare = -1;
            selectedMoves = List.of();
        }
    }

    private void startAiIfNeeded() {
        if (!againstAi || game.isWhiteToMove() || aiFuture != null
                || game.statusText().contains("wins")
                || game.statusText().startsWith("Draw")) return;
        ChessGame snapshot = game.copy();
        aiFuture = CompletableFuture.supplyAsync(() -> ChessAi.chooseMove(snapshot, 700));
    }

    private void drawButton(GuiGraphics g, int x, int y, int w, int h,
                            String label, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g.fill(x, y, x + w, y + h, hovered ? 0xFF39334C : 0xFF272B39);
        g.fill(x, y, x + 2, y + h, ACCENT);
        g.drawCenteredString(font, label, x + w / 2, y + 10, TEXT);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (aiFuture != null) return true;

        int tile = Math.max(25, Math.min(52, Math.min((height - 106) / 8, (width - 270) / 8)));
        int boardSize = tile * 8;
        int boardX = Math.max(12, (width - boardSize - 230) / 2);
        int boardY = Math.max(54, (height - boardSize) / 2 + 10);
        int panelX = boardX + boardSize + 20;

        int col = (int) ((event.x() - boardX) / tile);
        int row = (int) ((event.y() - boardY) / tile);
        if (event.x() >= boardX && event.x() < boardX + boardSize
                && event.y() >= boardY && event.y() < boardY + boardSize) {
            int square = row * 8 + col;
            if (selectedSquare >= 0) {
                ChessMove chosen = null;
                for (ChessMove move : selectedMoves) {
                    if (move.from() == selectedSquare && move.to() == square) {
                        chosen = move;
                        if (move.promotion() == 'Q') break;
                    }
                }
                if (chosen != null) {
                    if (game.play(chosen)) {
                        lastFrom = chosen.from();
                        lastTo = chosen.to();
                        selectedSquare = -1;
                        selectedMoves = List.of();
                        notice = "Played " + chosen.toUci();
                        startAiIfNeeded();
                    }
                    return true;
                }
            }
            char piece = game.pieceAt(square);
            if (piece != 0 && Character.isUpperCase(piece) == game.isWhiteToMove()) {
                if (againstAi && !game.isWhiteToMove()) return true;
                selectedSquare = square;
                selectedMoves = game.legalMovesFrom(square);
            } else {
                selectedSquare = -1;
                selectedMoves = List.of();
            }
            return true;
        }

        int buttonY = boardY + boardSize - 126;
        if (event.x() >= panelX + 10 && event.x() <= panelX + 200) {
            if (event.y() >= buttonY && event.y() <= buttonY + 28) {
                resetGame();
                return true;
            }
            if (event.y() >= buttonY + 36 && event.y() <= buttonY + 64) {
                undoTurn();
                return true;
            }
            if (event.y() >= buttonY + 72 && event.y() <= buttonY + 100) {
                againstAi = !againstAi;
                resetGame();
                notice = againstAi ? "You play White" : "Two-player mode";
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    private void resetGame() {
        if (aiFuture != null) aiFuture.cancel(true);
        aiFuture = null;
        game = new ChessGame();
        selectedSquare = -1;
        selectedMoves = List.of();
        lastFrom = -1;
        lastTo = -1;
        notice = againstAi ? "You play White" : "Two-player mode";
    }

    private void undoTurn() {
        if (aiFuture != null) {
            aiFuture.cancel(true);
            aiFuture = null;
        }
        if (game.undo() && againstAi && !game.isWhiteToMove()) game.undo();
        selectedSquare = -1;
        selectedMoves = List.of();
        notice = "Move undone";
    }

    private static String symbol(char piece) {
        return switch (piece) {
            case 'K' -> SYMBOLS[0];
            case 'Q' -> SYMBOLS[1];
            case 'R' -> SYMBOLS[2];
            case 'B' -> SYMBOLS[3];
            case 'N' -> SYMBOLS[4];
            case 'P' -> SYMBOLS[5];
            case 'k' -> SYMBOLS[6];
            case 'q' -> SYMBOLS[7];
            case 'r' -> SYMBOLS[8];
            case 'b' -> SYMBOLS[9];
            case 'n' -> SYMBOLS[10];
            case 'p' -> SYMBOLS[11];
            default -> "";
        };
    }

    private static int blend(int first, int second, float amount) {
        int a = Math.round(((first >> 16) & 255) * (1 - amount) + ((second >> 16) & 255) * amount);
        int b = Math.round(((first >> 8) & 255) * (1 - amount) + ((second >> 8) & 255) * amount);
        int c = Math.round((first & 255) * (1 - amount) + (second & 255) * amount);
        return 0xFF000000 | (a << 16) | (b << 8) | c;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        if (aiFuture != null) aiFuture.cancel(true);
        Minecraft.getInstance().setScreen(null);
    }
}
