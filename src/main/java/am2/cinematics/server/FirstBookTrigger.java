package am2.cinematics.server;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.play.server.S09PacketHeldItemChange;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;

/**
 * "O Despertar": toca quando um jogador de nível mágico 0 pega o Arcane
 * Compendium, a mesma condição com que o AM2 libera o nível 1
 * (AMEventHandler.onPlayerPickupItem). O AM2 não é alterado.
 */
public final class FirstBookTrigger {
    public static final String SCENE = "despertar";
    private static final String COMPENDIUM_CLASS = "am2.items.ItemArcaneCompendium";
    private static final String SEEN_ROOT = "amcinematics";
    /** Espera máxima pelo nível e por o jogador tocar o chão. */
    private static final int MAX_WAIT_TICKS = 40;

    private final Method properties, magicLevel;
    private final Map<UUID, Integer> waiting = new HashMap<UUID, Integer>();

    private FirstBookTrigger(Method properties, Method magicLevel) {
        this.properties = properties;
        this.magicLevel = magicLevel;
    }

    /** @return nulo se a versão do AM2 não tiver o que o gatilho precisa. */
    public static FirstBookTrigger create() {
        try {
            // Sem inicializar a classe: o AM2 faz isso quando quiser.
            Class<?> type = Class.forName("am2.playerextensions.ExtendedProperties", false,
                    FirstBookTrigger.class.getClassLoader());
            return new FirstBookTrigger(type.getMethod("For", EntityLivingBase.class), type.getMethod("getMagicLevel"));
        } catch (Exception e) {
            AMCinematics.log.warn("Gatilho do primeiro livro indisponível neste AM2: " + e);
            return null;
        }
    }

    /** Esquece que o jogador já viu as cinemáticas (para testes). */
    static void forget(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        persisted.removeTag(SEEN_ROOT);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private static boolean hasSeen(EntityPlayerMP player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG)
                .getCompoundTag(SEEN_ROOT).getBoolean(SCENE);
    }

    private static void markSeen(EntityPlayerMP player) {
        NBTTagCompound data = player.getEntityData();
        // PlayerPersisted sobrevive à morte do jogador.
        NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        NBTTagCompound seen = persisted.getCompoundTag(SEEN_ROOT);
        seen.setBoolean(SCENE, true);
        persisted.setTag(SEEN_ROOT, seen);
        data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
    }

    private int level(EntityPlayerMP player) {
        try {
            return (Integer) magicLevel.invoke(properties.invoke(null, player));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler o nível mágico", e);
        }
    }

    // HIGHEST: roda antes do AM2, enquanto o nível ainda é o anterior.
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPickup(EntityItemPickupEvent event) {
        if (!(event.entityPlayer instanceof EntityPlayerMP) || !isCompendium(event.item.getEntityItem())) return;
        EntityPlayerMP player = (EntityPlayerMP) event.entityPlayer;
        if (waiting.containsKey(player.getUniqueID()) || hasSeen(player) || level(player) > 0) return;
        waiting.put(player.getUniqueID(), 0);
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || waiting.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Integer>> it = waiting.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Integer> entry = it.next();
            EntityPlayerMP player = CinematicServer.find(entry.getKey());
            int waited = entry.getValue() + 1;
            entry.setValue(waited);
            if (player == null || !player.isEntityAlive()) {
                it.remove();
            } else if (level(player) < 1) {
                // O desbloqueio não aconteceu (outro mod cancelou a coleta).
                if (waited >= MAX_WAIT_TICKS) it.remove();
            } else if (player.onGround || player.isInWater() || waited >= MAX_WAIT_TICKS) {
                it.remove();
                start(player);
            }
        }
    }

    private void start(EntityPlayerMP player) {
        try {
            holdCompendium(player);
            CinematicServer.INSTANCE.playOn(player, SceneStore.read(SCENE), CinematicServer.MAX_RADIUS, true);
            markSeen(player);
        } catch (IOException e) {
            AMCinematics.log.error("Cena " + SCENE + " indisponível: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            AMCinematics.log.error("Cena " + SCENE + " inválida: " + e.getMessage());
        }
    }

    /** Seleciona o compêndio na barra rápida: no fim da cena ele está na mão. */
    private static void holdCompendium(EntityPlayerMP player) {
        for (int slot = 0; slot < 9; slot++) {
            if (!isCompendium(player.inventory.mainInventory[slot])) continue;
            if (player.inventory.currentItem != slot) {
                player.inventory.currentItem = slot;
                player.playerNetServerHandler.sendPacket(new S09PacketHeldItemChange(slot));
            }
            return;
        }
    }

    private static boolean isCompendium(ItemStack stack) {
        return stack != null && stack.getItem() != null
                && COMPENDIUM_CLASS.equals(stack.getItem().getClass().getName());
    }

    public void reset() {
        waiting.clear();
    }
}
