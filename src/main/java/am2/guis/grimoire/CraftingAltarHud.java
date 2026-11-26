package am2.guis.grimoire;

import am2.blocks.tileentities.TileEntityCraftingAltar;
import am2.blocks.tileentities.TileEntityLectern;
import am2.items.ItemEssence;
import am2.items.ItemsCommonProxy;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Items;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MovingObjectPosition;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL12;

import java.util.List;

/**
 * Grimoire-styled floating mini-HUD for the Crafting Altar:
 * appears when looking at the Crafting Altar / its Lectern, or standing near an active Altar,
 * showing spell progress, the exact next reagent / Etherium step, inventory match, and overload warnings.
 */
@SideOnly(Side.CLIENT)
public final class CraftingAltarHud {
    private static final RenderItem ITEMS = new RenderItem();
    private static final int PANEL = 0x171D3A;
    private static final int BORDER = 0x3D4C8A;
    private static final int TEXT = 0xEEF1FF;
    private static final int MUTED = 0x909AC9;
    private static final int GOLD = 0xFFE08C;
    private static final int CYAN = 0x7FE3FF;
    private static final int OK = 0x5CFFC0;
    private static final int DANGER = 0xFF5A72;

    private CraftingAltarHud() {}

    public static void render(Minecraft mc, int screenW, int screenH) {
        if (mc == null || mc.thePlayer == null || mc.theWorld == null || mc.currentScreen != null) return;

        TileEntityCraftingAltar altar = findRelevantAltar(mc);
        if (altar == null || !altar.structureValid()) return;

        TileEntityLectern lectern = altar.getLinkedLectern();
        boolean crafting = altar.isCrafting();
        boolean hasBook = lectern != null && lectern.hasStack();
        if (!crafting && !hasBook) return;

        ItemStack nextItem = crafting ? altar.getNextPlannedItem() : new ItemStack(ItemsCommonProxy.rune, 1, ItemsCommonProxy.rune.META_BLANK);
        boolean isEssencePower = crafting && nextItem != null
                && nextItem.getItem() instanceof ItemEssence && nextItem.getItemDamage() > ItemEssence.META_MAX;
        boolean isFinalParchment = crafting && nextItem != null && nextItem.getItem() == ItemsCommonProxy.spellParchment;
        boolean overpowered = (lectern != null && lectern.getOverpowered())
                || (altar.getPlannedPartCount() > 0 && altar.getMaxEffects() > 0 && altar.getPlannedPartCount() > altar.getMaxEffects());

        int added = altar.getAddedItemCount();
        int total = altar.getTotalPlannedItems();
        String spellName = altar.getCurrentSpellName();
        if (spellName == null || spellName.trim().isEmpty()) {
            spellName = GrimoireLang.tr("am2.altarhud.title");
        }

        String line1;
        String line2 = null;
        int line1Col = TEXT;
        int line2Col = MUTED;

        if (!crafting) {
            line1 = GrimoireLang.tr("am2.altarhud.start");
            line1Col = GOLD;
            if (altar.getPlannedPartCount() > 0 && altar.getMaxEffects() > 0) {
                line2 = GrimoireLang.trFmt("am2.altarhud.capacity", altar.getPlannedPartCount(), altar.getMaxEffects());
                line2Col = overpowered ? DANGER : OK;
            }
        } else if (isEssencePower) {
            int req = nextItem.stackSize;
            int got = Math.min(req, altar.getCurrentConsumedPower());
            line1 = GrimoireLang.trFmt("am2.altarhud.etherium", got, req);
            line1Col = CYAN;
            line2 = altar.switchIsOn()
                    ? GrimoireLang.tr("am2.altarhud.channeling")
                    : GrimoireLang.tr("am2.altarhud.pullLever");
            line2Col = altar.switchIsOn() ? OK : GOLD;
        } else if (isFinalParchment) {
            line1 = GrimoireLang.tr("am2.altarhud.finish");
            line1Col = OK;
            if (playerHasMatchingItem(mc.thePlayer, nextItem)) {
                line2 = "\u2714 " + GrimoireLang.tr("am2.altarhud.inInventory");
                line2Col = OK;
            }
        } else if (nextItem != null) {
            line1 = GrimoireLang.trFmt("am2.altarhud.next", nextItem.stackSize, nextItem.getDisplayName());
            line1Col = TEXT;
            if (playerHasMatchingItem(mc.thePlayer, nextItem)) {
                line2 = "\u2714 " + GrimoireLang.tr("am2.altarhud.inInventory");
                line2Col = OK;
            }
        } else {
            return;
        }

        if (overpowered) {
            line2 = GrimoireLang.trFmt("am2.altarhud.overpowered", altar.getPlannedPartCount(), altar.getMaxEffects());
            line2Col = DANGER;
        }

        FontRenderer font = mc.fontRenderer;
        int contentW = Math.max(font.getStringWidth(spellName) + 44, font.getStringWidth(line1) + 32);
        if (line2 != null) contentW = Math.max(contentW, Math.round(font.getStringWidth(line2) * 0.82f) + 32);
        int w = Math.min(screenW - 20, Math.max(168, Math.min(256, contentW + 10)));
        int h = (crafting && total > 0) ? 44 : 38;
        int x = (screenW - w) / 2;
        int y = 8;

        int accent = overpowered ? DANGER : (isFinalParchment ? OK : (isEssencePower ? CYAN : GOLD));

        GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
        GL11.glPushMatrix();
        try {
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_DEPTH_TEST);

            GrimoireDraw.rect(x - 1, y + 2, w + 2, h, 0x04060E, 0.50f);
            GrimoireDraw.panel(x, y, w, h, PANEL, overpowered ? DANGER : BORDER);
            float mid = x + w * 0.5f;
            float half = Math.max(16f, w * 0.44f);
            GrimoireDraw.hGrad(mid - half, y + 1, half, 1, accent, 0f, accent, 0.78f, true);
            GrimoireDraw.hGrad(mid, y + 1, half, 1, accent, 0.78f, accent, 0f, true);

            // Header: spell name + step counter
            GrimoireDecor.rune(5, x + 9, y + 9, 8, accent, 0.9f, true);
            String stepStr = (crafting && total > 0) ? (Math.min(added + 1, total) + "/" + total) : "";
            int sw = font.getStringWidth(stepStr);
            font.drawStringWithShadow(font.trimStringToWidth(spellName, w - 26 - sw), x + 16, y + 5, GOLD);
            if (!stepStr.isEmpty()) {
                font.drawStringWithShadow(stepStr, x + w - 7 - sw, y + 5, CYAN);
            }

            // Item icon medallion on the left
            int iconCx = x + 16;
            int iconCy = y + 26;
            GrimoireDraw.ring(iconCx, iconCy, 10.2f, 10.2f, 0x0D1124, 0.92f, 0, 1, 1, false);
            GrimoireDraw.ring(iconCx, iconCy, 10.4f, 1.0f, accent, 0.9f, 0, 1, 1, true);
            if (nextItem != null && nextItem.getItem() != null) {
                drawMiniStack(mc, font, nextItem, iconCx - 8, iconCy - 8);
            }

            // Next step text
            int textX = x + 31;
            int maxTxtW = w - 37;
            font.drawStringWithShadow(font.trimStringToWidth(line1, maxTxtW), textX, y + 17, line1Col);
            if (line2 != null) {
                GL11.glPushMatrix();
                GL11.glScalef(0.80f, 0.80f, 1f);
                font.drawStringWithShadow(font.trimStringToWidth(line2, Math.round(maxTxtW / 0.80f)),
                        Math.round(textX / 0.80f), Math.round((y + 28) / 0.80f), line2Col);
                GL11.glPopMatrix();
            }

            // Sleek progress bar at bottom while crafting
            if (crafting && total > 0) {
                float frac = Math.max(0f, Math.min(1f, added / (float) total));
                int bx = x + 6;
                int bw = w - 12;
                int by = y + h - 5;
                GrimoireDraw.rect(bx, by, bw, 3, 0x0B0F22, 0.9f);
                if (frac > 0.005f) {
                    GrimoireDraw.hGrad(bx, by, bw * frac, 3, GrimoireDraw.GOLD_MID, 0.95f, accent, 0.98f, false);
                }
            }
        } finally {
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1f, 1f, 1f, 1f);
        }
    }

    private static TileEntityCraftingAltar findRelevantAltar(Minecraft mc) {
        // 1. Direct look target (Crafting Altar or its linked Lectern)
        MovingObjectPosition mop = mc.objectMouseOver;
        if (mop != null && mop.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            TileEntity te = mc.theWorld.getTileEntity(mop.blockX, mop.blockY, mop.blockZ);
            if (te instanceof TileEntityCraftingAltar) {
                return (TileEntityCraftingAltar) te;
            }
            if (te instanceof TileEntityLectern) {
                // Lectern sits at (±2, -3, ±2) relative to the Altar controller
                for (int dx = -2; dx <= 2; dx += 4) {
                    for (int dz = -2; dz <= 2; dz += 4) {
                        TileEntity cand = mc.theWorld.getTileEntity(mop.blockX + dx, mop.blockY + 3, mop.blockZ + dz);
                        if (cand instanceof TileEntityCraftingAltar) {
                            return (TileEntityCraftingAltar) cand;
                        }
                    }
                }
            }
        }

        // 2. Active crafting altar within 6.5 blocks of the player
        EntityPlayer player = mc.thePlayer;
        double bestDistSq = 6.5 * 6.5;
        TileEntityCraftingAltar best = null;
        List<?> tes = mc.theWorld.loadedTileEntityList;
        for (int i = 0; i < tes.size(); i++) {
            Object obj = tes.get(i);
            if (!(obj instanceof TileEntityCraftingAltar)) continue;
            TileEntityCraftingAltar altar = (TileEntityCraftingAltar) obj;
            if (!altar.isCrafting()) continue;
            double dx = (altar.xCoord + 0.5) - player.posX;
            double dy = (altar.yCoord - 1.5) - player.posY;
            double dz = (altar.zCoord + 0.5) - player.posZ;
            double distSq = dx * dx + dy * dy + dz * dz;
            if (distSq < bestDistSq) {
                bestDistSq = distSq;
                best = altar;
            }
        }
        return best;
    }

    private static boolean playerHasMatchingItem(EntityPlayer player, ItemStack target) {
        if (player == null || target == null || target.getItem() == null) return false;
        int found = 0;
        for (ItemStack s : player.inventory.mainInventory) {
            if (s == null || s.getItem() == null) continue;
            if (matches(target, s)) {
                found += s.stackSize;
                if (found >= target.stackSize) return true;
            }
        }
        return false;
    }

    private static boolean matches(ItemStack target, ItemStack input) {
        if (target.getItem() == Items.potionitem && input.getItem() == Items.potionitem) {
            return (target.getItemDamage() & 0xF) == (input.getItemDamage() & 0xF);
        }
        return target.getItem() == input.getItem()
                && (target.getItemDamage() == input.getItemDamage() || target.getItemDamage() == Short.MAX_VALUE);
    }

    private static void drawMiniStack(Minecraft mc, FontRenderer font, ItemStack stack, int x, int y) {
        RenderHelper.enableGUIStandardItemLighting();
        GL11.glEnable(GL12.GL_RESCALE_NORMAL);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        ITEMS.zLevel = 100f;
        try {
            ITEMS.renderItemAndEffectIntoGUI(font, mc.getTextureManager(), stack, x, y);
        } catch (Throwable ignored) {
        }
        RenderHelper.disableStandardItemLighting();
        GL11.glDisable(GL12.GL_RESCALE_NORMAL);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glColor4f(1f, 1f, 1f, 1f);
    }
}
