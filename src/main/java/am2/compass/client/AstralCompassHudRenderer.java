package am2.compass.client;

import am2.compass.StructureTargetInfo;
import am2.guis.GuiAstralCompass;
import am2.items.ItemAstralCompass;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;
import java.util.List;

/** A navigation instrument: rotating cardinal dial, target needle and separate information plate. */
@SideOnly(Side.CLIENT)
public final class AstralCompassHudRenderer extends Gui {
    private static final Minecraft mc=Minecraft.getMinecraft();
    public static final int WIDTH=264, HEIGHT=96;
    private static final int SEGMENTS=96;
    private static final String[] CARDINALS={"N","E","S","W"};
    private static final float[] SIN=new float[SEGMENTS+1], COS=new float[SEGMENTS+1];
    static {for(int i=0;i<=SEGMENTS;i++){double angle=i*Math.PI*2/SEGMENTS;SIN[i]=(float)Math.sin(angle);COS[i]=(float)Math.cos(angle);}}

    @SubscribeEvent public void onRenderGameOverlay(RenderGameOverlayEvent.Post event) {
        if(event.type==RenderGameOverlayEvent.ElementType.ALL && mc.currentScreen==null && !mc.gameSettings.showDebugInfo) drawHud(event.resolution,false);
    }
    public static int[] drawHud(ScaledResolution res,boolean preview) {
        if(mc.thePlayer==null||mc.theWorld==null)return null;
        EntityPlayer player=mc.thePlayer;
        ItemAstralCompass.TargetData target=ItemAstralCompass.getTarget(player.getCurrentEquippedItem());
        AstralCompassPreferences settings=AstralCompassPreferences.get();
        if(target!=null) settings.remember(target);
        else if(preview) target=new ItemAstralCompass.TargetData("wizardry:wizard_tower","Wizard Tower",player.dimension,128,80,256,true);
        else return null;
        double scale=Math.min(settings.size/100.0,Math.min((res.getScaledWidth()-16.0)/WIDTH,(res.getScaledHeight()-16.0)/HEIGHT));
        scale=Math.max(0.1,scale);
        int w=(int)Math.ceil(WIDTH*scale),h=(int)Math.ceil(HEIGHT*scale);
        int left=8+(int)Math.round(Math.max(0,res.getScaledWidth()-w-16)*settings.x);
        int top=8+(int)Math.round(Math.max(0,res.getScaledHeight()-h-16)*settings.y);
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDisable(GL11.GL_ALPHA_TEST);
            GL11.glDisable(GL11.GL_CULL_FACE);GL11.glShadeModel(GL11.GL_SMOOTH);
            GL11.glDepthMask(false);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslatef(left,top,0);GL11.glScaled(scale,scale,1);
            float opacity=(100-settings.transparency)/100f;
            boolean same=player.dimension==target.dimension;
            double dx=target.x+0.5-player.posX,dz=target.z+0.5-player.posZ;
            int distance=(int)Math.round(Math.hypot(dx,dz));boolean arrived=same&&distance<=24;
            double yaw=Math.toRadians(MathHelper.wrapAngleTo180_float(player.rotationYaw));
            // MC yaw 0 faces +Z (south). Bearings are clockwise from the top of this dial.
            double bearing=Math.atan2(dz,dx)-Math.PI/2-yaw;
            drawInformationPlate(opacity);
            drawCompass(47,48,Math.PI-yaw,bearing,same,arrived,dx*dx+dz*dz<0.25,opacity);
            String category=StructureTargetInfo.categoryForId(target.id);
            StructureTargetInfo info=new StructureTargetInfo(target.id,target.name,category,target.dimension,true,0);
            label(StatCollector.translateToLocal("am2.astral_compass.hud.title"),6,0x77caff);
            label(info.getDisplayName(),18,0xf0f6ff);
            label(info.getModName(),30,0x94b8d4);
            label(AstralCompassPreferences.dimensionName(target.dimension),42,same?0x94b8d4:0xffce86);
            label(same?StatCollector.translateToLocalFormatted(arrived?"am2.astral_compass.hud.arrived":"am2.astral_compass.hud.distance",distance):GuiAstralCompass.text("other_dimension"),54,arrived?0x78e2ba:0xa1e5ff);
            label("X: "+target.x+(target.hasY?" Y: "+target.y:"")+" Z: "+target.z,66,0x9ab8d9);
            ItemAstralCompass.ClientSearchStatus status=ItemAstralCompass.getClientSearchStatus();
            String footer=status.status==ItemAstralCompass.STATUS_SEARCHING?GuiAstralCompass.statusText(status):GuiAstralCompass.text("source_"+target.source);
            List<String> lines=mc.fontRenderer.listFormattedStringToWidth(footer,WIDTH-112);
            for(int i=0;i<Math.min(2,lines.size());i++)label(lines.get(i),78+i*9,0x77caff);
        } finally {GL11.glPopMatrix();GL11.glPopAttrib();}
        return new int[]{left,top,w,h};
    }
    private static void label(String text,int y,int color){
        GL11.glColor4f(1,1,1,1);
        mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(text,WIDTH-112),104,y,color);
    }
    private static void color(int rgb,float alpha){GL11.glColor4f(((rgb>>16)&255)/255f,((rgb>>8)&255)/255f,(rgb&255)/255f,alpha);}
    private static void vertex(float x,float y){GL11.glVertex2f(x,y);}
    private static void triangle(float ax,float ay,float bx,float by,float cx,float cy,int rgb,float alpha){
        color(rgb,alpha);GL11.glBegin(GL11.GL_TRIANGLES);vertex(ax,ay);vertex(bx,by);vertex(cx,cy);GL11.glEnd();
    }
    private static void line(float ax,float ay,float bx,float by,int rgb,float alpha){
        color(rgb,alpha);GL11.glBegin(GL11.GL_LINES);vertex(ax,ay);vertex(bx,by);GL11.glEnd();
    }
    private static void ring(float cx,float cy,float inner,float outer,int rgb,float alpha){
        color(rgb,alpha);GL11.glBegin(GL11.GL_TRIANGLE_STRIP);
        for(int i=0;i<=SEGMENTS;i++){vertex(cx+SIN[i]*outer,cy-COS[i]*outer);vertex(cx+SIN[i]*inner,cy-COS[i]*inner);}
        GL11.glEnd();
    }
    private static void glass(float cx,float cy,float radius,float opacity){
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);color(0x061423,opacity*0.96f);vertex(cx,cy);
        color(0x102d43,opacity*0.88f);for(int i=0;i<=SEGMENTS;i++)vertex(cx+SIN[i]*radius,cy-COS[i]*radius);
        GL11.glEnd();
    }
    private static void drawInformationPlate(float opacity){
        GL11.glDisable(GL11.GL_TEXTURE_2D);GL11.glLineWidth(1f);
        // Offset shoulders join the circular bezel; this is independent of the chalk frame.
        GL11.glBegin(GL11.GL_TRIANGLE_FAN);
        color(0x071728,opacity*0.97f);vertex(170,48);
        color(0x0c2337,opacity*0.88f);
        vertex(92,2);vertex(253,2);vertex(262,11);vertex(262,85);vertex(253,94);vertex(92,94);vertex(86,82);vertex(86,14);vertex(92,2);
        GL11.glEnd();
        line(94,2,253,2,0x396780,0.75f);line(253,2,262,11,0x528fac,0.85f);
        line(262,11,262,85,0x396780,0.7f);line(262,85,253,94,0x396780,0.7f);line(253,94,94,94,0x28475e,0.75f);
        line(104,1,144,1,0xc4ad7c,0.9f);line(148,1,247,1,0x5fcce8,0.65f);
        line(90,24,99,24,0xb39a68,0.8f);line(90,72,99,72,0xb39a68,0.8f);
        line(104,16,250,16,0x3b637c,0.35f);
    }
    private static void drawCompass(float cx,float cy,double north,double bearing,boolean same,boolean arrived,boolean centered,float opacity){
        glass(cx,cy,43,opacity);
        ring(cx,cy,42.4f,44,0x18344a,0.95f);
        ring(cx,cy,41.8f,42.5f,0x75a9bb,0.8f);
        ring(cx,cy,37.4f,38.1f,0x476e7b,0.65f);
        ring(cx,cy,28.5f,29,0x416b7d,0.35f);
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(cx,cy,0);GL11.glRotatef((float)Math.toDegrees(north),0,0,1);
            // World-aligned graduations and rose, rotating beneath the target needle.
            GL11.glBegin(GL11.GL_LINES);
            for(int i=0;i<SEGMENTS;i+=2){boolean major=i%12==0;float inner=major?35:38;
                color(major?0xc2af86:0x628b9d,major?0.9f:0.6f);
                vertex(SIN[i]*inner,-COS[i]*inner);vertex(SIN[i]*40.5f,-COS[i]*40.5f);
            }GL11.glEnd();
            for(int i=0;i<8;i++) {
                GL11.glPushMatrix();GL11.glRotatef(i*45,0,0,1);
                float tip=i%2==0?23:16;
                triangle(0,-tip,-2.2f,0,0,3,0x46798b,0.38f);
                triangle(0,-tip,0,3,2.2f,0,0x294f68,0.4f);
                GL11.glPopMatrix();
            }
        } finally {GL11.glPopMatrix();}
        // Fixed heading index faces the player's front, not geographic north.
        triangle(cx,cy-37.5f,cx-2.4f,cy-41.2f,cx+2.4f,cy-41.2f,0xe6d3a6,0.95f);
        if(same&&!centered)drawDirectionNeedle(cx,cy,bearing,arrived?0x8ff1be:0x9defff,arrived?0x38b985:0x30afd9);
        else if(same) {
            ring(cx,cy,6.8f,8,0x78e2ba,0.9f);
            line(cx-4,cy,cx-1,cy+3,0x9ff1cb,1);line(cx-1,cy+3,cx+5,cy-4,0x9ff1cb,1);
        } else {
            // Cross-dimension target has no meaningful horizontal bearing.
            ring(cx,cy,7.5f,9,0xc4ad7c,0.9f);
            line(cx-3,cy-11,cx-3,cy+11,0xe6c88b,0.95f);line(cx+3,cy-11,cx+3,cy+11,0x6acfe8,0.85f);
        }
        GL11.glEnable(GL11.GL_TEXTURE_2D);GL11.glColor4f(1,1,1,1);
        for(int i=0;i<4;i++) {
            double angle=north+i*Math.PI/2;
            int x=Math.round(cx+(float)Math.sin(angle)*32.5f),y=Math.round(cy-(float)Math.cos(angle)*32.5f);
            String letter=CARDINALS[i];
            mc.fontRenderer.drawStringWithShadow(letter,x-mc.fontRenderer.getStringWidth(letter)/2,y-4,i==0?0xf0d49b:0xb2ccd6);
        }
        GL11.glColor4f(1,1,1,1);
    }
    private static void drawDirectionNeedle(float cx,float cy,double angleRad,int light,int shade){
        GL11.glPushMatrix();
        try {
            GL11.glTranslatef(cx,cy,0);GL11.glRotatef((float)Math.toDegrees(angleRad),0,0,1);
            triangle(0,-30,-6.5f,0,0,21,0x020d18,0.95f);
            triangle(0,-30,0,21,6.5f,0,0x020d18,0.95f);
            // Cyan front and warm counterweight make the target direction unambiguous.
            triangle(0,-28,-5,0,0,3,light,1);
            triangle(0,-28,0,3,5,0,shade,1);
            triangle(0,19,-5,0,0,-2,0x6d6b5b,1);
            triangle(0,19,0,-2,5,0,0xb7a67f,1);
            line(0,-25,0,-6,0xe8fcff,0.8f);
            triangle(0,-46,-2.8f,-41.5f,2.8f,-41.5f,shade,1);
            ring(0,0,2.2f,3.5f,0xe0cb9c,1);glass(0,0,2.1f,1);
        } finally {GL11.glPopMatrix();}
    }
}
