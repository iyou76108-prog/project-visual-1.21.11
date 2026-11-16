package dev.projectvisual.client.gui;

import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.Arrays;

public final class TicTacToeScreen extends Screen {
    private static final int CELL = 74;
    private static final int GAP = 7;
    private static final int BG = 0xF00D0F18;
    private static final int CARD = 0xFF1D2130;
    private static final int TEXT = 0xFFF3F0FF;
    private static final int MUTED = 0xFFAAA6BA;

    private final char[] board = new char[9];
    private boolean finished;
    private String status = "Твой ход — X";

    public TicTacToeScreen() {
        super(Component.literal("PROJECT VISUAL — Крестики-нолики"));
        resetBoard();
    }

    private void resetBoard() {
        Arrays.fill(board, ' ');
        finished = false;
        status = "Твой ход — X";
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.fill(0, 0, width, height, 0xB9000000);
        int panelW = 410, panelH = 390;
        int px = (width - panelW) / 2, py = (height - panelH) / 2;
        g.fill(px, py, px + panelW, py + panelH, BG);
        g.fill(px, py, px + panelW, py + 3, VisualFeatures.accentColor());
        g.drawCenteredString(font, "КРЕСТИКИ-НОЛИКИ", width / 2, py + 20, TEXT);
        g.drawCenteredString(font, "Ты — X, бот — O", width / 2, py + 43, MUTED);

        int boardSize = CELL * 3 + GAP * 2;
        int bx = width / 2 - boardSize / 2, by = py + 76;
        for (int i = 0; i < 9; i++) {
            int x = bx + (i % 3) * (CELL + GAP);
            int y = by + (i / 3) * (CELL + GAP);
            boolean hover = mouseX >= x && mouseX <= x + CELL && mouseY >= y && mouseY <= y + CELL;
            g.fill(x, y, x + CELL, y + CELL, hover && !finished ? 0xFF292E40 : CARD);
            if (board[i] != ' ') {
                int color = board[i] == 'X' ? 0xFF7DE0C1 : 0xFFFFC66D;
                g.drawCenteredString(font, Character.toString(board[i]), x + CELL / 2, y + 22, color);
            }
        }

        g.drawCenteredString(font, status, width / 2, by + boardSize + 14, finished ? 0xFFFFD58A : TEXT);
        int buttonY = py + panelH - 44;
        int resetX = px + 58, menuX = px + panelW - 168;
        g.fill(resetX, buttonY, resetX + 126, buttonY + 26, 0xFF343A52);
        g.drawCenteredString(font, "ЗАНОВО", resetX + 63, buttonY + 8, TEXT);
        g.fill(menuX, buttonY, menuX + 110, buttonY + 26, 0xFF343A52);
        g.drawCenteredString(font, "В МЕНЮ", menuX + 55, buttonY + 8, TEXT);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() != 0) return super.mouseClicked(event, doubleClick);
        int panelW = 410, panelH = 390;
        int px = (width - panelW) / 2, py = (height - panelH) / 2;
        int buttonY = py + panelH - 44;
        int resetX = px + 58, menuX = px + panelW - 168;
        if (event.y() >= buttonY && event.y() <= buttonY + 26) {
            if (event.x() >= resetX && event.x() <= resetX + 126) {
                resetBoard();
                return true;
            }
            if (event.x() >= menuX && event.x() <= menuX + 110) {
                Minecraft.getInstance().setScreen(new ClickGuiScreen(0, 1));
                return true;
            }
        }
        if (finished) return true;

        int boardSize = CELL * 3 + GAP * 2;
        int bx = width / 2 - boardSize / 2, by = py + 76;
        for (int i = 0; i < 9; i++) {
            int x = bx + (i % 3) * (CELL + GAP);
            int y = by + (i / 3) * (CELL + GAP);
            if (event.x() < x || event.x() > x + CELL || event.y() < y || event.y() > y + CELL) continue;
            if (board[i] != ' ') return true;
            board[i] = 'X';
            if (hasWon(board, 'X')) finish("Ты победил!");
            else if (isDraw(board)) finish("Ничья!");
            else makeBotMove();
            return true;
        }
        return true;
    }

    private void makeBotMove() {
        int bestIndex = -1, bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < board.length; i++) {
            if (board[i] != ' ') continue;
            board[i] = 'O';
            int score = minimax(board, false, 1);
            board[i] = ' ';
            if (score > bestScore) {
                bestScore = score;
                bestIndex = i;
            }
        }
        if (bestIndex >= 0) board[bestIndex] = 'O';
        if (hasWon(board, 'O')) finish("Бот победил!");
        else if (isDraw(board)) finish("Ничья!");
        else status = "Твой ход — X";
    }

    private static int minimax(char[] position, boolean botTurn, int depth) {
        if (hasWon(position, 'O')) return 10 - depth;
        if (hasWon(position, 'X')) return depth - 10;
        if (isDraw(position)) return 0;
        int best = botTurn ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        for (int i = 0; i < position.length; i++) {
            if (position[i] != ' ') continue;
            position[i] = botTurn ? 'O' : 'X';
            int score = minimax(position, !botTurn, depth + 1);
            position[i] = ' ';
            best = botTurn ? Math.max(best, score) : Math.min(best, score);
        }
        return best;
    }

    private static boolean hasWon(char[] cells, char mark) {
        int[][] lines = {
                {0,1,2}, {3,4,5}, {6,7,8},
                {0,3,6}, {1,4,7}, {2,5,8},
                {0,4,8}, {2,4,6}
        };
        for (int[] line : lines) {
            if (cells[line[0]] == mark && cells[line[1]] == mark && cells[line[2]] == mark) return true;
        }
        return false;
    }

    private static boolean isDraw(char[] cells) {
        if (hasWon(cells, 'X') || hasWon(cells, 'O')) return false;
        for (char cell : cells) if (cell == ' ') return false;
        return true;
    }

    private void finish(String message) {
        finished = true;
        status = message;
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(new ClickGuiScreen(0, 1));
    }
}
