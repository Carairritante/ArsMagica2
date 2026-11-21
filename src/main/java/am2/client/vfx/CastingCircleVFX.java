package am2.client.vfx;

import am2.AMCore;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.enums.Affinity;
import am2.items.ItemSpellBook;
import am2.items.ItemSpellStaff;
import am2.particles.AMParticle;
import am2.spell.SkillManager;
import am2.spell.SpellUtils;
import am2.spell.shapes.Beam;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;

@SideOnly(Side.CLIENT)
public class CastingCircleVFX {

	// Super Tier Archetypes
	public enum SuperTierType {
		LUNAR_LIGHTNING("lightning_lunar", new float[]{0.30f, 0.25f, 0.85f}, new float[]{0.85f, 0.86f, 1.0f}),
		BLIZZARD("blizzard", new float[]{0.0f, 0.83f, 1.0f}, new float[]{0.92f, 0.98f, 1.0f}),
		FIRESTORM("firestorm", new float[]{1.0f, 0.25f, 0.04f}, new float[]{1.0f, 0.84f, 0.12f}),
		STARFALL("starfall", new float[]{0.70f, 0.24f, 1.0f}, new float[]{1.0f, 0.88f, 0.31f}),
		STORM_LIGHTNING("storm_lightning", new float[]{0.0f, 0.88f, 1.0f}, new float[]{1.0f, 0.96f, 0.24f});

		public final String textureSuffix;
		public final float[] primaryColor;
		public final float[] secondaryColor;

		SuperTierType(String suffix, float[] primary, float[] secondary) {
			this.textureSuffix = suffix;
			this.primaryColor = primary;
			this.secondaryColor = secondary;
		}
	}

    private static final int MAX_VISIBLE_CASTERS = 8;
    private static final double MAX_DISTANCE_SQ = 96.0 * 96.0;
    private static final Map<EntityPlayer, VisualState> visualStates = new WeakHashMap<EntityPlayer, VisualState>();

    private static final Map<EntityPlayer, BodyPosition> bodyPositions = new WeakHashMap<EntityPlayer, BodyPosition>();

    private static final class BodyPosition {
        double x, y, z;

        void capture(EntityPlayer player, float partialTicks) {
            x = player.prevPosX + (player.posX - player.prevPosX) * partialTicks;
            y = player.prevPosY + (player.posY - player.prevPosY) * partialTicks;
            z = player.prevPosZ + (player.posZ - player.prevPosZ) * partialTicks;
        }
    }

    /** Sample before world rendering: camera mods may temporarily move entity coordinates. */
    public static void captureBodyPositions(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null) {
            bodyPositions.clear();
            return;
        }
        for (Object entity : mc.theWorld.playerEntities) {
            if (entity instanceof EntityPlayer) captureBodyPosition((EntityPlayer)entity, partialTicks);
        }
        if (mc.thePlayer != null) captureBodyPosition(mc.thePlayer, partialTicks);
    }

    private static void captureBodyPosition(EntityPlayer player, float partialTicks) {
        BodyPosition position = bodyPositions.get(player);
        if (position == null) {
            position = new BodyPosition();
            bodyPositions.put(player, position);
        }
        position.capture(player, partialTicks);
    }

    private static BodyPosition bodyPosition(EntityPlayer player) {
        BodyPosition position = bodyPositions.get(player);
        if (position == null) {
            captureBodyPosition(player, CastingAnimationState.getPartialTicks());
            position = bodyPositions.get(player);
        }
        return position;
    }

    private static final class VisualState {
        ItemStack held;
        int analyzedTick = Integer.MIN_VALUE, lastSeenTick = Integer.MIN_VALUE;
        int startedTick, lastParticleTick = Integer.MIN_VALUE, lastUseDuration;
        ItemStack spell;
        int lunar, solar;
        Affinity affinity = Affinity.NONE;
        SuperTierType tier;
        boolean isTornado;
        byte tornadoElem;
    }

    public static void renderAll(float partialTicks) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.theWorld == null || mc.renderViewEntity == null) return;
        Entity view = mc.renderViewEntity;
        double cx = view.prevPosX + (view.posX - view.prevPosX) * partialTicks;
        double cy = view.prevPosY + (view.posY - view.prevPosY) * partialTicks;
        double cz = view.prevPosZ + (view.posZ - view.prevPosZ) * partialTicks;
        Frustrum camera = new Frustrum();
        camera.setPosition(cx, cy, cz);
        int rendered = 0;
        // Keep the local caster visible even in a crowd. Other effects have a
        // finite distance, visibility test and per-frame draw budget.
        if (mc.thePlayer != null && renderCaster(mc.thePlayer, camera, cx, cy, cz, partialTicks)) rendered++;
        for (Object obj : mc.theWorld.playerEntities) {
            if (rendered >= MAX_VISIBLE_CASTERS) break;
            if (!(obj instanceof EntityPlayer) || obj == mc.thePlayer) continue;
            if (renderCaster((EntityPlayer)obj, camera, cx, cy, cz, partialTicks)) rendered++;
        }
    }

    private static boolean renderCaster(EntityPlayer player, Frustrum camera,
            double cx, double cy, double cz, float partialTicks) {
        if (player.isDead || player.isInvisible()) return false;
        ItemStack held = player.getHeldItem();
        if (!CastingAnimationState.isCasting(player, held)) return false;
        BodyPosition body = bodyPosition(player);
        double px = body.x, py = body.y, pz = body.z;
        double dx = px - cx, dy = py - cy, dz = pz - cz;
        if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) return false;
        // Bounds include the approved 32m outer layer and 12m upper halo.
        double feet = py - player.yOffset;
        if (player != Minecraft.getMinecraft().thePlayer && !camera.isBoundingBoxInFrustum(
                AxisAlignedBB.getBoundingBox(px - 19, feet - 1, pz - 19, px + 19, feet + 13, pz + 19))) return false;
        VisualState state = visualStateFor(player, held);
        int now = player.ticksExisted;
        if (state.spell == null) return false;
        int elapsed = Math.max(player.getItemInUseDuration(), now - state.startedTick);
        MahouVerticalCastingVFX.render(px - cx, feet - cy, pz - cz, state.affinity,
                state.tier, elapsed + partialTicks, (float)(dx * dx + dy * dy + dz * dz));
        spawnEdgeParticle(player, state, elapsed + partialTicks);
        return true;
    }

    private static VisualState visualStateFor(EntityPlayer player, ItemStack held) {
        VisualState state = visualStates.get(player);
        if (state == null) { state = new VisualState(); visualStates.put(player, state); }
        int now = player.ticksExisted;
        int useDuration = player.getItemInUseDuration();
        boolean changed = state.held != held || now < state.lastSeenTick || (long)now - state.lastSeenTick > 2
                || useDuration < state.lastUseDuration;
        state.lastUseDuration = useDuration;
        if (changed) { state.startedTick = now; state.analyzedTick = Integer.MIN_VALUE; }
        state.lastSeenTick = now;
        if (changed || (long)now - state.analyzedTick >= 4) {
            state.held = held; state.analyzedTick = now;
            state.spell = null; state.tier = null; state.lunar = state.solar = 0;
            state.isTornado = false; state.tornadoElem = 0;
            try {
                ItemStack spell = null;
                if (held.getItem() instanceof ItemSpellBook) spell = ((ItemSpellBook)held.getItem()).GetActiveItemStack(held);
                else if (held.getItem() instanceof ItemSpellStaff) {
                    if (held.hasTagCompound() && held.stackTagCompound.hasKey("spell_to_cast", 10))
                        spell = ItemStack.loadItemStackFromNBT(held.stackTagCompound.getCompoundTag("spell_to_cast"));
                } else if (held.getItem() instanceof ItemSpellBase) spell = held;
                if (safeDefinition(spell)) {
                    state.spell = spell;
                    state.lunar = getLunarCount(spell); state.solar = getSolarCount(spell);
                    state.affinity = safeAffinity(spell);
                    state.isTornado = hasAnyComponent(spell, am2.spell.components.Tornado.COMPONENT_ID);
                    if (state.isTornado) {
                        state.tornadoElem = am2.spell.components.Tornado.detectElementalSynergy(spell);
                    }
                    // Keep Beam spells on the two normal vertical layers to prevent huge Super Tier rings from cluttering 1st/3rd person view.
                    // Only trigger Super Tiers for the specific Silver / apocalypse spells (never on arbitrary affinity + 3 Lunar/Solar spells).
                    if (!state.isTornado && !Beam.isBeamSpell(spell))
                        state.tier = determineSuperTierType(spell, state.lunar, state.solar);
                }
            } catch (RuntimeException invalidSpell) {
                // A malformed visual description must never take down rendering.
                state.spell = null;
            }
        }
        return state;
    }

    /** Collect independently of visibility/FPS, on ticks before camera overrides. */
    public static void collectAtmosphere(Minecraft mc) {
        int reported = 0;
        if (mc.thePlayer != null && reportAtmosphere(mc.thePlayer, mc)) reported++;
        for (Object entity : mc.theWorld.playerEntities) {
            if (reported >= MAX_VISIBLE_CASTERS) break;
            if (entity instanceof EntityPlayer && entity != mc.thePlayer && reportAtmosphere((EntityPlayer)entity, mc)) reported++;
        }
    }

    private static boolean reportAtmosphere(EntityPlayer player, Minecraft mc) {
        if (player.isDead || player.isInvisible() || player.getDistanceSqToEntity(mc.thePlayer) > 64 * 64) return false;
        ItemStack held = player.getHeldItem();
        if (!CastingAnimationState.isCasting(player, held)) return false;
        VisualState state = visualStateFor(player, held);
        if (state.spell == null || state.tier == null) return false;
        int elapsed = Math.max(player.getItemInUseDuration(), player.ticksExisted - state.startedTick);
        CastingAtmosphereVFX.reportCasting(player, state.tier, elapsed);
        return true;
    }

    private static boolean safeDefinition(ItemStack spell) {
        if (spell == null || !spell.hasTagCompound()) return false;
        int stages = SpellUtils.instance.numStages(spell), groups = SpellUtils.instance.numShapeGroups(spell);
        if (stages < 0 || stages > 64 || groups < 0 || groups > 32) return false;
        for (int i = 0; i < stages; i++) {
            if (spell.stackTagCompound.getIntArray("SpellModifierIDs_" + i).length > 256
                    || spell.stackTagCompound.getIntArray("SpellComponentIDs_" + i).length > 256) return false;
        }
        for (int i = 0; i < groups; i++) if (SpellUtils.instance.getShapeGroupParts(spell, i).length > 256) return false;
        return true;
    }

    public static Affinity safeAffinity(ItemStack spell) {
        if (!safeDefinition(spell)) return Affinity.NONE;
        try { return SpellUtils.instance.mainAffinityFor(spell); }
        catch (RuntimeException invalidAffinity) { return Affinity.NONE; }
    }

    private static boolean allowParticles(EntityPlayer player, int interval) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.gameSettings.particleSetting >= 2 || mc.renderViewEntity == null) return false;
        BodyPosition body = bodyPosition(player);
        Entity view = mc.renderViewEntity;
        float partial = CastingAnimationState.getPartialTicks();
        double dx = body.x - (view.prevPosX + (view.posX - view.prevPosX) * partial);
        double dy = body.y - (view.prevPosY + (view.posY - view.prevPosY) * partial);
        double dz = body.z - (view.prevPosZ + (view.posZ - view.prevPosZ) * partial);
        if (dx * dx + dy * dy + dz * dz > 32 * 32) return false;
        VisualState state = visualStates.get(player);
        if (state == null) return false;
        int tick = player.ticksExisted;
        if (mc.gameSettings.particleSetting == 1) interval *= 2;
        if (tick % interval != 0 || state.lastParticleTick == tick) return false;
        state.lastParticleTick = tick;
        return true;
    }

	// =========================================================================
	// Modifier Inspector (3 Lunar or 3 Solar - Exhaustive Multi-Layer Detection)
	// =========================================================================
	public static int getLunarCount(ItemStack stack) {
		if (!safeDefinition(stack)) return 0;
		int count = 0;

		// 1. Check constructed classic stack
		try {
			ItemStack classic = SpellUtils.instance.constructSpellStack(stack);
			if (classic != null && classic.hasTagCompound()) {
				int numStages = SpellUtils.instance.numStages(classic);
				int classicCount = 0;
				for (int s = 0; s < numStages; s++) {
					ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(classic, s);
					if (mods != null) {
						for (ISpellModifier m : mods) {
							if (m instanceof am2.spell.modifiers.Lunar || (m != null && (m.getID() == 10 || m.getID() == 5010))) {
								classicCount++;
							}
						}
					}
				}
				count = Math.max(count, classicCount);
			}
		} catch (RuntimeException ignored) {}

		// 2. Check shape groups
		try {
			int numGroups = SpellUtils.instance.numShapeGroups(stack);
			int shapeGroupCount = 0;
			for (int g = 0; g < numGroups; g++) {
				int[] parts = SpellUtils.instance.getShapeGroupParts(stack, g);
				if (parts != null) {
					for (int id : parts) {
						if (id == 10 || id == 5010) {
							shapeGroupCount++;
						} else {
							ISkillTreeEntry entry = SkillManager.instance.getSkill(id);
							if (entry instanceof am2.spell.modifiers.Lunar || (entry != null && entry.getID() == 10)) {
								shapeGroupCount++;
							}
						}
					}
				}
			}
			count = Math.max(count, shapeGroupCount);
		} catch (RuntimeException ignored) {}

		// 3. Check base stack stages
		try {
			int numStages = SpellUtils.instance.numStages(stack);
			int baseStageCount = 0;
			for (int s = 0; s < numStages; s++) {
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(stack, s);
				if (mods != null) {
					for (ISpellModifier m : mods) {
						if (m instanceof am2.spell.modifiers.Lunar || (m != null && (m.getID() == 10 || m.getID() == 5010))) {
							baseStageCount++;
						}
					}
				}
			}
			count = Math.max(count, baseStageCount);
		} catch (RuntimeException ignored) {}

		// 4. Raw NBT integer array fallback
		try {
			NBTTagCompound tag = stack.stackTagCompound;
			int rawCount = 0;
			for (int s = 0; s < 12; s++) {
				int[] modIds = tag.getIntArray("SpellModifierIDs_" + s);
				if (modIds != null) {
					for (int id : modIds) {
						if (id == 10 || id == 5010) rawCount++;
					}
				}
			}
			count = Math.max(count, rawCount);
		} catch (RuntimeException ignored) {}

		return count;
	}

	public static int getSolarCount(ItemStack stack) {
		if (!safeDefinition(stack)) return 0;
		int count = 0;

		// 1. Check constructed classic stack
		try {
			ItemStack classic = SpellUtils.instance.constructSpellStack(stack);
			if (classic != null && classic.hasTagCompound()) {
				int numStages = SpellUtils.instance.numStages(classic);
				int classicCount = 0;
				for (int s = 0; s < numStages; s++) {
					ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(classic, s);
					if (mods != null) {
						for (ISpellModifier m : mods) {
							if (m instanceof am2.spell.modifiers.Solar || (m != null && (m.getID() == 12 || m.getID() == 5012))) {
								classicCount++;
							}
						}
					}
				}
				count = Math.max(count, classicCount);
			}
		} catch (RuntimeException ignored) {}

		// 2. Check shape groups
		try {
			int numGroups = SpellUtils.instance.numShapeGroups(stack);
			int shapeGroupCount = 0;
			for (int g = 0; g < numGroups; g++) {
				int[] parts = SpellUtils.instance.getShapeGroupParts(stack, g);
				if (parts != null) {
					for (int id : parts) {
						if (id == 12 || id == 5012) {
							shapeGroupCount++;
						} else {
							ISkillTreeEntry entry = SkillManager.instance.getSkill(id);
							if (entry instanceof am2.spell.modifiers.Solar || (entry != null && entry.getID() == 12)) {
								shapeGroupCount++;
							}
						}
					}
				}
			}
			count = Math.max(count, shapeGroupCount);
		} catch (RuntimeException ignored) {}

		// 3. Check base stack stages
		try {
			int numStages = SpellUtils.instance.numStages(stack);
			int baseStageCount = 0;
			for (int s = 0; s < numStages; s++) {
				ISpellModifier[] mods = SpellUtils.instance.getModifiersForStage(stack, s);
				if (mods != null) {
					for (ISpellModifier m : mods) {
						if (m instanceof am2.spell.modifiers.Solar || (m != null && (m.getID() == 12 || m.getID() == 5012))) {
							baseStageCount++;
						}
					}
				}
			}
			count = Math.max(count, baseStageCount);
		} catch (RuntimeException ignored) {}

		// 4. Raw NBT integer array fallback
		try {
			NBTTagCompound tag = stack.stackTagCompound;
			int rawCount = 0;
			for (int s = 0; s < 12; s++) {
				int[] modIds = tag.getIntArray("SpellModifierIDs_" + s);
				if (modIds != null) {
					for (int id : modIds) {
						if (id == 12 || id == 5012) rawCount++;
					}
				}
			}
			count = Math.max(count, rawCount);
		} catch (RuntimeException ignored) {}

		return count;
	}

	public static boolean hasThreeSolarOrLunar(ItemStack stack) {
		return getLunarCount(stack) >= 3 || getSolarCount(stack) >= 3;
	}

    private static boolean hasAnyComponent(ItemStack stack, int... ids) {
        if(stack==null||!stack.hasTagCompound())return false;
        try{
            ItemStack classic=SpellUtils.instance.constructSpellStack(stack);
            ItemStack[] sources=classic!=null&&classic!=stack?new ItemStack[]{classic,stack}:new ItemStack[]{stack};
            for(ItemStack source:sources){
                int stages=Math.max(0,Math.min(64,SpellUtils.instance.numStages(source)));
                for(int stage=0;stage<stages;stage++){
                    ISpellComponent[] parts=SpellUtils.instance.getComponentsForStage(source,stage);
                    if(parts!=null)for(ISpellComponent part:parts)if(part!=null)
                        for(int target:ids)if(part.getID()==target)return true;
                }
            }
        }catch(RuntimeException malformed){}
        // Component arrays store raw legacy IDs or SkillManager's verified +1000 IDs.
        // Do not treat unshifted shape IDs in shape groups as components.
        for(int stage=0;stage<12;stage++)for(int stored:stack.stackTagCompound.getIntArray("SpellComponentIDs_"+stage))
            for(int target:ids)if(stored==target||stored==target+1000)return true;
        return false;
    }


	private static SuperTierType determineSuperTierType(ItemStack stack, int lunarCount, int solarCount) {
		// Strict Silver / apocalypse spell matching only (never trigger Super Tiers from generic affinity or 3x Lunar/Solar fallback):
		// Blizzard (63), FireRain (66), FallingStar (65), Storm (52) + LightningDamage (34),
		// and LightningStrike (100) + 3x Lunar (Fallen Down).
		if (hasAnyComponent(stack, 63)) return SuperTierType.BLIZZARD;
		if (hasAnyComponent(stack, 66)) return SuperTierType.FIRESTORM;
		if (hasAnyComponent(stack, 65)) return SuperTierType.STARFALL;
		if (hasAnyComponent(stack, 52) && hasAnyComponent(stack, 34)) return SuperTierType.STORM_LIGHTNING;
		if (hasAnyComponent(stack, 100) && lunarCount >= 3) return SuperTierType.LUNAR_LIGHTNING;
		return null;
	}

    private static void spawnEdgeParticle(EntityPlayer player, VisualState state, float elapsed) {
        if (!allowParticles(player, 4)) return;
        int phase = elapsed >= 76 ? (state.tier == null ? 2 : 3) : elapsed >= 32 ? 2 : 1;
        double radius = phase == 1 ? 4.5 : phase == 2 ? 9.25 : 16;
        double height = phase == 1 ? 1.06 : phase == 2 ? 3.375 : 6.625;
        double angle = player.worldObj.rand.nextDouble() * Math.PI * 2;
        BodyPosition body = bodyPosition(player);
        AMParticle particle = (AMParticle)AMCore.proxy.particleManager.spawn(player.worldObj, "symbols",
                body.x + Math.sin(angle) * radius, body.y - player.yOffset + height,
                body.z + Math.cos(angle) * radius);
        if (particle != null) {
            particle.setDontRequireControllers();
            particle.setMaxAge(14);
            particle.setParticleScale(.075f);
            float[] color = state.tier == null ? palette(state.affinity) : state.tier.secondaryColor;
            particle.setRGBColorF(color[0], color[1], color[2]);
            particle.addVelocity(0, .025, 0);
        }
    }

    private static float[] palette(Affinity affinity) {
        switch (affinity) {
            case FIRE: return new float[]{1f,.22f,.06f};
            case EARTH: return new float[]{.60f,.35f,.15f};
            case AIR: return new float[]{.53f,1f,.80f};
            case WATER: return new float[]{.12f,.45f,1f};
            case ICE: return new float[]{.13f,.55f,1f};
            case LIGHTNING: return new float[]{1f,.94f,.15f};
            case LIFE: return new float[]{1f,.80f,.76f};
            case NATURE: return new float[]{.26f,.90f,.30f};
            case ENDER: return new float[]{.50f,.25f,.82f};
            case ARCANE: return new float[]{1f,.12f,.88f};
            default: return new float[]{.86f,.90f,1f};
        }
    }

	public static void clearAll() {
		visualStates.clear();
		bodyPositions.clear();
		CastingAnimationState.clear();
	}
}
