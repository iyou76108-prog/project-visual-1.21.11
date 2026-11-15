package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import java.util.Map;

public final class VisualHud {
    private static final int PANEL=0xC910121B, ACCENT=0xFF9B7BFF, TEXT=0xFFF3F0FF, MUTED=0xFFAAA6BA;
    private static boolean enabled=true;
    private VisualHud(){}
    public static boolean isEnabled(){return enabled;}
    public static void setEnabled(boolean value){enabled=value;}
    public static void register(){
        HudElementRegistry.attachElementBefore(VanillaHudElements.CHAT,
            Identifier.fromNamespaceAndPath(ProjectVisualClient.MOD_ID,"visual_hud"),VisualHud::render);
    }
    private static void render(GuiGraphics g,net.minecraft.client.DeltaTracker tickCounter){
        Minecraft mc=Minecraft.getInstance();
        if(!enabled||mc.options.hideGui||mc.player==null||mc.level==null)return;
        int x=10,y=10;
        if(VisualFeatures.isEnabled("Watermark")){
            g.fill(x,y,x+142,y+40,PANEL); g.fill(x,y,x+2,y+40,ACCENT);
            g.fill(x+10,y+9,x+15,y+14,ACCENT);
            g.drawString(mc.font,"PROJECT VISUAL",x+21,y+7,TEXT,false);
            if(VisualFeatures.isEnabled("FPS Counter")){
                g.drawString(mc.font,"FPS  "+mc.getFps(),x+10,y+24,MUTED,false);
            }
            if(VisualFeatures.isEnabled("Ping")){
                var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if(info!=null)g.drawString(mc.font,"PING  "+info.getLatency()+" ms",x+77,y+24,ACCENT,false);
            }
        }else if(VisualFeatures.isEnabled("FPS Counter")){
            g.drawString(mc.font,"FPS  "+mc.getFps(),x,y,TEXT,false);
        }
        if(VisualFeatures.isEnabled("Array List")||VisualFeatures.isEnabled("Module List")){
            int yy=10;
            for(Map.Entry<String,Boolean> entry:VisualFeatures.getStates().entrySet()){
                if(entry.getValue()&&!entry.getKey().equals("Watermark")&&!entry.getKey().equals("Array List")&&!entry.getKey().equals("Module List")){
                    int w=mc.font.width(entry.getKey());
                    g.fill(mc.getWindow().getGuiScaledWidth()-w-18,yy-2,mc.getWindow().getGuiScaledWidth()-8,yy+10,PANEL);
                    g.fill(mc.getWindow().getGuiScaledWidth()-9,yy-2,mc.getWindow().getGuiScaledWidth()-8,yy+10,ACCENT);
                    g.drawString(mc.font,entry.getKey(),mc.getWindow().getGuiScaledWidth()-w-14,yy,TEXT,false);
                    yy+=13;
                }
            }
        }
        if(VisualFeatures.isEnabled("Crosshair")){
            int cx=mc.getWindow().getGuiScaledWidth()/2,cy=mc.getWindow().getGuiScaledHeight()/2;
            g.fill(cx-5,cy,cx+5,cy+1,0xFFFFFFFF);
            g.fill(cx,cy-5,cx+1,cy+5,0xFFFFFFFF);
        }
    }
}
