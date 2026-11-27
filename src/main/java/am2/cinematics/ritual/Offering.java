package am2.cinematics.ritual;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * O que o ritual tira do altar e do jogador no começo: reagentes, Orbe, giz e
 * velas. Fica gravado no jogador até o fim, para devolver o que for devolvido
 * mesmo que o servidor caia no meio.
 */
final class Offering {
    private static final int CIRCLE_RADIUS = 3;

    private Offering() {}

    /**
     * Consome a oferta.
     *
     * @param reagents quanto tirar de cada item solto
     * @param takeOrb tira um item da mão (no criativo não)
     * @return o registro do que saiu
     */
    static NBTTagCompound take(Am2Ritual am2, EntityPlayerMP player, World world, int x, int y, int z,
            Map<EntityItem, Integer> reagents, boolean takeOrb) {
        NBTTagCompound offering = new NBTTagCompound();
        offering.setInteger("dim", world.provider.dimensionId);
        offering.setInteger("x", x);
        offering.setInteger("y", y);
        offering.setInteger("z", z);

        NBTTagList items = new NBTTagList();
        for (Map.Entry<EntityItem, Integer> entry : reagents.entrySet()) {
            EntityItem entity = entry.getKey();
            ItemStack rest = entity.getEntityItem().copy();
            ItemStack taken = rest.copy();
            taken.stackSize = entry.getValue();
            items.appendTag(taken.writeToNBT(new NBTTagCompound()));
            rest.stackSize -= entry.getValue();
            if (rest.stackSize <= 0) {
                entity.setDead();
            } else {
                entity.setEntityItemStack(rest);
            }
        }
        offering.setTag("itens", items);

        if (takeOrb) {
            ItemStack held = player.getCurrentEquippedItem();
            ItemStack orb = held.copy();
            orb.stackSize = 1;
            offering.setTag("orbe", orb.writeToNBT(new NBTTagCompound()));
            // A pilha vazia é retirada da mão pelo próprio tratamento do clique.
            held.stackSize--;
        }
        offering.setTag("circulo", takeCircle(am2, world, x, y, z));
        return offering;
    }

    /** Apaga o círculo como o ritual original, anotando os blocos que sumiram. */
    private static NBTTagList takeCircle(Am2Ritual am2, World world, int x, int y, int z) {
        List<NBTTagCompound> before = new ArrayList<NBTTagCompound>();
        for (int bx = x - CIRCLE_RADIUS; bx <= x + CIRCLE_RADIUS; bx++) {
            for (int bz = z - CIRCLE_RADIUS; bz <= z + CIRCLE_RADIUS; bz++) {
                Block block = world.getBlock(bx, y, bz);
                if (block == Blocks.air) continue;
                NBTTagCompound saved = new NBTTagCompound();
                saved.setInteger("x", bx);
                saved.setInteger("y", y);
                saved.setInteger("z", bz);
                saved.setString("bloco", Block.blockRegistry.getNameForObject(block));
                saved.setByte("meta", (byte) world.getBlockMetadata(bx, y, bz));
                TileEntity tile = world.getTileEntity(bx, y, bz);
                if (tile != null) {
                    NBTTagCompound data = new NBTTagCompound();
                    tile.writeToNBT(data);
                    saved.setTag("dados", data);
                }
                before.add(saved);
            }
        }
        am2.consumeCircle(world, x, y, z);
        NBTTagList removed = new NBTTagList();
        for (NBTTagCompound saved : before) {
            if (world.isAirBlock(saved.getInteger("x"), saved.getInteger("y"), saved.getInteger("z"))) {
                removed.appendTag(saved);
            }
        }
        return removed;
    }

    /** Devolve o Orbe (se tiver saído) ao jogador. */
    static void returnOrb(EntityPlayerMP player, NBTTagCompound offering) {
        if (offering.hasKey("orbe")) give(player, offering, offering.getCompoundTag("orbe"));
    }

    /** Põe o giz e as velas de volta onde ainda houver ar. */
    static void restoreCircle(NBTTagCompound offering) {
        World world = world(offering);
        if (world == null) return;
        NBTTagList circle = offering.getTagList("circulo", 10);
        for (int i = 0; i < circle.tagCount(); i++) {
            NBTTagCompound saved = circle.getCompoundTagAt(i);
            int x = saved.getInteger("x"), y = saved.getInteger("y"), z = saved.getInteger("z");
            Block block = Block.getBlockFromName(saved.getString("bloco"));
            if (block == null || !world.isAirBlock(x, y, z)) continue;
            int meta = saved.getByte("meta");
            world.setBlock(x, y, z, block, meta, 3);
            // Blocos podem escolher outro desenho ao serem postos.
            world.setBlockMetadataWithNotify(x, y, z, meta, 2);
            TileEntity tile = world.getTileEntity(x, y, z);
            if (tile != null && saved.hasKey("dados")) {
                tile.readFromNBT(saved.getCompoundTag("dados"));
                tile.markDirty();
                world.markBlockForUpdate(x, y, z);
            }
        }
    }

    /** Devolve tudo: ritual interrompido sem culpa do jogador. */
    static void refund(EntityPlayerMP player, NBTTagCompound offering) {
        restoreCircle(offering);
        NBTTagList items = offering.getTagList("itens", 10);
        for (int i = 0; i < items.tagCount(); i++) give(player, offering, items.getCompoundTagAt(i));
        returnOrb(player, offering);
    }

    private static void give(EntityPlayerMP player, NBTTagCompound offering, NBTTagCompound data) {
        ItemStack stack = ItemStack.loadItemStackFromNBT(data);
        if (stack == null) return;
        if (player.isEntityAlive()) {
            player.inventory.addItemStackToInventory(stack);
            if (stack.stackSize > 0) player.dropPlayerItemWithRandomChoice(stack, false);
            player.inventoryContainer.detectAndSendChanges();
            return;
        }
        // Morto, o inventário se perde ao renascer: o item volta para o altar.
        World world = world(offering);
        if (world != null) {
            world.spawnEntityInWorld(new EntityItem(world, offering.getInteger("x") + 0.5,
                    offering.getInteger("y") + 1.2, offering.getInteger("z") + 0.5, stack));
        }
    }

    private static World world(NBTTagCompound offering) {
        return MinecraftServer.getServer().worldServerForDimension(offering.getInteger("dim"));
    }
}
