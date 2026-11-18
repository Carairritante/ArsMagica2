package am2.guis.controls;

import cpw.mods.fml.relauncher.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import org.lwjgl.opengl.GL11;
import java.util.List;

@SideOnly(Side.CLIENT)
public final class GuiInstrumentButton extends GuiButton {
    public static final int GEAR=-2;
    private final int symbol;
    private final boolean selected;
    public GuiInstrumentButton(int id,int x,int y,int w,int h,String label){this(id,x,y,w,h,label,-1,false);}
    public GuiInstrumentButton(int id,int x,int y,int w,int h,String label,int symbol,boolean selected){super(id,x,y,w,h,label);this.symbol=symbol;this.selected=selected;}
    @Override public void drawButton(Minecraft mc,int x,int y){
        if(!visible)return;
        field_146123_n=x>=xPosition&&x<xPosition+width&&y>=yPosition&&y<yPosition+height;
        int rim=!enabled?0xff233546:selected?0xffc8ae7b:field_146123_n?0xff69cee5:0xff36566f;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try{
            drawRect(xPosition,yPosition,xPosition+width,yPosition+height,rim);
            drawGradientRect(xPosition+1,yPosition+1,xPosition+width-1,yPosition+height-1,selected?0xff214051:0xff152d40,0xff091623);
            if(selected)drawRect(xPosition+2,yPosition+3,xPosition+4,yPosition+height-3,0xffecd49b);
            int foreground=!enabled?0x677d90:field_146123_n||selected?0xf0faff:0xbcd4e1;
            if(symbol==GEAR){for(int i=0;i<3;i++){int yy=yPosition+5+i*4;drawRect(xPosition+6,yy,xPosition+width-6,yy+1,0xff77b6ca);int xx=xPosition+7+(i%2)*6;drawRect(xx,yy-1,xx+2,yy+2,0xffe0c493);}}
            else{List<String> lines=mc.fontRenderer.listFormattedStringToWidth(displayString,Math.max(1,width-12));int n=Math.min(2,lines.size());for(int i=0;i<n;i++){String line=lines.get(i);GL11.glColor4f(1,1,1,1);mc.fontRenderer.drawStringWithShadow(line,xPosition+(width-mc.fontRenderer.getStringWidth(line))/2,yPosition+(height-n*9)/2+i*9,foreground);}}
            mouseDragged(mc,x,y);
        }finally{GL11.glPopAttrib();}
    }
}
