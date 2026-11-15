package dev.projectvisual.client.gui;

import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.VisualFeatures;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;

public final class ClickGuiScreen extends Screen {
    private static final int BG=0xF00D0F18, SIDE=0xF0161925, CARD=0xFF1D2130, HOVER=0xFF282D40;
    private static final int ACCENT=0xFF9B7BFF, TEXT=0xFFF3F0FF, MUTED=0xFFAAA6BA;
    private static final String[] CATS={"HUD","Combat","Player","World","Animations","Camera","Interface","Cheat","Skins"};
    private static final String[] RIGHT={"Settings","Minigames","Friends"};
    private static final String[][] ITEMS={
        {"FPS Counter","Ping","CPS Counter","Keystrokes","Armor HUD","Potion HUD","Target HUD","Array List"},
        {"Hit Particles","Damage Tint","Hit Color","Crosshair","Target Info","Totem Counter"},
        {"China Hat","Player Trails","Name Tags","Item Physics","Hand Cosmetics","Cape Effects"},
        {"Fullbright","Weather","Time Changer","Ambience","Block Overlay","Breadcrumbs"},
        {"Swing Animation","Old Animations","Item Sway","Eating Animation","Rod Animation","Shield Animation"},
        {"View Model","Camera Clip","Zoom","Smooth Camera","Perspective"},
        {"Notifications","Watermark","Module List","Custom Font","Color Theme"},
        {"Player ESP","Hitbox Visual"},
        {"Skin Manager","Skin Preview","Capes","Outfits"}
    };
    private static final String[][] DESC={
        {"Performance counter","Connection latency","Clicks per second","Movement key display","Equipment durability","Active potion effects","Target information","Enabled module list"},
        {"Particles on hit","Damage flash","Custom hit feedback","Crosshair styles","Target details","Totem pop tracking"},
        {"Hat above player","Colored movement traces","Custom name labels","Dropped item rotation","Hand appearance","Cape visuals"},
        {"Brighter world","Weather visuals","Local world time","World color mood","Highlight selected blocks","Movement path markers"},
        {"Custom hand swing","Classic item animations","Subtle item movement","Food and drink motion","Fishing rod animation","Shield raise animation"},
        {"Hand position and scale","Camera collision options","Quick zoom control","Smoother camera motion","Third-person perspective"},
        {"On-screen alerts","Client branding","Visible enabled modules","Typography settings","Accent colors"},
        {"Outline nearby players","Visual-only hitbox outline"},
        {"Choose player skins","Preview before applying","Cape collection","Saved appearance presets"}
    };
    private int category=0, rightTab=-1, scroll=0;
    private static int language=0;

    public ClickGuiScreen(){super(Component.literal("PROJECT VISUAL"));}
    private String tr(String en,String ru,String kk){return language==1?ru:language==2?kk:en;}
    private String[] categories(){return language==1
        ?new String[]{"HUD","Бой","Игрок","Мир","Анимации","Камера","Интерфейс","Читы","Скины"}
        :language==2?new String[]{"HUD","Ұрыс","Ойыншы","Әлем","Анимация","Камера","Интерфейс","Читтер","Скиндер"}:CATS;}
    private String[] rightTabs(){return language==1?new String[]{"Настройки","Мини-игры","Друзья"}
        :language==2?new String[]{"Баптаулар","Мини-ойындар","Достар"}:RIGHT;}

    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        g.fill(0,0,width,height,0x99000000);
        int pw=Math.min(1000,width-24), ph=Math.min(540,height-24);
        int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154;
        int mainX=x+left+16, mainW=pw-left-right-48, rightX=x+pw-right;
        g.fill(x,y,x+pw,y+ph,BG); g.fill(x,y,x+pw,y+3,ACCENT);
        g.fill(x,y,x+left,y+ph,SIDE); g.fill(rightX,y,rightX+right,y+ph,SIDE);
        g.fill(x+left,y+55,x+left+1,y+ph-18,0x553F3B50);
        g.fill(rightX,y+55,rightX+1,y+ph-18,0x553F3B50);
        g.drawString(font,"PROJECT VISUAL",x+16,y+16,TEXT,false);
        g.drawString(font,"CLIENT  /  0.3",x+16,y+33,MUTED,false);
        g.drawString(font,tr("MODULES","МОДУЛИ","МОДУЛЬДЕР"),x+16,y+65,ACCENT,false);
        String[] cats=categories();
        for(int i=0;i<cats.length;i++){
            int cy=y+86+i*34; boolean a=i==category;
            if(a||(mx>=x+9&&mx<=x+left-9&&my>=cy-5&&my<=cy+22))
                g.fill(x+9,cy-5,x+left-9,cy+22,a?0x553F3568:HOVER);
            if(a)g.fill(x+9,cy-5,x+11,cy+22,ACCENT);
            g.drawString(font,cats[i],x+20,cy+3,a?TEXT:MUTED,false);
        }
        g.drawString(font,tr("WORKSPACE","РАБОЧАЯ ОБЛАСТЬ","ЖҰМЫС АЙМАҒЫ"),rightX+15,y+65,ACCENT,false);
        String[] tabs=rightTabs();
        for(int i=0;i<tabs.length;i++){
            int cy=y+86+i*38; boolean a=i==rightTab;
            if(a||(mx>=rightX+8&&mx<=rightX+right-8&&my>=cy-5&&my<=cy+23))
                g.fill(rightX+8,cy-5,rightX+right-8,cy+23,a?0x553F3568:HOVER);
            if(a)g.fill(rightX+8,cy-5,rightX+10,cy+23,ACCENT);
            g.drawString(font,tabs[i],rightX+19,cy+4,a?TEXT:MUTED,false);
        }
        if(rightTab>=0){
            renderRight(g,mx,my,mainX,mainW,y,ph);
        }else{
            String[] names=ITEMS[category], descriptions=DESC[category];
            g.drawString(font,cats[category],mainX,y+19,TEXT,false);
            g.drawString(font,tr("Visual modules / FPS-first","Визуальные модули / приоритет FPS","Визуалды модульдер / FPS басымдығы"),mainX,y+37,MUTED,false);
            int cardW=(mainW-12)/2, rows=Math.max(1,(ph-120)/58);
            int start=Math.min(scroll,Math.max(0,names.length-rows*2));
            for(int n=start;n<Math.min(names.length,start+rows*2);n++){
                int col=(n-start)%2,row=(n-start)/2,cx=mainX+col*(cardW+12),cy=y+74+row*58;
                boolean live=(category==0&&n==0)||(category==7);
                boolean hover=mx>=cx&&mx<=cx+cardW&&my>=cy&&my<=cy+48;
                g.fill(cx,cy,cx+cardW,cy+48,hover?HOVER:CARD);
                g.fill(cx,cy,cx+2,cy+48,live?ACCENT:0xFF45495A);
                g.drawString(font,names[n],cx+10,cy+7,TEXT,false);
                g.drawString(font,descriptions[n],cx+10,cy+23,MUTED,false);
                String state = "COMING SOON";
                boolean active = false;
                if (category == 0 && n == 0) { state = VisualHud.isEnabled() ? "ON" : "OFF"; active = VisualHud.isEnabled(); }
                if (category == 7 && n == 0) { state = VisualFeatures.isPlayerEsp() ? "ON" : "OFF"; active = VisualFeatures.isPlayerEsp(); }
                if (category == 7 && n == 1) { state = "IN DEVELOPMENT"; }
                g.drawString(font,state,cx+cardW-font.width(state)-8,cy+7,active?0xFF83E0B0:ACCENT,false);
            }
        }
        g.drawString(font,"RIGHT SHIFT  /  ESC",x+16,y+ph-23,MUTED,false);
        g.drawString(font,"PROJECT VISUAL  •  1.21.11",rightX+12,y+ph-23,ACCENT,false);
        super.render(g,mx,my,tick);
    }

    private void renderRight(GuiGraphics g,int mx,int my,int x,int w,int y,int ph){
        if(rightTab==0){
            g.drawString(font,tr("Settings","Настройки","Баптаулар"),x,y+19,TEXT,false);
            g.drawString(font,tr("Interface language","Язык интерфейса","Интерфейс тілі"),x,y+52,MUTED,false);
            String[] langs={"English","Русский","Қазақша"};
            for(int i=0;i<3;i++){
                int bx=x+i*(w/3), by=y+78, bw=w/3-5;
                g.fill(bx,by,bx+bw,by+30,i==language?0x663F3568:CARD);
                g.drawString(font,langs[i],bx+5,by+10,i==language?TEXT:MUTED,false);
            }
            g.drawString(font,tr("Changes apply instantly.","Изменения применяются сразу.","Өзгерістер бірден қолданылады."),x,y+128,MUTED,false);
            g.drawString(font,tr("More settings will be added later.","Другие настройки появятся позже.","Басқа баптаулар кейін қосылады."),x,y+148,MUTED,false);
        }else if(rightTab==1){
            g.drawString(font,tr("Minigames","Мини-игры","Мини-ойындар"),x,y+19,TEXT,false);
            g.drawString(font,tr("Coming in a future update.","Появятся в будущем обновлении.","Келесі жаңартуда қосылады."),x,y+54,MUTED,false);
        }else{
            g.drawString(font,tr("Friends","Друзья","Достар"),x,y+19,TEXT,false);
            g.drawString(font,tr("Find other PROJECT VISUAL users.","Найти пользователей PROJECT VISUAL.","PROJECT VISUAL қолданушыларын табу."),x,y+54,MUTED,false);
            g.drawString(font,tr("Online discovery needs a shared","Для поиска онлайн нужен общий","Онлайн іздеу үшін ортақ сервер"),x,y+76,MUTED,false);
            g.drawString(font,tr("presence server; not connected yet.","сервер присутствия; пока не подключён.","қажет; әзірге қосылмаған."),x,y+90,MUTED,false);
        }
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        if(e.button()!=0)return super.mouseClicked(e,dbl);
        double mx=e.x(),my=e.y(); int pw=Math.min(1000,width-24),ph=Math.min(540,height-24);
        int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154,rightX=x+pw-right;
        String[] cats=categories(),tabs=rightTabs();
        for(int i=0;i<cats.length;i++){int cy=y+86+i*34;if(mx>=x+9&&mx<=x+left-9&&my>=cy-5&&my<=cy+22){category=i;rightTab=-1;scroll=0;return true;}}
        for(int i=0;i<tabs.length;i++){int cy=y+86+i*38;if(mx>=rightX+8&&mx<=rightX+right-8&&my>=cy-5&&my<=cy+23){rightTab=i;return true;}}
        if(rightTab==0){
            int mainX=x+left+16,mainW=pw-left-right-48;
            for(int i=0;i<3;i++){int bx=mainX+i*(mainW/3),by=y+78,bw=mainW/3-5;if(mx>=bx&&mx<=bx+bw&&my>=by&&my<=by+30){language=i;return true;}}
        }else if(rightTab<0&&category==0){
            int mainX=x+left+16,mainW=pw-left-right-48,cardW=(mainW-12)/2;
            if(mx>=mainX&&mx<=mainX+cardW&&my>=y+74&&my<=y+122){VisualHud.setEnabled(!VisualHud.isEnabled());return true;}
        }
        if(rightTab<0&&category==7){
            int mainX=x+left+16,mainW=pw-left-right-48,cardW=(mainW-12)/2;
            int rows=Math.max(1,(ph-120)/58);
            int cardY=y+74;
            if(mx>=mainX&&mx<=mainX+cardW&&my>=cardY&&my<=cardY+48){VisualFeatures.setPlayerEsp(!VisualFeatures.isPlayerEsp());return true;}
            int hitX=mainX+cardW+12;
        }
        return super.mouseClicked(e,dbl);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(8,scroll+(dy<0?1:-1)));return true;}
    @Override public boolean isPauseScreen(){return false;}
}
