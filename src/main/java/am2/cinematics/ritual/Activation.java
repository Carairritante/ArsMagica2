package am2.cinematics.ritual;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;

/** O gesto que chama o ritual, reconhecido igual no cliente e no servidor. */
public final class Activation {
    /** {@code arsmagica2:rune} meta 19: Orbe Infinito vermelho. */
    public static final int RED_ORB_META = 19;

    private Activation() {}

    public static Item orb() {
        return GameRegistry.findItem("arsmagica2", "rune");
    }

    /** Agachado, com o Orbe vermelho na mão, sobre um bloco de esmeralda. */
    public static boolean matches(World world, EntityPlayer player, int x, int y, int z) {
        if (!player.isSneaking()) return false;
        ItemStack held = player.getCurrentEquippedItem();
        Item orb = orb();
        return orb != null && held != null && held.getItem() == orb && held.getItemDamage() == RED_ORB_META
                && world.getBlock(x, y, z) == Blocks.emerald_block;
    }
}
