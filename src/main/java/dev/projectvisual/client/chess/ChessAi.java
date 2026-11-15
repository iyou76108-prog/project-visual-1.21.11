package dev.projectvisual.client.chess;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/** A compact iterative-deepening alpha-beta chess opponent. */
public final class ChessAi {
    private static final int MATE_SCORE = 100_000;
    private static final int INFINITY = 1_000_000;

    private ChessAi() { }

    public static ChessMove chooseMove(ChessGame position, long timeLimitMillis) {
        List<ChessMove> rootMoves = position.legalMoves();
        if (rootMoves.isEmpty()) return null;

        Context context = new Context(System.nanoTime()
                + Math.max(100, timeLimitMillis) * 1_000_000L);
        ChessMove bestMove = rootMoves.get(0);
        orderMoves(position, rootMoves);

        for (int depth = 1; depth <= 5 && !context.timedOut; depth++) {
            int bestScore = -INFINITY;
            ChessMove depthBest = bestMove;
            boolean completed = true;
            for (ChessMove move : rootMoves) {
                if (context.outOfTime()) {
                    completed = false;
                    break;
                }
                position.play(move);
                int score = -negamax(position, depth - 1, -INFINITY, INFINITY, 1, context);
                position.undo();
                if (context.timedOut) {
                    completed = false;
                    break;
                }
                if (score > bestScore) {
                    bestScore = score;
                    depthBest = move;
                }
            }
            if (completed) bestMove = depthBest;
        }
        return bestMove;
    }

    private static int negamax(ChessGame game, int depth, int alpha, int beta,
                               int ply, Context context) {
        if (context.outOfTime()) return evaluateForSideToMove(game);

        List<ChessMove> moves = game.legalMoves();
        if (moves.isEmpty()) {
            return game.isInCheck() ? -MATE_SCORE + ply : 0;
        }
        if (game.isAutomaticDraw()) return 0;
        if (depth <= 0) return evaluateForSideToMove(game);

        orderMoves(game, moves);
        int best = -INFINITY;
        for (ChessMove move : moves) {
            if (context.outOfTime()) break;
            game.play(move);
            int score = -negamax(game, depth - 1, -beta, -alpha, ply + 1, context);
            game.undo();
            if (score > best) best = score;
            if (score > alpha) alpha = score;
            if (alpha >= beta) break;
        }
        return best == -INFINITY ? evaluateForSideToMove(game) : best;
    }

    private static void orderMoves(ChessGame game, List<ChessMove> moves) {
        moves.sort(Comparator.comparingInt((ChessMove move) -> moveOrderScore(game, move)).reversed());
    }

    private static int moveOrderScore(ChessGame game, ChessMove move) {
        char attacker = game.pieceAt(move.from());
        char victim = game.pieceAt(move.to());
        int score = victim == 0 ? 0 : 10 * value(victim) - value(attacker);
        if (move.promotion() != 0) score += value(move.promotion()) + 800;
        if (Character.toLowerCase(attacker) == 'p'
                && move.to() % 8 != move.from() % 8 && victim == 0) score += 100;
        return score;
    }

    private static int evaluateForSideToMove(ChessGame game) {
        int score = 0;
        for (int square = 0; square < 64; square++) {
            char piece = game.pieceAt(square);
            if (piece == 0) continue;
            score += (Character.isUpperCase(piece) ? 1 : -1) * pieceScore(piece, square);
        }
        return game.isWhiteToMove() ? score : -score;
    }

    private static int pieceScore(char piece, int square) {
        boolean white = Character.isUpperCase(piece);
        char kind = Character.toLowerCase(piece);
        int row = square / 8, col = square % 8;
        double center = 7.0 - Math.abs(3.5 - row) - Math.abs(3.5 - col);
        int base = value(kind);
        return switch (kind) {
            case 'p' -> base + (white ? 6 - row : row - 1) * 8
                    + (int) (center * 2);
            case 'n' -> base + (int) (center * 11);
            case 'b' -> base + (int) (center * 6);
            case 'r' -> base + (white ? (row == 1 ? 18 : 0) : (row == 6 ? 18 : 0));
            case 'q' -> base + (int) (center * 2);
            case 'k' -> base - (int) (center * 5)
                    + ((white ? row : 7 - row) >= 6 ? 12 : 0);
            default -> base;
        };
    }

    private static int value(char piece) {
        return switch (Character.toLowerCase(piece)) {
            case 'p' -> 100;
            case 'n' -> 320;
            case 'b' -> 335;
            case 'r' -> 500;
            case 'q' -> 900;
            case 'k' -> 20_000;
            default -> 0;
        };
    }

    private static final class Context {
        private final long deadline;
        private boolean timedOut;

        private Context(long deadline) {
            this.deadline = deadline;
        }

        private boolean outOfTime() {
            if (System.nanoTime() >= deadline) timedOut = true;
            return timedOut;
        }
    }
}
