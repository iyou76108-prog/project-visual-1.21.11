package dev.projectvisual.client.chess;

import java.util.ArrayList;
import java.util.List;

/** Self-contained chess rules and a bounded minimax opponent. Board index 0 is a1. */
public final class ChessEngine {
    public record Move(int from, int to, char promotion) {}
    public enum Result { ONGOING, CHECKMATE, STALEMATE, DRAW }
    private static final int[] KNIGHT={-17,-15,-10,-6,6,10,15,17};
    private static final int[] KING={-9,-8,-7,-1,1,7,8,9};
    private static final int[][] DIRS={{1,0},{-1,0},{0,1},{0,-1},{1,1},{1,-1},{-1,1},{-1,-1}};
    private char[] board=new char[64];
    private boolean whiteToMove=true, wk=true,wq=true,bk=true,bq=true;
    private int ep=-1, halfmove=0, fullmove=1;

    public ChessEngine(){ reset(); }

    /** Builds a local rules-engine position from a server-supplied FEN string. */
    public ChessEngine(String fen) {
        String[] fields = fen.trim().split("\\s+");
        if (fields.length < 4) throw new IllegalArgumentException("Invalid FEN");
        String[] ranks = fields[0].split("/");
        if (ranks.length != 8) throw new IllegalArgumentException("Invalid FEN board");
        board = new char[64];
        for (int row = 0; row < 8; row++) {
            int file = 0;
            int rank = 7 - row;
            for (int i = 0; i < ranks[row].length(); i++) {
                char value = ranks[row].charAt(i);
                if (value >= '1' && value <= '8') {
                    file += value - '0';
                } else if ("prnbqkPRNBQK".indexOf(value) >= 0 && file < 8) {
                    board[rank * 8 + file++] = value;
                } else {
                    throw new IllegalArgumentException("Invalid FEN piece placement");
                }
            }
            if (file != 8) throw new IllegalArgumentException("Invalid FEN rank width");
        }
        whiteToMove = fields[1].equals("w");
        String rights = fields[2];
        wk = rights.contains("K");
        wq = rights.contains("Q");
        bk = rights.contains("k");
        bq = rights.contains("q");
        ep = fields[3].equals("-") ? -1 : parseSquare(fields[3]);
        halfmove = fields.length > 4 ? Integer.parseInt(fields[4]) : 0;
        fullmove = fields.length > 5 ? Integer.parseInt(fields[5]) : 1;
    }

    private static int parseSquare(String value) {
        if (value.length() != 2 || value.charAt(0) < 'a' || value.charAt(0) > 'h'
                || value.charAt(1) < '1' || value.charAt(1) > '8') {
            throw new IllegalArgumentException("Invalid FEN square");
        }
        return (value.charAt(1) - '1') * 8 + value.charAt(0) - 'a';
    }
    private ChessEngine(ChessEngine p){
        board=p.board.clone(); whiteToMove=p.whiteToMove; wk=p.wk;wq=p.wq;bk=p.bk;bq=p.bq;
        ep=p.ep;halfmove=p.halfmove;fullmove=p.fullmove;
    }
    public void reset(){
        String back="RNBQKBNR";
        for(int x=0;x<8;x++){board[x]=back.charAt(x);board[8+x]='P';board[48+x]='p';board[56+x]=Character.toLowerCase(back.charAt(x));}
        whiteToMove=true;wk=wq=bk=bq=true;ep=-1;halfmove=0;fullmove=1;
    }
    public char pieceAt(int square){return square<0||square>=64?0:board[square];}
    public boolean whiteToMove(){return whiteToMove;}
    public int halfmoveClock(){return halfmove;}
    public String fen(){
        StringBuilder s=new StringBuilder();
        for(int rank=7;rank>=0;rank--){int empty=0;for(int f=0;f<8;f++){char c=board[rank*8+f];if(c==0)empty++;else{if(empty>0){s.append(empty);empty=0;}s.append(c);}}if(empty>0)s.append(empty);if(rank>0)s.append('/');}
        s.append(whiteToMove?" w ":" b ");String rights=""+(wk?'K':"")+(wq?'Q':"")+(bk?'k':"")+(bq?'q':"");
        s.append(rights.isEmpty()?"-":rights).append(' ').append(ep<0?"-":name(ep)).append(' ').append(halfmove).append(' ').append(fullmove);return s.toString();
    }
    public List<Move> legalMoves(){return legalMovesFor(whiteToMove);}
    public boolean play(Move move){
        for(Move m:legalMoves())if(m.equals(move)){apply(m);return true;}return false;
    }
    public boolean play(int from,int to){for(Move m:legalMoves())if(m.from==from&&m.to==to)return play(m);return false;}
    public Result result(){
        if(!legalMoves().isEmpty())return insufficientMaterial()?Result.DRAW:halfmove>=100?Result.DRAW:Result.ONGOING;
        return inCheck(whiteToMove)?Result.CHECKMATE:Result.STALEMATE;
    }
    public boolean inCheck(boolean white){int k=findKing(white);return k>=0&&attacked(k,!white);}
    private int findKing(boolean white){char k=white?'K':'k';for(int i=0;i<64;i++)if(board[i]==k)return i;return -1;}
    private boolean attacked(int sq,boolean byWhite){
        int f=sq%8,r=sq/8;
        int pawnRank=r+(byWhite?-1:1);
        for(int df:new int[]{-1,1})if(inside(f+df,pawnRank)){char p=board[pawnRank*8+f+df];if(p==(byWhite?'P':'p'))return true;}
        for(int d:KNIGHT){int t=sq+d;if(t>=0&&t<64&&Math.abs(t%8-f)<=2){char p=board[t];if(p==(byWhite?'N':'n'))return true;}}
        for(int[] v:DIRS){int nf=f+v[0],nr=r+v[1],step=1;while(inside(nf,nr)){char p=board[nr*8+nf];if(p!=0){if((byWhite?Character.isUpperCase(p):Character.isLowerCase(p))){char q=Character.toUpperCase(p);if(q=='K'&&step==1||q=='Q'||(step>0&&((v[0]==0||v[1]==0)?q=='R':q=='B')))return true;}break;}nf+=v[0];nr+=v[1];step++;}}
        return false;
    }
    private List<Move> legalMovesFor(boolean white){
        List<Move> out=new ArrayList<>();for(int i=0;i<64;i++){char p=board[i];if(p==0||Character.isUpperCase(p)!=white)continue;int f=i%8,r=i/8;char kind=Character.toUpperCase(p);
            if(kind=='P'){int dr=white?1:-1,nr=r+dr;if(inside(f,nr)&&board[nr*8+f]==0){addPawn(out,i,nr*8+f,white);if(r==(white?1:6)&&board[(r+2*dr)*8+f]==0)out.add(new Move(i,(r+2*dr)*8+f,(char)0));}
                for(int df:new int[]{-1,1})if(inside(f+df,nr)){int t=nr*8+f+df;char q=board[t];if(q!=0&&Character.isUpperCase(q)!=white||t==ep)addPawn(out,i,t,white);}}
            else if(kind=='N'){for(int d:KNIGHT){int t=i+d;if(t>=0&&t<64&&Math.abs(t%8-f)<=2&&ownOrEmpty(t,white))out.add(new Move(i,t,(char)0));}}
            else if(kind=='K'){for(int[] v:DIRS){int nf=f+v[0],nr2=r+v[1];if(inside(nf,nr2)&&ownOrEmpty(nr2*8+nf,white))out.add(new Move(i,nr2*8+nf,(char)0));}
                if(white&&i==4&&!inCheck(true)){if(wk&&board[5]==0&&board[6]==0&&board[7]=='R'&&!attacked(5,false)&&!attacked(6,false))out.add(new Move(4,6,(char)0));if(wq&&board[3]==0&&board[2]==0&&board[1]==0&&board[0]=='R'&&!attacked(3,false)&&!attacked(2,false))out.add(new Move(4,2,(char)0));}
                if(!white&&i==60&&!inCheck(false)){if(bk&&board[61]==0&&board[62]==0&&board[63]=='r'&&!attacked(61,true)&&!attacked(62,true))out.add(new Move(60,62,(char)0));if(bq&&board[59]==0&&board[58]==0&&board[57]==0&&board[56]=='r'&&!attacked(59,true)&&!attacked(58,true))out.add(new Move(60,58,(char)0));}}
            else {for(int[] v:DIRS){if(kind=='B'&&(v[0]==0||v[1]==0)||kind=='R'&&(v[0]!=0&&v[1]!=0))continue;int nf=f+v[0],nr2=r+v[1];while(inside(nf,nr2)){int t=nr2*8+nf;if(!ownOrEmpty(t,white))break;out.add(new Move(i,t,(char)0));if(board[t]!=0)break;nf+=v[0];nr2+=v[1];}}}
        }
        List<Move> legal=new ArrayList<>();for(Move m:out){ChessEngine c=new ChessEngine(this);c.apply(m);if(!c.inCheck(white))legal.add(m);}return legal;
    }
    private void addPawn(List<Move> l,int from,int to,boolean white){if(to/8==(white?7:0))l.add(new Move(from,to,white?'Q':'q'));else l.add(new Move(from,to,(char)0));}
    private boolean ownOrEmpty(int sq,boolean white){char p=board[sq];return p==0||Character.isUpperCase(p)!=white;}
    private void apply(Move m){
        char p=board[m.from],captured=board[m.to];boolean white=Character.isUpperCase(p);
        if(Character.toUpperCase(p)=='P'&&m.to==ep&&captured==0)board[m.to+(white?-8:8)]=0;
        board[m.to]=m.promotion!=0?m.promotion:p;board[m.from]=0;
        if(p=='K'){wk=wq=false;if(m.to==6){board[5]=board[7];board[7]=0;}if(m.to==2){board[3]=board[0];board[0]=0;}}
        if(p=='k'){bk=bq=false;if(m.to==62){board[61]=board[63];board[63]=0;}if(m.to==58){board[59]=board[56];board[56]=0;}}
        if(m.from==0||m.to==0)wq=false;if(m.from==7||m.to==7)wk=false;if(m.from==56||m.to==56)bq=false;if(m.from==63||m.to==63)bk=false;
        ep=Character.toUpperCase(p)=='P'&&Math.abs(m.to-m.from)==16?(m.from+m.to)/2:-1;
        halfmove=Character.toUpperCase(p)=='P'||captured!=0?0:halfmove+1;if(!white)fullmove++;whiteToMove=!whiteToMove;
    }
    private boolean insufficientMaterial(){int bishops=0,knights=0;for(char p:board){if(p=='P'||p=='p'||p=='R'||p=='r'||p=='Q'||p=='q')return false;if(p=='B'||p=='b')bishops++;if(p=='N'||p=='n')knights++;}return bishops+knights<=1;}
    public Move chooseAiMove(int depth){
        List<Move> moves=legalMoves();if(moves.isEmpty())return null;Move best=moves.get(0);int bestScore=whiteToMove?Integer.MIN_VALUE:Integer.MAX_VALUE;
        for(Move m:moves){ChessEngine c=new ChessEngine(this);c.apply(m);int score=c.search(Math.max(0,depth-1),Integer.MIN_VALUE+1,Integer.MAX_VALUE-1);if(whiteToMove?score>bestScore:score<bestScore){bestScore=score;best=m;}}
        return best;
    }
    private int search(int depth,int alpha,int beta){
        List<Move> moves=legalMoves();if(moves.isEmpty())return inCheck(whiteToMove)?(whiteToMove?-100000:100000):0;if(depth==0||halfmove>=100)return evaluate();
        if(whiteToMove){int best=Integer.MIN_VALUE;for(Move m:moves){ChessEngine c=new ChessEngine(this);c.apply(m);best=Math.max(best,c.search(depth-1,alpha,beta));alpha=Math.max(alpha,best);if(beta<=alpha)break;}return best;}
        int best=Integer.MAX_VALUE;for(Move m:moves){ChessEngine c=new ChessEngine(this);c.apply(m);best=Math.min(best,c.search(depth-1,alpha,beta));beta=Math.min(beta,best);if(beta<=alpha)break;}return best;
    }
    private int evaluate(){int score=0;for(int i=0;i<64;i++){char p=board[i];if(p==0)continue;int v=switch(Character.toUpperCase(p)){case 'P'->100;case 'N'->320;case 'B'->335;case 'R'->500;case 'Q'->900;default->0;};int f=i%8,r=i/8;v+=(3-Math.abs(3-f))*3+(3-Math.abs(3-r))*2;if(Character.isUpperCase(p))score+=v;else score-=v;}return score;}
    private static boolean inside(int f,int r){return f>=0&&f<8&&r>=0&&r<8;}
    public static String name(int sq){return ""+(char)('a'+sq%8)+(char)('1'+sq/8);}
}
