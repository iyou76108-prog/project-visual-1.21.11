package dev.projectvisual.client.chess;

import java.util.Comparator;
import java.util.List;

/** Medium opponent: iterative deepening, alpha-beta pruning and positional evaluation. */
public final class ChessAI {
    private static final int MATE=100_000;
    private ChessAI(){}
    public static ChessGame.Move chooseMove(ChessGame position,int maxDepth,long budgetMs){
        List<ChessGame.Move> moves=position.legalMoves();if(moves.isEmpty())return null;
        ChessGame.Move best=moves.get(0);Search ctx=new Search(System.nanoTime()+Math.max(100,budgetMs)*1_000_000L);
        for(int depth=1;depth<=Math.max(1,maxDepth);depth++){
            try{ChessGame.Move candidate=root(position,moves,depth,ctx);if(candidate!=null)best=candidate;}
            catch(Timeout stop){break;}
        }
        return best;
    }
    private static ChessGame.Move root(ChessGame g,List<ChessGame.Move> moves,int depth,Search s){
        order(g,moves);int alpha=-MATE,bestScore=-MATE;ChessGame.Move best=null;
        for(ChessGame.Move m:moves){s.time();ChessGame n=g.copy();n.applyForSearch(m);
            int score=-search(n,depth-1,-MATE,-alpha,1,s);
            if(score>bestScore){bestScore=score;best=m;}alpha=Math.max(alpha,score);
        }return best;
    }
    private static int search(ChessGame g,int depth,int alpha,int beta,int ply,Search s){
        s.time();String result=g.result();
        if(result!=null)return result.startsWith("Checkmate")?-MATE+ply:0;
        if(depth<=0)return evaluate(g);
        List<ChessGame.Move> moves=g.legalMoves();order(g,moves);int best=-MATE;
        for(ChessGame.Move m:moves){ChessGame n=g.copy();n.applyForSearch(m);
            int score=-search(n,depth-1,-beta,-alpha,ply+1,s);best=Math.max(best,score);
            alpha=Math.max(alpha,score);if(alpha>=beta)break;
        }return best;
    }
    private static int evaluate(ChessGame g){
        int score=0,wb=0,bb=0;
        for(int sq=0;sq<64;sq++){char p=g.pieceAt(sq);if(p==0)continue;boolean w=Character.isUpperCase(p);char k=Character.toLowerCase(p);
            int value=switch(k){case 'p'->100;case 'n'->320;case 'b'->335;case 'r'->500;case 'q'->900;case 'k'->20_000;default->0;};
            int x=sq%8,y=sq/8,advance=w?7-y:y,center=7-(Math.abs(3-x)+Math.abs(3-y));
            int positional=switch(k){case 'p'->advance*8+center*2;case 'n'->center*9;case 'b'->center*5;case 'r'->advance*2;case 'q'->center*2;case 'k'->advance<2?-center*3:center*2;default->0;};
            score+=(w?1:-1)*(value+positional);if(k=='b'){if(w)wb++;else bb++;}
        }
        if(wb>=2)score+=28;if(bb>=2)score-=28;return g.whiteToMove()?score:-score;
    }
    private static void order(ChessGame g,List<ChessGame.Move> moves){
        moves.sort(Comparator.comparingInt((ChessGame.Move m)->{
            char a=g.pieceAt(m.from()),v=g.pieceAt(m.to());int s=0;
            if(v!=0)s+=10*value(v)-value(a);if(m.promotion()!=0)s+=800+value(m.promotion());
            s+=7-(Math.abs(3-m.to()%8)+Math.abs(3-m.to()/8));return s;
        }).reversed());
    }
    private static int value(char p){return switch(Character.toLowerCase(p)){case 'p'->100;case 'n'->320;case 'b'->335;case 'r'->500;case 'q'->900;case 'k'->20_000;default->0;};}
    private static final class Search{final long deadline;long nodes;Search(long d){deadline=d;}void time(){if((++nodes&127)==0&&System.nanoTime()>deadline)throw new Timeout();}}
    private static final class Timeout extends RuntimeException{private static final long serialVersionUID=1L;}
}
