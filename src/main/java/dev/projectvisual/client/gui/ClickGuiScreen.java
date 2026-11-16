package dev.projectvisual.client.gui;

import dev.projectvisual.client.hud.VisualHud;
import dev.projectvisual.client.chess.ChessWorldGame;
import dev.projectvisual.client.minesweeper.MinesweeperWorldGame;
import dev.projectvisual.client.VisualFeatures;
import dev.projectvisual.client.gui.SkinManagerScreen;
import dev.projectvisual.client.gui.ModuleSettingsScreen;
import dev.projectvisual.client.gui.TicTacToeScreen;
import dev.projectvisual.client.hud.HudWindowManager;
import dev.projectvisual.client.hud.HudEditorScreen;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.gui.components.EditBox;

public final class ClickGuiScreen extends Screen {
    private static final int BG=0xF00D0F18, SIDE=0xF0161925, CARD=0xFF1D2130, HOVER=0xFF282D40;
    private static final int TEXT=0xFFF3F0FF, MUTED=0xFFAAA6BA;
    private static final String[] CATS={"HUD","Combat","Player","World","Animations","Camera","Interface","Cheat","Skins"};
    private static final String[] RIGHT={"Settings","Minigames","Friends"};
    private static final String[][] ITEMS={
        {"FPS Counter","Ping","CPS Counter","Keystrokes","Armor HUD","Potion HUD","Target HUD","Array List"},
        {"Hit Particles","Damage Tint","Hit Color","Crosshair","Target Info","Totem Counter"},
        {"China Hat","Player Trails","Name Tags","Item Physics","Hand Cosmetics","Cape Effects"},
        {"Fullbright","Weather","Time Changer","Ambience","Block Overlay","Breadcrumbs"},
        {"Swing Animation","Old Animations","Item Sway","Eating Animation","Rod Animation","Shield Animation"},
        {"View Model","Camera Clip","Zoom","Smooth Camera","Perspective"},
        {"Notifications","Watermark","Module List","Custom Font","Color Theme","Trainer"},
        {"Hitboxes","Chest ESP","Player ESP","Hitbox Visual"},
        {"Skin"}
    };
    private static final String[][] DESC={
        {"Performance counter","Connection latency","Clicks per second","Movement key display","Equipment durability","Active potion effects","Target information","Enabled module list"},
        {"Particles on confirmed hurt event","Screen-edge flash on damage","Green feedback on entity target","Replaces vanilla crosshair","Target name, health, armor and distance","Inventory count and pop counter"},
        {"Hat above player","Colored movement traces","Custom name labels","Dropped item rotation","Наручи и рунические браслеты на руках","Анимированная накидка с фиолетовыми швами"},
        {"Brighter world","Weather visuals","Local world time","World color mood","Highlight selected blocks","Movement path markers"},
        {"Custom hand swing","Classic item animations","Subtle item movement","Food and drink motion","Fishing rod animation","Shield raise animation"},
        {"Hand position and scale","Camera passes through blocks in third person","Quick zoom control","Smoother camera motion","Lock third-person view (back/front)"},
        {"On-screen alerts","Client branding","Visible enabled modules","В разработке","Accent colors","PvP quests in the top-right corner"},
        {"Meteor-style player targeting expansion (+0.5)","See chests and containers through walls","Outline nearby players","Visual-only player hitbox outline"},
        {"Open the complete skin library and training cases"},
    };
    private int category=0, rightTab=-1, scroll=0;
    private static int language=0;
    private static boolean secretTabUnlocked;
    private EditBox accentHexBox;

    public ClickGuiScreen(){this(0);}

    public ClickGuiScreen(int initialCategory){
        this(initialCategory, -1);
    }

    public ClickGuiScreen(int initialCategory, int initialRightTab){
        super(Component.literal("PROJECT VISUAL"));
        int maxCategory = secretTabUnlocked ? CATS.length - 1 : CATS.length - 2;
        this.category = Math.max(0, Math.min(maxCategory, initialCategory));
        this.rightTab = initialRightTab;
    }

    public static void unlockSecretTab() {
        secretTabUnlocked = true;
    }

    @Override
    protected void init() {
        super.init();
        int pw=Math.min(1000,width-24);
        int ph=Math.min(540,height-24);
        int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154;
        int mainX=x+left+16;
        accentHexBox = new EditBox(font, mainX, y+202, 112, 20, Component.literal("Accent HEX"));
        accentHexBox.setMaxLength(6);
        accentHexBox.setFilter(value -> value.matches("[0-9a-fA-F]{0,6}"));
        accentHexBox.setValue(String.format("%06X", VisualFeatures.customAccentColor() & 0x00FFFFFF));
        accentHexBox.setResponder(value -> {
            if (value.length() == 6) {
                try {
                    VisualFeatures.setCustomAccentColor(Integer.parseInt(value, 16));
                } catch (NumberFormatException ignored) {
                    // The edit box filter already limits input to hexadecimal characters.
                }
            }
        });
        accentHexBox.visible = rightTab == 0;
        addRenderableWidget(accentHexBox);
    }

    private String tr(String en,String ru,String kk){return language==1?ru:language==2?kk:en;}

    private String[] categories() {
        String[] all = language == 1
                ? new String[]{"HUD","Бой","Игрок","Мир","Анимации","Камера","Интерфейс","Читы","Скины"}
                : language == 2
                ? new String[]{"HUD","Ұрыс","Ойыншы","Әлем","Анимация","Камера","Интерфейс","Читтер","Скиндер"}
                : CATS;
        if (secretTabUnlocked) return all;
        String[] visible = new String[all.length - 1];
        System.arraycopy(all, 0, visible, 0, 7);
        visible[7] = all[8];
        return visible;
    }

    private int itemCategoryIndex() {
        return !secretTabUnlocked && category >= 7 ? category + 1 : category;
    }
    private String[] rightTabs(){return language==1?new String[]{"Настройки","Мини-игры","Друзья"}
        :language==2?new String[]{"Баптаулар","Мини-ойындар","Достар"}:RIGHT;}

    @Override public void render(GuiGraphics g,int mx,int my,float tick){
        if (accentHexBox != null) accentHexBox.visible = rightTab == 0;
        g.fill(0,0,width,height,0x99000000);
        int pw=Math.min(1000,width-24), ph=Math.min(540,height-24);
        int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154;
        int mainX=x+left+16, mainW=pw-left-right-48, rightX=x+pw-right;
        g.fill(x,y,x+pw,y+ph,BG); g.fill(x,y,x+pw,y+3,VisualFeatures.accentColor());
        g.fill(x,y,x+left,y+ph,SIDE); g.fill(rightX,y,rightX+right,y+ph,SIDE);
        g.fill(x+left,y+55,x+left+1,y+ph-18,0x553F3B50);
        g.fill(rightX,y+55,rightX+1,y+ph-18,0x553F3B50);
        g.drawString(font,"PROJECT VISUAL",x+16,y+16,TEXT,false);
        g.drawString(font,"CLIENT  /  0.3",x+16,y+33,MUTED,false);
        g.drawString(font,tr("MODULES","МОДУЛИ","МОДУЛЬДЕР"),x+16,y+65,VisualFeatures.accentColor(),false);
        String[] cats=categories();
        for(int i=0;i<cats.length;i++){
            int cy=y+86+i*34; boolean a=i==category;
            if(a||(mx>=x+9&&mx<=x+left-9&&my>=cy-5&&my<=cy+22))
                g.fill(x+9,cy-5,x+left-9,cy+22,a?0x553F3568:HOVER);
            if(a)g.fill(x+9,cy-5,x+11,cy+22,VisualFeatures.accentColor());
            g.drawString(font,cats[i],x+20,cy+3,a?TEXT:MUTED,false);
        }
        g.drawString(font,tr("WORKSPACE","РАБОЧАЯ ОБЛАСТЬ","ЖҰМЫС АЙМАҒЫ"),rightX+15,y+65,VisualFeatures.accentColor(),false);
        String[] tabs=rightTabs();
        for(int i=0;i<tabs.length;i++){
            int cy=y+86+i*38; boolean a=i==rightTab;
            if(a||(mx>=rightX+8&&mx<=rightX+right-8&&my>=cy-5&&my<=cy+23))
                g.fill(rightX+8,cy-5,rightX+right-8,cy+23,a?0x553F3568:HOVER);
            if(a)g.fill(rightX+8,cy-5,rightX+10,cy+23,VisualFeatures.accentColor());
            g.drawString(font,tabs[i],rightX+19,cy+4,a?TEXT:MUTED,false);
        }
        if(rightTab>=0){
            renderRight(g,mx,my,mainX,mainW,y,ph);
        }else{
            String[] names=ITEMS[itemCategoryIndex()], descriptions=DESC[itemCategoryIndex()];
            g.drawString(font,cats[category],mainX,y+19,TEXT,false);
            g.drawString(font,tr("Visual modules / FPS-first","Визуальные модули / приоритет FPS","Визуалды модульдер / FPS басымдығы"),mainX,y+37,MUTED,false);
            int cardW=(mainW-12)/2, rows=Math.max(1,(ph-120)/58);
            int start=Math.min(scroll,Math.max(0,names.length-rows*2));
            for(int n=start;n<Math.min(names.length,start+rows*2);n++){
                int col=(n-start)%2,row=(n-start)/2,cx=mainX+col*(cardW+12),cy=y+74+row*58;
                boolean hover=mx>=cx&&mx<=cx+cardW&&my>=cy&&my<=cy+48;
                g.fill(cx,cy,cx+cardW,cy+48,hover?HOVER:CARD);
                boolean available = isModuleAvailable(names[n]);
                boolean active = names[n].equals("Skin") || (available && VisualFeatures.isEnabled(names[n]));
                g.fill(cx,cy,cx+2,cy+48,active?VisualFeatures.accentColor():0xFF45495A);
                g.drawString(font,names[n],cx+10,cy+7,TEXT,false);
                g.drawString(font,descriptions[n],cx+10,cy+23,MUTED,false);
                String state = names[n].equals("Skin") ? "OPEN" : !available ? "SOON" : active ? "ON" : "OFF";
                g.drawString(font,state,cx+cardW-font.width(state)-8,cy+7,!available?0xFFD7A85D:active?0xFF83E0B0:MUTED,false);
            }
        }
        g.drawString(font,"RIGHT SHIFT  /  ESC",x+16,y+ph-23,MUTED,false);
        g.drawString(font,"PROJECT VISUAL  •  1.21.11",rightX+12,y+ph-23,VisualFeatures.accentColor(),false);
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
            g.drawString(font,tr("Custom visual color (HEX RGB)","Свой цвет визуалов (HEX RGB)","Визуал түсі (HEX RGB)"),x,y+176,MUTED,false);
            g.drawString(font,"#",x+118,y+208,VisualFeatures.accentColor(),false);
            g.fill(x+145,y+202,x+171,y+222,VisualFeatures.accentColor());
            g.drawString(font,tr("Examples:","Примеры:","Мысалдар:"),x+184,y+208,MUTED,false);
            int[] swatches={0xFF54D6C8,0xFF9B7BFF,0xFFFF5577,0xFFFFC857,0xFF61A8FF,0xFFFFFFFF};
            for(int i=0;i<swatches.length;i++){
                int sx=x+184+i*22;
                g.fill(sx,y+222,sx+15,y+237,swatches[i]);
                g.fill(sx,y+222,sx+15,y+223,0xFF000000);
            }
            g.drawString(font, "HUD EDITOR", x, y + 267, VisualFeatures.accentColor(), false);
            g.fill(x, y + 282, x + 174, y + 310,
                    HudWindowManager.isEditMode() ? 0xFF3A315E : 0xFF292E40);
            g.drawCenteredString(font, "EDIT HUD  →", x + 87, y + 292, TEXT);
            g.drawString(font, "H — выйти из редактора", x, y + 318, MUTED, false);
        }else if(rightTab==1){
            g.drawString(font,tr("Minigames","Мини-игры","Мини-ойындар"),x,y+19,TEXT,false);
            int by=y+48;
            g.fill(x,by,x+w,by+76,CARD);
            g.fill(x,by,x+3,by+76,VisualFeatures.accentColor());
            g.drawString(font,"Chess",x+12,by+10,TEXT,false);
            g.drawString(font,tr("3D board in the world  /  Medium AI","3D-доска в мире  /  Средний ИИ","3D тақта  /  Орташа ЖИ"),x+12,by+28,MUTED,false);
            g.drawString(font,tr("OPEN  >","ОТКРЫТЬ  >","АШУ  >"),x+12,by+51,VisualFeatures.accentColor(),false);
            g.fill(x,by+88,x+w,by+164,CARD);
            g.fill(x,by+88,x+3,by+164,VisualFeatures.accentColor());
            g.drawString(font,"Minesweeper",x+12,by+98,TEXT,false);
            g.drawString(font,tr("3D board  /  10 mines  /  Safe first click","3D-доска  /  10 мин  /  Первый ход безопасен","3D тақта  /  10 мина  /  Бірінші жүріс қауіпсіз"),x+12,by+116,MUTED,false);
            g.drawString(font,tr("OPEN  >","ОТКРЫТЬ  >","АШУ  >"),x+12,by+139,VisualFeatures.accentColor(),false);
            g.fill(x,by+176,x+w,by+252,CARD);
            g.fill(x,by+176,x+3,by+252,VisualFeatures.accentColor());
            g.drawString(font,"Pick the Lock",x+12,by+186,TEXT,false);
            g.drawString(font,tr("Timing game  /  Watcher replaces Slark","Тайминг-игра  /  вместо Сларка — Смотрящий","Тайминг ойыны  /  Сларк орнына — Бақылаушы"),x+12,by+204,MUTED,false);
            g.drawString(font,tr("OPEN  >","ОТКРЫТЬ  >","АШУ  >"),x+12,by+227,VisualFeatures.accentColor(),false);
            g.fill(x,by+264,x+w,by+340,CARD);
            g.fill(x,by+264,x+3,by+340,VisualFeatures.accentColor());
            g.drawString(font,"Крестики-нолики",x+12,by+274,TEXT,false);
            g.drawString(font,"Ты — X, бот — O  /  Минимакс ИИ",x+12,by+292,MUTED,false);
            g.drawString(font,"ИГРАТЬ  >",x+12,by+315,VisualFeatures.accentColor(),false);
            g.drawString(font,tr("Chess: right-click to move. Minesweeper: right-click reveal, Shift+right-click flag. Lockpick: click on the moving marker.","Шахматы: ПКМ ходить. Сапёр: ПКМ открыть, Shift+ПКМ флаг. Взлом: ЛКМ по маркеру в цветной зоне.","Шахмат: оң жақпен жүру. Сапер: ашу — оң жақ, жалау — Shift+оң жақ. Құлып: түсті аймақта бас."),x,y+356,MUTED,false);
        }else{
            g.drawString(font,tr("Friends","Друзья","Достар"),x,y+19,TEXT,false);
            g.drawString(font,tr("Find other PROJECT VISUAL users.","Найти пользователей PROJECT VISUAL.","PROJECT VISUAL қолданушыларын табу."),x,y+54,MUTED,false);
            g.drawString(font,tr("Online discovery needs a shared","Для поиска онлайн нужен общий","Онлайн іздеу үшін ортақ сервер"),x,y+76,MUTED,false);
            g.drawString(font,tr("presence server; not connected yet.","сервер присутствия; пока не подключён.","қажет; әзірге қосылмаған."),x,y+90,MUTED,false);
        }
    }

    private static void showHudWindow(String module) {
        String id = switch (module) {
            case "Trainer" -> "trainer";
            case "Watermark", "FPS Counter", "Ping" -> "watermark";
            case "Keystrokes", "CPS Counter" -> "controls";
            case "Armor HUD", "Potion HUD", "Totem Counter" -> "player_stats";
            case "Target HUD", "Target Info" -> "target";
            case "Array List", "Module List" -> "arraylist";
            case "Notifications" -> "notifications";
            default -> null;
        };
        if (id != null) HudWindowManager.setVisible(id, true);
    }

    private static boolean isModuleAvailable(String module) {
        return !module.equals("Custom Font");
    }

    @Override public boolean mouseClicked(MouseButtonEvent e,boolean dbl){
        double mx=e.x(),my=e.y(); int pw=Math.min(1000,width-24),ph=Math.min(540,height-24);
        if(e.button()==1 && rightTab<0){
            int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154;
            int mainX=x+left+16,mainW=pw-left-right-48,cardW=(mainW-12)/2;
            String[] names=ITEMS[itemCategoryIndex()];
            int rows=Math.max(1,(ph-120)/58);
            int start=Math.min(scroll,Math.max(0,names.length-rows*2));
            for(int n=start;n<Math.min(names.length,start+rows*2);n++){
                int col=(n-start)%2,row=(n-start)/2;
                int cx=mainX+col*(cardW+12),cy=y+74+row*58;
                if(mx>=cx&&mx<=cx+cardW&&my>=cy&&my<=cy+48){
                    String module=names[n];
                    if(module.equals("Skin")){
                        Minecraft.getInstance().setScreen(new SkinManagerScreen());
                    }else if(isModuleAvailable(module)){
                        Minecraft.getInstance().setScreen(new ModuleSettingsScreen(module,category));
                    }
                    return true;
                }
            }
            return true;
        }
        if(e.button()!=0)return super.mouseClicked(e,dbl);

        int x=(width-pw)/2,y=(height-ph)/2,left=160,right=154,rightX=x+pw-right;
        String[] cats=categories(),tabs=rightTabs();
        for(int i=0;i<cats.length;i++){int cy=y+86+i*34;if(mx>=x+9&&mx<=x+left-9&&my>=cy-5&&my<=cy+22){category=i;rightTab=-1;scroll=0;return true;}}
        for(int i=0;i<tabs.length;i++){int cy=y+86+i*38;if(mx>=rightX+8&&mx<=rightX+right-8&&my>=cy-5&&my<=cy+23){rightTab=i;return true;}}
        if(rightTab==0){
            int mainX=x+left+16,mainW=pw-left-right-48;
            for(int i=0;i<3;i++){int bx=mainX+i*(mainW/3),by=y+78,bw=mainW/3-5;if(mx>=bx&&mx<=bx+bw&&my>=by&&my<=by+30){language=i;return true;}}
            int[] swatches={0xFF54D6C8,0xFF9B7BFF,0xFFFF5577,0xFFFFC857,0xFF61A8FF,0xFFFFFFFF};
            for(int i=0;i<swatches.length;i++){
                int sx=mainX+184+i*22;
                if(mx>=sx&&mx<=sx+15&&my>=y+222&&my<=y+237){
                    VisualFeatures.setCustomAccentColor(swatches[i]);
                    if(accentHexBox!=null)accentHexBox.setValue(String.format("%06X",swatches[i]&0x00FFFFFF));
                    return true;
                }
            }
            if(mx>=mainX&&mx<=mainX+174&&my>=y+282&&my<=y+310){
                Minecraft.getInstance().setScreen(new HudEditorScreen());
                return true;
            }
        }else if(rightTab==1){
            int mainX=x+left+16,mainW=pw-left-right-48,by=y+48;
            if(mx>=mainX&&mx<=mainX+mainW&&my>=by&&my<=by+76){
                ChessWorldGame.start();
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if(mx>=mainX&&mx<=mainX+mainW&&my>=by+88&&my<=by+164){
                MinesweeperWorldGame.start();
                Minecraft.getInstance().setScreen(null);
                return true;
            }
            if(mx>=mainX&&mx<=mainX+mainW&&my>=by+176&&my<=by+252){
                Minecraft.getInstance().setScreen(new LockpickScreen());
                return true;
            }
            if(mx>=mainX&&mx<=mainX+mainW&&my>=by+264&&my<=by+340){
                Minecraft.getInstance().setScreen(new TicTacToeScreen());
                return true;
            }
        }else if(rightTab<0){
            int mainX=x+left+16,mainW=pw-left-right-48,cardW=(mainW-12)/2;
            String[] names=ITEMS[itemCategoryIndex()];
            int rows=Math.max(1,(ph-120)/58);
            int start=Math.min(scroll,Math.max(0,names.length-rows*2));
            for(int n=start;n<Math.min(names.length,start+rows*2);n++){
                int col=(n-start)%2,row=(n-start)/2;
                int cx=mainX+col*(cardW+12),cy=y+74+row*58;
                if(mx>=cx&&mx<=cx+cardW&&my>=cy&&my<=cy+48){
                    String module=names[n];
                    if(!isModuleAvailable(module)){
                        if (Minecraft.getInstance().player != null) {
                            Minecraft.getInstance().player.displayClientMessage(
                                    Component.literal("§eЭтот модуль пока в разработке."), true);
                        }
                        return true;
                    }
                    if(module.equals("Skin")){
                        Minecraft.getInstance().setScreen(new SkinManagerScreen());
                        return true;
                    }
                    VisualFeatures.toggle(module);
                    boolean enabled = VisualFeatures.isEnabled(module);
                    VisualHud.notifyToggle(module, enabled);
                    if(module.equals("Player ESP")) VisualFeatures.setPlayerEsp(enabled);
                    if(enabled) showHudWindow(module);
                    return true;
                }
            }
        }
        return super.mouseClicked(e,dbl);
    }
    @Override public boolean mouseScrolled(double x,double y,double dx,double dy){scroll=Math.max(0,Math.min(8,scroll+(dy<0?1:-1)));return true;}
    @Override public boolean isPauseScreen(){return false;}
}
