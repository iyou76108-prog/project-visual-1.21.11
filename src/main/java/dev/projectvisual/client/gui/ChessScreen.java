package dev.projectvisual.client.gui;

import dev.projectvisual.client.chess.ChessEngine;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.List;

/** Local chess board. Online matchmaking is intentionally not simulated without a live service. */
public final class ChessScreen extends Screen {
    private static final int LIGHT=0xFFE8DCC7,DARK=0xFF796451,SELECT=0xAA65B9F2,MOVE=0xAA70C98B;
    private final ChessEngine game=new ChessEngine();
    private int selected=-1;
    private boolean againstAi=true, aiThinking=false;
    private String notice="White to move";
    public ChessScreen(){super(Component.literal("PROJECT VISUAL Chess"));}
    @Override protected void init(){super.init();}
    @Override public void render(GuiGraphics g,int mx,int my,float delta){
        g.fill(0,0,width,height,0xF00D0F18);
        int boardSize=Math.min(480,Math.min(height-90,width-300));boardSize=Math.max(240,boardSize);
        int cell=boardSize/8,bx=(width-boardSize)/2-80,by=(height-boardSize)/2;
        g.drawCenteredString(font,"PROJECT VISUAL  /  CHESS",width/2,Math.max(8,by-30),0xFFF3F0FF);
        for(int r=0;r<8;r++)for(int f=0;f<8;f++){
            int sq=r*8+f,x=bx+f*cell,y=by+(7-r)*cell;
            g.fill(x,y,x+cell,y+cell,((r+f)&1)==0?LIGHT:DARK);
            if(selected==sq)g.fill(x,y,x+cell,y+cell,SELECT);
            else if(selected>=0&&isLegalTarget(selected,sq))g.fill(x,y,x+cell,y+cell,MOVE);
            char p=game.pieceAt(sq);if(p!=0){String symbol=symbol(p);int color=Character.isUpperCase(p)?0xFFF9F5EA:0xFF211D28;g.drawCenteredString(font,symbol,x+cell/2,y+cell/2-4,color);}
        }
        int panel=bx+boardSize+22;
        g.drawString(font,"MODE",panel,by+8,0xFFAAA6BA);
        g.drawString(font,againstAi?"Player vs AI":"Local game",panel,by+25,0xFFF3F0FF);
        g.drawString(font,"TURN",panel,by+57,0xFFAAA6BA);
        g.drawString(font,game.whiteToMove()?"White":"Black",panel,by+74,0xFFF3F0FF);
        g.drawString(font,notice,panel,by+104,0xFFBDB5D1);
        g.drawString(font,"Click a piece, then a square",panel,by+144,0xFFAAA6BA);
        g.drawString(font,"N B R Q = promotion to queen",panel,by+160,0xFFAAA6BA);
        g.drawString(font,"R  Restart",panel,by+196,0xFFAAA6BA);
        g.drawString(font,"ESC  Back",panel,by+214,0xFFAAA6BA);
        super.render(g,mx,my,delta);
    }
    private boolean isLegalTarget(int from,int to){for(ChessEngine.Move m:game.legalMoves())if(m.from()==from&&m.to()==to)return true;return false;}
    private static String symbol(char p){return switch(p){case 'K','k'->"♚";case 'Q','q'->"♛";case 'R','r'->"♜";case 'B','b'->"♝";case 'N','n'->"♞";default->"♟";};}
    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(e.button()!=0)return super.mouseClicked(e,dbl);
        int boardSize=Math.min(480,Math.min(height-90,width-300));boardSize=Math.max(240,boardSize);
        int cell=boardSize/8,bx=(width-boardSize)/2-80,by=(height-boardSize)/2;
        int f=(int)(e.x()-bx)/cell,r=7-(int)(e.y()-by)/cell;
        if(f>=0&&f<8&&r>=0&&r<8){
            int sq=r*8+f;char p=game.pieceAt(sq);
            if(selected<0){if(p!=0&&Character.isUpperCase(p)==game.whiteToMove())selected=sq;}
            else {if(!game.play(selected,sq)){if(p!=0&&Character.isUpperCase(p)==game.whiteToMove())selected=sq;else notice="Illegal move";}else{selected=-1;updateStatus();if(againstAi&&game.result()==ChessEngine.Result.ONGOING&& !game.whiteToMove())runAi();}}
            return true;
        }
        return super.mouseClicked(e,dbl);
    }
    private void runAi(){ChessEngine.Move move=game.chooseAiMove(3);if(move!=null)game.play(move);updateStatus();}
    private void updateStatus(){notice=switch(game.result()){case CHECKMATE->"Checkmate";case STALEMATE->"Stalemate";case DRAW->"Draw";case ONGOING->game.whiteToMove()?"White to move":"Black to move";};if(game.result()==ChessEngine.Result.ONGOING&&game.inCheck(game.whiteToMove()))notice+=" - check";}
    @Override public boolean keyPressed(net.minecraft.client.input.KeyEvent e){
        if(e.key()==82){game.reset();selected=-1;notice="White to move";return true;}
        return super.keyPressed(e);
    }
    @Override public boolean isPauseScreen(){return false;}
}
