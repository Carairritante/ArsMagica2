package am2.cinematics.ritual;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttribute;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * A recompensa do ritual novo, somada à original do AM2 ({@code hasRitual}, que
 * já dá 23.000 de mana): 80.000 de mana no total, recuperação 50% mais rápida,
 * 1.000 de burnout com recuperação em dobro e, sobre grama ou terra a céu
 * aberto, mais 25% de recuperação ("o mundo te alimenta").
 *
 * São modificadores dos atributos do próprio AM2, não gravados no jogador: são
 * repostos enquanto ele tiver a marca do ritual novo e a fusão, e somem sem o mod.
 */
public final class FusionReward {
    /** Em {@code PlayerPersisted}: sobrevive à morte e guarda quem fez o ritual novo. */
    private static final String DONE_TAG = "amcinematicsRitualNovo";
    private static final int CHECK_TICKS = 20;

    private static final int ADD = 0, MULTIPLY = 2;
    private static final AttributeModifier MANA = modifier("6f1d3a52-0c1e-4b8a-9d57-1a2b7c9e4101",
            "ritual: mana", 80000 - 23000, ADD);
    private static final AttributeModifier BURNOUT = modifier("6f1d3a52-0c1e-4b8a-9d57-1a2b7c9e4102",
            "ritual: burnout", 1000, ADD);
    // O atributo do AM2 é o tempo para encher a barra: 2/3 do tempo é 50% mais rápido.
    private static final AttributeModifier REGENERATION = modifier("6f1d3a52-0c1e-4b8a-9d57-1a2b7c9e4103",
            "ritual: mana mais rapida", -1.0 / 3, MULTIPLY);
    private static final AttributeModifier RECOVERY = modifier("6f1d3a52-0c1e-4b8a-9d57-1a2b7c9e4104",
            "ritual: burnout em dobro", 1.0, MULTIPLY);
    // 0,8 do tempo é mais 25% de recuperação.
    private static final AttributeModifier WORLD = modifier("6f1d3a52-0c1e-4b8a-9d57-1a2b7c9e4105",
            "ritual: o mundo alimenta", -0.2, MULTIPLY);

    final Am2Ritual am2;

    private FusionReward(Am2Ritual am2) {
        this.am2 = am2;
    }

    /** @return nulo se a versão do AM2 não tiver o que o ritual precisa. */
    public static FusionReward create() {
        Am2Ritual am2 = Am2Ritual.create(Activation.orb());
        return am2 == null ? null : new FusionReward(am2);
    }

    private static AttributeModifier modifier(String id, String name, double amount, int operation) {
        return new AttributeModifier(UUID.fromString(id), "amcinematics " + name, amount, operation).setSaved(false);
    }

    /** O jogador completou o ritual novo (mesmo antes de a recompensa existir). */
    boolean completed(EntityPlayer player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG).getBoolean(DONE_TAG);
    }

    void setCompleted(EntityPlayer player, boolean value) {
        NBTTagCompound data = player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        if (value) data.setBoolean(DONE_TAG, true);
        else data.removeTag(DONE_TAG);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
    }

    @SubscribeEvent
    public void onTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof EntityPlayerMP
                && event.player.ticksExisted % CHECK_TICKS == 0) update(event.player);
    }

    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.player instanceof EntityPlayerMP) update(event.player);
    }

    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP) update(event.player);
    }

    /** Põe ou tira os modificadores conforme o estado atual do jogador. */
    void update(EntityPlayer player) {
        boolean earned = completed(player) && am2.fused(player);
        set(player, am2.attribute("maxManaBonus"), MANA, earned);
        set(player, am2.attribute("maxBurnoutBonus"), BURNOUT, earned);
        set(player, am2.attribute("manaRegenTimeModifier"), REGENERATION, earned);
        set(player, am2.attribute("burnoutReductionRate"), RECOVERY, earned);
        set(player, am2.attribute("manaRegenTimeModifier"), WORLD, earned && onNaturalGround(player));
    }

    private static void set(EntityPlayer player, IAttribute attribute, AttributeModifier modifier, boolean wanted) {
        IAttributeInstance instance = player.getAttributeMap().getAttributeInstance(attribute);
        if (instance == null) return;
        AttributeModifier current = instance.getModifier(modifier.getID());
        if (wanted && current == null) instance.applyModifier(modifier);
        else if (!wanted && current != null) instance.removeModifier(current);
    }

    /** Sobre grama ou terra (um pulo não interrompe) e vendo o céu. */
    private static boolean onNaturalGround(EntityPlayer player) {
        World world = player.worldObj;
        int x = MathHelper.floor_double(player.posX), z = MathHelper.floor_double(player.posZ);
        int y = MathHelper.floor_double(player.boundingBox.minY - 0.05);
        Block below = world.getBlock(x, y, z);
        if (below == Blocks.air) below = world.getBlock(x, y - 1, z);
        return (below == Blocks.grass || below == Blocks.dirt)
                && world.canBlockSeeTheSky(x, MathHelper.floor_double(player.boundingBox.minY) + 1, z);
    }
}
