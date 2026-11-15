package dev.projectvisual.client.chess;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

final class ChessGameTest {
    private static int square(String name) {
        return ('8' - name.charAt(1)) * 8 + name.charAt(0) - 'a';
    }

    private static ChessMove move(String from, String to) {
        return new ChessMove(square(from), square(to));
    }

    @Test
    void initialPositionHasTwentyLegalMoves() {
        ChessGame game = new ChessGame();
        assertEquals(20, game.legalMoves().size());
        assertTrue(game.isWhiteToMove());
        assertFalse(game.isInCheck());
    }

    @Test
    void castlingMovesTheKingAndRook() {
        ChessGame game = ChessGame.fromFen(
                "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1");
        assertTrue(game.play(move("e1", "g1")));
        assertEquals('K', game.pieceAt(square("g1")));
        assertEquals('R', game.pieceAt(square("f1")));
        assertEquals(0, game.pieceAt(square("h1")));
    }

    @Test
    void enPassantRemovesTheCapturedPawn() {
        ChessGame game = ChessGame.fromFen(
                "4k3/8/8/3pP3/8/8/8/4K3 w - d6 0 1");
        assertTrue(game.play(move("e5", "d6")));
        assertEquals('P', game.pieceAt(square("d6")));
        assertEquals(0, game.pieceAt(square("d5")));
    }

    @Test
    void promotionCanTurnPawnIntoQueen() {
        ChessGame game = ChessGame.fromFen(
                "4k3/P7/8/8/8/8/8/4K3 w - - 0 1");
        assertTrue(game.play(new ChessMove(square("a7"), square("a8"), 'Q')));
        assertEquals('Q', game.pieceAt(square("a8")));
    }

    @Test
    void checkmateAndStalemateAreDistinguished() {
        ChessGame mate = ChessGame.fromFen(
                "7k/6Q1/6K1/8/8/8/8/8 b - - 0 1");
        assertTrue(mate.isInCheck());
        assertTrue(mate.legalMoves().isEmpty());
        assertTrue(mate.statusText().contains("checkmate"));

        ChessGame stalemate = ChessGame.fromFen(
                "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1");
        assertFalse(stalemate.isInCheck());
        assertTrue(stalemate.legalMoves().isEmpty());
        assertTrue(stalemate.statusText().contains("stalemate"));
    }

    @Test
    void fiftyMoveRuleIsDetected() {
        ChessGame game = ChessGame.fromFen(
                "4k3/8/8/8/8/8/8/4K3 w - - 99 1");
        assertTrue(game.play(move("e1", "d1")));
        assertTrue(game.isAutomaticDraw());
        assertTrue(game.statusText().contains("50-move"));
    }

    @Test
    void threefoldRepetitionIsDetected() {
        ChessGame game = new ChessGame();
        for (int repetition = 0; repetition < 2; repetition++) {
            assertTrue(game.play(move("g1", "f3")));
            assertTrue(game.play(move("g8", "f6")));
            assertTrue(game.play(move("f3", "g1")));
            assertTrue(game.play(move("f6", "g8")));
        }
        assertTrue(game.isAutomaticDraw());
    }

    @Test
    void undoRestoresPositionAndTurn() {
        ChessGame game = new ChessGame();
        String initial = game.fen();
        assertTrue(game.play(move("e2", "e4")));
        assertTrue(game.undo());
        assertEquals(initial, game.fen());
        assertTrue(game.isWhiteToMove());
    }
}
