package dev.projectvisual.client.hud;

import dev.projectvisual.client.ProjectVisualClient;
import dev.projectvisual.client.VisualFeatures;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
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
    private static void line(GuiGraphics g,Minecraft mc,String value,int x,int y){
        int w=mc.font.width(value);
        g.fill(x-4,y-3,x+w+5,y+10,PANEL);
        g.drawString(mc.font,value,x,y,TEXT,false);
    }
    private static void render(GuiGraphics g,net.minecraft.client.DeltaTracker tickCounter){
        Minecraft mc=Minecraft.getInstance();
        if(!enabled||mc.options.hideGui||mc.player==null||mc.level==null)return;
        int x=10,y=10;
        if(VisualFeatures.isEnabled("Watermark")){
            g.fill(x,y,x+142,y+40,PANEL); g.fill(x,y,x+2,y+40,ACCENT);
            g.fill(x+10,y+9,x+15,y+14,ACCENT);
            g.drawString(mc.font,"PROJECT VISUAL",x+21,y+7,TEXT,false);
            if(VisualFeatures.isEnabled("FPS Counter"))g.drawString(mc.font,"FPS  "+mc.getFps(),x+10,y+24,MUTED,false);
            if(VisualFeatures.isEnabled("Ping")){
                var info=mc.getConnection()==null?null:mc.getConnection().getPlayerInfo(mc.player.getUUID());
                if(info!=null)g.drawString(mc.font,"PING  "+info.getLatency()+" ms",x+77,y+24,ACCENT,false);
            }
        }else if(VisualFeatures.isEnabled("FPS Counter"))line(g,mc,"FPS  "+mc.getFps(),x,y);
        int yy=56;
        if(VisualFeatures.isEnabled("Keystrokes")){
            String keys=(mc.options.keyUp.isDown()?"W ":"_ ")+(mc.options.keyLeft.isDown()?"A ":"_ ")+
                (mc.options.keyDown.isDown()?"S ":"_ ")+(mc.options.keyRight.isDown()?"D":"_");
            line(g,mc,"KEYS  "+keys,x,yy); yy+=17;
            line(g,mc,"JUMP "+(mc.options.keyJump.isDown()?"ON":"OFF"),x,yy); yy+=17;
        }
        if(VisualFeatures.isEnabled("Armor HUD")){
            Inventory inv=mc.player.getInventory();
            StringBuilder armor=new StringBuilder("ARMOR  ");
            for(ItemStack stack:inv.armor) armor.append(stack.isEmpty()?"-":stack.getMaxDamage()-stack.getDamage()).append("  ");
            line(g,mc,armor.toString(),x,yy); yy+=17;
        }
        if(VisualFeatures.isEnabled("Potion HUD")){
            int count=0;
            for(var effect:mc.player.getActiveEffects()){
                if(count++>=5)break;
                line(g,mc,effect.getEffect().value().getDisplayName().getString()+"  "+(effect.getDuration()/20)+"s",x,yy);
                yy+=15;
            }
        }
        if(VisualFeatures.isEnabled("Totem Counter")){
            int count=0;
            for(ItemStack stack:mc.player.getInventory().getNonEquipmentItems())
                if(stack.is(net.minecraft.world.item.Items.TOTEM_OF_UNDYING))count+=stack.getCount();
            line(g,mc,"TOTEMS  "+count,x,yy);
        }
        if(VisualFeatures.isEnabled("Array List")||VisualFeatures.isEnabled("Module List")){
            int ry=10;
            for(Map.Entry<String,Boolean> entry:VisualFeatures.getStates().entrySet()){
                if(entry.getValue()&&!entry.getKey().equals("Watermark")&&!entry.getKey().equals("Array List")&&!entry.getKey().equals("Module List")){
                    int w=mc.font.width(entry.getKey()),rx=mc.getWindow().getGuiScaledWidth()-w-14;
                    g.fill(rx-4,ry-2,rx+w+5,ry+11,PANEL);
                    g.fill(rx+w+3,ry-2,rx+w+5,ry+11,ACCENT);
                    g.drawString(mc.font,entry.getKey(),rx,ry,TEXT,false); ry+=14;
                }
            }
        }
        if(VisualFeatures.isEnabled("Crosshair")){
            int cx=mc.getWindow().getGuiScaledWidth()/2,cy=mc.getWindow().getGuiScaledHeight()/2;
            g.fill(cx-5,cy,cx+5,cy+1,0xFFFFFFFF); g.fill(cx,cy-5,cx+1,cy+5,0xFFFFFFFF);
        }
    }
}
