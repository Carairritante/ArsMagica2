package am2.client.vfx;

import am2.api.spell.ItemSpellBase;
import am2.items.ItemSpellBook;
import am2.items.ItemSpellStaff;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;

/** Visual state only: it neither starts a cast nor changes the use duration. */
@SideOnly(Side.CLIENT)
public final class CastingAnimationState {
    private static final Map<EntityLivingBase, Progress> progress = new WeakHashMap<EntityLivingBase, Progress>();
    private static float partialTicks;

    private static final class Progress {
        Item item;
        int tick = Integer.MIN_VALUE;
        float previous, current;
    }

    private CastingAnimationState() {}

    public static void setPartialTicks(float value) { partialTicks = value; }
    public static float getPartialTicks() { return partialTicks; }

    public static boolean isCasting(EntityPlayer player, ItemStack stack) {
        if (player == null || stack == null || !(stack.getItem() instanceof ItemSpellBook || stack.getItem() instanceof ItemSpellBase || stack.getItem() instanceof ItemSpellStaff)) return false;
        ItemStack held = player.getHeldItem();
        if (held == null || held.getItem() != stack.getItem()) return false;
        ItemStack using = player.getItemInUse();
        if (using != null && using.getItem() == stack.getItem() && player.getItemInUseCount() > 0) return true;
        Minecraft mc = Minecraft.getMinecraft();
        if (player == mc.thePlayer) {
            // Empty books and a refreshed ItemStack can have no use countdown.
            // Respect the user's bound use key, without changing spell behavior.
            return mc.currentScreen == null && mc.inGameHasFocus && !player.isSneaking()
                    && mc.gameSettings.keyBindUseItem.getIsKeyPressed();
        }
        // Remote players synchronize this flag, not their local use countdown.
        return player.isEating();
    }

    public static float getOpenProgress(EntityLivingBase entity, ItemStack stack, float partial) {
        if (!(entity instanceof EntityPlayer) || stack == null) return 0.0f;
        Progress state = progress.get(entity);
        if (state == null || state.item != stack.getItem() || entity.ticksExisted < state.tick) {
            state = new Progress(); state.item = stack.getItem();
            progress.put(entity, state);
        }
        if (state.tick != entity.ticksExisted) {
            state.tick = entity.ticksExisted;
            state.previous = state.current;
            float target = isCasting((EntityPlayer)entity, stack) ? 1.0f : 0.0f;
            state.current += MathHelper.clamp_float(target - state.current, -0.2f, 0.25f);
        }
        return state.previous + (state.current - state.previous) * MathHelper.clamp_float(partial, 0.0f, 1.0f);
    }

    public static void clear() { progress.clear(); }
}
