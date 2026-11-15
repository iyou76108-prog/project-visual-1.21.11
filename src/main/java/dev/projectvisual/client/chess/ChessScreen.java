package dev.projectvisual.client.chess;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Local chess game against the built-in medium-strength AI. */
public final class ChessScreen extends Screen {
    private static final int BG=0xF00D0F18,LIGHT=0xFFE6D9BE,DARK=0xFF6F5B4B;
    private ChessGame game=new ChessGame();
    private int selected=-1,boardX,boardY,boardSize,cell,panelX,searchVersion;
    private List<ChessGame.Move> targets=List.of();
    private boolean aiThinking,flipped;
    private String notice="Your move — White";

    public ChessScreen(){super(Component.literal("PROJECT VISUAL — Chess"));}

    @Override public void render(GuiGraphics g,int mx,int my,float partial){
        g.fill(0,0,width,height,BG);g.fill(0,0,width,3,0xFF8B72E8);
        boardSize=Math.min(456,Math.min(height-86,width-230));boardSize=Math.max(240,boardSize);
        cell=boardSize/8;boardSize=cell*8;boardX=Math.max(12,(width-boardSize-190)/2);
        boardY=Math.max(38,(height-boardSize)/2+5);panelX=boardX+boardSize+22;
        g.drawString(font,"PROJECT VISUAL  /  CHESS",boardX,14,0xFFF1ECFF,false);
        g.drawString(font,"ESC  ·  Back to menu",boardX,height-18,0xFFAAA6BA,false);
        drawBoard(g);drawPanel(g);super.render(g,mx,my,partial);
    }

    private void drawBoard(GuiGraphics g){
        g.fill(boardX-3,boardY-3,boardX+boardSize+3,boardY+boardSize+3,0xFF272536);
        for(int row=0;row<8;row++)for(int col=0;col<8;col++){
            int x=boardX+col*cell,y=boardY+row*cell;
            int sq=flipped?(7-row)*8+(7-col):row*8+col;
            g.fill(x,y,x+cell,y+cell,((row+col)&1)==0?LIGHT:DARK);
            if(sq==selected)g.fill(x+2,y+2,x+cell-2,y+cell-2,0x8877C7A5);
            for(ChessGame.Move m:targets)if(m.to()==sq)g.fill(x+cell/2-5,y+cell/2-5,x+cell/2+5,y+cell/2+5,0xBB77C7A5);
            char p=game.pieceAt(sq);if(p!=0)drawPiece(g,p,x,y);
            if(col==0)g.drawString(font,Integer.toString(flipped?row+1:8-row),x+3,y+2,0xAA24212A,false);
            if(row==7)g.drawString(font,Character.toString((char)(flipped?'h'-col:'a'+col)),x+cell-9,y+cell-11,0xAA24212A,false);
        }
    }
    private void drawPiece(GuiGraphics g,char p,int x,int y){
        boolean white=Character.isUpperCase(p);int d=Math.max(22,cell-15),left=x+(cell-d)/2,top=y+(cell-d)/2;
        g.fill(left-2,top-2,left+d+2,top+d+2,white?0xFF897A61:0xFFB5A8D5);
        g.fill(left,top,left+d,top+d,white?0xFFF7F0DD:0xFF292735);
        String label=switch(Character.toLowerCase(p)){case 'k'->"K";case 'q'->"Q";case 'r'->"R";case 'b'->"B";case 'n'->"N";default->"P";};
        g.drawCenteredString(font,label,x+cell/2,y+cell/2-4,white?0xFF2B2630:0xFFF3ECDD);
    }
    private void drawPanel(GuiGraphics g){
        int y=boardY+2;g.drawString(font,"CHESS",panelX,y,0xFFF1ECFF,false);y+=24;
        String result=game.result();
        if(result!=null)g.drawString(font,result,panelX,y,0xFFFFD58B,false);
        else if(aiThinking)g.drawString(font,"AI is thinking…",panelX,y,0xFFBCA9FF,false);
        else{g.drawString(font,game.whiteToMove()?"Your turn (White)":"AI turn (Black)",panelX,y,0xFFB6E3C7,false);if(game.isInCheck(game.whiteToMove()))g.drawString(font,"CHECK!",panelX,y+15,0xFFFF7676,false);}
        y+=42;g.drawString(font,"Pieces",panelX,y,0xFFAAA6BA,false);y+=18;
        g.drawString(font,"White: "+countPieces(true),panelX,y,0xFFF7F0DD,false);y+=17;
        g.drawString(font,"Black: "+countPieces(false),panelX,y,0xFFB7B1C9,false);y+=17;y+=34;
        button(g,panelX,y,150,29,"New game",true);y+=39;
        button(g,panelX,y,150,29,flipped?"Unflip board":"Flip board",false);y+=48;
        g.drawString(font,"Rules included:",panelX,y,0xFFAAA6BA,false);y+=17;
        for(String s:new String[]{"Castling","En passant","Promotion","Check / mate","Stalemate","Draw rules"}){g.drawString(font,"• "+s,panelX,y,0xFFAAA6BA,false);y+=15;}
        if(notice!=null&&!notice.isBlank())g.drawString(font,notice,panelX,boardY+boardSize-15,0xFFBCA9FF,false);
    }
    private void button(GuiGraphics g,int x,int y,int w,int h,String label,boolean primary){
        g.fill(x,y,x+w,y+h,primary?0xFF41365F:0xFF282D40);g.drawCenteredString(font,label,x+w/2,y+10,0xFFF3F0FF);
    }
    private int countPieces(boolean white){
        int count=0;for(int i=0;i<64;i++){char p=game.pieceAt(i);if(p!=0&&Character.isUpperCase(p)==white&&Character.toLowerCase(p)!='k')count++;}return count;
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(e.button()!=0)return super.mouseClicked(e,dbl);
        double mx=e.x(),my=e.y();int newGameY=boardY+2+24+42+18+17+17+34;
        if(mx>=panelX&&mx<=panelX+150&&my>=newGameY+39&&my<=newGameY+68){flipped=!flipped;return true;}
        if(mx>=panelX&&mx<=panelX+150&&my>=newGameY&&my<=newGameY+29){
            searchVersion++;game=new ChessGame();selected=-1;targets=List.of();aiThinking=false;notice="New game started";return true;
        }
        if(aiThinking||game.result()!=null)return true;
        if(mx>=boardX&&mx<boardX+boardSize&&my>=boardY&&my<boardY+boardSize){
            int col=(int)((mx-boardX)/cell),row=(int)((my-boardY)/cell);
            int sq=flipped?(7-row)*8+(7-col):row*8+col;char p=game.pieceAt(sq);
            if(selected<0){if(p!=0&&Character.isUpperCase(p)&&game.whiteToMove())select(sq);}
            else if(p!=0&&Character.isUpperCase(p))select(sq);
            else if(game.play(selected,sq)){
                selected=-1;targets=List.of();notice="Move played";
                if(game.result()==null)startAiMove();else notice=game.result();
            }else{selected=-1;targets=List.of();}
            return true;
        }
        return super.mouseClicked(e,dbl);
    }
    private void select(int sq){selected=sq;targets=game.legalMoves().stream().filter(m->m.from()==sq).toList();}
    private void startAiMove(){
        aiThinking=true;int version=++searchVersion;notice="Calculating a move…";
        ChessGame snapshot=game.copy();Minecraft mc=Minecraft.getInstance();
        CompletableFuture.supplyAsync(()->ChessAI.chooseMove(snapshot,5,650)).thenAccept(move->mc.execute(()->{
            if(mc.screen!=this||version!=searchVersion)return;aiThinking=false;
            if(move!=null&&!game.whiteToMove()){game.play(move);String result=game.result();notice=result==null?"Your turn — White":result;}
        }));
    }
    @Override public boolean isPauseScreen(){return false;}
}
