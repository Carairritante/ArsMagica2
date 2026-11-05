package am2.items.renderers;

import am2.blocks.tileentities.TileEntityCelestialPrism;
import am2.network.AMDataWriter;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import am2.texture.ResourceManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderBlocks;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.IItemRenderer;
import net.minecraftforge.client.model.AdvancedModelLoader;
import net.minecraftforge.client.model.IModelCustom;
import net.minecraftforge.client.model.obj.WavefrontObject;
import net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka;
import org.lwjgl.opengl.GL11;

import java.util.Map;

import static net.minecraftforge.client.IItemRenderer.ItemRenderType.*;

@SideOnly(Side.CLIENT)
public class RenderItemBoxOfIllusions implements IItemRenderer {

    IModelCustom model;
    ResourceLocation texture1 = new ResourceLocation("arsmagica2", "obj/boxOfIllusions.png");
    private ResourceLocation rLoc_celestial;
    private final WavefrontObject model_celestial;

    public RenderItemBoxOfIllusions() {
        model =  AdvancedModelLoader.loadModel(new ResourceLocation("arsmagica2", "obj/makeupboxjoined.obj"));
        model_celestial = (WavefrontObject)AdvancedModelLoader.loadModel(ResourceManager.getOBJFilePath("celestial_prism.obj"));
        rLoc_celestial = new ResourceLocation("arsmagica2", ResourceManager.getCustomBlockTexturePath("fractal.png"));
    }

    @Override
    public boolean handleRenderType(ItemStack item, ItemRenderType type) {
        return true;
    }

    private void renderCelestial(TileEntityCelestialPrism tile, double d, double d1, double d2, float f){
        Minecraft.getMinecraft().renderEngine.bindTexture(rLoc_celestial);
        GL11.glScalef(0.7f, 0.7f, 0.7f);
        GL11.glTranslated(0.1, 0.1, 0.05);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glDisable(GL11.GL_CULL_FACE);
        RenderHelper.disableStandardItemLighting();
        Tessellator tessellator = Tessellator.instance;

        try{
            model_celestial.renderAll();
        }catch (Throwable t){

        }

        RenderHelper.enableStandardItemLighting();
        GL11.glEnable(GL11.GL_CULL_FACE);
        GL11.glDisable(GL11.GL_BLEND);
    }

    @Override
    public boolean shouldUseRenderHelper(ItemRenderType type, ItemStack item, ItemRendererHelper helper) {
        return true;
    }

    public static boolean doRotations = false;
    public static int rotationTick = 0;
    public static volatile String localPlayerModel = null;
    private static long lastToggleTimeMs = 0L;

    public static boolean isValidIllusionModel(String model) {
        return IllusionEntityHelper.isValidMob(model);
    }

    public static void enforceLocalPlayerModel(net.minecraft.entity.player.EntityPlayer localPlayer) {
        if (localPlayer == null) {
            return;
        }
        String name = localPlayer.getCommandSenderName();
        if (localPlayerModel == null || !isValidIllusionModel(localPlayerModel)) {
            localPlayerModel = null;
            MysteriumPatchesFixesMagicka.playerModelMap.remove(name);
            IllusionEntityHelper.clearPlayerCache(name);
        } else {
            MysteriumPatchesFixesMagicka.playerModelMap.put(name, localPlayerModel);
        }
    }

    public static void onBoxRightClick(net.minecraft.entity.player.EntityPlayer player) {
        EntityPlayer local = Minecraft.getMinecraft().thePlayer;
        if (local == null || player != local) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - lastToggleTimeMs < 250L) {
            return;
        }
        lastToggleTimeMs = now;
        doRotations = true;
        rotationTick = 0;
        toggleLocalPlayerIllusion(local);
    }

    @Override
    public void renderItem(ItemRenderType type, ItemStack item, Object... data) {
        Minecraft.getMinecraft().renderEngine.bindTexture(texture1);
        boolean isLocalHolder = data != null && data.length > 1 && data[1] == Minecraft.getMinecraft().thePlayer;

        if (type == EQUIPPED_FIRST_PERSON) { // yes, I know switches exist. Don't want them.
            if (cooldown > 0) cooldown--;
            GL11.glPushMatrix();
            GL11.glScalef(1f, 1f, 1f);
            GL11.glRotated(130, 0, -1 ,0);
            GL11.glTranslated(-0.02, 0.5, -0.6);
            GL11.glPushMatrix();
            if (doRotations && isLocalHolder) {
                rotationTick++;
                GL11.glRotated(rotationTick * 9, 0, 1, 0);
                if (rotationTick > 40) {
                    doRotations = false;
                    rotationTick = 0;
                }
            }
            model.renderAll();
            renderCelestial(new TileEntityCelestialPrism(), 1, 1,1, 1);
            GL11.glPopMatrix();
            GL11.glPopMatrix();
        } else if (type == INVENTORY) {
            GL11.glPushMatrix();
            GL11.glScalef(1.2f, 1.2f, 1.2f);
            GL11.glTranslated(0.02, -0.4, 0.02);
            GL11.glPushMatrix();
            model.renderAll();
            renderCelestial(new TileEntityCelestialPrism(), 1, 1,1, 1);
            GL11.glPopMatrix();
            GL11.glPopMatrix();
        } else if (type == EQUIPPED) { // third person
            if (cooldown > 0) cooldown--;
            GL11.glPushMatrix();
            GL11.glScalef(1.1f, 1.1f, 1.1f);
            GL11.glTranslated(0.415, 0.4, 0.615);
            GL11.glPushMatrix();
            if (doRotations && isLocalHolder) {
                rotationTick++;
                GL11.glRotated(rotationTick * 9, 0, 1, 0);
                if (rotationTick > 40) {
                    doRotations = false;
                    rotationTick = 0;
                }
            }
            model.renderAll();
            renderCelestial(new TileEntityCelestialPrism(), 1, 1,1, 1);
            GL11.glPopMatrix();
            GL11.glPopMatrix();
        } else {
            GL11.glPushMatrix();
            GL11.glScalef(1, 1, 1);
            GL11.glPushMatrix();
            model.renderAll();
            renderCelestial(new TileEntityCelestialPrism(), 1, 1,1, 1);
            GL11.glPopMatrix();
            GL11.glPopMatrix();
        }
    }

    private int cooldown = 0; // to prevent glitches in case calling would happen twice

    private void setCustomRenderer() {
        if (cooldown > 0) return;
        EntityPlayer local = Minecraft.getMinecraft().thePlayer;
        if (local != null) {
            toggleLocalPlayerIllusion(local);
            cooldown = 20;
        }
    }

    public static void toggleLocalPlayerIllusion(net.minecraft.entity.player.EntityPlayer player) {
        if (player == null) {
            return;
        }
        String playerName = player.getCommandSenderName();
        String current = MysteriumPatchesFixesMagicka.playerModelMap.get(playerName);
        if (localPlayerModel != null || current != null || player.isSneaking()) {
            localPlayerModel = null;
            MysteriumPatchesFixesMagicka.playerModelMap.remove(playerName);
            IllusionEntityHelper.clearPlayerCache(playerName);
            IllusionEntityHelper.cleanupDisguisePhysics(player);
            syncLocalModelToServer(playerName, "");
            return;
        }

        String chosenModel = findIllusionModelFromInventory(player);
        if (chosenModel != null) {
            localPlayerModel = chosenModel;
            MysteriumPatchesFixesMagicka.playerModelMap.put(playerName, chosenModel);
            syncLocalModelToServer(playerName, chosenModel);
        } else {
            localPlayerModel = null;
            MysteriumPatchesFixesMagicka.playerModelMap.remove(playerName);
            syncLocalModelToServer(playerName, "");
        }
    }

    private static String findIllusionModelFromInventory(net.minecraft.entity.player.EntityPlayer player) {
        ItemStack[] inv = player.inventory.mainInventory;
        if (inv == null) {
            return null;
        }
        // Check hotbar first (0..8), then main inventory (9..end) so the player can control priority via hotbar
        for (int i = 0; i < inv.length; i++) {
            String match = getModelForCatalyst(inv[i], player);
            if (match != null) {
                return match;
            }
        }
        return null;
    }

    private static String getModelForCatalyst(ItemStack stack, net.minecraft.entity.player.EntityPlayer player) {
        return IllusionEntityHelper.getModelForCatalyst(stack, player);
    }

    public static void syncLocalModelToServer(String playerName, String modelValue) {
        if (playerName == null || playerName.isEmpty()) {
            return;
        }
        AMDataWriter writer = new AMDataWriter();
        writer.add(1);
        writer.add(playerName);
        writer.add(modelValue != null ? modelValue : "");
        AMNetHandler.INSTANCE.sendPacketToServer(AMPacketIDs.SYNCMAPTOSERVER, writer.generate());
    }

}
