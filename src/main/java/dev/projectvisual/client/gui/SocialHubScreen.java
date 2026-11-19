package dev.projectvisual.client.gui;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.projectvisual.client.account.OnlineMatchSession;
import dev.projectvisual.client.account.ProjectVisualApiClient;
import dev.projectvisual.client.chess.ChessWorldGame;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public final class SocialHubScreen extends Screen {
    private static final int BG=0xF00D0F18, CARD=0xFF1D2130, TEXT=0xFFF3F0FF, MUTED=0xFFAAA6BA;
    private static final int PANEL_W=790, PANEL_H=540;
    private final List<FriendRow> friends=new ArrayList<>();
    private final List<GameRow> games=new ArrayList<>();
    private EditBox usernameBox;
    private String message="Обновляю друзей и приглашения...";
    private String localUserId;
    private int scrollFriends;

    private record FriendRow(String id,String username,String status,boolean incoming,boolean online,String skin) {}
    private record GameRow(String id,String type,String status,String player1Id,String player2Id,
                           String player1Name,String player2Name) {}

    public SocialHubScreen(){super(Component.literal("PROJECT VISUAL — Друзья"));}

    @Override protected void init(){
        int x=(width-Math.min(PANEL_W,width-24))/2;
        int y=(height-Math.min(PANEL_H,height-24))/2;
        usernameBox=new EditBox(font,x+22,y+77,210,22,Component.literal("Ник PROJECT VISUAL"));
        usernameBox.setMaxLength(16);
        usernameBox.setHint(Component.literal("Имя пользователя"));
        addRenderableWidget(usernameBox);
        refresh();
    }

    private void refresh(){
        ProjectVisualApiClient.refreshProfile(ignored->{
            localUserId=ProjectVisualApiClient.minecraftUserId();
            refreshRows();
        });
        refreshRows();
    }

    private void refreshRows(){
        ProjectVisualApiClient.getFriends(this::readFriends);
        ProjectVisualApiClient.getGames(this::readGames);
    }

    private void readFriends(String response){
        try{
            JsonObject root=JsonParser.parseString(response).getAsJsonObject();
            if(root.has("error")){message=root.get("error").getAsString();return;}
            JsonArray array=root.getAsJsonArray("friends");
            friends.clear();
            if(array!=null)for(var item:array){
                JsonObject f=item.getAsJsonObject();
                friends.add(new FriendRow(value(f,"id"),value(f,"username"),value(f,"status"),
                        bool(f,"incoming"),bool(f,"online"),value(f,"equippedSkin")));
            }
            message="";
        }catch(RuntimeException e){message="Не удалось разобрать список друзей.";}
    }

    private void readGames(String response){
        try{
            JsonObject root=JsonParser.parseString(response).getAsJsonObject();
            if(root.has("error")){message=root.get("error").getAsString();return;}
            JsonArray array=root.getAsJsonArray("games");
            games.clear();
            if(array!=null)for(var item:array){
                JsonObject g=item.getAsJsonObject();
                JsonObject p1=g.has("player1")&&g.get("player1").isJsonObject()?g.getAsJsonObject("player1"):new JsonObject();
                JsonObject p2=g.has("player2")&&g.get("player2").isJsonObject()?g.getAsJsonObject("player2"):new JsonObject();
                games.add(new GameRow(value(g,"id"),value(g,"type"),value(g,"status"),
                        value(g,"player1Id"),value(g,"player2Id"),value(p1,"username"),value(p2,"username")));
            }
            message="";
        }catch(RuntimeException e){message="Не удалось разобрать матчи.";}
    }

    private static String value(JsonObject object,String key){
        return object.has(key)&&!object.get(key).isJsonNull()?object.get(key).getAsString():"";
    }
    private static boolean bool(JsonObject object,String key){
        return object.has(key)&&!object.get(key).isJsonNull()&&object.get(key).getAsBoolean();
    }

    @Override public void render(GuiGraphics g,int mouseX,int mouseY,float partialTick){
        g.fill(0,0,width,height,0xB9000000);
        int panelW=Math.min(PANEL_W,width-24),panelH=Math.min(PANEL_H,height-24);
        int x=(width-panelW)/2,y=(height-panelH)/2;
        int rightX=x+panelW/2+8;
        int colW=panelW/2-28;
        g.fill(x,y,x+panelW,y+panelH,BG);
        g.fill(x,y,x+panelW,y+3,0xFF9B7BFF);
        g.drawString(font,"PROJECT VISUAL / SOCIAL HUB",x+22,y+18,0xFFB89AFF,false);
        g.drawString(font,"ДРУЗЬЯ",x+22,y+53,TEXT,false);
        g.fill(x+22,y+106,x+colW+22,y+107,0xFF363B50);
        g.drawString(font,"Добавь зарегистрированного игрока по нику",x+22,y+111,MUTED,false);
        drawButton(g,x+244,y+76,x+colW+20,y+100,"ДОБАВИТЬ",0xFF393353);
        g.drawString(font,"ОНЛАЙН ДРУЗЬЯ И ЗАПРОСЫ",x+22,y+142,MUTED,false);

        int maxFriends=Math.min(7,friends.size()-scrollFriends);
        if(maxFriends<=0)g.drawString(font,"Пока нет друзей. Добавь ник выше.",x+22,y+168,MUTED,false);
        for(int i=0;i<maxFriends;i++){
            FriendRow friend=friends.get(i+scrollFriends);
            int rowY=y+163+i*48;
            g.fill(x+18,rowY,x+colW+26,rowY+43,CARD);
            String online=friend.online()?"● online":"○ offline";
            g.drawString(font,friend.username(),x+28,rowY+7,TEXT,false);
            g.drawString(font,online,x+28,rowY+23,friend.online()?0xFF7DE0C1:MUTED,false);
            if(!friend.skin().isEmpty())g.drawString(font,"Скин: "+friend.skin(),x+124,rowY+23,MUTED,false);
            if("ACCEPTED".equals(friend.status())){
                drawButton(g,x+colW-110,rowY+8,x+colW-56,rowY+34,"♟",0xFF393353);
                drawButton(g,x+colW-52,rowY+8,x+colW+16,rowY+34,"XO",0xFF293D45);
            }else if(friend.incoming()){
                drawButton(g,x+colW-112,rowY+8,x+colW+16,rowY+34,"ПРИНЯТЬ",0xFF393353);
            }else{
                g.drawString(font,"ОЖИДАНИЕ",x+colW-98,rowY+18,0xFFD6B879,false);
            }
        }

        g.drawString(font,"МАТЧИ",rightX,y+53,TEXT,false);
        drawButton(g,rightX+colW-112,y+42,rightX+colW+18,y+68,"ОБНОВИТЬ",0xFF293D45);
        g.fill(rightX,y+76,rightX+colW+18,y+77,0xFF363B50);
        int count=Math.min(5,games.size());
        if(count==0)g.drawString(font,"Приглашений пока нет.",rightX,y+96,MUTED,false);
        for(int i=0;i<count;i++){
            GameRow game=games.get(i);
            int rowY=y+88+i*77;
            g.fill(rightX,rowY,rightX+colW+18,rowY+68,CARD);
            String title="CHESS".equals(game.type())?"ШАХМАТЫ":"КРЕСТИКИ-НОЛИКИ";
            String opponent=game.player1Id()!=null&&game.player1Id().equals(localUserId)?game.player2Name():game.player1Name();
            g.drawString(font,title,rightX+10,rowY+8,0xFFB89AFF,false);
            g.drawString(font,"Соперник: "+(opponent==null?"":opponent),rightX+10,rowY+25,MUTED,false);
            String state="ACTIVE".equals(game.status())?"ИГРАЕТСЯ":"INVITED".equals(game.status())?"ПРИГЛАШЕНИЕ":"ЗАВЕРШЕНО";
            g.drawString(font,state,rightX+10,rowY+43,MUTED,false);
            if("ACTIVE".equals(game.status())){
                drawButton(g,rightX+colW-100,rowY+39,rightX+colW+8,rowY+63,"ОТКРЫТЬ",0xFF393353);
            }else if("INVITED".equals(game.status())&&game.player2Id()!=null&&game.player2Id().equals(localUserId)){
                drawButton(g,rightX+colW-100,rowY+39,rightX+colW+8,rowY+63,"ПРИНЯТЬ",0xFF393353);
            }
        }

        if(!message.isBlank())g.drawString(font,message,x+22,y+panelH-40,0xFFFFCF8C,false);
        g.drawString(font,"ESC — назад",x+panelW-98,y+panelH-22,MUTED,false);
        super.render(g,mouseX,mouseY,partialTick);
    }

    private void drawButton(GuiGraphics g,int x1,int y1,int x2,int y2,String label,int color){
        g.fill(x1,y1,x2,y2,color);
        g.drawCenteredString(font,label,(x1+x2)/2,y1+(y2-y1-font.lineHeight)/2, TEXT);
    }

    @Override public boolean mouseClicked(MouseButtonEvent event,boolean doubleClick){
        if(super.mouseClicked(event,doubleClick))return true;
        if(event.button()!=0)return false;
        int panelW=Math.min(PANEL_W,width-24),panelH=Math.min(PANEL_H,height-24);
        int x=(width-panelW)/2,y=(height-panelH)/2;
        int colW=panelW/2-28,rightX=x+panelW/2+8;
        if(event.x()>=x+244&&event.x()<=x+colW+20&&event.y()>=y+76&&event.y()<=y+100){
            String name=usernameBox.getValue().trim();
            if(name.isEmpty()){message="Введи ник игрока.";return true;}
            ProjectVisualApiClient.addFriend(name,response->{
                try{
                    JsonObject root=JsonParser.parseString(response).getAsJsonObject();
                    message=root.has("error")?root.get("error").getAsString():"Запрос отправлен.";
                }catch(RuntimeException e){message="Ошибка ответа сервера.";}
                refreshRows();
            });
            return true;
        }
        if(event.x()>=rightX+colW-112&&event.x()<=rightX+colW+18&&event.y()>=y+42&&event.y()<=y+68){
            refresh();return true;
        }
        int count=Math.min(7,friends.size()-scrollFriends);
        for(int i=0;i<count;i++){
            FriendRow friend=friends.get(i+scrollFriends);
            int rowY=y+163+i*48;
            if(event.y()<rowY||event.y()>rowY+43)continue;
            if("ACCEPTED".equals(friend.status())){
                if(event.x()>=x+colW-110&&event.x()<=x+colW-56){
                    createGame(friend.username(),"CHESS");return true;
                }
                if(event.x()>=x+colW-52&&event.x()<=x+colW+16){
                    createGame(friend.username(),"TIC_TAC_TOE");return true;
                }
            }else if(friend.incoming()&&event.x()>=x+colW-112&&event.x()<=x+colW+16){
                ProjectVisualApiClient.respondToFriendRequest(friend.id(),true,response->{
                    message="Запрос в друзья принят.";refreshRows();
                });
                return true;
            }
        }
        for(GameRow game:games){
            int rowY=y+88+games.indexOf(game)*77;
            if(event.y()<rowY||event.y()>rowY+68||event.x()<rightX+colW-100||event.x()>rightX+colW+8)continue;
            if("INVITED".equals(game.status())&&game.player2Id()!=null&&game.player2Id().equals(localUserId)){
                ProjectVisualApiClient.acceptGame(game.id(),response->openGame(game.id()));
            }else if("ACTIVE".equals(game.status())){
                openGame(game.id());
            }
            return true;
        }
        return true;
    }

    private void createGame(String username,String type){
        ProjectVisualApiClient.createGame(username,type,response->{
            try{
                JsonObject root=JsonParser.parseString(response).getAsJsonObject();
                message=root.has("error")?root.get("error").getAsString():"Приглашение отправлено.";
            }catch(RuntimeException e){message="Не удалось создать матч.";}
            refreshRows();
        });
    }

    private void openGame(String gameId){
        ProjectVisualApiClient.getGame(gameId,response->{
            if(!OnlineMatchSession.attach(response)){
                message=OnlineMatchSession.lastError().isBlank()?"Не удалось открыть матч.":OnlineMatchSession.lastError();
                return;
            }
            var match=OnlineMatchSession.snapshot();
            if(match==null)return;
            if(match.isChess()){
                Minecraft.getInstance().setScreen(null);
                ChessWorldGame.startOnline();
            }else if(match.isTicTacToe()){
                Minecraft.getInstance().setScreen(new TicTacToeScreen(true));
            }
        });
    }

    @Override public void onClose(){
        Minecraft.getInstance().setScreen(new ClickGuiScreen(0,2));
    }

    @Override public boolean isPauseScreen(){return false;}
}
