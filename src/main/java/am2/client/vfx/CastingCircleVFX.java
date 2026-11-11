package am2.client.vfx;

import am2.AMCore;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.api.spell.component.interfaces.ISpellModifier;
import am2.api.spell.component.interfaces.ISkillTreeEntry;
import am2.api.spell.enums.Affinity;
import am2.items.ItemSpellBook;
import am2.items.ItemSpellStaff;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.spell.SkillManager;
import am2.spell.SpellUtils;
import am2.spell.components.Blizzard;
import am2.spell.components.FallingStar;
import am2.spell.components.FireRain;
import am2.spell.components.LightningDamage;
import am2.spell.components.LightningStrike;
import am2.spell.components.Storm;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.HashMap;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.client.renderer.culling.Frustrum;
import net.minecraft.util.AxisAlignedBB;

@SideOnly(Side.CLIENT)
public class CastingCircleVFX {

	// Standard (Normal) Casting Circle Textures - Per Affinity
	private static final class AffinityTextures {
		final ResourceLocation ground;
		final ResourceLocation outer;
		final ResourceLocation matrix;
		final ResourceLocation core;
		final float[] primaryColor;
		final float[] secondaryColor;

		AffinityTextures(String name, float[] primary, float[] secondary) {
			this.ground = new ResourceLocation("arsmagica2", "textures/vfx_affinities/ground_" + name + ".png");
			this.outer = new ResourceLocation("arsmagica2", "textures/vfx_affinities/outer_" + name + ".png");
			this.matrix = new ResourceLocation("arsmagica2", "textures/vfx_affinities/matrix_" + name + ".png");
			this.core = new ResourceLocation("arsmagica2", "textures/vfx_affinities/core_" + name + ".png");
			this.primaryColor = primary;
			this.secondaryColor = secondary;
		}
	}

	private static final Map<Affinity, AffinityTextures> affinityTextureCache = new HashMap<Affinity, AffinityTextures>();

	// Super Tier Archetypes
	public enum SuperTierType {
		LUNAR_LIGHTNING("lightning_lunar", new float[]{0.44f, 0.81f, 1.0f}, new float[]{0.88f, 0.86f, 1.0f}),
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

	private static final class SuperTierTextures {
		final ResourceLocation ground;
		final ResourceLocation core;
		final ResourceLocation matrix;
		final ResourceLocation sat;
		final ResourceLocation intimate;
		final ResourceLocation phase2;
		final ResourceLocation phase3;

		SuperTierTextures(String suffix) {
			this.ground = new ResourceLocation("arsmagica2", "textures/vfx_affinities/ground_" + suffix + ".png");
			this.core = new ResourceLocation("arsmagica2", "textures/vfx_affinities/core_" + suffix + ".png");
			this.matrix = new ResourceLocation("arsmagica2", "textures/vfx_affinities/matrix_" + suffix + ".png");
			this.sat = new ResourceLocation("arsmagica2", "textures/vfx_affinities/supertier_sat_" + suffix + ".png");
			this.intimate = new ResourceLocation("arsmagica2", "textures/vfx_affinities/intimate_" + suffix + ".png");
			this.phase2 = new ResourceLocation("arsmagica2", "textures/vfx_affinities/phase2_" + suffix + ".png");
			this.phase3 = new ResourceLocation("arsmagica2", "textures/vfx_affinities/phase3_" + suffix + ".png");
		}
	}

	private static final Map<SuperTierType, SuperTierTextures> textureCache = new HashMap<SuperTierType, SuperTierTextures>();

	static {
		for (SuperTierType type : SuperTierType.values()) {
			textureCache.put(type, new SuperTierTextures(type.textureSuffix));
		}

		affinityTextureCache.put(Affinity.NONE, new AffinityTextures("none", new float[]{0.50f, 0.82f, 1.0f}, new float[]{1.0f, 1.0f, 1.0f}));
		affinityTextureCache.put(Affinity.ARCANE, new AffinityTextures("arcane", new float[]{0.84f, 0.0f, 1.0f}, new float[]{1.0f, 0.67f, 1.0f}));
		affinityTextureCache.put(Affinity.WATER, new AffinityTextures("water", new float[]{0.0f, 0.40f, 1.0f}, new float[]{0.50f, 0.82f, 1.0f}));
		affinityTextureCache.put(Affinity.FIRE, new AffinityTextures("fire", new float[]{1.0f, 0.20f, 0.0f}, new float[]{1.0f, 0.80f, 0.0f}));
		affinityTextureCache.put(Affinity.EARTH, new AffinityTextures("earth", new float[]{1.0f, 0.48f, 0.0f}, new float[]{1.0f, 0.88f, 0.70f}));
		affinityTextureCache.put(Affinity.AIR, new AffinityTextures("air", new float[]{0.36f, 0.90f, 1.0f}, new float[]{0.90f, 0.99f, 1.0f}));
		affinityTextureCache.put(Affinity.LIGHTNING, new AffinityTextures("lightning", new float[]{1.0f, 0.92f, 0.0f}, new float[]{1.0f, 1.0f, 0.80f}));
		affinityTextureCache.put(Affinity.ICE, new AffinityTextures("ice", new float[]{0.0f, 0.83f, 1.0f}, new float[]{0.78f, 0.97f, 1.0f}));
		affinityTextureCache.put(Affinity.NATURE, new AffinityTextures("nature", new float[]{0.0f, 0.90f, 0.24f}, new float[]{0.78f, 1.0f, 0.82f}));
		affinityTextureCache.put(Affinity.LIFE, new AffinityTextures("life", new float[]{1.0f, 0.82f, 0.0f}, new float[]{1.0f, 0.97f, 0.70f}));
		affinityTextureCache.put(Affinity.ENDER, new AffinityTextures("ender", new float[]{0.67f, 0.0f, 1.0f}, new float[]{0.94f, 0.70f, 1.0f}));
	}

	// =========================================================================
	// Main Render Hook (Called every frame during RenderWorldLastEvent)
	// =========================================================================
    private static final float[] INTIMATE_HEIGHTS = {0.65f, 1.15f, 1.65f};
    private static final float[] INTIMATE_RADII = {0.68f, 0.88f, 1.06f};
    private static final float[] SAT_ANGLES = {0, 120, 240};
    private static final float[] SAT_TILT_X = {0, 55, -55};
    private static final float[] SAT_TILT_Z = {65, -35, -35};
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
        // Include the full 75-block ring, its expansion and the sky tiers.
        double feet = py - player.yOffset;
        if (player != Minecraft.getMinecraft().thePlayer && !camera.isBoundingBoxInFrustum(
                AxisAlignedBB.getBoundingBox(px - 45, feet - 1, pz - 45, px + 45, feet + 25, pz + 45))) return false;
        VisualState state = visualStateFor(player, held);
        int now = player.ticksExisted;
        if (state.spell == null) return false;
        int elapsed = Math.max(player.getItemInUseDuration(), now - state.startedTick);
        if (state.tier != null) renderSuperTierPlayerCircle(px - cx, py - cy, pz - cz, player, state.tier, elapsed, partialTicks);
        else renderNormalPlayerCircle(px - cx, py - cy, pz - cz, player, state.affinity,
                Math.min(1.0f, (elapsed + partialTicks) / 8.0f), partialTicks);
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
                    if (state.lunar >= 3 || state.solar >= 3)
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
		// 1. Explicit Component Precision Check
		// Fire: FireDamage (15), FireRain (66), Ignition (24)
		boolean hasFire = hasAnyComponent(stack, 15, 66, 24);
		// Ice: FrostDamage (20), Freeze (19), Blizzard (63)
		boolean hasIce = hasAnyComponent(stack, 20, 19, 63);
		// Starfall: FallingStar (65), MagicDamage (35), AstralDistortion (3)
		boolean hasStar = hasAnyComponent(stack, 65, 35, 3);
		// Lightning: LightningDamage (34), Storm (52), LightningStrike (100)
		boolean hasLightning = hasAnyComponent(stack, 34, 52, 100);

		if (hasIce) return SuperTierType.BLIZZARD;
		if (hasFire) return SuperTierType.FIRESTORM;
		if (hasStar) return SuperTierType.STARFALL;
		if (hasAnyComponent(stack, 52)) return SuperTierType.STORM_LIGHTNING;
		if (hasLightning) return (lunarCount >= 3) ? SuperTierType.LUNAR_LIGHTNING : SuperTierType.STORM_LIGHTNING;

		// 2. Affinity Resolution
		Affinity affinity = safeAffinity(stack);
		if (affinity == Affinity.ICE) return SuperTierType.BLIZZARD;
		if (affinity == Affinity.FIRE) return SuperTierType.FIRESTORM;
		if (affinity == Affinity.ARCANE || affinity == Affinity.ENDER) return SuperTierType.STARFALL;
		if (affinity == Affinity.LIGHTNING || affinity == Affinity.AIR) {
			return (lunarCount >= 3) ? SuperTierType.LUNAR_LIGHTNING : SuperTierType.STORM_LIGHTNING;
		}

		// 3. Fallback: Solar -> Firestorm, Lunar -> Lunar Lightning
		if (solarCount >= 3) return SuperTierType.FIRESTORM;
		return SuperTierType.LUNAR_LIGHTNING;
	}

	// =========================================================================
	// SUPER TIER MULTIAXIAL EVOLVING 3D RENDERER (Phases 1, 2, 3)
	// =========================================================================
	private static void renderSuperTierPlayerCircle(double x, double y, double z, EntityPlayer player, SuperTierType type, int useTicks, float partialTicks) {
		float timeSec = (useTicks + partialTicks) / 20.0f;
		float loopTime = timeSec % 6.0f;
		float rotDeg = (loopTime / 6.0f) * 360.0f;

		// 1. Phase 1 Staggered Appearance (Aparecendo aos poucos com aceleração e assentamento)
		// Tier 0 Ground: manifests smoothly from 0.0s to 0.8s
		float groundScale = 0.0f;
		if (timeSec < 0.6f) {
			float p = timeSec / 0.6f;
			groundScale = 0.001f + 1.149f * (p * p * (3.0f - 2.0f * p)); // smoothstep to 1.15
		} else if (timeSec < 0.8f) {
			float p = (timeSec - 0.6f) / 0.2f;
			groundScale = 1.15f - 0.15f * (p * p * (3.0f - 2.0f * p)); // settle to 1.0
		} else {
			groundScale = 1.0f;
		}
		if (groundScale <= 0.005f) return;

		// Intimate Body Rings & Vertical Tiers (Waist, Knees, Chest, Head): manifest from 0.6s to 1.4s
		float bodyScale = 0.0f;
		if (timeSec >= 0.6f) {
			if (timeSec < 1.2f) {
				float p = (timeSec - 0.6f) / 0.6f;
				bodyScale = 0.001f + 1.099f * (p * p * (3.0f - 2.0f * p)); // smoothstep to 1.10
			} else if (timeSec < 1.4f) {
				float p = (timeSec - 1.2f) / 0.2f;
				bodyScale = 1.10f - 0.10f * (p * p * (3.0f - 2.0f * p)); // settle to 1.0
			} else {
				bodyScale = 1.0f;
			}
		}

		// Orbiting Gyroscopic Satellites: manifest from 1.2s to 2.0s
		float satScale = 0.0f;
		if (timeSec >= 1.2f) {
			if (timeSec < 1.8f) {
				float p = (timeSec - 1.2f) / 0.6f;
				satScale = 0.001f + 1.119f * (p * p * (3.0f - 2.0f * p)); // smoothstep to 1.12
			} else if (timeSec < 2.0f) {
				float p = (timeSec - 1.8f) / 0.2f;
				satScale = 1.12f - 0.12f * (p * p * (3.0f - 2.0f * p)); // settle to 1.0
			} else {
				satScale = 1.0f;
			}
		}

		// Central Pillar: erupts alongside the ground mandala
		float pillarScale = groundScale;

		// Phase 2 scaling (Pops at 2.0s = 40 ticks - Metade do tempo anterior de 4.0s)
		float p2Scale = 0.0f;
		if (timeSec >= 2.0f) {
			if (timeSec < 2.3f) {
				float prog = (timeSec - 2.0f) / 0.3f;
				p2Scale = 0.001f + 1.119f * prog;
			} else if (timeSec < 2.4f) {
				float prog = (timeSec - 2.3f) / 0.1f;
				p2Scale = 1.12f - 0.12f * prog;
			} else {
				p2Scale = 1.0f;
			}
		}

		// Phase 3 scaling (Pops at 4.0s = 80 ticks - Metade do tempo anterior de 8.0s)
		float p3Scale = 0.0f;
		if (timeSec >= 4.0f) {
			if (timeSec < 4.3f) {
				float prog = (timeSec - 4.0f) / 0.3f;
				p3Scale = 0.001f + 1.119f * prog;
			} else if (timeSec < 4.4f) {
				float prog = (timeSec - 4.3f) / 0.1f;
				p3Scale = 1.12f - 0.12f * prog;
			} else {
				p3Scale = 1.0f;
			}
		}

		SuperTierTextures tex = textureCache.get(type);
		float[] pCol = type.primaryColor;
		float[] sCol = type.secondaryColor;

		// Compensate for player sneak (Shift) so ground circles never sink into the floor
		float effectiveOffset = player.yOffset;
		if (effectiveOffset > 0.5f) {
			if (player.ySize > 0.0f) {
				effectiveOffset -= player.ySize;
			} else if (player.isSneaking()) {
				effectiveOffset -= 0.2f;
			}
		}
		float baseY = (float) (y - effectiveOffset) + 0.035f;
		Tessellator t = Tessellator.instance;
		Minecraft mc = Minecraft.getMinecraft();
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE); // Cinema additive blending
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-1.0f, -1.0f);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		// =====================================================================
		// 1. PHASE 1: CONJUNÇÃO INICIAL (0s - 12s)
		// =====================================================================

		// Tier 0 Ground Astrolabe (15 blocks diameter = 240u, radius 7.5 blocks)
		mc.renderEngine.bindTexture(tex.ground);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY, (float) z);
		GL11.glRotatef(rotDeg, 0.0f, 1.0f, 0.0f);
		float gScale = 7.5f * groundScale;
		GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.85f * Math.min(1.0f, groundScale));
		renderQuad(t, -gScale, gScale, -gScale, gScale);
		if (type == SuperTierType.STARFALL) {
			GL11.glColor4f(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], 0.45f * Math.min(1.0f, groundScale));
			renderQuad(t, -gScale * 0.985f, gScale * 0.985f, -gScale * 0.985f, gScale * 0.985f);
		}
		GL11.glPopMatrix();

		// Tier 0 Ground Inner Matrix (10 blocks diameter = 160u, radius 5.0 blocks)
		mc.renderEngine.bindTexture(tex.matrix);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.005f, (float) z);
		GL11.glRotatef(-rotDeg, 0.0f, 1.0f, 0.0f);
		float mScale = 5.0f * groundScale;
		GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.90f * Math.min(1.0f, groundScale));
		renderQuad(t, -mScale, mScale, -mScale, mScale);
		if (type == SuperTierType.STARFALL) {
			GL11.glColor4f(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], 0.45f * Math.min(1.0f, groundScale));
			renderQuad(t, -mScale * 0.985f, mScale * 0.985f, -mScale * 0.985f, mScale * 0.985f);
		}
		GL11.glPopMatrix();

		// Tier 0 Ground Core (5.25 blocks diameter = 84u, radius 2.625 blocks, pulsating)
		mc.renderEngine.bindTexture(tex.core);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.010f, (float) z);
		GL11.glRotatef(rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
		float corePulse = 1.0f + 0.08f * (float) Math.sin(timeSec * Math.PI * 2.0f);
		float cScale = 2.625f * groundScale * corePulse;
		GL11.glColor4f(sCol[0], sCol[1], sCol[2], 1.0f * Math.min(1.0f, groundScale));
		renderQuad(t, -cScale, cScale, -cScale, cScale);
		if (type == SuperTierType.STARFALL) {
			GL11.glColor4f(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], 0.55f * Math.min(1.0f, groundScale));
			renderQuad(t, -cScale * 0.98f, cScale * 0.98f, -cScale * 0.98f, cScale * 0.98f);
		}
		GL11.glPopMatrix();

		// Intimate Body Rings (Waist Y+0.65, Chest Y+1.15, Crown Y+1.65)
		if (bodyScale > 0.001f) {
			mc.renderEngine.bindTexture(tex.intimate);
			float[] intimateHeights = INTIMATE_HEIGHTS;
			float[] intimateRadii = INTIMATE_RADII;
			for (int i = 0; i < 3; i++) {
				GL11.glPushMatrix();
				GL11.glTranslatef((float) x, baseY + intimateHeights[i], (float) z);
				GL11.glRotatef(-rotDeg * 4.0f + i * 45.0f, 0.0f, 1.0f, 0.0f);
				float irScale = intimateRadii[i] * bodyScale;
				GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.80f * Math.min(1.0f, bodyScale));
				renderQuad(t, -irScale, irScale, -irScale, irScale);
				GL11.glPopMatrix();
			}

			// Vertical Tiers (Floating Multiaxial Rings)
			// Tier 1 (Knees, Y+1.625, radius 4.375 blocks)
			mc.renderEngine.bindTexture(tex.matrix);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 1.625f, (float) z);
			GL11.glRotatef(-rotDeg, 0.0f, 1.0f, 0.0f);
			float t1Scale = 4.375f * bodyScale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.75f * Math.min(1.0f, bodyScale));
			renderQuad(t, -t1Scale, t1Scale, -t1Scale, t1Scale);
			GL11.glPopMatrix();

			// Tier 2 (Chest, Y+3.375, radius 5.625 blocks) + Inner Core
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 3.375f, (float) z);
			GL11.glRotatef(rotDeg, 0.0f, 1.0f, 0.0f);
			float t2Scale = 5.625f * bodyScale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.85f * Math.min(1.0f, bodyScale));
			renderQuad(t, -t2Scale, t2Scale, -t2Scale, t2Scale);
			GL11.glPopMatrix();

			mc.renderEngine.bindTexture(tex.core);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 3.38f, (float) z);
			GL11.glRotatef(-rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
			float c2Scale = 2.25f * bodyScale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.90f * Math.min(1.0f, bodyScale));
			renderQuad(t, -c2Scale, c2Scale, -c2Scale, c2Scale);
			GL11.glPopMatrix();

			// Tier 3 (Head, Y+5.125, radius 6.56 blocks)
			mc.renderEngine.bindTexture(tex.ground);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 5.125f, (float) z);
			GL11.glRotatef(-rotDeg, 0.0f, 1.0f, 0.0f);
			float t3Scale = 6.56f * bodyScale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.80f * Math.min(1.0f, bodyScale));
			renderQuad(t, -t3Scale, t3Scale, -t3Scale, t3Scale);
			GL11.glPopMatrix();

			// Tier 4 (Sky Portal, Y+7.375, radius 7.81 blocks)
			mc.renderEngine.bindTexture(tex.matrix);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 7.375f, (float) z);
			GL11.glRotatef(rotDeg, 0.0f, 1.0f, 0.0f);
			float t4Scale = 7.81f * bodyScale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.85f * Math.min(1.0f, bodyScale));
			renderQuad(t, -t4Scale, t4Scale, -t4Scale, t4Scale);
			GL11.glPopMatrix();

			// Tier 4 Inner Celestial Disc (radius 4.5 blocks, counter-rotating)
			mc.renderEngine.bindTexture(tex.core);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 7.39f, (float) z);
			GL11.glRotatef(-rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
			float t4Inner = 4.5f * bodyScale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.95f * Math.min(1.0f, bodyScale));
			renderQuad(t, -t4Inner, t4Inner, -t4Inner, t4Inner);
			GL11.glPopMatrix();
		}

		// 3 Phase 1 Orbiting Gyroscopic Satellites (Radius 7.1875 blocks = 115u, Y+3.5 blocks)
		if (satScale > 0.001f) {
			mc.renderEngine.bindTexture(tex.sat);
			float satDist = 7.1875f * satScale;
			float satSize = 2.0f * satScale;
			float[] satAngles = SAT_ANGLES;
			float[] satTiltX = SAT_TILT_X;
			float[] satTiltZ = SAT_TILT_Z;

			for (int i = 0; i < 3; i++) {
				double orbitAngle = Math.toRadians(rotDeg * 0.5f + satAngles[i]);
				double ox = satDist * Math.cos(orbitAngle);
				double oz = satDist * Math.sin(orbitAngle);

				GL11.glPushMatrix();
				GL11.glTranslatef((float) (x + ox), baseY + 3.5f, (float) (z + oz));
				GL11.glRotatef(rotDeg * 2.0f + satAngles[i], 0.0f, 1.0f, 0.0f);
				GL11.glRotatef(satTiltX[i], 1.0f, 0.0f, 0.0f);
				GL11.glRotatef(satTiltZ[i], 0.0f, 0.0f, 1.0f);
				GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.95f * Math.min(1.0f, satScale));
				renderQuad(t, -satSize, satSize, -satSize, satSize);

				// Inner gymbal plate at 90 deg
				GL11.glRotatef(90.0f, 1.0f, 0.0f, 0.0f);
				float innerSat = satSize * 0.72f;
				GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.95f * Math.min(1.0f, satScale));
				renderQuad(t, -innerSat, innerSat, -innerSat, innerSat);
				GL11.glPopMatrix();
			}
		}

		// Central Vertical Energy Column (Mana Beam)
		if (type == SuperTierType.STARFALL) {
			renderStarfallCelestialBeam(t, (float) x, baseY, (float) z, pillarScale, timeSec);
		} else {
			renderCentralPillar(t, (float) x, baseY, (float) z, 1.2f * pillarScale, 20.0f, pCol, sCol, timeSec);
		}

		// =====================================================================
		// 2. PHASE 2: EXPANSÃO TITÂNICA (Surge aos 4s / 80 ticks)
		// =====================================================================
		if (p2Scale > 0.001f) {
			mc.renderEngine.bindTexture(tex.phase2);

			// Phase 2 Doubled Ground Astrolabe (50 blocks diameter = 800u, radius 25.0 blocks!)
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 0.002f, (float) z);
			GL11.glRotatef(-rotDeg, 0.0f, 1.0f, 0.0f);
			float p2Ground = 25.0f * p2Scale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.85f * Math.min(1.0f, p2Scale));
			renderQuad(t, -p2Ground, p2Ground, -p2Ground, p2Ground);
			if (type == SuperTierType.STARFALL) {
				GL11.glColor4f(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], 0.40f * Math.min(1.0f, p2Scale));
				renderQuad(t, -p2Ground * 0.99f, p2Ground * 0.99f, -p2Ground * 0.99f, p2Ground * 0.99f);
			}
			GL11.glPopMatrix();

			// Phase 2 Sky Ring (45 blocks diameter = 720u, Y+13.75 blocks = 220u)
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 13.75f, (float) z);
			GL11.glRotatef(rotDeg, 0.0f, 1.0f, 0.0f);
			float p2Sky = 22.5f * p2Scale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.80f * Math.min(1.0f, p2Scale));
			renderQuad(t, -p2Sky, p2Sky, -p2Sky, p2Sky);
			GL11.glPopMatrix();

			// Phase 2 Sky Inner Expansion Matrix (radius 15.0 blocks, counter-rotating)
			mc.renderEngine.bindTexture(tex.matrix);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 13.78f, (float) z);
			GL11.glRotatef(-rotDeg * 1.5f, 0.0f, 1.0f, 0.0f);
			float p2SkyInner = 15.0f * p2Scale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.85f * Math.min(1.0f, p2Scale));
			renderQuad(t, -p2SkyInner, p2SkyInner, -p2SkyInner, p2SkyInner);
			GL11.glPopMatrix();

			// 4 Phase 2 External Satellites (Radius 22.5 blocks = 360u, size 8.75 blocks = 140u, Y+7.5 blocks = 120u)
			float p2SatDist = 22.5f * p2Scale;
			float p2SatSize = 4.375f * p2Scale;
			for (int i = 0; i < 4; i++) {
				double p2OrbitAngle = Math.toRadians(-rotDeg * 0.5f + i * 90.0f);
				double p2ox = p2SatDist * Math.cos(p2OrbitAngle);
				double p2oz = p2SatDist * Math.sin(p2OrbitAngle);

				GL11.glPushMatrix();
				GL11.glTranslatef((float) (x + p2ox), baseY + 7.5f, (float) (z + p2oz));
				GL11.glRotatef(-rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
				GL11.glRotatef(i % 2 == 0 ? 50.0f : -50.0f, 1.0f, 0.0f, 0.0f);
				GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.90f * Math.min(1.0f, p2Scale));
				renderQuad(t, -p2SatSize, p2SatSize, -p2SatSize, p2SatSize);
				GL11.glPopMatrix();
			}
		}

		// =====================================================================
		// 3. PHASE 3: CATACLISMO DO HORIZONTE (Surge aos 8s / 160 ticks)
		// =====================================================================
		if (p3Scale > 0.001f) {
			mc.renderEngine.bindTexture(tex.phase3);

			// Phase 3 Doubled Ground Horizon (75 blocks diameter = 1200u, radius 37.5 blocks! FORMATO ÚNICO!)
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 0.001f, (float) z);
			GL11.glRotatef(rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
			float p3Ground = 37.5f * p3Scale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.90f * Math.min(1.0f, p3Scale));
			renderQuad(t, -p3Ground, p3Ground, -p3Ground, p3Ground);
			if (type == SuperTierType.STARFALL) {
				GL11.glColor4f(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], 0.40f * Math.min(1.0f, p3Scale));
				renderQuad(t, -p3Ground * 0.992f, p3Ground * 0.992f, -p3Ground * 0.992f, p3Ground * 0.992f);
			}
			GL11.glPopMatrix();

			// Phase 3 Sky Dome (70 blocks diameter = 1120u, Y+20.0 blocks = 320u)
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 20.0f, (float) z);
			GL11.glRotatef(-rotDeg * 2.0f, 0.0f, 1.0f, 0.0f);
			float p3Sky = 35.0f * p3Scale;
			GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.85f * Math.min(1.0f, p3Scale));
			renderQuad(t, -p3Sky, p3Sky, -p3Sky, p3Sky);
			GL11.glPopMatrix();

			// Phase 3 Sky Inner Zenith Singularity (radius 18.0 blocks, rotating fast)
			mc.renderEngine.bindTexture(tex.core);
			GL11.glPushMatrix();
			GL11.glTranslatef((float) x, baseY + 20.05f, (float) z);
			GL11.glRotatef(rotDeg * 3.0f, 0.0f, 1.0f, 0.0f);
			float p3Zenith = 18.0f * p3Scale;
			GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.95f * Math.min(1.0f, p3Scale));
			renderQuad(t, -p3Zenith, p3Zenith, -p3Zenith, p3Zenith);
			GL11.glPopMatrix();
		}

		// Starfall Domain Overhaul: Ground Shockwaves, Perimeter Nodes & Ascending Starlight Chamber
		if (type == SuperTierType.STARFALL) {
			renderStarfallChamberAndShockwaves(t, (float) x, baseY, (float) z, timeSec, p2Scale, p3Scale, rotDeg);
		}

		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);

		// Spawn Rising Arcane Symbols from the concentric orbital rims
		if (player.worldObj != null && player.worldObj.isRemote && allowParticles(player, 2)) {
			double angle = player.worldObj.rand.nextDouble() * Math.PI * 2.0;
			double maxDist = (p3Scale > 0.5f) ? 37.5 : ((p2Scale > 0.5f) ? 25.0 : 7.5);
			double spawnDist = (0.2 + player.worldObj.rand.nextDouble() * 0.8) * maxDist;
			BodyPosition body = bodyPosition(player);
			double spX = body.x + Math.cos(angle) * spawnDist;
			double feetY = body.y - player.yOffset;
			double spY = feetY + 0.05;
			double spZ = body.z + Math.sin(angle) * spawnDist;

			AMParticle particle = (AMParticle) AMCore.proxy.particleManager.spawn(player.worldObj, "symbols", spX, spY, spZ);
			if (particle != null) {
				particle.setDontRequireControllers();
				particle.setMaxAge(24 + player.worldObj.rand.nextInt(16));
				particle.setParticleScale(0.12f + player.worldObj.rand.nextFloat() * 0.10f);
				particle.setRGBColorF(sCol[0], sCol[1], sCol[2]);
				particle.addVelocity(0.0, 0.065 + player.worldObj.rand.nextDouble() * 0.055, 0.0);
			}
		}

	}

	// =========================================================================
	// Central Vertical Luminous Column
	// =========================================================================
	private static void renderCentralPillar(Tessellator t, float cx, float cy, float cz, float radius, float height, float[] pCol, float[] sCol, float timeSec) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		int segments = 24;
		double step = (Math.PI * 2.0) / segments;

		float alpha = 0.35f + 0.10f * (float) Math.sin(timeSec * 4.0f);
		GL11.glColor4f(pCol[0], pCol[1], pCol[2], alpha);

		t.startDrawingQuads();
		t.setColorRGBA_F(pCol[0], pCol[1], pCol[2], alpha);
		for (int i = 0; i < segments; i++) {
			double a1 = i * step;
			double a2 = (i + 1) * step;

			double x1 = cx + Math.cos(a1) * radius;
			double z1 = cz + Math.sin(a1) * radius;
			double x2 = cx + Math.cos(a2) * radius;
			double z2 = cz + Math.sin(a2) * radius;

			t.addVertex(x1, cy, z1);
			t.addVertex(x1, cy + height, z1);
			t.addVertex(x2, cy + height, z2);
			t.addVertex(x2, cy, z2);
		}
		t.draw();
		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	// =========================================================================
	// Starfall Exclusive Celestial Beam: Reaches 76 blocks high into the Sky Portal
	// Composed of: Hot White Plasma Needle, Cyan Sheath, Astral Violet Corona,
	// Twin DNA Ascending Helices, and Shockwave Pulse Rings!
	// =========================================================================
	private static final float[] STARFALL_WHITE = {0.92f, 0.98f, 1.0f};
	private static final float[] STARFALL_CYAN = {0.20f, 0.75f, 1.0f};
	private static final float[] STARFALL_VIOLET = {0.62f, 0.18f, 1.0f};

	private static void renderStarfallCelestialBeam(Tessellator t, float cx, float cy, float cz, float pillarScale, float timeSec) {
		if (pillarScale <= 0.005f) return;
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		float beamHeight = 76.0f;
		int segments = 24;
		double step = (Math.PI * 2.0) / segments;

		// 1. Núcleo Branco Incandescente (Hot White Core Needle)
		float coreRadius = 0.48f * pillarScale;
		float coreAlpha = Math.min(1.0f, (0.85f + 0.15f * (float) Math.sin(timeSec * 6.0f)) * pillarScale);
		t.startDrawingQuads();
		t.setColorRGBA_F(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], coreAlpha);
		for (int i = 0; i < segments; i++) {
			double a1 = i * step, a2 = (i + 1) * step;
			double x1 = cx + Math.cos(a1) * coreRadius, z1 = cz + Math.sin(a1) * coreRadius;
			double x2 = cx + Math.cos(a2) * coreRadius, z2 = cz + Math.sin(a2) * coreRadius;
			t.addVertex(x1, cy, z1);
			t.addVertex(x1, cy + beamHeight, z1);
			t.addVertex(x2, cy + beamHeight, z2);
			t.addVertex(x2, cy, z2);
		}
		t.draw();

		// 2. Coluna Interna Ciano Estelar (Inner Celestial Sheath)
		float midRadius = 1.35f * pillarScale;
		float midAlpha = Math.min(1.0f, (0.42f + 0.12f * (float) Math.sin(timeSec * 3.5f)) * pillarScale);
		t.startDrawingQuads();
		t.setColorRGBA_F(STARFALL_CYAN[0], STARFALL_CYAN[1], STARFALL_CYAN[2], midAlpha);
		for (int i = 0; i < segments; i++) {
			double a1 = i * step, a2 = (i + 1) * step;
			double x1 = cx + Math.cos(a1) * midRadius, z1 = cz + Math.sin(a1) * midRadius;
			double x2 = cx + Math.cos(a2) * midRadius, z2 = cz + Math.sin(a2) * midRadius;
			t.addVertex(x1, cy, z1);
			t.addVertex(x1, cy + beamHeight, z1);
			t.addVertex(x2, cy + beamHeight, z2);
			t.addVertex(x2, cy, z2);
		}
		t.draw();

		// 3. Coluna Externa Violeta Astral (Outer Astral Corona)
		float outRadius = 2.40f * pillarScale;
		float outAlpha = Math.min(1.0f, (0.22f + 0.08f * (float) Math.sin(timeSec * 2.0f)) * pillarScale);
		t.startDrawingQuads();
		t.setColorRGBA_F(STARFALL_VIOLET[0], STARFALL_VIOLET[1], STARFALL_VIOLET[2], outAlpha);
		for (int i = 0; i < segments; i++) {
			double a1 = i * step, a2 = (i + 1) * step;
			double x1 = cx + Math.cos(a1) * outRadius, z1 = cz + Math.sin(a1) * outRadius;
			double x2 = cx + Math.cos(a2) * outRadius, z2 = cz + Math.sin(a2) * outRadius;
			t.addVertex(x1, cy, z1);
			t.addVertex(x1, cy + beamHeight, z1);
			t.addVertex(x2, cy + beamHeight, z2);
			t.addVertex(x2, cy, z2);
		}
		t.draw();

		// 4. Dupla Hélice Espiralada de Fitas (Twin DNA Ascending Spirals)
		float spiralRadius = 1.65f * pillarScale;
		int spiralSteps = 48;
		float yStep = beamHeight / spiralSteps;

		// Hélice 1: Ciano
		t.startDrawingQuads();
		t.setColorRGBA_F(STARFALL_CYAN[0], STARFALL_CYAN[1], STARFALL_CYAN[2], 0.65f * pillarScale);
		for (int i = 0; i < spiralSteps; i++) {
			float y1 = cy + i * yStep, y2 = cy + (i + 1) * yStep;
			double angle1 = timeSec * 3.5f + i * 0.32;
			double angle2 = timeSec * 3.5f + (i + 1) * 0.32;
			double hx1 = cx + Math.cos(angle1) * spiralRadius, hz1 = cz + Math.sin(angle1) * spiralRadius;
			double hx2 = cx + Math.cos(angle2) * spiralRadius, hz2 = cz + Math.sin(angle2) * spiralRadius;
			float ribbonW = 0.20f;
			t.addVertex(hx1 - ribbonW, y1, hz1);
			t.addVertex(hx1 + ribbonW, y1 + ribbonW, hz1);
			t.addVertex(hx2 + ribbonW, y2 + ribbonW, hz2);
			t.addVertex(hx2 - ribbonW, y2, hz2);
		}
		t.draw();

		// Hélice 2: Violeta (oposta a 180 graus)
		t.startDrawingQuads();
		t.setColorRGBA_F(STARFALL_VIOLET[0], STARFALL_VIOLET[1], STARFALL_VIOLET[2], 0.65f * pillarScale);
		for (int i = 0; i < spiralSteps; i++) {
			float y1 = cy + i * yStep, y2 = cy + (i + 1) * yStep;
			double angle1 = timeSec * 3.5f + i * 0.32 + Math.PI;
			double angle2 = timeSec * 3.5f + (i + 1) * 0.32 + Math.PI;
			double hx1 = cx + Math.cos(angle1) * spiralRadius, hz1 = cz + Math.sin(angle1) * spiralRadius;
			double hx2 = cx + Math.cos(angle2) * spiralRadius, hz2 = cz + Math.sin(angle2) * spiralRadius;
			float ribbonW = 0.20f;
			t.addVertex(hx1 - ribbonW, y1, hz1);
			t.addVertex(hx1 + ribbonW, y1 + ribbonW, hz1);
			t.addVertex(hx2 + ribbonW, y2 + ribbonW, hz2);
			t.addVertex(hx2 - ribbonW, y2, hz2);
		}
		t.draw();

		// 5. Anéis de Pulso Ascendentes (Ascending Shockwave Rings)
		for (int r = 0; r < 4; r++) {
			float prog = ((timeSec * 0.50f + r * 0.25f) % 1.0f);
			float ry = cy + prog * beamHeight;
			float ringR = (1.1f + 1.4f * prog) * pillarScale;
			float ringAlpha = (1.0f - prog) * 0.85f * pillarScale;
			t.startDrawingQuads();
			t.setColorRGBA_F(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], ringAlpha);
			for (int i = 0; i < segments; i++) {
				double a1 = i * step, a2 = (i + 1) * step;
				double ix1 = cx + Math.cos(a1) * (ringR - 0.22f), iz1 = cz + Math.sin(a1) * (ringR - 0.22f);
				double ox1 = cx + Math.cos(a1) * ringR, oz1 = cz + Math.sin(a1) * ringR;
				double ox2 = cx + Math.cos(a2) * ringR, oz2 = cz + Math.sin(a2) * ringR;
				double ix2 = cx + Math.cos(a2) * (ringR - 0.22f), iz2 = cz + Math.sin(a2) * (ringR - 0.22f);
				t.addVertex(ix1, ry, iz1);
				t.addVertex(ox1, ry, oz1);
				t.addVertex(ox2, ry, oz2);
				t.addVertex(ix2, ry, iz2);
			}
			t.draw();
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	// =========================================================================
	// Starfall Exclusive Domain Chamber & Runic Ground Shockwaves
	// 1. Dual-blade ground shockwaves expanding on Phase 2 (25m) and Phase 3 (37.5m)
	// 2. 12 & 24 4-pointed cross star flares orbiting on the perimeter rings
	// 3. 24 radial boundary runic spikes defining the cataclysm border
	// 4. 16 ascending 76m Starlight Chamber Ribbons linking ground to the sky portal
	// =========================================================================
	private static void drawGroundRing(Tessellator t, float cx, float cy, float cz, float innerR, float outerR, float[] color, float alpha) {
		if (alpha <= 0.005f || innerR >= outerR) return;
		int segments = 48;
		double step = (Math.PI * 2.0) / segments;
		t.startDrawingQuads();
		t.setColorRGBA_F(color[0], color[1], color[2], alpha);
		for (int i = 0; i < segments; i++) {
			double a1 = i * step, a2 = (i + 1) * step;
			double cos1 = Math.cos(a1), sin1 = Math.sin(a1);
			double cos2 = Math.cos(a2), sin2 = Math.sin(a2);
			t.addVertex(cx + cos1 * innerR, cy, cz + sin1 * innerR);
			t.addVertex(cx + cos1 * outerR, cy, cz + sin1 * outerR);
			t.addVertex(cx + cos2 * outerR, cy, cz + sin2 * outerR);
			t.addVertex(cx + cos2 * innerR, cy, cz + sin2 * innerR);
		}
		t.draw();
	}

	private static void drawGroundStarFlare(Tessellator t, float sx, float sy, float sz, float size, float rotAngle, float[] color, float alpha) {
		if (alpha <= 0.005f || size <= 0.005f) return;
		double cosR = Math.cos(rotAngle), sinR = Math.sin(rotAngle);
		float armL = size;
		float armW = size * 0.16f;

		t.startDrawingQuads();
		// Longitudinal ray
		t.setColorRGBA_F(color[0], color[1], color[2], alpha * 0.85f);
		double lx1 = -armL * cosR - (-armW) * sinR, lz1 = -armL * sinR + (-armW) * cosR;
		double lx2 = armL * cosR - (-armW) * sinR,  lz2 = armL * sinR + (-armW) * cosR;
		double lx3 = armL * cosR - armW * sinR,      lz3 = armL * sinR + armW * cosR;
		double lx4 = -armL * cosR - armW * sinR,     lz4 = -armL * sinR + armW * cosR;
		t.addVertex(sx + lx1, sy, sz + lz1);
		t.addVertex(sx + lx2, sy, sz + lz2);
		t.addVertex(sx + lx3, sy, sz + lz3);
		t.addVertex(sx + lx4, sy, sz + lz4);

		// Transversal ray
		double tx1 = -armW * cosR - (-armL) * sinR, tz1 = -armW * sinR + (-armL) * cosR;
		double tx2 = armW * cosR - (-armL) * sinR,  tz2 = armW * sinR + (-armL) * cosR;
		double tx3 = armW * cosR - armL * sinR,      tz3 = armW * sinR + armL * cosR;
		double tx4 = -armW * cosR - armL * sinR,     tz4 = -armW * sinR + armL * cosR;
		t.addVertex(sx + tx1, sy, sz + tz1);
		t.addVertex(sx + tx2, sy, sz + tz2);
		t.addVertex(sx + tx3, sy, sz + tz3);
		t.addVertex(sx + tx4, sy, sz + tz4);

		// Incandescent White Core Flare
		float coreS = size * 0.38f;
		t.setColorRGBA_F(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], alpha);
		double cx1 = -coreS * cosR - (-coreS) * sinR, cz1 = -coreS * sinR + (-coreS) * cosR;
		double cx2 = coreS * cosR - (-coreS) * sinR,  cz2 = coreS * sinR + (-coreS) * cosR;
		double cx3 = coreS * cosR - coreS * sinR,      cz3 = coreS * sinR + coreS * cosR;
		double cx4 = -coreS * cosR - coreS * sinR,     cz4 = -coreS * sinR + coreS * cosR;
		t.addVertex(sx + cx1, sy + 0.001f, sz + cz1);
		t.addVertex(sx + cx2, sy + 0.001f, sz + cz2);
		t.addVertex(sx + cx3, sy + 0.001f, sz + cz3);
		t.addVertex(sx + cx4, sy + 0.001f, sz + cz4);
		t.draw();
	}

	private static void drawGroundSpike(Tessellator t, float cx, float cy, float cz, double angle, float rInner, float rOuter, float halfW, float[] color, float alpha) {
		if (alpha <= 0.005f) return;
		double cos = Math.cos(angle), sin = Math.sin(angle);
		double perpX = -sin, perpZ = cos;

		double bx1 = cx + cos * rInner - perpX * halfW;
		double bz1 = cz + sin * rInner - perpZ * halfW;
		double bx2 = cx + cos * rInner + perpX * halfW;
		double bz2 = cz + sin * rInner + perpZ * halfW;
		double tx1 = cx + cos * rOuter - perpX * (halfW * 0.15f);
		double tz1 = cz + sin * rOuter - perpZ * (halfW * 0.15f);
		double tx2 = cx + cos * rOuter + perpX * (halfW * 0.15f);
		double tz2 = cz + sin * rOuter + perpZ * (halfW * 0.15f);

		t.startDrawingQuads();
		t.setColorRGBA_F(color[0], color[1], color[2], alpha);
		t.addVertex(bx1, cy, bz1);
		t.addVertex(tx1, cy, tz1);
		t.addVertex(tx2, cy, tz2);
		t.addVertex(bx2, cy, bz2);
		t.draw();
	}

	private static void renderStarfallChamberAndShockwaves(Tessellator t, float cx, float cy, float cz, float timeSec, float p2Scale, float p3Scale, float rotDeg) {
		GL11.glDisable(GL11.GL_TEXTURE_2D);

		// 1. Onda de Choque Terrestre da Fase 2 (Ao surgir aos 2.0s / 40 ticks, raio 0 -> 25m)
		if (timeSec >= 2.0f && timeSec < 2.7f) {
			float p = (timeSec - 2.0f) / 0.7f;
			float waveR = 25.0f * (float) Math.sin(p * Math.PI * 0.5);
			float waveAlpha = (1.0f - p) * 0.85f;
			drawGroundRing(t, cx, cy + 0.012f, cz, Math.max(0.0f, waveR - 0.55f), waveR, STARFALL_CYAN, waveAlpha);
			drawGroundRing(t, cx, cy + 0.013f, cz, Math.max(0.0f, waveR - 0.22f), waveR, STARFALL_WHITE, waveAlpha * 0.95f);
		}

		// 2. Onda de Choque Terrestre do Cataclismo da Fase 3 (Ao surgir aos 4.0s / 80 ticks, raio 0 -> 37.5m)
		if (timeSec >= 4.0f && timeSec < 4.85f) {
			float p = (timeSec - 4.0f) / 0.85f;
			float waveR = 37.5f * (float) Math.sin(p * Math.PI * 0.5);
			float waveAlpha = (1.0f - p) * 0.95f;
			drawGroundRing(t, cx, cy + 0.014f, cz, Math.max(0.0f, waveR - 0.70f), waveR, STARFALL_VIOLET, waveAlpha);
			drawGroundRing(t, cx, cy + 0.015f, cz, Math.max(0.0f, waveR - 0.28f), waveR, STARFALL_WHITE, waveAlpha * 1.0f);
		}

		// 3. Fase 2: 12 Nós Estelares em Cruz de 4 Pontas Orbitando a Borda de 25m
		if (p2Scale > 0.05f) {
			float r2 = 25.0f * p2Scale;
			float flareSize = 1.35f * p2Scale * (1.0f + 0.12f * (float) Math.sin(timeSec * 4.0f));
			float flareAlpha = 0.80f * Math.min(1.0f, p2Scale);
			for (int i = 0; i < 12; i++) {
				double a = Math.toRadians(-rotDeg + i * 30.0f);
				float nx = cx + (float) Math.cos(a) * r2;
				float nz = cz + (float) Math.sin(a) * r2;
				drawGroundStarFlare(t, nx, cy + 0.003f, nz, flareSize, (float) (a + timeSec * 2.0f), STARFALL_CYAN, flareAlpha);
			}
		}

		// 4. Fase 3: 24 Nós Estelares + 24 Espículas Radiais na Borda do Horizonte (37.5m)
		if (p3Scale > 0.05f) {
			float r3 = 37.5f * p3Scale;
			float flareSize = 1.75f * p3Scale * (1.0f + 0.15f * (float) Math.sin(timeSec * 5.0f));
			float flareAlpha = 0.88f * Math.min(1.0f, p3Scale);
			for (int i = 0; i < 24; i++) {
				double a = Math.toRadians(rotDeg * 2.0f + i * 15.0f);
				float nx = cx + (float) Math.cos(a) * r3;
				float nz = cz + (float) Math.sin(a) * r3;
				float[] col = (i % 2 == 0) ? STARFALL_CYAN : STARFALL_VIOLET;
				drawGroundStarFlare(t, nx, cy + 0.002f, nz, flareSize, (float) (a - timeSec * 3.0f), col, flareAlpha);

				// Espículas radiais apontando para dentro e para fora (Borda rúnica)
				float spikeLen = 1.6f * p3Scale;
				float halfW = 0.22f * p3Scale;
				drawGroundSpike(t, cx, cy + 0.002f, cz, a, r3 - spikeLen, r3, halfW, col, flareAlpha * 0.70f);
				drawGroundSpike(t, cx, cy + 0.002f, cz, a, r3, r3 + spikeLen * 0.75f, halfW * 0.8f, STARFALL_WHITE, flareAlpha * 0.55f);
			}

			// 5. Fase 3: Starlight Chamber Ribbons (As 16 Fitas de Luz Ascendentes de 76 Blocos!)
			// Conectam o círculo de 75m do solo ao portal cósmico no zênite (96m)
			float chamberHeight = 76.0f;
			int ribbonCount = 16;
			float ribbonWidth = 0.55f * p3Scale;
			for (int i = 0; i < ribbonCount; i++) {
				double a = Math.toRadians(rotDeg * 0.40f + i * (360.0f / ribbonCount));
				float rx = cx + (float) Math.cos(a) * r3;
				float rz = cz + (float) Math.sin(a) * r3;

				// Vetor normal tangencial ao anel
				float tx = (float) -Math.sin(a) * ribbonWidth;
				float tz = (float) Math.cos(a) * ribbonWidth;

				float[] rCol = (i % 2 == 0) ? STARFALL_CYAN : STARFALL_VIOLET;
				float rAlpha = Math.min(1.0f, (0.35f + 0.18f * (float) Math.sin(timeSec * 3.5f + i * 0.8f)) * p3Scale);

				// Fita colorida exterior (Ciano / Violeta)
				t.startDrawingQuads();
				t.setColorRGBA_F(rCol[0], rCol[1], rCol[2], rAlpha);
				t.addVertex(rx - tx, cy, rz - tz);
				t.addVertex(rx - tx, cy + chamberHeight, rz - tz);
				t.addVertex(rx + tx, cy + chamberHeight, rz + tz);
				t.addVertex(rx + tx, cy, rz + tz);
				t.draw();

				// Fita fina central incandescente (Hot White Spine)
				float spineW = ribbonWidth * 0.28f;
				float stx = (float) -Math.sin(a) * spineW;
				float stz = (float) Math.cos(a) * spineW;
				t.startDrawingQuads();
				t.setColorRGBA_F(STARFALL_WHITE[0], STARFALL_WHITE[1], STARFALL_WHITE[2], rAlpha * 0.85f);
				t.addVertex(rx - stx, cy, rz - stz);
				t.addVertex(rx - stx, cy + chamberHeight, rz - stz);
				t.addVertex(rx + stx, cy + chamberHeight, rz + stz);
				t.addVertex(rx + stx, cy, rz + stz);
				t.draw();
			}
		}

		GL11.glEnable(GL11.GL_TEXTURE_2D);
	}

	// =========================================================================
	// Standard (Normal) Casting Circle for non-super tier spells
	// =========================================================================
	private static void renderNormalPlayerCircle(double x, double y, double z, EntityPlayer player, Affinity affinity, float expand, float partialTicks) {
		if (expand <= 0.01f) return;

		float age = player.ticksExisted + partialTicks;

		if (affinity == null) affinity = Affinity.NONE;
		AffinityTextures tex = affinityTextureCache.get(affinity);
		if (tex == null) tex = affinityTextureCache.get(Affinity.NONE);

		float[] pCol = tex.primaryColor;
		float[] sCol = tex.secondaryColor;

		Tessellator t = Tessellator.instance;
		float lightX = OpenGlHelper.lastBrightnessX, lightY = OpenGlHelper.lastBrightnessY;

		GL11.glPushMatrix();
		GL11.glPushAttrib(GL11.GL_ALL_ATTRIB_BITS);
		GL11.glDisable(GL11.GL_LIGHTING);
		GL11.glDisable(GL11.GL_CULL_FACE);
		GL11.glEnable(GL11.GL_BLEND);
		GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
		GL11.glDepthMask(false);
		GL11.glEnable(GL11.GL_POLYGON_OFFSET_FILL);
		GL11.glPolygonOffset(-1.0f, -1.0f);
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240.0f, 240.0f);

		float effectiveOffset = player.yOffset;
		if (effectiveOffset > 0.5f) {
			if (player.ySize > 0.0f) {
				effectiveOffset -= player.ySize;
			} else if (player.isSneaking()) {
				effectiveOffset -= 0.2f;
			}
		}
		float baseY = (float) (y - effectiveOffset) + 0.035f;
		float masterRadius = 1.65f * expand;
		float alphaPulse = 0.85f + 0.15f * (float) Math.sin(age * 0.2f);
		float masterAlpha = expand * alphaPulse;

		Minecraft mc = Minecraft.getMinecraft();

		// Layer 1: Ground Glow Mandala (Counter-clockwise slow)
		mc.renderEngine.bindTexture(tex.ground);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY, (float) z);
		GL11.glRotatef(-age * 0.45f, 0.0f, 1.0f, 0.0f);
		float gScale = masterRadius * 1.15f;
		GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.65f * masterAlpha);
		renderQuad(t, -gScale, gScale, -gScale, gScale);
		GL11.glPopMatrix();

		// Layer 2: Outer Runic Circle (Clockwise)
		mc.renderEngine.bindTexture(tex.outer);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.005f, (float) z);
		GL11.glRotatef(age * 0.75f, 0.0f, 1.0f, 0.0f);
		float outerScale = masterRadius;
		GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.90f * masterAlpha);
		renderQuad(t, -outerScale, outerScale, -outerScale, outerScale);
		GL11.glPopMatrix();

		// Layer 3: Middle Elemental Matrix (Counter-Clockwise)
		mc.renderEngine.bindTexture(tex.matrix);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.010f, (float) z);
		GL11.glRotatef(-age * 1.25f, 0.0f, 1.0f, 0.0f);
		float midScale = masterRadius * 0.80f;
		GL11.glColor4f(pCol[0], pCol[1], pCol[2], 0.95f * masterAlpha);
		renderQuad(t, -midScale, midScale, -midScale, midScale);
		GL11.glPopMatrix();

		// Layer 4: Core Sigil (Pulsing Center)
		mc.renderEngine.bindTexture(tex.core);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.015f, (float) z);
		GL11.glRotatef(age * 1.85f, 0.0f, 1.0f, 0.0f);
		float corePulse = 1.0f + 0.07f * (float) Math.sin(age * 0.40f);
		float coreScale = masterRadius * 0.48f * corePulse;
		GL11.glColor4f(sCol[0], sCol[1], sCol[2], 1.0f * masterAlpha);
		renderQuad(t, -coreScale, coreScale, -coreScale, coreScale);
		GL11.glPopMatrix();

		// Layer 5: Smooth Vector Boundary Ring
		GL11.glDisable(GL11.GL_TEXTURE_2D);
		GL11.glPushMatrix();
		GL11.glTranslatef((float) x, baseY + 0.02f, (float) z);
		GL11.glRotatef(age * 1.5f, 0.0f, 1.0f, 0.0f);
		GL11.glColor4f(sCol[0], sCol[1], sCol[2], 0.65f * masterAlpha);
		renderSmoothRing(masterRadius * 0.98f, 64, 2.5f);
		GL11.glPopMatrix();

		GL11.glEnable(GL11.GL_TEXTURE_2D);
		GL11.glDepthMask(true);
		GL11.glDisable(GL11.GL_BLEND);
		GL11.glEnable(GL11.GL_LIGHTING);
		GL11.glPopAttrib();
		GL11.glPopMatrix();
		OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lightX, lightY);

		// Normal particles tinted to elemental primary color
		if (player.worldObj != null && player.worldObj.isRemote && allowParticles(player, 3)) {
			double angle = player.worldObj.rand.nextDouble() * Math.PI * 2.0;
			double spawnDist = (0.4 + player.worldObj.rand.nextDouble() * 0.6) * masterRadius;
			BodyPosition body = bodyPosition(player);
			double spX = body.x + Math.cos(angle) * spawnDist;
			double feetY = body.y - player.yOffset;
			double spY = feetY + 0.05;
			double spZ = body.z + Math.sin(angle) * spawnDist;

			AMParticle particle = (AMParticle) AMCore.proxy.particleManager.spawn(player.worldObj, "symbols", spX, spY, spZ);
			if (particle != null) {
				particle.setDontRequireControllers();
				particle.setMaxAge(18 + player.worldObj.rand.nextInt(10));
				particle.setParticleScale(0.08f + player.worldObj.rand.nextFloat() * 0.06f);
				particle.setRGBColorF(pCol[0], pCol[1], pCol[2]);
				particle.addVelocity(0.0, 0.045 + player.worldObj.rand.nextDouble() * 0.035, 0.0);
			}
		}
	}

	private static void renderQuad(Tessellator t, double minX, double maxX, double minZ, double maxZ) {
		t.startDrawingQuads();
		t.setNormal(0.0f, 1.0f, 0.0f);
		t.addVertexWithUV(minX, 0.0, minZ, 0.0, 0.0);
		t.addVertexWithUV(minX, 0.0, maxZ, 0.0, 1.0);
		t.addVertexWithUV(maxX, 0.0, maxZ, 1.0, 1.0);
		t.addVertexWithUV(maxX, 0.0, minZ, 1.0, 0.0);
		t.draw();
	}

	private static void renderSmoothRing(float radius, int segments, float lineWidth) {
		GL11.glLineWidth(lineWidth);
		Tessellator t = Tessellator.instance;
		t.startDrawing(GL11.GL_LINE_LOOP);
		double step = (2.0 * Math.PI) / segments;
		for (int i = 0; i < segments; i++) {
			double theta = i * step;
			t.addVertex(Math.cos(theta) * radius, 0.0, Math.sin(theta) * radius);
		}
		t.draw();
	}

	public static void clearAll() {
		visualStates.clear();
		bodyPositions.clear();
		CastingAnimationState.clear();
	}
}
