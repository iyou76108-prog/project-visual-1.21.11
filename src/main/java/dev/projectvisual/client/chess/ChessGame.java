package dev.projectvisual.client.chess;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Complete local chess rules. White pieces are uppercase, black pieces lowercase. */
public final class ChessGame {
    public record Move(int from, int to, char promotion) {
        public Move(int from, int to) { this(from, to, 'q'); }
        public String uci() { return squareName(from) + squareName(to) + (promotion == 0 ? "" : Character.toString(promotion)); }
    }

    private final char[] board = new char[64];
    private boolean whiteToMove = true;
    private boolean whiteKingSide = true, whiteQueenSide = true, blackKingSide = true, blackQueenSide = true;
    private int enPassantSquare = -1, halfmoveClock = 0, fullmoveNumber = 1;
    private final Map<String, Integer> repetitions = new HashMap<>();

    public ChessGame() {
        String[] rows = {"rnbqkbnr","pppppppp","........","........","........","........","PPPPPPPP","RNBQKBNR"};
        for (int y=0;y<8;y++) for (int x=0;x<8;x++) {
            char p=rows[y].charAt(x); board[y*8+x]=p=='.'?0:p;
        }
        repetitions.put(positionKey(),1);
    }

    private ChessGame(ChessGame other) {
        System.arraycopy(other.board,0,board,0,64);
        whiteToMove=other.whiteToMove;
        whiteKingSide=other.whiteKingSide; whiteQueenSide=other.whiteQueenSide;
        blackKingSide=other.blackKingSide; blackQueenSide=other.blackQueenSide;
        enPassantSquare=other.enPassantSquare; halfmoveClock=other.halfmoveClock; fullmoveNumber=other.fullmoveNumber;
        repetitions.putAll(other.repetitions);
    }

    public ChessGame copy(){return new ChessGame(this);}
    public boolean whiteToMove(){return whiteToMove;}
    public char pieceAt(int square){return square>=0&&square<64?board[square]:0;}
    public int halfmoveClock(){return halfmoveClock;}
    public static String squareName(int s){return ""+(char)('a'+s%8)+(char)('8'-s/8);}

    public List<Move> legalMoves(){
        List<Move> legal=new ArrayList<>();
        for(Move m:pseudoMoves()){
            ChessGame n=copy(); n.applyUnchecked(m);
            if(!n.isInCheck(whiteToMove)) legal.add(m);
        }
        return legal;
    }

    public boolean play(int from,int to){
        Move chosen=null;
        for(Move m:legalMoves()) if(m.from==from&&m.to==to){
            if(chosen==null||m.promotion=='q')chosen=m;
            if(m.promotion=='q')break;
        }
        if(chosen==null)return false;
        applyUnchecked(chosen);return true;
    }

    public boolean play(Move move){
        if(move==null||!legalMoves().contains(move))return false;
        applyUnchecked(move);return true;
    }

    void applyForSearch(Move move){applyUnchecked(move);}

    public boolean isInCheck(boolean white){
        char king=white?'K':'k'; int sq=-1;
        for(int i=0;i<64;i++)if(board[i]==king){sq=i;break;}
        return sq>=0&&isSquareAttacked(sq,!white);
    }

    public String result(){
        List<Move> moves=legalMoves();
        if(moves.isEmpty()){
            if(isInCheck(whiteToMove))return whiteToMove?"Checkmate — Black wins":"Checkmate — White wins";
            return "Draw — stalemate";
        }
        if(halfmoveClock>=100)return "Draw — 50-move rule";
        if(repetitions.getOrDefault(positionKey(),0)>=3)return "Draw — threefold repetition";
        if(isInsufficientMaterial())return "Draw — insufficient material";
        return null;
    }

    public String positionKey(){
        StringBuilder k=new StringBuilder(80);
        for(char p:board)k.append(p==0?'.':p);
        k.append(whiteToMove?'w':'b');
        k.append(whiteKingSide?'K':'-').append(whiteQueenSide?'Q':'-');
        k.append(blackKingSide?'k':'-').append(blackQueenSide?'q':'-').append(':').append(enPassantSquare);
        return k.toString();
    }

    public String fen(){
        StringBuilder f=new StringBuilder();
        for(int y=0;y<8;y++){
            int empty=0;
            for(int x=0;x<8;x++){
                char p=board[y*8+x];
                if(p==0)empty++;
                else{if(empty>0){f.append(empty);empty=0;}f.append(p);}
            }
            if(empty>0)f.append(empty);if(y<7)f.append('/');
        }
        f.append(whiteToMove?" w ":" b ");
        String rights=(whiteKingSide?"K":"")+(whiteQueenSide?"Q":"")+(blackKingSide?"k":"")+(blackQueenSide?"q":"");
        f.append(rights.isEmpty()?"-":rights).append(' ');
        f.append(enPassantSquare<0?"-":squareName(enPassantSquare)).append(' ').append(halfmoveClock).append(' ').append(fullmoveNumber);
        return f.toString();
    }

    private List<Move> pseudoMoves(){
        List<Move> out=new ArrayList<>(48);
        for(int from=0;from<64;from++){
            char p=board[from];if(p==0||isWhite(p)!=whiteToMove)continue;
            int x=from%8,y=from/8;
            switch(Character.toLowerCase(p)){
                case 'p'->pawnMoves(out,from,x,y,p);
                case 'n'->{int[][] d={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};for(int[] v:d)addIfAvailable(out,from,x+v[0],y+v[1]);}
                case 'b'->slide(out,from,x,y,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1}});
                case 'r'->slide(out,from,x,y,new int[][]{{1,0},{-1,0},{0,1},{0,-1}});
                case 'q'->slide(out,from,x,y,new int[][]{{1,1},{1,-1},{-1,1},{-1,-1},{1,0},{-1,0},{0,1},{0,-1}});
                case 'k'->{for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0)addIfAvailable(out,from,x+dx,y+dy);castleMoves(out,from);}
                default->{}
            }
        }
        return out;
    }

    private void pawnMoves(List<Move> out,int from,int x,int y,char pawn){
        int dir=isWhite(pawn)?-1:1,start=isWhite(pawn)?6:1,oneY=y+dir;
        if(inside(x,oneY)&&board[oneY*8+x]==0){
            addPawn(out,from,oneY*8+x,oneY);
            int twoY=y+2*dir;if(y==start&&board[twoY*8+x]==0)out.add(new Move(from,twoY*8+x));
        }
        for(int dx:new int[]{-1,1}){
            int tx=x+dx,ty=y+dir;if(!inside(tx,ty))continue;int to=ty*8+tx;char victim=board[to];
            if(victim!=0&&isWhite(victim)!=isWhite(pawn)&&Character.toLowerCase(victim)!='k')addPawn(out,from,to,ty);
            else if(to==enPassantSquare)out.add(new Move(from,to));
        }
    }
    private void addPawn(List<Move> out,int from,int to,int y){
        if(y==0||y==7){out.add(new Move(from,to,'q'));out.add(new Move(from,to,'r'));out.add(new Move(from,to,'b'));out.add(new Move(from,to,'n'));}
        else out.add(new Move(from,to));
    }
    private void slide(List<Move> out,int from,int x,int y,int[][] dirs){
        for(int[] d:dirs){int tx=x+d[0],ty=y+d[1];while(inside(tx,ty)){
            int to=ty*8+tx;char p=board[to];
            if(p==0)out.add(new Move(from,to));
            else{if(isWhite(p)!=whiteToMove&&Character.toLowerCase(p)!='k')out.add(new Move(from,to));break;}
            tx+=d[0];ty+=d[1];
        }}
    }
    private void addIfAvailable(List<Move> out,int from,int x,int y){
        if(!inside(x,y))return;int to=y*8+x;char p=board[to];
        if(p==0||(isWhite(p)!=whiteToMove&&Character.toLowerCase(p)!='k'))out.add(new Move(from,to));
    }
    private void castleMoves(List<Move> out,int from){
        if(isInCheck(whiteToMove))return;
        if(whiteToMove&&from==60){
            if(whiteKingSide&&board[61]==0&&board[62]==0&&board[63]=='R'&&!isSquareAttacked(61,false)&&!isSquareAttacked(62,false))out.add(new Move(60,62));
            if(whiteQueenSide&&board[59]==0&&board[58]==0&&board[57]==0&&board[56]=='R'&&!isSquareAttacked(59,false)&&!isSquareAttacked(58,false))out.add(new Move(60,58));
        }else if(!whiteToMove&&from==4){
            if(blackKingSide&&board[5]==0&&board[6]==0&&board[7]=='r'&&!isSquareAttacked(5,true)&&!isSquareAttacked(6,true))out.add(new Move(4,6));
            if(blackQueenSide&&board[3]==0&&board[2]==0&&board[1]==0&&board[0]=='r'&&!isSquareAttacked(3,true)&&!isSquareAttacked(2,true))out.add(new Move(4,2));
        }
    }

    private boolean isSquareAttacked(int target,boolean byWhite){
        int x=target%8,y=target/8,pawnY=y+(byWhite?1:-1);
        for(int dx:new int[]{-1,1}){int px=x+dx;if(inside(px,pawnY)&&board[pawnY*8+px]==(byWhite?'P':'p'))return true;}
        int[][] knight={{1,2},{2,1},{2,-1},{1,-2},{-1,-2},{-2,-1},{-2,1},{-1,2}};
        for(int[] d:knight){int nx=x+d[0],ny=y+d[1];if(inside(nx,ny)&&board[ny*8+nx]==(byWhite?'N':'n'))return true;}
        for(int dx=-1;dx<=1;dx++)for(int dy=-1;dy<=1;dy++)if(dx!=0||dy!=0){
            int nx=x+dx,ny=y+dy;if(inside(nx,ny)&&board[ny*8+nx]==(byWhite?'K':'k'))return true;
        }
        int[][] dirs={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
        for(int i=0;i<dirs.length;i++){
            int dx=dirs[i][0],dy=dirs[i][1],nx=x+dx,ny=y+dy;
            while(inside(nx,ny)){
                char p=board[ny*8+nx];
                if(p!=0){
                    if(isWhite(p)==byWhite){char k=Character.toLowerCase(p);if(k=='q'||(i<4&&k=='r')||(i>=4&&k=='b'))return true;}
                    break;
                }
                nx+=dx;ny+=dy;
            }
        }
        return false;
    }

    private void applyUnchecked(Move m){
        char moving=board[m.from],captured=board[m.to];boolean white=isWhite(moving),pawn=Character.toLowerCase(moving)=='p';
        int fx=m.from%8,tx=m.to%8,fy=m.from/8,ty=m.to/8;
        if(pawn&&m.to==enPassantSquare&&captured==0&&fx!=tx){
            int cs=m.to+(white?8:-8);captured=board[cs];board[cs]=0;
        }
        board[m.from]=0;
        if(pawn&&(ty==0||ty==7)){char promoted=m.promotion==0?'q':Character.toLowerCase(m.promotion);board[m.to]=white?Character.toUpperCase(promoted):promoted;}
        else board[m.to]=moving;
        if(Character.toLowerCase(moving)=='k'&&Math.abs(tx-fx)==2){
            if(tx==6){int rf=ty*8+7,rt=ty*8+5;board[rt]=board[rf];board[rf]=0;}
            else{int rf=ty*8,rt=ty*8+3;board[rt]=board[rf];board[rf]=0;}
        }
        if(moving=='K'){whiteKingSide=false;whiteQueenSide=false;}
        if(moving=='k'){blackKingSide=false;blackQueenSide=false;}
        if(m.from==63||m.to==63)whiteKingSide=false;if(m.from==56||m.to==56)whiteQueenSide=false;
        if(m.from==7||m.to==7)blackKingSide=false;if(m.from==0||m.to==0)blackQueenSide=false;
        enPassantSquare=pawn&&Math.abs(ty-fy)==2?(m.from+m.to)/2:-1;
        halfmoveClock=pawn||captured!=0?0:halfmoveClock+1;if(!white)fullmoveNumber++;
        whiteToMove=!whiteToMove;repetitions.merge(positionKey(),1,Integer::sum);
    }

    private boolean isInsufficientMaterial(){
        int bishops=0,knights=0,bishopColor=-1;boolean sameColor=true;
        for(int i=0;i<64;i++){char p=board[i];if(p==0||Character.toLowerCase(p)=='k')continue;
            char k=Character.toLowerCase(p);if(k=='p'||k=='q'||k=='r')return false;
            if(k=='n')knights++;if(k=='b'){bishops++;int c=(i%8+i/8)&1;if(bishopColor<0)bishopColor=c;else if(c!=bishopColor)sameColor=false;}
        }
        return bishops+knights<=1||(knights==0&&bishops>0&&sameColor);
    }
    private static boolean isWhite(char p){return Character.isUpperCase(p);}
    private static boolean inside(int x,int y){return x>=0&&x<8&&y>=0&&y<8;}
}
