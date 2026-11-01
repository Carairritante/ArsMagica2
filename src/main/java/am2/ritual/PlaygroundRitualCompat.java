package am2.ritual;

import am2.LogHelper;
import am2.RitualShapeHelper;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.api.spell.component.interfaces.ISpellComponent;
import am2.blocks.BlocksCommonProxy;
import am2.items.ItemCrystalPhylactery;
import am2.items.ItemsCommonProxy;
import am2.spell.SkillManager;
import am2.spell.components.BanishRain;
import cpw.mods.fml.common.Loader;
import net.minecraft.block.Block;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemPotion;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.tileentity.TileEntityBrewingStand;
import net.minecraft.world.World;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Optional, read-only access to ritual definitions supplied by the installed addon. */
public final class PlaygroundRitualCompat {
    private static final String REGISTRY = "airburn.am2playground.rituals.PGRituals";
    private static final String PREFIX = "am2.architect.pg.";
    private static List<Descriptor> cached;

    private PlaygroundRitualCompat() {}

    public static final class Descriptor {
        public final String key, compendiumId, parentId, nameKey, activationKey, noteKey, controller;
        public final Block centerBlock;
        public final IRitualInteraction interaction;
        public final boolean filledPhylactery, potionInfusion, activationUnverified;

        private Descriptor(String key, String compendiumId, String parentId, String controller,
                           Block center, IRitualInteraction interaction, boolean phylactery,
                           boolean potion, boolean unverified) {
            this.key = key;
            this.compendiumId = compendiumId;
            this.parentId = parentId;
            this.controller = controller;
            this.centerBlock = center;
            this.interaction = interaction;
            this.filledPhylactery = phylactery;
            this.potionInfusion = potion;
            this.activationUnverified = unverified;
            this.nameKey = PREFIX + "ritual." + key;
            this.activationKey = PREFIX + "activate." + key;
            this.noteKey = unverified ? PREFIX + "note.activation_unverified"
                    : phylactery ? PREFIX + "note.phylactery"
                    : potion ? PREFIX + "note.potions" : "";
        }
    }

    /** Call after mod initialization. No addon classes are referenced when it is absent. */
    public static synchronized List<Descriptor> descriptors() {
        if (cached != null) return cached;
        if (!Loader.isModLoaded("am2pg")) return Collections.emptyList();
        // Do not freeze an empty result when a caller asks before the core's skill registration.
        if (SkillManager.instance.getAllComponents().isEmpty()) return Collections.emptyList();
        List<Descriptor> result = new ArrayList<Descriptor>();
        try {
            Class<?> registry = Class.forName(REGISTRY);
            Method get = registry.getMethod("getRitual", String.class);
            add(result, get, "hellReapersSong", "hell_reapers_song", "callOfHell_overview", "EnderIntervention", Blocks.anvil, false, false, false);
            add(result, get, "originOfKnowledge", "origin_of_knowledge", "descentOfTheEnd_overview", "NetherIntervention", Blocks.enchanting_table, false, false, false);
            add(result, get, "tornSpace", "tornSpace_overview", "rituals_overview", "VoidDamage", Blocks.bedrock, false, false, false);
            add(result, get, "breakthrough", "breakthrough_overview", "rituals_overview", "LifeTap", BlocksCommonProxy.liquidEssence, false, false, false);
            add(result, get, "target", "target_overview", "rituals_overview", "Mark", null, false, false, true);
            add(result, get, "impactEvent", "impactEvent_overview", "rituals_overview", "FallingStar", null, false, false, true);
            add(result, get, "villageCall_villager", "villageCall_villager", "villageCall_overview", "DivineIntervention", BlocksCommonProxy.wizardChalk, false, false, false);
            add(result, get, "villageCall_mobMageVillager", "villageCall_mobMageVillager", "villageCall_overview", "DivineIntervention", BlocksCommonProxy.wizardChalk, false, false, false);
            add(result, get, "soultrapRevival", "soultrapRevival_overview", "rituals_overview", "LifeTap", BlocksCommonProxy.inertSpawner, true, false, false);
            add(result, get, "soultrapAlteration", "soultrapAlteration_overview", "rituals_overview", "ManaLink", Blocks.mob_spawner, true, false, false);
            add(result, get, "hellspawnCall_hellCow", "hellspawnCall_hellCow", "callOfHell_overview", "EnderIntervention", Blocks.fire, false, false, false);
            add(result, get, "potionInfusion_random", "potionInfusion_random", "potionInfusion_overview", "RandomDamage", Blocks.brewing_stand, false, true, false);
            add(result, get, "potionInfusion_heal", "potionInfusion_heal", "potionInfusion_overview", "Heal", Blocks.brewing_stand, false, true, false);
            add(result, get, "potionInfusion_harm", "potionInfusion_harm", "potionInfusion_overview", "MagicDamage", Blocks.brewing_stand, false, true, false);
            add(result, get, "potionInfusion_clear", "potionInfusion_clear", "potionInfusion_overview", "Dispel", Blocks.brewing_stand, false, true, false);

            if (Loader.isModLoaded("AWWayofTime")) {
                Block altar = optionalBlock("airburn.am2playground.compat.bloodmagic.PGBloodMagicCompat", "blockAltar");
                if (altar != null) {
                    String[] summons = {"airElemental", "fireElemental", "waterElemental", "earthElemental", "shadeElemental", "holyElemental",
                            "smallEarthGolem", "lowerGuardian", "iceDemon", "fallenAngel", "boulderFist", "shade", "bileDemon", "wingedFireDemon"};
                    for (String summon : summons) {
                        String key = "hellspawnCall_" + summon;
                        add(result, get, key, key, "callOfHell_overview", "EnderIntervention", altar, false, false, false);
                    }
                    add(result, get, "damnedMobilization", "damnedMobilization", "callOfHell_overview", "CrossDimensionalIntervention", altar, false, false, false);
                }
            }
            if (Loader.isModLoaded("Thaumcraft")) {
                add(result, get, "chillingLook", "chillingLook", "descentOfTheEnd_overview", "TrueSight", null, false, false, true);
                Block eldritch = optionalBlock("thaumcraft.common.config.ConfigBlocks", "blockEldritch");
                if (eldritch != null) add(result, get, "eldritchMobilization", "eldritchMobilization", "descentOfTheEnd_overview", "CrossDimensionalIntervention", eldritch, false, false, false);
            }

            Method infuse = registry.getMethod("getRitual", IRitualInteraction.class);
            Map<String, IRitualInteraction> buffs = new TreeMap<String, IRitualInteraction>();
            for (Integer id : SkillManager.instance.getAllComponents()) {
                ISpellComponent component = SkillManager.instance.getComponent(id);
                if (!(component instanceof IRitualInteraction) || component instanceof BanishRain) continue;
                IRitualInteraction ritual = (IRitualInteraction)component;
                if (ritual.getRitualShape() != RitualShapeHelper.instance.hourglass) continue;
                String controller = SkillManager.instance.getSkillName(component);
                if (controller != null) buffs.put(controller, ritual);
            }
            for (Map.Entry<String, IRitualInteraction> buff : buffs.entrySet()) {
                Object value = infuse.invoke(null, buff.getValue());
                if (!(value instanceof IRitualInteraction)) continue;
                result.add(new Descriptor("potionBuff_" + buff.getKey(), "potionInfusion_overview", "potionInfusion_overview",
                        buff.getKey(), Blocks.brewing_stand, (IRitualInteraction)value, false, true, false));
            }
        } catch (ReflectiveOperationException | RuntimeException | LinkageError failure) {
            LogHelper.warn("Ritual guide could not finish reading the optional Playground API: " + failure.getClass().getSimpleName());
        }
        cached = Collections.unmodifiableList(result);
        return cached;
    }

    private static void add(List<Descriptor> result, Method lookup, String key, String compendium,
                            String parent, String controller, Block center, boolean phylactery,
                            boolean potion, boolean unverified) throws ReflectiveOperationException {
        Object value = lookup.invoke(null, key);
        if (value instanceof IRitualInteraction) result.add(new Descriptor(key, compendium, parent,
                controller, center, (IRitualInteraction)value, phylactery, potion, unverified));
    }

    private static Block optionalBlock(String owner, String field) {
        try {
            Object value = Class.forName(owner).getField(field).get(null);
            return value instanceof Block ? (Block)value : null;
        } catch (ReflectiveOperationException | RuntimeException | LinkageError unavailable) {
            return null;
        }
    }

    /** Checks only the original filled-item predicate, without using or draining the item. */
    public static boolean validPhylactery(ItemStack stack) {
        if (stack == null || stack.getItem() != ItemsCommonProxy.crystalPhylactery
                || stack.getItemDamage() != ItemCrystalPhylactery.META_FULL || !stack.hasTagCompound()) return false;
        String entity = ItemsCommonProxy.crystalPhylactery.getSpawnClass(stack);
        return entity != null && EntityList.stringToClassMapping.containsKey(entity);
    }

    /** Preparation warning only; no potion or brewing-stand contents are ever changed. */
    public static String potionWarningKey(Descriptor descriptor, World world, int x, int y, int z) {
        if (descriptor == null || !descriptor.potionInfusion) return null;
        TileEntity tile = world.getTileEntity(x, y, z);
        if (!(tile instanceof TileEntityBrewingStand)) return PREFIX + "note.potion_required";
        TileEntityBrewingStand brewing = (TileEntityBrewingStand)tile;
        boolean potion = false, effects = false;
        for (int slot = 0; slot < Math.min(3, brewing.getSizeInventory()); slot++) {
            ItemStack stack = brewing.getStackInSlot(slot);
            if (stack == null || !(stack.getItem() instanceof ItemPotion)) continue;
            potion = true;
            try {
                List effectList = ((ItemPotion)stack.getItem()).getEffects(stack);
                effects |= effectList != null && !effectList.isEmpty();
            } catch (RuntimeException | LinkageError invalidPotion) {
                // Unknown/modded potion data must not be advertised as an effect-bearing potion.
            }
        }
        if (!potion) return PREFIX + "note.potion_required";
        return descriptor.key.equals("potionInfusion_clear") && !effects ? PREFIX + "note.effect_potion_required" : null;
    }
}
