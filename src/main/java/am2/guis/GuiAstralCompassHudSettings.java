package am2.guis;

import am2.compass.client.AstralCompassHudRenderer;
import am2.compass.client.AstralCompassPreferences;
import am2.guis.controls.*;
import cpw.mods.fml.relauncher.*;
import net.minecraft.client.gui.*;

/** Local HUD preview; no item, permission or world changes. */
@SideOnly(Side.CLIENT)
public final class GuiAstralCompassHudSettings extends GuiScreen {
    private final GuiScreen parent;
    private final AstralCompassPreferences prefs=AstralCompassPreferences.get();
    private int left, top, panelWidth;
    private int[] preview;
    private boolean dragging;
    private int grabX,grabY;
    public GuiAstralCompassHudSettings(GuiScreen parent){this.parent=parent;}
    @SuppressWarnings("unchecked") @Override public void initGui(){
        panelWidth=Math.min(350,width-16);left=(width-panelWidth)/2;top=height-110;
        buttonList.clear();
        int half=(panelWidth-34)/2;
        buttonList.add(new GuiButtonArcaneChalk(1,left+12,top+36,24,20,"-"));
        buttonList.add(new GuiButtonArcaneChalk(2,left+half-14,top+36,24,20,"+"));
        buttonList.add(new GuiButtonArcaneChalk(3,left+half+20,top+36,24,20,"-"));
        buttonList.add(new GuiButtonArcaneChalk(4,left+panelWidth-36,top+36,24,20,"+"));
        int third=(panelWidth-40)/3;
        buttonList.add(new GuiButtonArcaneChalk(5,left+12,top+75,third,20,GuiAstralCompass.text("reset")));
        buttonList.add(new GuiButtonArcaneChalk(6,left+20+third,top+75,third,20,GuiAstralCompass.text("position")));
        buttonList.add(new GuiButtonArcaneChalk(0,left+28+third*2,top+75,third,20,GuiAstralCompass.text("done")));
    }
    @Override protected void actionPerformed(GuiButton b){
        if(b.id==0){prefs.save();mc.displayGuiScreen(parent);return;}
        if(b.id==1)prefs.size=Math.max(50,prefs.size-10);
        if(b.id==2)prefs.size=Math.min(150,prefs.size+10);
        if(b.id==3)prefs.transparency=Math.max(0,prefs.transparency-5);
        if(b.id==4)prefs.transparency=Math.min(100,prefs.transparency+5);
        if(b.id==5){prefs.x=prefs.y=0;prefs.size=100;prefs.transparency=45;}
        if(b.id==6){if(prefs.x<0.5)prefs.x=1;else{prefs.x=0;prefs.y=prefs.y<0.5?1:0;}}
    }
    @Override public void drawScreen(int x,int y,float partial){
        preview=AstralCompassHudRenderer.drawHud(new ScaledResolution(mc,mc.displayWidth,mc.displayHeight),true);
        ArcaneChalkStyle.panel(left,top,panelWidth,103);
        drawCenteredString(fontRendererObj,GuiAstralCompass.text("hud_settings"),width/2,top+8,0xecf7ff);
        drawCenteredString(fontRendererObj,GuiAstralCompass.text("drag_hint"),width/2,top+22,0x77caff);
        int half=(panelWidth-34)/2;
        drawCenteredString(fontRendererObj,prefs.size+"%",left+half/2+12,top+42,0xecf7ff);
        drawCenteredString(fontRendererObj,prefs.transparency+"%",left+panelWidth-half/2-12,top+42,0xecf7ff);
        drawCenteredString(fontRendererObj,GuiAstralCompass.text("size"),left+half/2+12,top+60,0x9ab8d9);
        drawCenteredString(fontRendererObj,GuiAstralCompass.text("transparency"),left+panelWidth-half/2-12,top+60,0x9ab8d9);
        super.drawScreen(x,y,partial);
    }
    @Override protected void mouseClicked(int x,int y,int button){
        if(button==0 && preview!=null && !(x>=left&&x<left+panelWidth&&y>=top&&y<top+103)
                && x>=preview[0]&&x<preview[0]+preview[2]&&y>=preview[1]&&y<preview[1]+preview[3]){
            dragging=true;grabX=x-preview[0];grabY=y-preview[1];return;
        }
        super.mouseClicked(x,y,button);
    }
    @Override protected void mouseClickMove(int x,int y,int button,long held){
        if(dragging&&preview!=null){prefs.x=Math.max(0,Math.min(1,(x-grabX-8.0)/Math.max(1,width-preview[2]-16)));prefs.y=Math.max(0,Math.min(1,(y-grabY-8.0)/Math.max(1,height-preview[3]-16)));}
        else super.mouseClickMove(x,y,button,held);
    }
    @Override protected void mouseMovedOrUp(int x,int y,int button){dragging=false;super.mouseMovedOrUp(x,y,button);}
    @Override protected void keyTyped(char key,int code){if(code==1){prefs.save();mc.displayGuiScreen(parent);}else super.keyTyped(key,code);}
    @Override public void onGuiClosed(){prefs.save();}
    @Override public boolean doesGuiPauseGame(){return false;}
}
