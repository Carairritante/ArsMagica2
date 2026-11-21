package am2.spell;

import am2.LogHelper;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.buffs.BuffList;
import am2.shield.ManaShieldRules;
import am2.shield.ManaShieldWard;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTBase;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.Teleporter;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.event.world.WorldEvent;

/** Exact marked destinations, executed after entity/component iteration has ended. */
public final class RecallTravel {
    private static final int LIMIT = 256;
    private static final Map<EntityPlayerMP, Request> PENDING = new LinkedHashMap<EntityPlayerMP, Request>();
    private static final class Request {
        final EntityPlayerMP player;
        final ItemStack stack;
        boolean confirmed;
        final EntityLivingBase caster;
        final World source;
        final int dimension;
        final double x, y, z;
        Request(ItemStack stack, EntityPlayerMP player, EntityLivingBase caster, int dimension, double x, double y, double z) {
            this.stack = stack; this.player = player; this.caster = caster; this.source = player.worldObj;
            this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        }
    }
    public static boolean combination(ItemStack stack) {
        return combination(SpellUtils.instance.getComponentsForStage(stack, 0));
    }
    public static boolean combination(ISpellComponent[] components) {
        String[] names = new String[components.length];
        for (int i = 0; i < names.length; i++) names[i] = components[i] == null ? null : SkillManager.instance.getSkillName(components[i]);
        return RecallCombination.qualifies(names);
    }
    public static boolean qualifier(ISpellComponent component) {
        return RecallCombination.intervention(SkillManager.instance.getSkillName(component));
    }
    public static void message(EntityLivingBase caster, String key) {
        if (caster instanceof EntityPlayer && caster.worldObj != null && !caster.worldObj.isRemote)
            ((EntityPlayer)caster).addChatMessage(new ChatComponentTranslation(key));
    }
    public static boolean validCoordinates(double x, double y, double z) {
        return !Double.isNaN(x) && !Double.isInfinite(x) && Math.abs(x) <= 29999872
            && !Double.isNaN(z) && !Double.isInfinite(z) && Math.abs(z) <= 29999872
            && !Double.isNaN(y) && !Double.isInfinite(y) && y >= 0 && y < 4096;
    }
    public static boolean queue(ItemStack stack, EntityLivingBase target, EntityLivingBase caster, int dimension, double x, double y, double z) {
        if (!(target instanceof EntityPlayerMP) || target.worldObj.isRemote) {
            if (!target.worldObj.isRemote) message(caster, "am2.recall.playersOnly");
            return target.worldObj.isRemote;
        }
        if (!validCoordinates(x, y, z) || !DimensionManager.isDimensionRegistered(dimension)) {
            message(caster, "am2.recall.invalidDestination"); return false;
        }
        if (target.ridingEntity != null || target.riddenByEntity != null) {
            message(caster, "am2.recall.dismount"); return false;
        }
        if (ManaShieldWard.hostileAction(caster, target, ManaShieldRules.Action.CONTROL)) return false;
        EntityPlayerMP player = (EntityPlayerMP)target;
        if (player.isDead || player.getHealth() <= 0 || player.playerNetServerHandler == null) return false;
        synchronized (PENDING) {
            if (!PENDING.containsKey(player) && PENDING.size() >= LIMIT) return false;
            PENDING.put(player, new Request(stack, player, caster, dimension, x, y, z));
        }
        return true;
    }
    public static boolean suppressIntervention(Object stack) {
        return stack instanceof ItemStack && combination((ItemStack)stack);
    }
    /** Commit only after native stage validation succeeded, including optional addon checks. */
    public static void confirm(ItemStack stack, Entity target) {
        synchronized (PENDING) {
            Request request = PENDING.get(target);
            if (request != null && request.stack == stack) request.confirmed = true;
        }
    }
    @SubscribeEvent public void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        ArrayList<Request> requests;
        synchronized (PENDING) { requests = new ArrayList<Request>(PENDING.values()); PENDING.clear(); }
        for (Request request : requests) execute(request);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        synchronized (PENDING) { PENDING.remove(event.player); }
    }
    @SubscribeEvent public void unload(WorldEvent.Unload event) {
        if (event.world.isRemote) return;
        synchronized (PENDING) {
            java.util.Iterator<Request> it = PENDING.values().iterator();
            while (it.hasNext()) {
                Request request = it.next();
                if (request.source == event.world) it.remove();
            }
        }
    }
    private static void execute(Request r) {
        if (!r.confirmed) return;
        EntityPlayerMP p = r.player;
        if (p.isDead || p.getHealth() <= 0 || p.worldObj != r.source || p.playerNetServerHandler == null) return;
        if (p.isPotionActive(BuffList.astralDistortion) || r.caster.isPotionActive(BuffList.astralDistortion)) {
            message(r.caster, "am2.tooltip.cantTeleport"); return;
        }
        if (p.ridingEntity != null || p.riddenByEntity != null) { message(r.caster, "am2.recall.dismount"); return; }
        if (ManaShieldWard.hostileAction(r.caster, p, ManaShieldRules.Action.CONTROL)) return;
        if (!DimensionManager.isDimensionRegistered(r.dimension)) { message(r.caster, "am2.recall.invalidDestination"); return; }
        WorldServer source = (WorldServer)p.worldObj;
        double oldX = p.posX, oldY = p.posY, oldZ = p.posZ;
        int oldDimension = p.dimension;
        DreamState dream = null;
        boolean committed = false;
        am2.shield.ManaShieldWard.enterActor(p);
        try {
            WorldServer destination = DimensionManager.getWorld(r.dimension);
            if (destination == null) { DimensionManager.initDimension(r.dimension); destination = DimensionManager.getWorld(r.dimension); }
            if (destination == null || r.y >= destination.getHeight()) {
                message(r.caster, "am2.recall.invalidDestination"); return;
            }
            dream = DreamState.prepare(p, destination);
            if (dream != null) dream.apply();
            committed = transfer(p, destination, r.x, r.y, r.z);
            if (!committed) message(r.caster, "am2.recall.invalidDestination");
        } catch (Exception failure) {
            LogHelper.error("Recall dimension transfer failed: %s", failure.toString());
            message(r.caster, "am2.recall.invalidDestination");
        } finally {
            if (!committed) {
                if (dream != null) dream.restore();
                if (p.worldObj != source || p.dimension != oldDimension || !source.loadedEntityList.contains(p)) {
                    try {
                        if (!transfer(p, source, oldX, oldY, oldZ))
                            LogHelper.error("Recall could not restore player after rejected dimension transfer.");
                    } catch (Exception rollback) { LogHelper.error("Recall transfer rollback failed: %s", rollback.toString()); }
                }
            }
            am2.shield.ManaShieldWard.exitActor(p);
        }
    }
    private static boolean transfer(EntityPlayerMP p, WorldServer destination, double x, double y, double z) {
        int oldDimension = p.dimension;
        boolean alreadyHere = p.worldObj == destination && p.dimension == destination.provider.dimensionId;
        if (p.worldObj != destination || p.dimension != destination.provider.dimensionId)
            p.mcServer.getConfigurationManager().transferPlayerToDimension(p, destination.provider.dimensionId, new MarkTeleporter(destination, x, y, z));
        if (p.worldObj != destination || p.dimension != destination.provider.dimensionId) return false;
        // Vanilla skips portal placement AND world spawning on departures from the End.
        p.setLocationAndAngles(x, y, z, p.rotationYaw, p.rotationPitch);
        if (!destination.loadedEntityList.contains(p)) {
            if (oldDimension != 1 && !alreadyHere) return false;
            if (!destination.spawnEntityInWorld(p)) return false;
        }
        if (!destination.loadedEntityList.contains(p)) return false;
        p.motionX = p.motionY = p.motionZ = 0;
        p.fallDistance = 0;
        p.setPositionAndUpdate(x, y, z);
        return true;
    }
    /** Optional Witchery flags: snapshot ONLY the three booleans we change, never inventory. */
    private static final class DreamState {
        final NBTTagCompound nbt;
        final Class<?> dream;
        final boolean to;
        final NBTBase walking, nightmare, demonic;
        DreamState(NBTTagCompound nbt, Class<?> dream, boolean to) {
            this.nbt = nbt; this.dream = dream; this.to = to;
            walking = copy(nbt.getTag("WITCSpiritWalking"));
            NBTTagCompound spirit = nbt.getCompoundTag("WITCSpiritWorld");
            nightmare = copy(spirit.getTag("Nightmare")); demonic = copy(spirit.getTag("Demonic"));
        }
        static NBTBase copy(NBTBase tag) { return tag == null ? null : tag.copy(); }
        static DreamState prepare(EntityPlayerMP player, WorldServer destination) throws Exception {
            String provider = "com.emoniph.witchery.dimension.WorldProviderDreamWorld";
            boolean from = player.worldObj.provider.getClass().getName().equals(provider);
            boolean to = destination.provider.getClass().getName().equals(provider);
            if (!from && !to) return null;
            Class<?> infusion = Class.forName("com.emoniph.witchery.infusion.Infusion");
            NBTTagCompound nbt = (NBTTagCompound)infusion.getMethod("getNBT", Entity.class).invoke(null, player);
            return new DreamState(nbt, Class.forName(provider), to);
        }
        void apply() throws Exception {
            if (to) dream.getMethod("setPlayerHasNightmare", NBTTagCompound.class, boolean.class, boolean.class).invoke(null, nbt, false, false);
            dream.getMethod("setPlayerIsSpiritWalking", NBTTagCompound.class, boolean.class).invoke(null, nbt, to);
        }
        static void restoreTag(NBTTagCompound tag, String key, NBTBase value) {
            if (value == null) tag.removeTag(key); else tag.setTag(key, value);
        }
        void restore() {
            restoreTag(nbt, "WITCSpiritWalking", walking);
            NBTTagCompound spirit = nbt.getCompoundTag("WITCSpiritWorld");
            restoreTag(spirit, "Nightmare", nightmare); restoreTag(spirit, "Demonic", demonic);
        }
    }
    private static final class MarkTeleporter extends Teleporter {
        final double x, y, z;
        MarkTeleporter(WorldServer world, double x, double y, double z) { super(world); this.x = x; this.y = y; this.z = z; }
        @Override public void placeInPortal(Entity entity, double oldX, double oldY, double oldZ, float yaw) {
            entity.setLocationAndAngles(x, y, z, entity.rotationYaw, entity.rotationPitch);
        }
        @Override public boolean placeInExistingPortal(Entity entity, double oldX, double oldY, double oldZ, float yaw) { placeInPortal(entity, oldX, oldY, oldZ, yaw); return true; }
        @Override public boolean makePortal(Entity entity) { return false; }
        @Override public void removeStalePortalLocations(long time) {}
    }
}
