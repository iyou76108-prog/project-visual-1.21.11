package dev.projectvisual.client.chess;

/** A chess move using 0..63 squares (a8 = 0, h1 = 63). */
public record ChessMove(int from, int to, char promotion) {
    public ChessMove {
        if (from < 0 || from >= 64 || to < 0 || to >= 64 || from == to) {
            throw new IllegalArgumentException("Invalid chess move");
        }
        if (promotion != 0 && "QRBNqrbn".indexOf(promotion) < 0) {
            throw new IllegalArgumentException("Invalid promotion piece");
        }
    }

    public ChessMove(int from, int to) {
        this(from, to, (char) 0);
    }

    public String toUci() {
        String value = squareName(from) + squareName(to);
        return promotion == 0 ? value : value + Character.toLowerCase(promotion);
    }

    public static String squareName(int square) {
        return "" + (char) ('a' + square % 8) + (char) ('8' - square / 8);
    }
}
