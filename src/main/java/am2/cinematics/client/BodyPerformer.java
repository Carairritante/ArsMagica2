package am2.cinematics.client;

import am2.cinematics.scene.Pose;
import am2.cinematics.scene.Scene;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Method;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.entity.RenderPlayer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Atua com o corpo do jogador-âncora durante a cena: cabeça, braços com
 * cotovelo, veias e brilho dos olhos. Só muda o desenho; tudo o que é alterado
 * no jogador é devolvido logo depois de ele ser desenhado.
 */
final class BodyPerformer {
    private final VeinModel veins = new VeinModel();
    private final LimbBender bender = new LimbBender();
    private final Method skin = ReflectionHelper.findMethod(Render.class, null,
            new String[] { "getEntityTexture", "func_110775_a" }, Entity.class);
    private final Pose.Key pose = new Pose.Key();
    private final Scene.ActorKey key = new Scene.ActorKey();

    private EntityPlayer posed;
    private ModelBiped model;
    private float pitch, prevPitch, headYaw, prevHeadYaw, bodyYaw, prevBodyYaw;
    private boolean lifted;

    /** Antes de o jogador ser desenhado (RenderPlayerEvent.Pre). */
    void before(Scene scene, double time, EntityPlayer player, RenderPlayer renderer, float sceneYaw) {
        restore();
        lifted = false;
        if (!Pose.sample(scene.pose, time, pose)) return;
        posed = player;
        model = renderer.modelBipedMain;
        pitch = player.rotationPitch;
        prevPitch = player.prevRotationPitch;
        headYaw = player.rotationYawHead;
        prevHeadYaw = player.prevRotationYawHead;
        bodyYaw = player.renderYawOffset;
        prevBodyYaw = player.prevRenderYawOffset;
        // O corpo olha para a frente da cena, inclusive na tela de quem só assiste.
        player.renderYawOffset = player.prevRenderYawOffset = sceneYaw;

        double weight = pose.weight;
        double shake = pose.tremor * weight;
        float newPitch = (float) (pitch + (pose.head - pitch) * weight + shake * Math.sin(time * 4.1));
        player.rotationPitch = player.prevRotationPitch = newPitch;
        float forward = player.renderYawOffset;
        float turn = net.minecraft.util.MathHelper.wrapAngleTo180_float(headYaw - forward);
        float newYaw = (float) (forward + turn * (1 - weight) + shake * 0.6 * Math.sin(time * 5.3 + 1));
        player.rotationYawHead = player.prevRotationYawHead = newYaw;
        // Os braços originais saem; os articulados são desenhados em during().
        model.bipedRightArm.isHidden = true;
        model.bipedLeftArm.isHidden = true;
        if (pose.lift > 0.001) {
            // Flutuar é só deslocar o desenho; a sombra fica no chão.
            GL11.glPushMatrix();
            GL11.glTranslated(0, pose.lift, 0);
            lifted = true;
        }
    }

    /** Desfaz a flutuação; chamar só em RenderPlayerEvent.Post. */
    void land() {
        if (!lifted) return;
        lifted = false;
        GL11.glPopMatrix();
    }

    /** Com a matriz do modelo ativa (RenderPlayerEvent.Specials.Post). */
    void during(Minecraft mc, Scene scene, double time, double anim, EntityPlayer player, RenderPlayer renderer) {
        ModelBiped body = renderer.modelBipedMain;
        boolean acting = posed == player && Pose.sample(scene.pose, time, pose);
        float[] right = null, left = null;
        double rightElbow = 0, leftElbow = 0;
        if (acting) {
            right = blend(body.bipedRightArm.rotateAngleX, body.bipedRightArm.rotateAngleY,
                    body.bipedRightArm.rotateAngleZ, pose.right, pose.weight);
            left = blend(body.bipedLeftArm.rotateAngleX, body.bipedLeftArm.rotateAngleY,
                    body.bipedLeftArm.rotateAngleZ, pose.left, pose.weight);
            rightElbow = Math.toRadians(pose.right[Pose.ELBOW] * pose.weight);
            leftElbow = Math.toRadians(pose.left[Pose.ELBOW] * pose.weight);
            model.bipedRightArm.isHidden = false;
            model.bipedLeftArm.isHidden = false;
            mc.getTextureManager().bindTexture(skinOf(renderer, player));
            GL11.glColor4f(1, 1, 1, 1);
            bender.render(body.bipedRightArm, right, rightElbow, 1, 0);
            bender.render(body.bipedLeftArm, left, leftElbow, 1, 0);
        }
        veins.draw(mc, scene, time, anim, body, acting ? bender : null, right, rightElbow, left, leftElbow);
        drawEyes(scene, time, body);
    }

    /** Depois de o jogador ser desenhado; seguro chamar a qualquer momento. */
    void restore() {
        if (posed == null) return;
        posed.rotationPitch = pitch;
        posed.prevRotationPitch = prevPitch;
        posed.rotationYawHead = headYaw;
        posed.prevRotationYawHead = prevHeadYaw;
        posed.renderYawOffset = bodyYaw;
        posed.prevRenderYawOffset = prevBodyYaw;
        model.bipedRightArm.isHidden = false;
        model.bipedLeftArm.isHidden = false;
        posed = null;
        model = null;
    }

    private static float[] blend(float x, float y, float z, double[] target, double weight) {
        return new float[] {
                (float) (x + (Math.toRadians(target[Pose.X]) - x) * weight),
                (float) (y + (Math.toRadians(target[Pose.Y]) - y) * weight),
                (float) (z + (Math.toRadians(target[Pose.Z]) - z) * weight) };
    }

    /** A mesma textura que o renderizador usa (outros mods podem trocá-la). */
    private ResourceLocation skinOf(RenderPlayer renderer, EntityPlayer player) {
        try {
            return (ResourceLocation) skin.invoke(renderer, player);
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao obter a skin", e);
        }
    }

    /** Dois brilhos na posição usual dos olhos de uma skin (linha 5 do rosto). */
    private void drawEyes(Scene scene, double time, ModelBiped body) {
        for (Scene.Actor actor : scene.actors) {
            if (actor.type != Scene.ActorType.EYES || !actor.sample(time, key) || key.alpha <= 0.01) continue;
            float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
            GL11.glPushMatrix();
            GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
            try {
                body.bipedHead.postRender(0.0625F);
                GL11.glDisable(GL11.GL_TEXTURE_2D);
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glDisable(GL11.GL_FOG);
                GL11.glEnable(GL11.GL_BLEND);
                OpenGlHelper.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
                GL11.glDepthMask(false);
                GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);
                int alpha = (int) (key.alpha * 255);
                for (int side = -1; side <= 1; side += 2) {
                    double x = side * 2;
                    // Halo, cor e miolo, logo à frente da camada de chapéu.
                    eye(x, 1.9, 1.3, -4.56, actor.color, alpha / 3);
                    eye(x, 1.05, 0.62, -4.57, actor.color, alpha);
                    eye(x, 0.7, 0.35, -4.58, 0xFFFFFF, alpha);
                }
            } finally {
                OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
                GL11.glPopAttrib();
                GL11.glPopMatrix();
                GL11.glColor4f(1, 1, 1, 1);
            }
        }
    }

    private static void eye(double x, double halfWidth, double halfHeight, double z, int color, int alpha) {
        double y = -3.5, s = 0.0625;
        Tessellator tess = Tessellator.instance;
        tess.startDrawingQuads();
        tess.setColorRGBA_I(color, alpha);
        tess.addVertex((x - halfWidth) * s, (y - halfHeight) * s, z * s);
        tess.addVertex((x - halfWidth) * s, (y + halfHeight) * s, z * s);
        tess.addVertex((x + halfWidth) * s, (y + halfHeight) * s, z * s);
        tess.addVertex((x + halfWidth) * s, (y - halfHeight) * s, z * s);
        tess.draw();
    }
}
