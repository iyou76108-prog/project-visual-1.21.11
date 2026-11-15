package dev.projectvisual.client.chess;

import com.google.gson.JsonObject;
import dev.projectvisual.client.gui.FriendsScreen;
import dev.projectvisual.client.network.GlobalServiceClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Online chess board synchronized exclusively from the global service. */
public final class OnlineChessScreen extends Screen {
    private static final int LIGHT = 0xFFE7D8BC;
    private static final int DARK = 0xFF80634D;
    private static final int ACCENT = 0xFF9C7CF5;
    private static final int TEXT = 0xFFF4F0FA;
    private static final int MUTED = 0xFFB4AEC4;
    private static final int PANEL = 0xF0141824;

    private static final String[] SYMBOLS = {
            "♔","♕","♖","♗","♘","♙","♚","♛","♜","♝","♞","♟"
    };

    private final String matchId;
    private final boolean playingWhite;
    private final String opponentName;
    private ChessGame game;
    private int selected = -1;
    private java.util.List<ChessMove> selectedMoves = java.util.List.of();
    private boolean waitingForServer;
    private boolean resigned;
    private boolean gameOver;
    private String message = "Connected to PROJECT VISUAL global chess.";

    public OnlineChessScreen(String matchId, String color, String initialFen, String opponentName) {
        super(Component.literal("PROJECT VISUAL — Online Chess"));
        this.matchId = matchId;
        this.playingWhite = !"black".equalsIgnoreCase(color);
        this.opponentName = opponentName == null ? "Friend" : opponentName;
        this.game = ChessGame.fromFen(initialFen);
        GlobalServiceClient.get().setEventListener(this::handleEvent);
    }

    private void handleEvent(JsonObject event) {
        String type = event.has("type") ? event.get("type").getAsString() : "";
        if (!matchId.equals(event.has("matchId") ? event.get("matchId").getAsString() : "")) return;
        switch (type) {
            case "match_state" -> {
                try {
                    game = ChessGame.fromFen(event.get("fen").getAsString());
                    waitingForServer = false;
                    gameOver = event.has("gameOver") && event.get("gameOver").getAsBoolean();
                    selected = -1;
                    selectedMoves = java.util.List.of();
                    if (gameOver) {
                        boolean draw = event.has("draw") && event.get("draw").getAsBoolean();
                        boolean mate = event.has("checkmate") && event.get("checkmate").getAsBoolean();
                        message = draw ? "Draw." : mate
                                ? (game.isWhiteToMove() ? "Black wins by checkmate." : "White wins by checkmate.")
                                : "Game finished.";
                    } else {
                        message = event.has("check") && event.get("check").getAsBoolean()
                                ? "Check!" : "Position synchronized.";
                    }
                } catch (RuntimeException error) {
                    message = "Received an invalid board position.";
                }
            }
            case "match_resigned" -> {
                resigned = true;
                String winnerId = event.has("winnerId") ? event.get("winnerId").getAsString() : "";
                message = winnerId.isBlank() ? "Opponent resigned." : "The match was resigned.";
            }
            case "error" -> {
                waitingForServer = false;
                message = event.has("error") ? event.get("error").getAsString() : "Server error.";
            }
            default -> { }
        }
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        graphics.fill(0, 0, width, height, 0xF0090B12);
        int tile = Math.max(28, Math.min(62, Math.min((height - 100) / 8, (width - 250) / 8)));
        int boardSize = tile * 8;
        int boardX = Math.max(12, (width - boardSize - 220) / 2);
        int boardY = Math.max(50, (height - boardSize) / 2 + 6);
        int panelX = boardX + boardSize + 18;

        graphics.drawCenteredString(font, "PROJECT VISUAL  /  ONLINE CHESS", width / 2, 18, TEXT);
        graphics.fill(boardX - 4, boardY - 4, boardX + boardSize + 4,
                boardY + boardSize + 4, 0xFF342A26);

        for (int displayRow = 0; displayRow < 8; displayRow++) {
            for (int displayCol = 0; displayCol < 8; displayCol++) {
                int square = toGameSquare(displayRow, displayCol);
                int x = boardX + displayCol * tile;
                int y = boardY + displayRow * tile;
                int color = ((displayRow + displayCol) & 1) == 0 ? LIGHT : DARK;
                if (square == selected) color = blend(color, ACCENT, 0.62f);
                graphics.fill(x, y, x + tile, y + tile, color);

                if (displayRow == 7) {
                    char file = (char) ((playingWhite ? 'a' : 'h') + (playingWhite ? displayCol : -displayCol));
                    graphics.drawString(font, String.valueOf(file), x + tile - 8, y + tile - 10,
                            color == LIGHT ? DARK : LIGHT, false);
                }
                if (displayCol == 0) {
                    int rank = playingWhite ? 8 - displayRow : displayRow + 1;
                    graphics.drawString(font, String.valueOf(rank), x + 3, y + 2,
                            color == LIGHT ? DARK : LIGHT, false);
                }

                char piece = game.pieceAt(square);
                if (piece != 0) {
                    graphics.drawCenteredString(font, symbol(piece), x + tile / 2,
                            y + tile / 2 - 5,
                            Character.isUpperCase(piece) ? 0xFFFFFDF5 : 0xFF24212B);
                }
            }
        }

        for (ChessMove move : selectedMoves) {
            int display = toDisplaySquare(move.to());
            int col = display % 8;
            int row = display / 8;
            int x = boardX + col * tile + tile / 2;
            int y = boardY + row * tile + tile / 2;
            int radius = Math.max(3, tile / 9);
            graphics.fill(x - radius, y - radius, x + radius, y + radius,
                    game.pieceAt(move.to()) == 0 ? 0x88433A4F : 0xA0E85D69);
        }

        graphics.fill(panelX, boardY, panelX + 204, boardY + boardSize, PANEL);
        graphics.drawString(font, "MATCH", panelX + 12, boardY + 12, ACCENT, false);
        graphics.drawString(font, "You: " + (playingWhite ? "White" : "Black"),
                panelX + 12, boardY + 34, TEXT, false);
        graphics.drawString(font, "Opponent: " + opponentName,
                panelX + 12, boardY + 53, TEXT, false);
        graphics.drawString(font, message, panelX + 12, boardY + 80, MUTED, false);
        graphics.drawString(font, waitingForServer ? "WAITING FOR SERVER" : game.statusText(),
                panelX + 12, boardY + 108, waitingForServer ? ACCENT : MUTED, false);

        int resignY = boardY + boardSize - 45;
        boolean hover = mouseX >= panelX + 10 && mouseX <= panelX + 194
                && mouseY >= resignY && mouseY <= resignY + 30;
        graphics.fill(panelX + 10, resignY, panelX + 194, resignY + 30,
                hover ? 0xFF39334C : 0xFF272B39);
        graphics.drawCenteredString(font, "Resign", panelX + 102, resignY + 10, TEXT);
        graphics.drawCenteredString(font, "ESC  ·  Back", width / 2, height - 15, MUTED);
        super.render(graphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        if (waitingForServer || resigned || gameOver || game.statusText().contains("wins")
                || game.statusText().startsWith("Draw")) return true;

        int tile = Math.max(28, Math.min(62, Math.min((height - 100) / 8, (width - 250) / 8)));
        int boardSize = tile * 8;
        int boardX = Math.max(12, (width - boardSize - 220) / 2);
        int boardY = Math.max(50, (height - boardSize) / 2 + 6);
        int panelX = boardX + boardSize + 18;
        int resignY = boardY + boardSize - 45;
        if (event.x() >= panelX + 10 && event.x() <= panelX + 194
                && event.y() >= resignY && event.y() <= resignY + 30) {
            GlobalServiceClient.get().resign(matchId);
            resigned = true;
            message = "You resigned.";
            return true;
        }

        if (event.x() < boardX || event.x() >= boardX + boardSize
                || event.y() < boardY || event.y() >= boardY + boardSize) {
            return super.mouseClicked(event, doubleClick);
        }
        if (game.isWhiteToMove() != playingWhite) {
            message = "Waiting for your opponent's move.";
            return true;
        }
        int displayCol = (int) ((event.x() - boardX) / tile);
        int displayRow = (int) ((event.y() - boardY) / tile);
        int square = toGameSquare(displayRow, displayCol);

        if (selected >= 0) {
            for (ChessMove move : selectedMoves) {
                if (move.to() == square) {
                    if (game.play(move)) {
                        waitingForServer = true;
                        selected = -1;
                        selectedMoves = java.util.List.of();
                        message = "Move sent. Waiting for server confirmation.";
                        GlobalServiceClient.get().sendMove(matchId, move.toUci());
                    }
                    return true;
                }
            }
        }

        char piece = game.pieceAt(square);
        if (piece != 0 && Character.isUpperCase(piece) == playingWhite) {
            selected = square;
            selectedMoves = game.legalMovesFrom(square);
        } else {
            selected = -1;
            selectedMoves = java.util.List.of();
        }
        return true;
    }

    private int toGameSquare(int displayRow, int displayCol) {
        int row = playingWhite ? displayRow : 7 - displayRow;
        int col = playingWhite ? displayCol : 7 - displayCol;
        return row * 8 + col;
    }

    private int toDisplaySquare(int square) {
        int row = square / 8;
        int col = square % 8;
        return playingWhite ? row * 8 + col : (7 - row) * 8 + (7 - col);
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
    public void onClose() {
        GlobalServiceClient.get().setEventListener(null);
        Minecraft.getInstance().setScreen(new FriendsScreen());
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
