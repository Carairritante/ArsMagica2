package am2.shield;

import am2.buffs.BuffEffectManaShield;
import am2.buffs.BuffList;
import am2.playerextensions.ExtendedProperties;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.AbstractCollection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetHandlerPlayServer;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.DamageSource;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/** Authoritative ward. The potion is a display mirror, never the source of truth. */
public final class ManaShieldWard {
    private static final String KEY = "AM2ManaShieldWard";
    private static final Map<EntityLivingBase, State> STATES = Collections.synchronizedMap(new WeakHashMap<EntityLivingBase, State>());
    private static final ThreadLocal<ArrayDeque<Entity>> ACTORS = new ThreadLocal<ArrayDeque<Entity>>() {
        protected ArrayDeque<Entity> initialValue() { return new ArrayDeque<Entity>(); }
    };
    private static final ThreadLocal<ArrayDeque<DamageFrame>> DAMAGE = new ThreadLocal<ArrayDeque<DamageFrame>>() {
        protected ArrayDeque<DamageFrame> initialValue() { return new ArrayDeque<DamageFrame>(); }
    };
    private static final ThreadLocal<Integer> INTERNAL = new ThreadLocal<Integer>() {
        protected Integer initialValue() { return 0; }
    };
    private static final ThreadLocal<ArrayDeque<EntityLivingBase>> HEALTH_COSTS = new ThreadLocal<ArrayDeque<EntityLivingBase>>() {
        protected ArrayDeque<EntityLivingBase> initialValue() { return new ArrayDeque<EntityLivingBase>(); }
    };
    private static Field badEffectField;

    private static final class State {
        int lastTick = Integer.MIN_VALUE;
        long numericDeathTick = Long.MIN_VALUE;
        boolean leaving;
        final Map<String, Long> resisted = new HashMap<String, Long>();
    }
    private static final class DamageFrame {
        final EntityLivingBase target;
        final DamageSource source;
        DamageFrame(EntityLivingBase target, DamageSource source) { this.target = target; this.source = source; }
    }
    public ManaShieldWard() {}

    private static NBTTagCompound data(EntityLivingBase target) {
        return target.getEntityData().getCompoundTag(KEY);
    }
    private static State state(EntityLivingBase target) {
        synchronized (STATES) {
            State state = STATES.get(target);
            if (state == null) { state = new State(); STATES.put(target, state); }
            return state;
        }
    }
    private static boolean server(EntityLivingBase target) {
        return target != null && target.worldObj != null && !target.worldObj.isRemote;
    }
    public static boolean active(EntityLivingBase target) {
        if (!server(target) || INTERNAL.get() > 0 || HEALTH_COSTS.get().contains(target)
                || !target.getEntityData().hasKey(KEY, 10)) return false;
        return data(target).getInteger("remaining") > 0 && !state(target).leaving;
    }
    public static void activate(EntityLivingBase target, int duration, int amplifier) {
        if (!server(target) || duration <= 0) return;
        NBTTagCompound tag = data(target);
        int previous = tag.getInteger("remaining") > 0 ? tag.getInteger("amplifier") : 0;
        tag.setInteger("remaining", Math.max(duration, tag.getInteger("remaining")));
        tag.setInteger("amplifier", Math.max(Math.max(0, Math.min(4, amplifier)), previous));
        target.getEntityData().setTag(KEY, tag);
        state(target).leaving = false;
        mirror(target);
        cleanse(target);
    }
    private static void end(EntityLivingBase target) {
        // Keep a tombstone: an old visible potion must not resurrect a dispelled ward.
        NBTTagCompound tag = data(target); tag.setInteger("remaining", 0);
        target.getEntityData().setTag(KEY, tag);
        state(target).resisted.clear();
    }
    private static void mirror(EntityLivingBase target) {
        NBTTagCompound tag = data(target);
        int duration = tag.getInteger("remaining"), amp = tag.getInteger("amplifier");
        PotionEffect existing = target.getActivePotionEffect(BuffList.manaShield);
        if (duration <= 0 && existing == null) return;
        if (duration > 0 && existing != null && existing.getAmplifier() == amp
                && Math.abs(existing.getDuration() - duration) <= 2) return;
        INTERNAL.set(INTERNAL.get() + 1);
        try {
            target.removePotionEffect(BuffList.manaShield.id);
            if (duration > 0) target.addPotionEffect(new BuffEffectManaShield(duration, amp + 1));
        } finally { INTERNAL.set(INTERNAL.get() - 1); }
    }
    @SubscribeEvent public void update(LivingEvent.LivingUpdateEvent event) {
        EntityLivingBase target = event.entityLiving;
        if (!server(target)) return;
        if (!target.getEntityData().hasKey(KEY, 10)) {
            PotionEffect old = target.getActivePotionEffect(BuffList.manaShield);
            if (old != null) activate(target, old.getDuration(), old.getAmplifier());
        }
        if (!target.getEntityData().hasKey(KEY, 10)) return;
        State state = state(target);
        if (state.lastTick == target.ticksExisted || state.leaving) return;
        state.lastTick = target.ticksExisted;
        NBTTagCompound tag = data(target);
        tag.setInteger("remaining", Math.max(0, tag.getInteger("remaining") - 1));
        if (target.isDead) end(target);
        // Some mods remove potion entries through a raw map rather than the public API.
        if (tag.getInteger("remaining") > 0 && target.getActivePotionEffect(BuffList.manaShield) == null
                && !resist(target, ManaShieldRules.Action.DISPEL)) end(target);
        mirror(target);
    }
    @SubscribeEvent public void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        State state = STATES.get(event.player);
        if (state != null) state.leaving = true;
    }
    @SubscribeEvent public void login(PlayerEvent.PlayerLoggedInEvent event) {
        State state = STATES.get(event.player);
        if (state != null) state.leaving = false;
    }
    @SubscribeEvent(priority = EventPriority.LOWEST) public void died(LivingDeathEvent event) {
        if (!event.isCanceled() && server(event.entityLiving)
                && event.entityLiving.getEntityData().hasKey(KEY, 10)) end(event.entityLiving);
    }

    public static void enterActor(Object actor) {
        if (actor instanceof Entity) ACTORS.get().push((Entity)actor);
    }
    public static void exitActor(Object actor) {
        ArrayDeque<Entity> stack = ACTORS.get();
        if (!stack.isEmpty() && stack.peek() == actor) stack.pop();
    }
    public static void enterPacket(Object handler) {
        if (handler instanceof NetHandlerPlayServer) enterActor(((NetHandlerPlayServer)handler).playerEntity);
    }
    public static void exitPacket(Object handler) {
        if (handler instanceof NetHandlerPlayServer) exitActor(((NetHandlerPlayServer)handler).playerEntity);
    }
    public static void enterDamage(Object target, Object source) {
        if (target instanceof EntityLivingBase && source instanceof DamageSource)
            DAMAGE.get().push(new DamageFrame((EntityLivingBase)target, (DamageSource)source));
    }
    public static void exitDamage(Object target, Object source) {
        ArrayDeque<DamageFrame> stack = DAMAGE.get();
        if (!stack.isEmpty() && stack.peek().target == target && stack.peek().source == source) stack.pop();
    }
    private static DamageFrame damageFor(EntityLivingBase target) {
        for (DamageFrame frame : DAMAGE.get()) if (frame.target == target) return frame;
        return null;
    }
    private static boolean voluntary(EntityLivingBase target) {
        return origin(ACTORS.get().peek()) == target;
    }
    private static Entity origin(Entity actor) {
        if (actor instanceof EntityThrowable) {
            EntityLivingBase owner = ((EntityThrowable)actor).getThrower();
            if (owner != null) return owner;
        }
        if (actor instanceof EntityArrow && ((EntityArrow)actor).shootingEntity != null)
            return ((EntityArrow)actor).shootingEntity;
        return actor;
    }
    private static String actorKey(EntityLivingBase target, ManaShieldRules.Action kind) {
        Entity actor = ACTORS.get().peek();
        return kind.name() + ":" + (actor == null ? -1 : actor.getEntityId());
    }
    public static boolean resist(EntityLivingBase target, ManaShieldRules.Action kind) {
        if (!active(target)) return false;
        ExtendedProperties properties = ExtendedProperties.For(target);
        if (properties == null) return false;
        float mana = properties.getCurrentMana();
        State state = state(target);
        long now = target.worldObj.getTotalWorldTime();
        String key = actorKey(target, kind);
        Long last = state.resisted.get(key);
        // Coalesce repeated writes in a single hostile operation/continuous effect.
        if (last != null && now == last) return true;
        if (Float.isNaN(mana) || Float.isInfinite(mana) || mana <= 0) return false;
        double price = ManaShieldRules.actionCost(properties.getMaxMana(), kind);
        if (mana < price) return false;
        properties.deductMana((float)price); properties.forceSync();
        if (state.resisted.size() >= 64) state.resisted.clear();
        state.resisted.put(key, now);
        return true;
    }
    public static float filterHealth(Object object, float requested) {
        if (!(object instanceof EntityLivingBase)) return requested;
        EntityLivingBase target = (EntityLivingBase)object;
        if (!active(target) || Float.isNaN(requested) || requested >= target.getHealth()) return requested;
        DamageFrame frame = damageFor(target);
        if (frame == null && requested <= 0) {
            return resist(target, ManaShieldRules.Action.DIRECT_DEATH) ? target.getHealth() : requested;
        }
        ExtendedProperties properties = ExtendedProperties.For(target);
        if (properties == null) return requested;
        float loss = target.getHealth() - requested;
        ManaShieldRules.Absorption result = ManaShieldRules.absorb(loss, properties.getCurrentMana(),
                data(target).getInteger("amplifier"));
        float health = Math.min(target.getHealth(), requested + result.absorbed);
        if (frame != null && health <= 0) state(target).numericDeathTick = target.worldObj.getTotalWorldTime();
        if (result.manaSpent <= 0) return requested;
        properties.deductMana(result.manaSpent); properties.forceSync();
        return health;
    }
    public static boolean preventDeath(Object object, Object source) {
        if (!(object instanceof EntityLivingBase)) return false;
        EntityLivingBase target = (EntityLivingBase)object;
        if (!active(target) || damageFor(target) != null) return false;
        // An unpaid numeric lethal hit cannot purchase a cheaper second chance,
        // including a cached direct-death defense from earlier in this same tick.
        if (target.getHealth() <= 0 && state(target).numericDeathTick == target.worldObj.getTotalWorldTime()) return false;
        if (!resist(target, ManaShieldRules.Action.DIRECT_DEATH)) return false;
        if (target.getHealth() <= 0) {
            INTERNAL.set(INTERNAL.get() + 1);
            try { target.setHealth(1); } finally { INTERNAL.set(INTERNAL.get() - 1); }
        }
        target.deathTime = 0;
        return true;
    }
    public static boolean preventSetDead(Object object) {
        if (!(object instanceof EntityLivingBase)) return false;
        EntityLivingBase target = (EntityLivingBase)object;
        Entity actor = origin(ACTORS.get().peek());
        // Do not turn logout, unloading or ordinary lifecycle cleanup into combat.
        return actor != null && actor != target && preventDeath(target, null);
    }
    public static boolean preventPotion(Object object, Object effect) {
        if (!(object instanceof EntityLivingBase) || !(effect instanceof PotionEffect)) return false;
        EntityLivingBase target = (EntityLivingBase)object;
        PotionEffect potion = (PotionEffect)effect;
        if (server(target) && INTERNAL.get() == 0 && BuffList.manaShield != null
                && potion.getPotionID() == BuffList.manaShield.id) {
            // An explicit NEW potion application from an addon is a fresh cast;
            // a previously dispelled ward's tombstone must not block it forever.
            activate(target, potion.getDuration(), potion.getAmplifier()); return true;
        }
        if (!active(target)) return false;
        return harmful(potion.getPotionID()) && resist(target, ManaShieldRules.Action.STATUS);
    }
    private static boolean harmful(int id) {
        if (id < 0 || id >= Potion.potionTypes.length || Potion.potionTypes[id] == null) return false;
        boolean harmful;
        try {
            if (badEffectField == null) {
                badEffectField = cpw.mods.fml.relauncher.ReflectionHelper.findField(Potion.class, "isBadEffect", "field_76418_K");
            }
            harmful = badEffectField.getBoolean(Potion.potionTypes[id]);
        } catch (RuntimeException invalid) { return false;
        } catch (IllegalAccessException invalid) { return false; }
        return harmful;
    }
    private static void cleanse(EntityLivingBase target) {
        for (Object raw : new ArrayList(target.getActivePotionEffects())) {
            PotionEffect effect = (PotionEffect)raw;
            if (!harmful(effect.getPotionID())) continue;
            if (!resist(target, ManaShieldRules.Action.STATUS)) return;
            if (effect instanceof am2.buffs.BuffEffect) ((am2.buffs.BuffEffect)effect).stopEffect(target);
            target.removePotionEffect(effect.getPotionID());
        }
    }
    public static boolean preventClear(Object object) {
        if (!(object instanceof EntityLivingBase)) return false;
        EntityLivingBase target = (EntityLivingBase)object;
        if (!active(target)) return false;
        if (voluntary(target)) { end(target); return false; }
        if (resist(target, ManaShieldRules.Action.DISPEL)) return true;
        end(target); return false;
    }
    public static boolean preventRemove(Object object, int potionId) {
        if (!(object instanceof EntityLivingBase)) return false;
        EntityLivingBase target = (EntityLivingBase)object;
        if (!active(target) || !target.isPotionActive(potionId)) return false;
        // Removing a harmful effect helps its recipient; it is not an attack.
        if (harmful(potionId)) return false;
        if (voluntary(target)) {
            if (potionId == BuffList.manaShield.id) end(target);
            return false;
        }
        if (resist(target, ManaShieldRules.Action.DISPEL)) return true;
        end(target); return false;
    }
    /** An explicit health-for-resource trade must not turn into a free mana loop. */
    public static boolean healthCostAttack(EntityLivingBase caster, Entity target, DamageSource source, float amount) {
        if (caster != target) return target.attackEntityFrom(source, amount);
        HEALTH_COSTS.get().push(caster);
        try { return target.attackEntityFrom(source, amount); }
        finally { HEALTH_COSTS.get().pop(); }
    }
    public static boolean hostileAction(EntityLivingBase caster, Entity target, ManaShieldRules.Action kind) {
        return target instanceof EntityLivingBase && caster != target && resist((EntityLivingBase)target, kind);
    }

    public static boolean preventControlFrom(Object object, Object attacker) {
        return object instanceof EntityLivingBase && attacker instanceof Entity && object != origin((Entity)attacker)
            && resist((EntityLivingBase)object, ManaShieldRules.Action.CONTROL);
    }
    public static boolean preventControl(Object object) {
        return preventControlFrom(object, ACTORS.get().peek());
    }
    public static boolean preventDrop(Object object) {
        return object instanceof EntityLivingBase && ACTORS.get().peek() != null
            && ACTORS.get().peek() != object && resist((EntityLivingBase)object, ManaShieldRules.Action.THEFT);
    }
    public static boolean preventTheft(Object object) {
        return object instanceof EntityLivingBase && !voluntary((EntityLivingBase)object)
            && resist((EntityLivingBase)object, ManaShieldRules.Action.THEFT);
    }
    public static Object filterHeldItem(Object target, Object held) {
        return held != null && preventTheft(target) ? null : held;
    }
    public static boolean preventFire(Object object, int seconds) {
        return seconds > 0 && object instanceof EntityLivingBase && !voluntary((EntityLivingBase)object)
            && resist((EntityLivingBase)object, ManaShieldRules.Action.STATUS);
    }
    /** Preserve the view's read semantics while intercepting hostile iterator/collection removal. */
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static Collection guardEffects(final Collection effects, Object object) {
        if (!(object instanceof EntityLivingBase) || !active((EntityLivingBase)object)) return effects;
        final EntityLivingBase target = (EntityLivingBase)object;
        return new AbstractCollection() {
            public int size() { return effects.size(); }
            public boolean contains(Object value) { return effects.contains(value); }
            public void clear() { if (!preventClear(target)) effects.clear(); }
            public boolean remove(Object value) {
                if (!effects.contains(value)) return false;
                return !(value instanceof PotionEffect && preventRemove(target, ((PotionEffect)value).getPotionID()))
                    && effects.remove(value);
            }
            public Iterator iterator() {
                final Iterator delegate = effects.iterator();
                return new Iterator() {
                    Object current; boolean removable;
                    public boolean hasNext() { return delegate.hasNext(); }
                    public Object next() { current = delegate.next(); removable = true; return current; }
                    public void remove() {
                        if (!removable) throw new IllegalStateException();
                        removable = false;
                        if (!(current instanceof PotionEffect) || !preventRemove(target, ((PotionEffect)current).getPotionID())) delegate.remove();
                    }
                };
            }
        };
    }
}
