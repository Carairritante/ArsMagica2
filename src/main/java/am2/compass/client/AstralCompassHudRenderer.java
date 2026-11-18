package am2.compass.client;

import am2.compass.StructureTargetInfo;
import am2.guis.GuiAstralCompass;
import am2.guis.GuiAstralCompassHudSettings;
import am2.guis.controls.ArcaneChalkStyle;
import am2.items.ItemAstralCompass;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.*;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import org.lwjgl.opengl.GL11;

@SideOnly(Side.CLIENT)
public final class AstralCompassHudRenderer extends Gui {
    private static final Minecraft mc=Minecraft.getMinecraft();
    public static final int WIDTH=264, HEIGHT=96;
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
            GL11.glDisable(GL11.GL_LIGHTING);GL11.glDisable(GL11.GL_DEPTH_TEST);GL11.glDepthMask(false);GL11.glEnable(GL11.GL_BLEND);GL11.glBlendFunc(GL11.GL_SRC_ALPHA,GL11.GL_ONE_MINUS_SRC_ALPHA);
            GL11.glTranslatef(left,top,0);GL11.glScaled(scale,scale,1);
            int alpha=(int)Math.round(255*(100-settings.transparency)/100.0);
            Gui.drawRect(1,1,WIDTH-1,HEIGHT-1,(alpha<<24)|0x08182e);
            Gui.drawRect(4,0,WIDTH-4,1,0xc04a9dde);Gui.drawRect(0,4,1,HEIGHT-4,0xa0386fa9);
            Gui.drawRect(WIDTH-1,4,WIDTH,HEIGHT-4,0xa0284f83);Gui.drawRect(4,HEIGHT-1,WIDTH-4,HEIGHT,0xc0284f83);
            ArcaneChalkStyle.seal(mc,11,28,45);
            boolean same=player.dimension==target.dimension;double dx=target.x+0.5-player.posX,dz=target.z+0.5-player.posZ;
            int distance=(int)Math.round(Math.hypot(dx,dz));boolean arrived=same&&distance<=24;
            if(same)drawDirectionNeedle(28,45,Math.atan2(dz,dx)-Math.PI/2-Math.toRadians(MathHelper.wrapAngleTo180_float(player.rotationYaw)),arrived?0x78e2ba:0x67ccff);
            String category=StructureTargetInfo.categoryForId(target.id);
            StructureTargetInfo info=new StructureTargetInfo(target.id,target.name,category,target.dimension,true,0);
            label(StatCollector.translateToLocal("am2.astral_compass.hud.title"),7,0x77caff);
            label(info.getDisplayName(),20,0xf0f6ff);label(info.getModName(),32,0x94b8d4);
            label(AstralCompassPreferences.dimensionName(target.dimension),44,same?0x94b8d4:0xffce86);
            label(same?StatCollector.translateToLocalFormatted(arrived?"am2.astral_compass.hud.arrived":"am2.astral_compass.hud.distance",distance):GuiAstralCompass.text("other_dimension"),56,arrived?0x78e2ba:0xa1e5ff);
            label("X: "+target.x+(target.hasY?" Y: "+target.y:"")+" Z: "+target.z,68,0x9ab8d9);
            int status=ItemAstralCompass.getClientSearchStatus().status;
            if(status!=0)label(GuiAstralCompass.text("status_"+status),81,0x77caff);
        } finally {GL11.glPopMatrix();GL11.glPopAttrib();}
        return new int[]{left,top,w,h};
    }
    private static void label(String text,int y,int color){mc.fontRenderer.drawStringWithShadow(mc.fontRenderer.trimStringToWidth(text,WIDTH-66),58,y,color);}
	private static void drawDirectionNeedle(float cx, float cy, double angleRad, int color) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glTranslatef(cx, cy, 0);
		GL11.glRotatef((float) Math.toDegrees(angleRad), 0, 0, 1);

		float r = ((color >> 16) & 255) / 255f;
		float g = ((color >> 8) & 255) / 255f;
		float b = (color & 255) / 255f;
		GL11.glColor4f(r, g, b, 0.95f);

		GL11.glBegin(GL11.GL_TRIANGLES);
		GL11.glVertex2f(0f, -19f);
		GL11.glVertex2f(-4f, -4f);
		GL11.glVertex2f(4f, -4f);
		GL11.glEnd();

		GL11.glPopMatrix();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glColor4f(1f, 1f, 1f, 1f);
	}
}
