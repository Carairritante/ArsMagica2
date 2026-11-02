package am2.ritual;

import am2.AMCore;
import am2.api.blocks.MultiblockStructureDefinition;
import am2.api.blocks.MultiblockStructureDefinition.BlockCoord;
import am2.api.blocks.MultiblockStructureDefinition.BlockDec;
import am2.api.blocks.MultiblockStructureDefinition.StructureGroup;
import am2.api.spell.component.interfaces.IRitualInteraction;
import am2.blocks.BlocksCommonProxy;
import am2.items.ItemRitualArchitect;
import am2.items.ItemsCommonProxy;
import am2.playerextensions.ExtendedProperties;
import am2.playerextensions.SkillData;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import am2.api.SkillTreeEntry;
import am2.utility.InventoryUtilities;
import net.minecraft.block.Block;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.event.world.BlockEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/** Read-only ritual plans shared by the client overlay and the authoritative creative builder. */
public final class RitualArchitect {
    public static final int CORRECT = 0, MISSING = 1, BLOCKED = 2, UNSUPPORTED = 3, UNLOADED = 4;
    private static final String DATA = "am2RitualArchitect";
    private static List<Entry> catalog;
    private static final Map<EntityPlayer, Long> catalogViews = Collections.synchronizedMap(new WeakHashMap<EntityPlayer, Long>());

    private RitualArchitect() {}

    public static final class Entry {
        public final int index;
        public final String id, compendiumId, controller, nameKey, activationKey;
        public final String noteKey;
        public final boolean playground;
        public final PlaygroundRitualCompat.Descriptor compat;
        public final Block centerBlock;
        public final boolean dynamicReagents;

        private Entry(int index, String id, String controller, Block centerBlock, boolean dynamic) {
            this.index = index;
            this.id = this.compendiumId = id;
            this.controller = controller;
            this.centerBlock = centerBlock;
            this.dynamicReagents = dynamic;
            this.playground = false;
            this.compat = null;
            this.noteKey = "";
            this.nameKey = "am2.architect.ritual." + id;
            this.activationKey = "am2.architect.activate." + id;
        }

        public IRitualInteraction interaction() {
            if (compat != null) return compat.interaction;
            Object skill = SkillManager.instance.getSkill(controller);
            return skill instanceof IRitualInteraction ? (IRitualInteraction)skill : null;
        }

        private Entry(int index, PlaygroundRitualCompat.Descriptor descriptor) {
            this.index = index;
            this.id = "pg:" + descriptor.key;
            this.compendiumId = descriptor.compendiumId;
            this.controller = descriptor.controller;
            this.nameKey = descriptor.nameKey;
            this.activationKey = descriptor.activationKey;
            this.noteKey = descriptor.noteKey;
            this.centerBlock = descriptor.centerBlock;
            this.dynamicReagents = false;
            this.playground = true;
            this.compat = descriptor;
        }

        public int orientationCount() {
            if (!playground) return 4;
            int count = 1;
            IRitualInteraction ritual = interaction();
            if (ritual != null) for (int mutex : ritual.getRitualShape().getMutexList())
                count = Math.max(count, nonEmptyGroups(ritual.getRitualShape(), mutex).size());
            return Math.min(4, count);
        }

        public AxisAlignedBB reagentBounds(int x, int y, int z) {
            int radius = Math.max(0, Math.min(16, interaction().getReagentSearchRadius()));
            if (playground) return AxisAlignedBB.getBoundingBox(x - radius - 1, y, z - radius - 1,
                    x + radius + 1, y + 2, z + radius + 1);
            return AxisAlignedBB.getBoundingBox(x - radius, y, z - radius,
                    x + radius + 1, y + 1, z + radius + 1);
        }
    }

    public static synchronized List<Entry> entries() {
        if (catalog == null) {
            List<Entry> list = new ArrayList<Entry>();
            add(list, "world_fusion", "ManaLink", Blocks.emerald_block, false);
            add(list, "purification", "Light", BlocksCommonProxy.obelisk, false);
            add(list, "corruption_prism", "FireDamage", BlocksCommonProxy.obelisk, false);
            add(list, "corruption_obelisk", "AstralDistortion", Blocks.mob_spawner, false);
            add(list, "extend_blind", "Blind", null, false);
            add(list, "extend_charm", "Charm", null, false);
            add(list, "extend_entangle", "Entangle", null, false);
            add(list, "extend_flight", "Flight", null, false);
            add(list, "extend_freeze", "Freeze", null, false);
            add(list, "extend_fury", "Fury", null, false);
            add(list, "extend_gravitywell", "GravityWell", null, false);
            add(list, "extend_haste", "Haste", null, false);
            add(list, "extend_invisibility", "Invisibility", null, false);
            add(list, "extend_leap", "Leap", null, false);
            add(list, "extend_levitation", "Levitate", null, false);
            add(list, "extend_manashield", "ManaShield", null, false);
            add(list, "extend_nightvision", "NightVision", null, false);
            add(list, "extend_shield", "Shield", null, false);
            add(list, "extend_slow", "Slow", null, false);
            add(list, "extend_slowfall", "Slowfall", null, false);
            add(list, "extend_swiftswim", "SwiftSwim", null, false);
            add(list, "extend_truesight", "TrueSight", null, false);
            add(list, "extend_waterbreathing", "WaterBreathing", null, false);
            add(list, "extend_waterygrave", "WateryGrave", null, false);
            add(list, "extend_shrink", "Shrink", null, false);
            add(list, "call_rain", "BanishRain", null, false);
            add(list, "recall_to_gateway", "Recall", null, true);
            add(list, "recall_other", "Recall", null, true);
            add(list, "spatial_fracture", "Blink", BlocksCommonProxy.blackAurem, false);
            add(list, "otherworldly_ritual", "Transplace", BlocksCommonProxy.inertSpawner, false);
            add(list, "extend_curse", "Curse", null, false);
            add(list, "extend_deafen", "Deafen", null, false);
            add(list, "extend_digspeed", "DigSpeed", null, false);
            add(list, "extend_fatigue", "Fatigue", null, false);
            add(list, "extend_hunger", "Hunger", null, false);
            add(list, "extend_poison", "Poison", null, false);
            add(list, "extend_saturation", "Saturation", null, false);
            add(list, "extend_weakness", "Weakness", null, false);
            add(list, "extend_wither", "Wither", null, false);
            add(list, "spawner_extraction_lifetap", "LifeTap", Blocks.mob_spawner, false);
            add(list, "spawner_extraction_rift", "Rift", Blocks.mob_spawner, false);
            for (PlaygroundRitualCompat.Descriptor descriptor : PlaygroundRitualCompat.descriptors())
                list.add(new Entry(list.size(), descriptor));
            catalog = Collections.unmodifiableList(list);
        }
        return catalog;
    }

    private static void add(List<Entry> entries, String id, String controller, Block center, boolean dynamic) {
        entries.add(new Entry(entries.size(), id, controller, center, dynamic));
    }

    public static Entry getEntry(int index) {
        return index >= 0 && index < entries().size() ? entries().get(index) : null;
    }

    public static Entry getEntry(String id) {
        if (id == null || id.length() > 128) return null;
        for (Entry entry : entries()) if (entry.id.equals(id)) return entry;
        return null;
    }

    public static boolean canUse(EntityPlayer player, Entry entry) {
        if (player == null || entry == null || entry.interaction() == null) return false;
        if (player.capabilities.isCreativeMode || !AMCore.config.stagedCompendium()) return true;
        Long view = catalogViews.get(player);
        ItemStack held = player.getHeldItem();
        if (view != null && held != null && held.getItem() instanceof ItemRitualArchitect
                && token(held) == view.longValue()) return true;
        ExtendedProperties properties = ExtendedProperties.For(player);
        if (properties == null) return false;
        if (entry.playground) {
            boolean discovered = unlocked(properties, entry.compendiumId) || unlocked(properties, entry.compat.parentId);
            if (!discovered) return false;
            if (entry.compat.key.startsWith("potionBuff_")) {
                SkillTreeEntry skill = SkillTreeManager.instance.getSkillTreeEntry(SkillManager.instance.getSkill(entry.controller));
                return skill != null && SkillData.For(player).isEntryKnown(skill);
            }
            return true;
        }
        // Native Compendium saves/unlocks the parent chapter and all of its ritual subentries together.
        return (properties.hasCompendiumEntry(entry.id) && !properties.isEntryLocked(entry.id))
                || (properties.hasCompendiumEntry("rituals_overview") && !properties.isEntryLocked("rituals_overview"));
    }

    private static boolean unlocked(ExtendedProperties properties, String id) {
        return id != null && properties.hasCompendiumEntry(id) && !properties.isEntryLocked(id);
    }

    public static void updateView(EntityPlayer player, long token, boolean catalogView) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return;
        if (catalogView && token != 0L) catalogViews.put(player, token);
        else catalogViews.remove(player);
    }

    private static NBTTagCompound read(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(DATA, 10)) return null;
        return stack.getTagCompound().getCompoundTag(DATA);
    }

    private static NBTTagCompound write(ItemStack stack) {
        if (!stack.hasTagCompound()) stack.setTagCompound(new NBTTagCompound());
        if (!stack.getTagCompound().hasKey(DATA, 10)) stack.getTagCompound().setTag(DATA, new NBTTagCompound());
        return stack.getTagCompound().getCompoundTag(DATA);
    }

    public static long token(ItemStack stack) {
        NBTTagCompound data = read(stack);
        return data == null ? 0L : data.getLong("token");
    }

    public static void ensureToken(ItemStack stack) {
        if (token(stack) == 0L) {
            long value;
            do { value = UUID.randomUUID().getLeastSignificantBits(); } while (value == 0L);
            write(stack).setLong("token", value);
        }
    }

    public static Entry selected(ItemStack stack) {
        NBTTagCompound data = read(stack);
        if (data != null && data.hasKey("selectedId", 8)) return getEntry(data.getString("selectedId"));
        return getEntry(data == null ? 0 : data.getInteger("selected"));
    }

    public static int rotation(ItemStack stack) {
        NBTTagCompound data = read(stack);
        return data == null ? 0 : data.getInteger("rotation") & 3;
    }

    public static void select(ItemStack stack, int index, int rotation) {
        if (stack == null || getEntry(index) == null || rotation < 0 || rotation > 3) return;
        NBTTagCompound data = write(stack);
        data.setInteger("selected", index);
        data.setString("selectedId", getEntry(index).id);
        data.setInteger("rotation", rotation);
    }

    public static final class Anchor {
        public final int dimension, x, y, z;
        private Anchor(int dimension, int x, int y, int z) {
            this.dimension = dimension; this.x = x; this.y = y; this.z = z;
        }
    }

    public static Anchor anchor(ItemStack stack) {
        NBTTagCompound data = read(stack);
        if (data == null || !data.getBoolean("anchored")) return null;
        int x = data.getInteger("x"), y = data.getInteger("y"), z = data.getInteger("z");
        return validCoordinate(x, y, z) ? new Anchor(data.getInteger("dimension"), x, y, z) : null;
    }

    public static void setAnchor(ItemStack stack, World world, int x, int y, int z) {
        if (stack == null || world == null || !validCoordinate(x, y, z)) return;
        NBTTagCompound data = write(stack);
        data.setBoolean("anchored", true);
        data.setInteger("dimension", world.provider.dimensionId);
        data.setInteger("x", x); data.setInteger("y", y); data.setInteger("z", z);
    }

    public static void clearAnchor(ItemStack stack) {
        if (stack != null) write(stack).setBoolean("anchored", false);
    }

    private static boolean validCoordinate(int x, int y, int z) {
        return x > -29999980 && x < 29999980 && z > -29999980 && z < 29999980 && y > 0 && y < 256;
    }

    public static final class Cell {
        public final int x, y, z, meta;
        public final Block block;
        private Cell(int x, int y, int z, Block block, int meta) {
            this.x = x; this.y = y; this.z = z; this.block = block; this.meta = meta;
        }

        public int state(World world) {
            if (world == null || !validCoordinate(x, y, z) || !world.blockExists(x, y, z)) return UNLOADED;
            Block current = world.getBlock(x, y, z);
            if (current == block && (meta < 0 || world.getBlockMetadata(x, y, z) == meta)) return CORRECT;
            if (!world.isAirBlock(x, y, z)) return BLOCKED;
            if (!world.isSideSolid(x, y - 1, z, ForgeDirection.UP)) return UNSUPPORTED;
            return MISSING;
        }
    }

    public static List<Cell> plan(ItemStack stack) {
        Anchor anchor = anchor(stack);
        return anchor == null ? Collections.<Cell>emptyList() : plan(selected(stack), rotation(stack), anchor.x, anchor.y, anchor.z);
    }

    private static List<StructureGroup> nonEmptyGroups(MultiblockStructureDefinition definition, int mutex) {
        List<StructureGroup> result = new ArrayList<StructureGroup>();
        for (StructureGroup group : definition.getGroupsForMutex(mutex))
            if (!group.getAllowedBlocks().isEmpty()) result.add(group);
        return result;
    }

    public static List<Cell> plan(Entry entry, int rotation, int x, int y, int z) {
        if (entry == null || entry.interaction() == null) return Collections.emptyList();
        List<Cell> cells = new ArrayList<Cell>();
        Set<String> occupied = new HashSet<String>();
        MultiblockStructureDefinition definition = entry.interaction().getRitualShape();
        if (definition == null) return cells;
        for (int mutex : definition.getMutexList()) {
            List<StructureGroup> alternatives = nonEmptyGroups(definition, mutex);
            if (alternatives.isEmpty()) continue;
            // Addon orientations are the actual alternatives, including mirrored layouts.
            StructureGroup chosen = alternatives.get(entry.playground ? (rotation & 3) % alternatives.size() : 0);
            for (Map.Entry<BlockCoord, ArrayList<BlockDec>> part : chosen.getAllowedBlocks().entrySet()) {
                BlockCoord relative = part.getKey();
                int rx = relative.x, rz = relative.z;
                if (!entry.playground) for (int i = 0; i < (rotation & 3); i++) { int oldX = rx; rx = -rz; rz = oldX; }
                if (part.getValue().isEmpty()) continue;
                BlockDec block = part.getValue().get(0);
                int px = x + rx, py = y + relative.y, pz = z + rz;
                String key = px + ":" + py + ":" + pz;
                if (occupied.add(key)) cells.add(new Cell(px, py, pz, block.getBlock(), block.getMeta()));
            }
        }
        if (entry.centerBlock != null && occupied.add(x + ":" + y + ":" + z))
            cells.add(new Cell(x, y, z, entry.centerBlock, -1));
        Collections.sort(cells, new Comparator<Cell>() {
            public int compare(Cell a, Cell b) {
                if (a.y != b.y) return a.y < b.y ? -1 : 1;
                if (a.z != b.z) return a.z < b.z ? -1 : 1;
                return a.x == b.x ? 0 : a.x < b.x ? -1 : 1;
            }
        });
        return Collections.unmodifiableList(cells);
    }

    public static final class Reagent {
        public final ItemStack stack;
        public final int required;
        public int present;
        private Reagent(ItemStack stack) { this.stack = stack.copy(); this.required = Math.max(1, stack.stackSize); }
    }

    public static final class Status {
        public final List<Cell> cells;
        public final List<Reagent> reagents = new ArrayList<Reagent>();
        public final List<String> warnings = new ArrayList<String>();
        public int correct, missing, blocked, extraItems;
        public boolean structureReady, reagentsReady;
        private Status(List<Cell> cells) { this.cells = cells; }
    }

    public static Status status(World world, ItemStack stack) {
        Status status = new Status(plan(stack));
        Anchor anchor = anchor(stack);
        Entry entry = selected(stack);
        if (world == null || anchor == null || entry == null || entry.interaction() == null
                || anchor.dimension != world.provider.dimensionId) return status;
        for (Cell cell : status.cells) {
            int state = cell.state(world);
            if (state == CORRECT) status.correct++;
            else if (state == MISSING) status.missing++;
            else status.blocked++;
        }
        status.structureReady = !status.cells.isEmpty() && status.correct == status.cells.size();
        ItemStack[] required = entry.interaction().getReagents();
        if (entry.dynamicReagents) {
            // These are combination placeholders, not a prescribed rune color/sequence.
            status.reagents.add(new Reagent(new ItemStack(ItemsCommonProxy.rune,
                    entry.id.equals("recall_other") ? 1 : 3, ItemsCommonProxy.rune.META_BLANK)));
            if (entry.id.equals("recall_other")) status.reagents.add(new Reagent(new ItemStack(ItemsCommonProxy.itemOre, 1, ItemsCommonProxy.itemOre.META_VINTEUMDUST)));
        } else if (required != null) for (ItemStack reagent : required) if (reagent != null) status.reagents.add(new Reagent(reagent));
        AxisAlignedBB bounds = entry.reagentBounds(anchor.x, anchor.y, anchor.z);
        if (!world.checkChunksExist((int)bounds.minX, anchor.y, (int)bounds.minZ, (int)Math.ceil(bounds.maxX), anchor.y, (int)Math.ceil(bounds.maxZ))) return status;
        List<EntityItem> ground = world.getEntitiesWithinAABB(EntityItem.class, bounds);
        // Read-only accounting: never use RitualShapeHelper.checkForRitual, which can split actual stacks.
        for (EntityItem item : ground) {
            if (item == null || item.isDead || item.getEntityItem() == null) continue;
            ItemStack offered = item.getEntityItem();
            int remaining = Math.max(0, offered.stackSize);
            if (entry.dynamicReagents) {
                if (offered.getItem() == ItemsCommonProxy.rune && offered.getItemDamage() >= 0 && offered.getItemDamage() <= 16) {
                    status.reagents.get(0).present += remaining;
                    continue;
                }
                if (entry.id.equals("recall_other") && offered.getItem() == ItemsCommonProxy.itemOre
                        && offered.getItemDamage() == ItemsCommonProxy.itemOre.META_VINTEUMDUST) {
                    status.reagents.get(1).present += remaining;
                    continue;
                }
                status.extraItems += remaining;
                continue;
            }
            for (Reagent reagent : status.reagents) {
                boolean phylactery = entry.playground && entry.compat.filledPhylactery
                        && reagent.stack.getItem() == ItemsCommonProxy.crystalPhylactery;
                if (phylactery && !PlaygroundRitualCompat.validPhylactery(offered)) continue;
                boolean compareNbt = !(entry.playground && entry.compat.filledPhylactery);
                if (InventoryUtilities.compareItemStacks(offered, reagent.stack, true, false, compareNbt, true)) {
                    int used = Math.min(remaining, Math.max(0, reagent.required - reagent.present));
                    reagent.present += used; remaining -= used;
                    if (remaining == 0) break;
                }
            }
            status.extraItems += remaining;
        }
        if (entry.id.equals("recall_to_gateway")) status.extraItems += Math.max(0, status.reagents.get(0).present - 3);
        status.reagentsReady = !entry.dynamicReagents && status.extraItems == 0;
        for (Reagent reagent : status.reagents) status.reagentsReady &= reagent.present >= reagent.required;
        if (entry.playground) {
            if (entry.compat.filledPhylactery) for (Reagent reagent : status.reagents)
                if (reagent.stack.getItem() == ItemsCommonProxy.crystalPhylactery && reagent.present < reagent.required)
                    status.warnings.add("am2.architect.pg.note.filled_phylactery_required");
            String potionWarning = PlaygroundRitualCompat.potionWarningKey(entry.compat, world, anchor.x, anchor.y, anchor.z);
            if (potionWarning != null) status.warnings.add(potionWarning);
            if (entry.compat.activationUnverified) status.warnings.add(entry.noteKey);
            String centerWarning = centerPreparationWarning(entry, world, anchor);
            if (centerWarning != null) status.warnings.add(centerWarning);
            status.reagentsReady &= status.warnings.isEmpty();
        }
        return status;
    }

    private static String centerPreparationWarning(Entry entry, World world, Anchor anchor) {
        if (!entry.playground) return null;
        if (entry.centerBlock == BlocksCommonProxy.liquidEssence) {
            int[][] offsets = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
            ForgeDirection[] faces = {ForgeDirection.EAST, ForgeDirection.WEST, ForgeDirection.SOUTH, ForgeDirection.NORTH};
            for (int i = 0; i < offsets.length; i++) {
                int x = anchor.x + offsets[i][0], z = anchor.z + offsets[i][1];
                if (!world.blockExists(x, anchor.y, z) || !world.isSideSolid(x, anchor.y, z, faces[i]))
                    return "am2.architect.pg.note.liquid_containment_required";
            }
        }
        if (entry.centerBlock == Blocks.fire && world.getBlock(anchor.x, anchor.y - 1, anchor.z) != Blocks.netherrack)
            return "am2.architect.pg.note.fire_support_required";
        return null;
    }

    /** Returns a translation key. This never performs a spell or creates/drops reagents. */
    public static String build(EntityPlayer player, ItemStack stack) {
        if (player == null || player.worldObj == null || player.worldObj.isRemote) return "am2.architect.server_only";
        if (!player.capabilities.isCreativeMode) return "am2.architect.creative_only";
        if (stack == null || !(stack.getItem() instanceof ItemRitualArchitect) || player.getHeldItem() != stack) return "am2.architect.invalid_item";
        World world = player.worldObj;
        Anchor anchor = anchor(stack);
        Entry entry = selected(stack);
        if (anchor == null) return "am2.architect.no_anchor";
        if (anchor.dimension != world.provider.dimensionId) return "am2.architect.wrong_dimension";
        if (player.getDistanceSq(anchor.x + 0.5, anchor.y + 0.5, anchor.z + 0.5) > 1024.0) return "am2.architect.too_far";
        if (!canUse(player, entry)) return "am2.architect.locked";
        List<Cell> cells = plan(stack);
        if (cells.isEmpty() || cells.size() > 128) return "am2.architect.invalid_plan";
        if (world.captureBlockSnapshots || !world.capturedBlockSnapshots.isEmpty()) return "am2.architect.busy";
        String centerWarning = centerPreparationWarning(entry, world, anchor);
        if (centerWarning != null) return centerWarning;
        List<Cell> missing = new ArrayList<Cell>();
        for (Cell cell : cells) {
            int state = cell.state(world);
            if (state == UNLOADED) return "am2.architect.unloaded";
            if (state == BLOCKED) return "am2.architect.obstructed";
            if (state == UNSUPPORTED) return "am2.architect.support";
            if ((cell.block == BlocksCommonProxy.obelisk || cell.block == BlocksCommonProxy.blackAurem)
                    && (!world.blockExists(cell.x, cell.y + 1, cell.z) || cell.y >= 255
                    || !world.isAirBlock(cell.x, cell.y + 1, cell.z))) return "am2.architect.obstructed";
            if (!world.canMineBlock(player, cell.x, cell.y, cell.z) || !player.canPlayerEdit(cell.x, cell.y, cell.z, 1, stack)) return "am2.architect.protected";
            if (state != CORRECT) missing.add(cell);
        }
        if (missing.isEmpty()) return "am2.architect.already_built";
        List<BlockSnapshot> snapshots = new ArrayList<BlockSnapshot>();
        boolean success = false;
        try {
            world.captureBlockSnapshots = true;
            for (Cell cell : missing) {
                int metadata = cell.meta < 0 ? (cell.block == BlocksCommonProxy.wizardChalk ? world.rand.nextInt(16)
                        : cell.block == BlocksCommonProxy.obelisk || cell.block == BlocksCommonProxy.blackAurem ? 2 : 0) : cell.meta;
                if (!world.setBlock(cell.x, cell.y, cell.z, cell.block, metadata, 2)) return "am2.architect.failed";
            }
            world.captureBlockSnapshots = false;
            snapshots.addAll((List<BlockSnapshot>)world.capturedBlockSnapshots.clone());
            world.capturedBlockSnapshots.clear();
            BlockEvent.PlaceEvent event = snapshots.size() == 1
                    ? ForgeEventFactory.onPlayerBlockPlace(player, snapshots.get(0), ForgeDirection.UP)
                    : ForgeEventFactory.onPlayerMultiBlockPlace(player, snapshots, ForgeDirection.UP);
            if (event.isCanceled()) return "am2.architect.protected";
            success = true;
        } finally {
            world.captureBlockSnapshots = false;
            if (!world.capturedBlockSnapshots.isEmpty()) {
                snapshots.addAll((List<BlockSnapshot>)world.capturedBlockSnapshots.clone());
                world.capturedBlockSnapshots.clear();
            }
            if (!success) {
                boolean restoring = world.restoringBlockSnapshots;
                world.restoringBlockSnapshots = true;
                try { for (int i = snapshots.size() - 1; i >= 0; i--) snapshots.get(i).restore(true, false); }
                finally { world.restoringBlockSnapshots = restoring; }
            }
        }
        for (BlockSnapshot snapshot : snapshots) {
            Block block = world.getBlock(snapshot.x, snapshot.y, snapshot.z);
            if (!block.hasTileEntity(world.getBlockMetadata(snapshot.x, snapshot.y, snapshot.z))) block.onBlockAdded(world, snapshot.x, snapshot.y, snapshot.z);
            world.markAndNotifyBlock(snapshot.x, snapshot.y, snapshot.z, null, snapshot.replacedBlock, block, 3);
        }
        return "am2.architect.built";
    }
}
