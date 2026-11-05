package am2.items.renderers;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelSlime;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.EntitySlime;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

@SideOnly(Side.CLIENT)
public class RenderIllusionSlime {

    private static final ResourceLocation slimeTexture = new ResourceLocation("arsmagica2", "textures/mobs/slime.png");
    private static final ModelBase innerModel = new ModelSlime(16);
    private static final ModelBase outerModel = new ModelSlime(0);

    public static void render(EntityLivingBase elb, double x, double y, double z, float yaw, float partialTick) {
        if (!(elb instanceof EntitySlime)) {
            return;
        }
        EntitySlime slime = (EntitySlime) elb;
        Minecraft.getMinecraft().renderEngine.bindTexture(slimeTexture);

        GL11.glPushMatrix();
        GL11.glDisable(GL11.GL_CULL_FACE);

        GL11.glTranslatef((float) x, (float) y, (float) z);

        float renderYaw = slime.prevRenderYawOffset + (slime.renderYawOffset - slime.prevRenderYawOffset) * partialTick;
        GL11.glRotatef(180.0F - renderYaw, 0.0F, 1.0F, 0.0F);

        float f5 = 0.0625F;
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glScalef(-1.0F, -1.0F, 1.0F);

        float size = (float) slime.getSlimeSize();
        float squish = (slime.prevSquishFactor + (slime.squishFactor - slime.prevSquishFactor) * partialTick) / (size * 0.5F + 1.0F);
        float invSquish = 1.0F / (squish + 1.0F);
        GL11.glScalef(invSquish * size, 1.0F / invSquish * size, invSquish * size);

        GL11.glTranslatef(0.0F, -24.0F * f5 - 0.0078125F, 0.0F);

        float limbSwingAmount = slime.prevLimbSwingAmount + (slime.limbSwingAmount - slime.prevLimbSwingAmount) * partialTick;
        float limbSwing = slime.limbSwing - slime.limbSwingAmount * (1.0F - partialTick);
        if (limbSwingAmount > 1.0F) {
            limbSwingAmount = 1.0F;
        }

        GL11.glEnable(GL11.GL_ALPHA_TEST);

        // Render pass 1: Inner core, eyes e mouth
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);
        innerModel.setLivingAnimations(slime, limbSwing, limbSwingAmount, partialTick);
        innerModel.render(slime, limbSwing, limbSwingAmount, (float) slime.ticksExisted + partialTick, 0.0F, slime.rotationPitch, f5);

        // Render pass 2: Translucent outer jelly com olhos destacados em alta visibilidade
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glColor4f(1.0F, 1.0F, 1.0F, 1.0F);

        outerModel.setLivingAnimations(slime, limbSwing, limbSwingAmount, partialTick);
        outerModel.render(slime, limbSwing, limbSwingAmount, (float) slime.ticksExisted + partialTick, 0.0F, slime.rotationPitch, f5);

        GL11.glDisable(GL11.GL_BLEND);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glPopMatrix();
    }
}
