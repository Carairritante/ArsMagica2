package am2.cinematics.client;

import am2.cinematics.scene.Scene;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelBox;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

/**
 * Casca do corpo do jogador com as veias de mana. O alfa da textura diz quando
 * cada trecho acende, então a propagação é só o limite do teste de alfa.
 * O mapa de UV é o mesmo de tools/make_veins.py.
 */
final class VeinModel extends ModelBase {
    private static final ResourceLocation TEXTURE = new ResourceLocation("amcinematics", "textures/veins.png");

    private final ModelRenderer head, body, rightArm, leftArm, rightLeg, leftLeg;
    private final Scene.ActorKey key = new Scene.ActorKey();
    private LimbBender bender;
    private float[] rightAngles, leftAngles;
    private double rightElbow, leftElbow, rightWiden = 1, leftWiden = 1;

    VeinModel() {
        textureWidth = 64;
        textureHeight = 64;
        // A cabeça passa por cima da camada de chapéu da skin (0,5).
        head = part(0, 0, -4, -8, -4, 8, 8, 8, 0.52F);
        body = part(0, 16, -4, 0, -2, 8, 12, 4, 0.06F);
        rightArm = part(24, 16, -3, -2, -2, 4, 12, 4, 0.06F);
        leftArm = part(40, 16, -1, -2, -2, 4, 12, 4, 0.06F);
        rightLeg = part(0, 32, -2, 0, -2, 4, 12, 4, 0.05F);
        leftLeg = part(16, 32, -2, 0, -2, 4, 12, 4, 0.04F);
    }

    private ModelRenderer part(int u, int v, float x, float y, float z, int width, int height, int depth, float grow) {
        ModelRenderer renderer = new ModelRenderer(this, u, v);
        renderer.addBox(x, y, z, width, height, depth, grow);
        return renderer;
    }

    /**
     * Chamar com a matriz do modelo do jogador ativa (RenderPlayerEvent.Specials).
     *
     * @param bender se não for nulo, os braços são articulados com os ângulos dados
     */
    void draw(Minecraft mc, Scene scene, double time, double anim, ModelBiped pose, LimbBender bender,
            float[] right, double rightElbow, float[] left, double leftElbow) {
        this.bender = bender;
        this.rightAngles = right;
        this.rightElbow = rightElbow;
        this.leftAngles = left;
        this.leftElbow = leftElbow;
        for (Scene.Actor actor : scene.actors) {
            if (actor.type != Scene.ActorType.VEINS || !actor.sample(time, key)) continue;
            if (key.open <= 0.001 || key.alpha <= 0.01) continue;
            double beat = Scene.heartbeat(anim - actor.keys.get(0).t);
            render(mc, actor.color, (float) Math.min(1, key.alpha * (0.78 + 0.35 * beat)), (float) key.open, pose);
        }
    }

    private void render(Minecraft mc, int color, float intensity, float reveal, ModelBiped pose) {
        copy(pose.bipedHead, head);
        copy(pose.bipedBody, body);
        copy(pose.bipedRightArm, rightArm);
        copy(pose.bipedLeftArm, leftArm);
        copy(pose.bipedRightLeg, rightLeg);
        copy(pose.bipedLeftLeg, leftLeg);
        rightWiden = widen(pose.bipedRightArm);
        leftWiden = widen(pose.bipedLeftArm);

        float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        try {
            mc.getTextureManager().bindTexture(TEXTURE);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_FOG);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glDepthMask(false);
            GL11.glEnable(GL11.GL_ALPHA_TEST);
            // Alfa 255 = coração, 5 = ponta mais distante (ver make_veins.py).
            GL11.glAlphaFunc(GL11.GL_GREATER, (255 - 250 * reveal - 0.5F) / 255);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240, 240);

            // Escurece a pele sob a veia, para a cor aparecer em qualquer skin.
            OpenGlHelper.glBlendFunc(GL11.GL_ZERO, GL11.GL_ONE_MINUS_SRC_COLOR, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glColor4f(0.75F * intensity, 0.75F * intensity, 0.75F * intensity, 1);
            renderParts();

            OpenGlHelper.glBlendFunc(GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ONE, GL11.GL_ZERO);
            GL11.glColor4f((color >> 16 & 255) / 255F * intensity, (color >> 8 & 255) / 255F * intensity,
                    (color & 255) / 255F * intensity, 1);
            renderParts();
            // Miolo quase branco.
            GL11.glColor4f(0.22F * intensity, 0.22F * intensity, 0.22F * intensity, 1);
            renderParts();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);
            GL11.glPopAttrib();
            GL11.glColor4f(1, 1, 1, 1);
        }
    }

    private void renderParts() {
        head.render(0.0625F);
        body.render(0.0625F);
        if (bender == null) {
            rightArm.render(0.0625F);
            leftArm.render(0.0625F);
        } else {
            // Em braço fino a casca encolhe junto, a partir da face encostada no tronco.
            bender.render(rightArm, rightAngles, rightElbow, rightWiden, 1);
            bender.render(leftArm, leftAngles, leftElbow, leftWiden, -1);
        }
        rightLeg.render(0.0625F);
        leftLeg.render(0.0625F);
    }

    /** Largura do braço do jogador em relação aos 4 pixels da casca. */
    private static double widen(ModelRenderer arm) {
        if (arm.cubeList.isEmpty()) return 1;
        ModelBox box = (ModelBox) arm.cubeList.get(0);
        return Math.abs(box.posX2 - box.posX1) / 4.0;
    }

    private static void copy(ModelRenderer from, ModelRenderer to) {
        to.setRotationPoint(from.rotationPointX, from.rotationPointY, from.rotationPointZ);
        to.rotateAngleX = from.rotateAngleX;
        to.rotateAngleY = from.rotateAngleY;
        to.rotateAngleZ = from.rotateAngleZ;
    }
}
