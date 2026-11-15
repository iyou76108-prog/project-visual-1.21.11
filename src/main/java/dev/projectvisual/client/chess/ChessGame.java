package dev.projectvisual.client.chess;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Self-contained chess rules engine.
 *
 * Board indexing: a8 = 0, h8 = 7, a1 = 56, h1 = 63.
 * Uppercase pieces are White; lowercase pieces are Black.
 */
public final class ChessGame {
    private static final int WHITE_KING_SIDE = 1;
    private static final int WHITE_QUEEN_SIDE = 2;
    private static final int BLACK_KING_SIDE = 4;
    private static final int BLACK_QUEEN_SIDE = 8;

    private char[] board = new char[64];
    private boolean whiteToMove = true;
    private int castlingRights = WHITE_KING_SIDE | WHITE_QUEEN_SIDE
            | BLACK_KING_SIDE | BLACK_QUEEN_SIDE;
    private int enPassantSquare = -1;
    private int halfmoveClock;
    private int fullmoveNumber = 1;
    private final Deque<State> undoStack = new ArrayDeque<>();
    private final List<String> positionHistory = new ArrayList<>();

    public ChessGame() {
        String backRank = "rnbqkbnr";
        for (int file = 0; file < 8; file++) {
            board[file] = backRank.charAt(file);
            board[8 + file] = 'p';
            board[48 + file] = 'P';
            board[56 + file] = Character.toUpperCase(backRank.charAt(file));
        }
        positionHistory.add(positionKey());
    }

    private ChessGame(ChessGame source) {
        board = source.board.clone();
        whiteToMove = source.whiteToMove;
        castlingRights = source.castlingRights;
        enPassantSquare = source.enPassantSquare;
        halfmoveClock = source.halfmoveClock;
        fullmoveNumber = source.fullmoveNumber;
        for (State state : source.undoStack) undoStack.addLast(state.copy());
        positionHistory.addAll(source.positionHistory);
    }

    public ChessGame copy() {
        return new ChessGame(this);
    }

    public char pieceAt(int square) {
        return square < 0 || square >= 64 ? 0 : board[square];
    }

    public boolean isWhiteToMove() {
        return whiteToMove;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    public List<ChessMove> legalMoves() {
        boolean movingSide = whiteToMove;
        List<ChessMove> legal = new ArrayList<>();
        for (ChessMove move : pseudoLegalMoves()) {
            ChessGame test = copyPositionOnly();
            test.applyUnchecked(move);
            if (!test.isInCheck(movingSide)) legal.add(move);
        }
        return legal;
    }

    public List<ChessMove> legalMovesFrom(int square) {
        List<ChessMove> result = new ArrayList<>();
        for (ChessMove move : legalMoves()) {
            if (move.from() == square) result.add(move);
        }
        return result;
    }

    public boolean play(ChessMove requested) {
        if (requested == null) return false;
        for (ChessMove legal : legalMoves()) {
            if (legal.from() == requested.from()
                    && legal.to() == requested.to()
                    && Character.toUpperCase(legal.promotion())
                    == Character.toUpperCase(requested.promotion())) {
                undoStack.push(snapshot());
                applyUnchecked(legal);
                positionHistory.add(positionKey());
                return true;
            }
        }
        return false;
    }

    public boolean undo() {
        if (undoStack.isEmpty()) return false;
        if (!positionHistory.isEmpty()) positionHistory.remove(positionHistory.size() - 1);
        restore(undoStack.pop());
        return true;
    }

    public boolean canUndo() {
        return !undoStack.isEmpty();
    }

    public boolean isInCheck(boolean white) {
        int king = findKing(white);
        return king < 0 || isSquareAttacked(king, !white);
    }

    public boolean isInCheck() {
        return isInCheck(whiteToMove);
    }

    public boolean isAutomaticDraw() {
        if (halfmoveClock >= 100 || isInsufficientMaterial()) return true;
        Map<String, Integer> counts = new HashMap<>();
        for (String key : positionHistory) {
            int count = counts.merge(key, 1, Integer::sum);
            if (count >= 3) return true;
        }
        return false;
    }

    public String statusText() {
        List<ChessMove> moves = legalMoves();
        if (moves.isEmpty()) {
            if (isInCheck()) return (whiteToMove ? "Black" : "White") + " wins by checkmate";
            return "Draw by stalemate";
        }
        if (halfmoveClock >= 100) return "Draw by the 50-move rule";
        if (isThreefoldRepetition()) return "Draw by threefold repetition";
        if (isInsufficientMaterial()) return "Draw: insufficient material";
        if (isInCheck()) return (whiteToMove ? "White" : "Black") + " is in check";
        return (whiteToMove ? "White" : "Black") + " to move";
    }

    public String fen() {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                char piece = board[row * 8 + col];
                if (piece == 0) empty++;
                else {
                    if (empty > 0) {
                        out.append(empty);
                        empty = 0;
                    }
                    out.append(piece);
                }
            }
            if (empty > 0) out.append(empty);
            if (row != 7) out.append('/');
        }
        out.append(whiteToMove ? " w " : " b ");
        if (castlingRights == 0) out.append('-');
        else {
            if ((castlingRights & WHITE_KING_SIDE) != 0) out.append('K');
            if ((castlingRights & WHITE_QUEEN_SIDE) != 0) out.append('Q');
            if ((castlingRights & BLACK_KING_SIDE) != 0) out.append('k');
            if ((castlingRights & BLACK_QUEEN_SIDE) != 0) out.append('q');
        }
        out.append(' ');
        out.append(enPassantSquare < 0 ? "-" : ChessMove.squareName(enPassantSquare));
        out.append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return out.toString();
    }

    private ChessGame copyPositionOnly() {
        ChessGame copy = new ChessGame();
        copy.board = board.clone();
        copy.whiteToMove = whiteToMove;
        copy.castlingRights = castlingRights;
        copy.enPassantSquare = enPassantSquare;
        copy.halfmoveClock = halfmoveClock;
        copy.fullmoveNumber = fullmoveNumber;
        copy.undoStack.clear();
        copy.positionHistory.clear();
        copy.positionHistory.add(positionKey());
        return copy;
    }

    private List<ChessMove> pseudoLegalMoves() {
        List<ChessMove> moves = new ArrayList<>(48);
        for (int from = 0; from < 64; from++) {
            char piece = board[from];
            if (piece == 0 || isWhite(piece) != whiteToMove) continue;
            switch (Character.toLowerCase(piece)) {
                case 'p' -> addPawnMoves(moves, from, piece);
                case 'n' -> addKnightMoves(moves, from);
                case 'b' -> addSlidingMoves(moves, from,
                        new int[][]{{-1,-1},{-1,1},{1,-1},{1,1}});
                case 'r' -> addSlidingMoves(moves, from,
                        new int[][]{{-1,0},{1,0},{0,-1},{0,1}});
                case 'q' -> addSlidingMoves(moves, from,
                        new int[][]{{-1,-1},{-1,1},{1,-1},{1,1},
                                {-1,0},{1,0},{0,-1},{0,1}});
                case 'k' -> addKingMoves(moves, from);
                default -> { }
            }
        }
        return moves;
    }

    private void addPawnMoves(List<ChessMove> moves, int from, char pawn) {
        boolean white = isWhite(pawn);
        int row = from / 8, col = from % 8;
        int direction = white ? -1 : 1;
        int nextRow = row + direction;
        if (inside(nextRow, col) && board[nextRow * 8 + col] == 0) {
            addPawnDestination(moves, from, nextRow * 8 + col, nextRow == (white ? 0 : 7));
            int startRow = white ? 6 : 1;
            int twoRow = row + direction * 2;
            if (row == startRow && board[twoRow * 8 + col] == 0) {
                moves.add(new ChessMove(from, twoRow * 8 + col));
            }
        }
        for (int dc : new int[]{-1, 1}) {
            int targetCol = col + dc;
            if (!inside(nextRow, targetCol)) continue;
            int to = nextRow * 8 + targetCol;
            char target = board[to];
            if (target != 0 && isWhite(target) != white
                    && Character.toLowerCase(target) != 'k') {
                addPawnDestination(moves, from, to, nextRow == (white ? 0 : 7));
            } else if (to == enPassantSquare) {
                int captured = to + (white ? 8 : -8);
                char capturedPiece = board[captured];
                if (Character.toLowerCase(capturedPiece) == 'p'
                        && isWhite(capturedPiece) != white) {
                    moves.add(new ChessMove(from, to));
                }
            }
        }
    }

    private void addPawnDestination(List<ChessMove> moves, int from, int to, boolean promotion) {
        if (!promotion) {
            moves.add(new ChessMove(from, to));
            return;
        }
        moves.add(new ChessMove(from, to, 'Q'));
        moves.add(new ChessMove(from, to, 'R'));
        moves.add(new ChessMove(from, to, 'B'));
        moves.add(new ChessMove(from, to, 'N'));
    }

    private void addKnightMoves(List<ChessMove> moves, int from) {
        int row = from / 8, col = from % 8;
        int[][] jumps = {{-2,-1},{-2,1},{-1,-2},{-1,2},
                {1,-2},{1,2},{2,-1},{2,1}};
        for (int[] jump : jumps) {
            int r = row + jump[0], c = col + jump[1];
            if (inside(r, c)) addIfAvailable(moves, from, r * 8 + c);
        }
    }

    private void addSlidingMoves(List<ChessMove> moves, int from, int[][] directions) {
        int row = from / 8, col = from % 8;
        for (int[] direction : directions) {
            int r = row + direction[0], c = col + direction[1];
            while (inside(r, c)) {
                int to = r * 8 + c;
                char target = board[to];
                if (target == 0) moves.add(new ChessMove(from, to));
                else {
                    if (isWhite(target) != whiteToMove
                            && Character.toLowerCase(target) != 'k') {
                        moves.add(new ChessMove(from, to));
                    }
                    break;
                }
                r += direction[0];
                c += direction[1];
            }
        }
    }

    private void addKingMoves(List<ChessMove> moves, int from) {
        int row = from / 8, col = from % 8;
        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (inside(r, c)) addIfAvailable(moves, from, r * 8 + c);
            }
        }

        if (whiteToMove && from == 60 && !isSquareAttacked(60, false)) {
            if ((castlingRights & WHITE_KING_SIDE) != 0 && board[63] == 'R'
                    && board[61] == 0 && board[62] == 0
                    && !isSquareAttacked(61, false) && !isSquareAttacked(62, false)) {
                moves.add(new ChessMove(60, 62));
            }
            if ((castlingRights & WHITE_QUEEN_SIDE) != 0 && board[56] == 'R'
                    && board[59] == 0 && board[58] == 0 && board[57] == 0
                    && !isSquareAttacked(59, false) && !isSquareAttacked(58, false)) {
                moves.add(new ChessMove(60, 58));
            }
        } else if (!whiteToMove && from == 4 && !isSquareAttacked(4, true)) {
            if ((castlingRights & BLACK_KING_SIDE) != 0 && board[7] == 'r'
                    && board[5] == 0 && board[6] == 0
                    && !isSquareAttacked(5, true) && !isSquareAttacked(6, true)) {
                moves.add(new ChessMove(4, 6));
            }
            if ((castlingRights & BLACK_QUEEN_SIDE) != 0 && board[0] == 'r'
                    && board[3] == 0 && board[2] == 0 && board[1] == 0
                    && !isSquareAttacked(3, true) && !isSquareAttacked(2, true)) {
                moves.add(new ChessMove(4, 2));
            }
        }
    }

    private void addIfAvailable(List<ChessMove> moves, int from, int to) {
        char target = board[to];
        if (target == 0 || (isWhite(target) != whiteToMove
                && Character.toLowerCase(target) != 'k')) {
            moves.add(new ChessMove(from, to));
        }
    }

    private boolean isSquareAttacked(int square, boolean byWhite) {
        int row = square / 8, col = square % 8;

        int pawnSourceRow = row + (byWhite ? 1 : -1);
        for (int dc : new int[]{-1, 1}) {
            int sourceCol = col + dc;
            if (inside(pawnSourceRow, sourceCol)) {
                char p = board[pawnSourceRow * 8 + sourceCol];
                if (p == (byWhite ? 'P' : 'p')) return true;
            }
        }

        int[][] knightJumps = {{-2,-1},{-2,1},{-1,-2},{-1,2},
                {1,-2},{1,2},{2,-1},{2,1}};
        for (int[] jump : knightJumps) {
            int r = row + jump[0], c = col + jump[1];
            if (inside(r, c) && board[r * 8 + c] == (byWhite ? 'N' : 'n')) return true;
        }

        for (int dr = -1; dr <= 1; dr++) {
            for (int dc = -1; dc <= 1; dc++) {
                if (dr == 0 && dc == 0) continue;
                int r = row + dr, c = col + dc;
                if (inside(r, c) && board[r * 8 + c] == (byWhite ? 'K' : 'k')) return true;
            }
        }

        int[][] directions = {{-1,0},{1,0},{0,-1},{0,1},
                {-1,-1},{-1,1},{1,-1},{1,1}};
        for (int i = 0; i < directions.length; i++) {
            int dr = directions[i][0], dc = directions[i][1];
            int r = row + dr, c = col + dc;
            while (inside(r, c)) {
                char p = board[r * 8 + c];
                if (p != 0) {
                    if (isWhite(p) == byWhite) {
                        char kind = Character.toLowerCase(p);
                        if (kind == 'q' || (i < 4 && kind == 'r')
                                || (i >= 4 && kind == 'b')) return true;
                    }
                    break;
                }
                r += dr;
                c += dc;
            }
        }
        return false;
    }

    private int findKing(boolean white) {
        char king = white ? 'K' : 'k';
        for (int i = 0; i < 64; i++) if (board[i] == king) return i;
        return -1;
    }

    private void applyUnchecked(ChessMove move) {
        int from = move.from(), to = move.to();
        char moving = board[from];
        boolean movingWhite = isWhite(moving);
        char captured = board[to];
        int capturedSquare = to;

        if (Character.toLowerCase(moving) == 'p' && to == enPassantSquare
                && captured == 0 && from % 8 != to % 8) {
            capturedSquare = to + (movingWhite ? 8 : -8);
            captured = board[capturedSquare];
            board[capturedSquare] = 0;
        }

        updateCastlingRights(moving, from, captured, capturedSquare);

        board[from] = 0;
        char placed = moving;
        if (move.promotion() != 0) {
            placed = movingWhite
                    ? Character.toUpperCase(move.promotion())
                    : Character.toLowerCase(move.promotion());
        }
        board[to] = placed;

        if (Character.toLowerCase(moving) == 'k' && Math.abs(to - from) == 2) {
            if (to > from) {
                board[from + 1] = board[from + 3];
                board[from + 3] = 0;
            } else {
                board[from - 1] = board[from - 4];
                board[from - 4] = 0;
            }
        }

        enPassantSquare = -1;
        if (Character.toLowerCase(moving) == 'p' && Math.abs(to - from) == 16) {
            enPassantSquare = (from + to) / 2;
        }

        if (Character.toLowerCase(moving) == 'p' || captured != 0) halfmoveClock = 0;
        else halfmoveClock++;
        if (!whiteToMove) fullmoveNumber++;
        whiteToMove = !whiteToMove;
    }

    private void updateCastlingRights(char moving, int from, char captured, int capturedSquare) {
        switch (moving) {
            case 'K' -> castlingRights &= ~(WHITE_KING_SIDE | WHITE_QUEEN_SIDE);
            case 'k' -> castlingRights &= ~(BLACK_KING_SIDE | BLACK_QUEEN_SIDE);
            case 'R' -> {
                if (from == 63) castlingRights &= ~WHITE_KING_SIDE;
                if (from == 56) castlingRights &= ~WHITE_QUEEN_SIDE;
            }
            case 'r' -> {
                if (from == 7) castlingRights &= ~BLACK_KING_SIDE;
                if (from == 0) castlingRights &= ~BLACK_QUEEN_SIDE;
            }
            default -> { }
        }
        if (captured == 'R') {
            if (capturedSquare == 63) castlingRights &= ~WHITE_KING_SIDE;
            if (capturedSquare == 56) castlingRights &= ~WHITE_QUEEN_SIDE;
        } else if (captured == 'r') {
            if (capturedSquare == 7) castlingRights &= ~BLACK_KING_SIDE;
            if (capturedSquare == 0) castlingRights &= ~BLACK_QUEEN_SIDE;
        }
    }

    private boolean isThreefoldRepetition() {
        String current = positionKey();
        int occurrences = 0;
        for (String key : positionHistory) if (key.equals(current)) occurrences++;
        return occurrences >= 3;
    }

    private boolean isInsufficientMaterial() {
        int minors = 0;
        int knights = 0;
        int bishops = 0;
        int bishopSquareColor = -1;
        boolean bishopsOnSameColor = true;
        boolean whiteHasKnight = false, blackHasKnight = false;
        for (int square = 0; square < 64; square++) {
            char p = board[square];
            if (p == 0 || Character.toLowerCase(p) == 'k') continue;
            char kind = Character.toLowerCase(p);
            if (kind == 'p' || kind == 'r' || kind == 'q') return false;
            if (kind == 'n') {
                knights++;
                if (isWhite(p)) whiteHasKnight = true;
                else blackHasKnight = true;
            } else if (kind == 'b') {
                bishops++;
                int color = ((square / 8) + (square % 8)) & 1;
                if (bishopSquareColor < 0) bishopSquareColor = color;
                else if (bishopSquareColor != color) bishopsOnSameColor = false;
            }
            minors++;
        }
        if (minors <= 1) return true;
        if (knights == 0 && bishops == minors && bishopsOnSameColor) return true;
        return minors == 2 && knights == 2 && bishops == 0
                && (whiteHasKnight ^ blackHasKnight);
    }

    private String positionKey() {
        StringBuilder key = new StringBuilder(70);
        key.append(board).append(whiteToMove ? 'w' : 'b')
                .append(castlingRights).append(':');
        key.append(enPassantSquare < 0 ? "-" : enPassantSquare);
        return key.toString();
    }

    private State snapshot() {
        return new State(board.clone(), whiteToMove, castlingRights, enPassantSquare,
                halfmoveClock, fullmoveNumber);
    }

    private void restore(State state) {
        board = state.board.clone();
        whiteToMove = state.whiteToMove;
        castlingRights = state.castlingRights;
        enPassantSquare = state.enPassantSquare;
        halfmoveClock = state.halfmoveClock;
        fullmoveNumber = state.fullmoveNumber;
    }

    private static boolean inside(int row, int col) {
        return row >= 0 && row < 8 && col >= 0 && col < 8;
    }

    private static boolean isWhite(char piece) {
        return Character.isUpperCase(piece);
    }

    private static final class State {
        private final char[] board;
        private final boolean whiteToMove;
        private final int castlingRights;
        private final int enPassantSquare;
        private final int halfmoveClock;
        private final int fullmoveNumber;

        private State(char[] board, boolean whiteToMove, int castlingRights,
                      int enPassantSquare, int halfmoveClock, int fullmoveNumber) {
            this.board = board;
            this.whiteToMove = whiteToMove;
            this.castlingRights = castlingRights;
            this.enPassantSquare = enPassantSquare;
            this.halfmoveClock = halfmoveClock;
            this.fullmoveNumber = fullmoveNumber;
        }

        private State copy() {
            return new State(board.clone(), whiteToMove, castlingRights,
                    enPassantSquare, halfmoveClock, fullmoveNumber);
        }
    }
}
