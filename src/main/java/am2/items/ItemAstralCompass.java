package am2.items;

import am2.AMCore;
import am2.compass.AstralStructureLocator;
import am2.compass.StructureTargetInfo;
import am2.network.AMDataWriter;
import am2.network.AMNetHandler;
import am2.network.AMPacketIDs;
import am2.playerextensions.ExtendedProperties;
import am2.texture.ResourceManager;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.StatCollector;
import net.minecraft.world.World;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * End-game arcane divination compass forged via the Astral Divination Ritual.
 * Requires Magic Level >= 50 and consumes 20,000 mana per structure lock.
 */
public class ItemAstralCompass extends ArsMagicaItem {

	public static final int ACT_REQUEST_CATALOG = 0;
	public static final int ACT_SEARCH = 1;
	public static final int ACT_CLEAR = 2;
	public static final int ACT_CANCEL = 3;
	public static final int STATUS_IDLE=0, STATUS_SEARCHING=1, STATUS_FOUND=2,
            STATUS_NOT_FOUND=3, STATUS_CANCELLED=4, STATUS_FAILED=5, STATUS_REJECTED=6, STATUS_TIMEOUT=7;
	public static final class ClientSearchStatus {
        public final int status; public final String id;
        public final int phase, samples, radius;
        ClientSearchStatus(int status,String id) { this(status,id,0,0,AstralStructureLocator.DEFAULT_RADIUS); }
        ClientSearchStatus(int status,String id,int phase,int samples,int radius) { this.status=status;this.id=id;this.phase=phase;this.samples=samples;this.radius=radius; }
    }
	private static volatile ClientSearchStatus clientSearchStatus = new ClientSearchStatus(STATUS_IDLE, "");
	private static volatile int clientSearchProtocol;
	private static volatile java.lang.ref.WeakReference<Object> clientProtocolWorld = new java.lang.ref.WeakReference<Object>(null);

	private static final String TAG_ROOT = "am2AstralCompass";
	private static final String TAG_HAS_TARGET = "hasTarget";
	private static final String TAG_TARGET_ID = "targetId";
	private static final String TAG_TARGET_NAME = "targetName";
	private static final String TAG_TARGET_DIM = "targetDim";
	private static final String TAG_TARGET_X = "targetX";
	private static final String TAG_TARGET_Y = "targetY";
	private static final String TAG_TARGET_Z = "targetZ";
	private static final String TAG_HAS_Y = "hasY";
	private static final String TAG_COOLDOWN_UNTIL = "cooldownUntil";

	private static volatile List<StructureTargetInfo> clientCatalog = AstralStructureLocator.getBuiltinCatalog();

	public ItemAstralCompass() {
		super();
		setMaxStackSize(1);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public void registerIcons(IIconRegister register) {
		this.itemIcon = ResourceManager.RegisterTexture("astral_compass", register);
	}

	@Override
	@SideOnly(Side.CLIENT)
	public boolean hasEffect(ItemStack stack) {
		return hasTarget(stack);
	}

	@Override
	public EnumRarity getRarity(ItemStack stack) {
		return EnumRarity.epic;
	}

	@Override
	public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
		if (!world.isRemote && player instanceof EntityPlayerMP) {
			handleServerAction((EntityPlayerMP)player,player.inventory.currentItem,ACT_REQUEST_CATALOG,"");
		} else if (world.isRemote) {
			AMCore.proxy.openAstralCompassGui(player);
		}
		return stack;
	}

	@Override
	@SideOnly(Side.CLIENT)
	@SuppressWarnings("unchecked")
	public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean adv) {
		list.add(EnumChatFormatting.AQUA + StatCollector.translateToLocalFormatted(
				"am2.astral_compass.tooltip.req",
				AstralStructureLocator.REQUIRED_MAGIC_LEVEL,
				(int) AstralStructureLocator.MANA_COST));
		if (hasTarget(stack)) {
			TargetData target = getTarget(stack);
			if (target != null) {
				list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("am2.astral_compass.gui.source_" + target.source));
				list.add(EnumChatFormatting.GOLD + StatCollector.translateToLocalFormatted(
						"am2.astral_compass.tooltip.target",
						target.name, target.x, target.z, target.dimension));
				if (player != null && player.worldObj != null && player.worldObj.provider.dimensionId == target.dimension) {
					int dist = (int) Math.round(Math.hypot(target.x + 0.5 - player.posX, target.z + 0.5 - player.posZ));
					list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocalFormatted(
							"am2.astral_compass.tooltip.distance", dist));
				}
			}
		} else {
			list.add(EnumChatFormatting.GRAY + StatCollector.translateToLocal("am2.astral_compass.tooltip.idle"));
		}
		list.add(EnumChatFormatting.DARK_AQUA + StatCollector.translateToLocal("am2.astral_compass.tooltip.use"));
	}

	public static final class TargetData {
		public final String id;
		public final String name;
		public final int dimension;
		public final int x;
		public final int y;
		public final int z;
		public final boolean hasY;
		public final String source;

		public TargetData(String id, String name, int dimension, int x, int y, int z, boolean hasY) {
			this(id,name,dimension,x,y,z,hasY,"unknown");
		}

		public TargetData(String id, String name, int dimension, int x, int y, int z, boolean hasY,String source) {
			this.id = id;
			this.name = name;
			this.dimension = dimension;
			this.x = x;
			this.y = y;
			this.z = z;
			this.hasY = hasY;
			this.source = normalizeSource(source);
		}
	}

    public static String normalizeSource(String source) {
        return "recorded".equals(source)||"predicted".equals(source)||"biome".equals(source)||"region".equals(source)?source:"unknown";
    }

	public static boolean hasTarget(ItemStack stack) {
		if (stack == null || !(stack.getItem() instanceof ItemAstralCompass) || !stack.hasTagCompound()) {
			return false;
		}
		NBTTagCompound tag = stack.getTagCompound().getCompoundTag(TAG_ROOT);
		return tag != null && tag.getBoolean(TAG_HAS_TARGET);
	}

	public static TargetData getTarget(ItemStack stack) {
		if (!hasTarget(stack)) return null;
		NBTTagCompound tag = stack.getTagCompound().getCompoundTag(TAG_ROOT);
		return new TargetData(
				tag.getString(TAG_TARGET_ID),
				tag.getString(TAG_TARGET_NAME),
				tag.getInteger(TAG_TARGET_DIM),
				tag.getInteger(TAG_TARGET_X),
				tag.getInteger(TAG_TARGET_Y),
				tag.getInteger(TAG_TARGET_Z),
				tag.getBoolean(TAG_HAS_Y), tag.hasKey("targetSource") ? tag.getString("targetSource") : "unknown");
	}

	public static void setTarget(ItemStack stack, AstralStructureLocator.SearchResult result) {
		if (stack == null || result == null) return;
		NBTTagCompound root = stack.hasTagCompound() ? stack.getTagCompound() : new NBTTagCompound();
		NBTTagCompound tag = root.getCompoundTag(TAG_ROOT);
		tag.setBoolean(TAG_HAS_TARGET, true);
		tag.setString(TAG_TARGET_ID, result.targetId);
		tag.setString(TAG_TARGET_NAME, result.displayName);
		tag.setInteger(TAG_TARGET_DIM, result.dimension);
		tag.setInteger(TAG_TARGET_X, result.x);
		tag.setInteger(TAG_TARGET_Y, result.y);
		tag.setInteger(TAG_TARGET_Z, result.z);
		tag.setBoolean(TAG_HAS_Y, result.hasY);
		tag.setString("targetSource", result.source);
		root.setTag(TAG_ROOT, tag);
		stack.setTagCompound(root);
	}

	public static void clearTarget(ItemStack stack) {
		if (stack == null || !stack.hasTagCompound()) return;
		NBTTagCompound root = stack.getTagCompound();
		if (root.hasKey(TAG_ROOT)) {
			NBTTagCompound tag = root.getCompoundTag(TAG_ROOT);
			tag.setBoolean(TAG_HAS_TARGET, false);
			tag.removeTag(TAG_TARGET_ID);
			tag.removeTag(TAG_TARGET_NAME);
			root.setTag(TAG_ROOT, tag);
		}
	}

	public static void handleServerAction(EntityPlayerMP player, int heldSlot, int action, String targetId) {
        am2.compass.AstralCompassService.enqueue(player,heldSlot,action,targetId);
    }
    public static void handleServerAction(EntityPlayerMP player,int heldSlot,int action,String targetId,int radius,boolean next) {
        am2.compass.AstralCompassService.enqueue(player,heldSlot,action,targetId,radius,next);
    }

    public static void processServerAction(EntityPlayerMP player, int heldSlot, int action, String targetId) {
        processServerAction(player,heldSlot,action,targetId,AstralStructureLocator.DEFAULT_RADIUS,false);
    }
    public static void processServerAction(EntityPlayerMP player,int heldSlot,int action,String targetId,int radius,boolean next) {
		if (player == null || player.isDead || player.worldObj == null) return;
		if (heldSlot < 0 || heldSlot >= 9 || player.inventory.currentItem != heldSlot) return;

		ItemStack stack = player.inventory.getStackInSlot(heldSlot);
		if (stack == null || !(stack.getItem() instanceof ItemAstralCompass)) return;

        long clock=net.minecraft.server.MinecraftServer.getServer().worldServers[0].getTotalWorldTime();
        if(action==ACT_REQUEST_CATALOG){
            long previous=player.getEntityData().getLong("am2AstralCatalogUntil");
            if(clock<previous)return;
            player.getEntityData().setLong("am2AstralCatalogUntil",clock+20);
        }

		if (action == ACT_REQUEST_CATALOG) {
			sendCatalogToClient(player);
			return;
		}

        if (action == ACT_CANCEL) {
            am2.compass.AstralCompassService.cancel(player);
            return;
        }

		if (action == ACT_CLEAR) {
			am2.compass.AstralCompassService.cancel(player);
			clearTarget(stack);
			player.inventory.markDirty();
			player.openContainer.detectAndSendChanges();
			player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.cleared"));
			return;
		}

		if (action == ACT_SEARCH) {
            if(!AstralStructureLocator.validRadius(radius)) {sendSearchStatus(player,STATUS_REJECTED,targetId);return;}
			NBTTagCompound tag = player.getEntityData();
			long now = clock;
			long cooldownUntil = tag.getLong("am2AstralRequestUntil");
			if (now < cooldownUntil) {
				sendSearchStatus(player, STATUS_REJECTED, targetId);
				player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.err_cooldown"));
				return;
			}

			ExtendedProperties props = ExtendedProperties.For(player);
			if (props == null || !AstralStructureLocator.hasRequiredLevel(props.getMagicLevel())) {
				sendSearchStatus(player, STATUS_REJECTED, targetId);
				player.addChatMessage(new ChatComponentTranslation(
						"am2.astral_compass.err_level",
						AstralStructureLocator.REQUIRED_MAGIC_LEVEL,
						props == null ? 0 : props.getMagicLevel()));
				return;
			}

			if (!AstralStructureLocator.hasEnoughMana(props.getCurrentMana(), props.getBonusCurrentMana())) {
				sendSearchStatus(player, STATUS_REJECTED, targetId);
				int available = (int) Math.floor(props.getCurrentMana() + props.getBonusCurrentMana());
				player.addChatMessage(new ChatComponentTranslation(
						"am2.astral_compass.err_mana",
						(int) AstralStructureLocator.MANA_COST,
						available));
				return;
			}

            StructureTargetInfo selected=null;
            for(StructureTargetInfo info:AstralStructureLocator.discoverCatalog(player.worldObj)) if(info.id.equals(targetId)){selected=info;break;}
            if(selected==null){sendSearchStatus(player,STATUS_REJECTED,targetId);return;}
            if(selected.dimensionRestricted && selected.preferredDimension!=player.dimension){
                sendSearchStatus(player,STATUS_REJECTED,targetId);
                player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.err_dimension",selected.preferredDimension));return;
            }
            tag.setLong("am2AstralRequestUntil",now+AstralStructureLocator.COOLDOWN_TICKS);
            TargetData excluded=next?getTarget(stack):null;
            if(next&&(excluded==null||!excluded.id.equals(selected.id)||excluded.dimension!=player.dimension
                    ||AstralStructureLocator.ID_WITCHWOOD_FOREST.equals(selected.id))) {
                sendSearchStatus(player,STATUS_REJECTED,targetId);return;
            }
            am2.compass.AstralCompassService.start(player,stack,selected,radius,excluded);
        }
    }

    public static void finishSearch(EntityPlayerMP player,ItemStack stack,AstralStructureLocator.SearchResult result) {
            ExtendedProperties props=ExtendedProperties.For(player);
            // Mana may have changed while the incremental search ran.
            if(props==null || !AstralStructureLocator.hasRequiredLevel(props.getMagicLevel())
                    || !AstralStructureLocator.hasEnoughMana(props.getCurrentMana(),props.getBonusCurrentMana())){
                sendSearchStatus(player, STATUS_REJECTED, "");
                player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.err_resources_changed"));return;
            }

			if (result == null) {
				sendSearchStatus(player, STATUS_NOT_FOUND, "");
				player.addChatMessage(new ChatComponentTranslation("am2.astral_compass.err_not_found"));
				return;
			}

			props.deductMana(AstralStructureLocator.MANA_COST);
			props.forceSync();
			setTarget(stack, result);
			sendSearchStatus(player, STATUS_FOUND, result.targetId);
			player.inventory.markDirty();
			player.openContainer.detectAndSendChanges();

			int dist = (int) Math.round(Math.hypot(result.x + 0.5 - player.posX, result.z + 0.5 - player.posZ));
			player.worldObj.playSoundAtEntity(player, "mob.endermen.portal", 0.7F, 1.4F);
			player.addChatMessage(new ChatComponentTranslation(
					"am2.astral_compass.locked",
					result.displayName, result.x, result.z, dist));
	}

	public static void sendCatalogToClient(EntityPlayerMP player) {
		if (player == null || player.worldObj == null) return;
		List<StructureTargetInfo> catalog = AstralStructureLocator.discoverCatalog(player.worldObj);
		AMDataWriter writer = new AMDataWriter();
		writer.add(catalog.size());
		for (StructureTargetInfo info : catalog) {
			writer.add(info.id);
			writer.add(info.rawName);
			writer.add(info.category);
			writer.add(info.preferredDimension);
			writer.add(info.dimensionRestricted);
			writer.add(info.glyphIndex);
		}
		// Optional trailer: legacy clients finish after the catalog and ignore this.
		writer.add(0x414d3244).add(player.dimension).add(player.worldObj.provider.getDimensionName()).add(2);
		AMNetHandler.INSTANCE.sendPacketToClientPlayer(player, AMPacketIDs.ASTRAL_COMPASS_ACTION, writer.generate());
	}

    public static void sendSearchStatus(EntityPlayerMP player,int status,String id) {
        if(player != null) AMNetHandler.INSTANCE.sendPacketToClientPlayer(player, AMPacketIDs.ASTRAL_COMPASS_STATUS,
                new AMDataWriter().add(status).add(id == null ? "" : id).generate());
    }
    public static void sendSearchProgress(EntityPlayerMP player,int phase,int samples,int radius) {
        AMNetHandler.INSTANCE.sendPacketToClientPlayer(player,AMPacketIDs.ASTRAL_COMPASS_PROGRESS,
                new AMDataWriter().add(phase).add(samples).add(radius).generate());
    }
    @SideOnly(Side.CLIENT)
    public static ClientSearchStatus getClientSearchStatus() { return clientSearchStatus; }
    @SideOnly(Side.CLIENT)
    public static int getClientSearchProtocol() {
        return clientProtocolWorld.get()==net.minecraft.client.Minecraft.getMinecraft().theWorld?clientSearchProtocol:0;
    }
    @SideOnly(Side.CLIENT)
    public static void setClientSearchProtocol(int version) {
        clientSearchProtocol=Math.max(0,Math.min(2,version));
        clientProtocolWorld=new java.lang.ref.WeakReference<Object>(net.minecraft.client.Minecraft.getMinecraft().theWorld);
    }
    @SideOnly(Side.CLIENT)
    public static void setClientSearchStatus(int status,String id) {
        if(status >= STATUS_IDLE && status <= STATUS_TIMEOUT) clientSearchStatus = new ClientSearchStatus(status,id);
    }
    @SideOnly(Side.CLIENT)
    public static void setClientSearchProgress(int phase,int samples,int radius) {
        ClientSearchStatus old=clientSearchStatus;
        if(old.status==STATUS_SEARCHING&&phase>=0&&phase<=5&&samples>=0&&samples<=10000000&&AstralStructureLocator.validRadius(radius))
            clientSearchStatus=new ClientSearchStatus(old.status,old.id,phase,samples,radius);
    }

	@SideOnly(Side.CLIENT)
	public static void setClientCatalog(List<StructureTargetInfo> catalog) {
		if (catalog == null || catalog.isEmpty()) {
			clientCatalog = AstralStructureLocator.getBuiltinCatalog();
		} else {
			clientCatalog = new ArrayList<StructureTargetInfo>(catalog);
		}
	}

	@SideOnly(Side.CLIENT)
	public static List<StructureTargetInfo> getClientCatalog() {
		return Collections.unmodifiableList(clientCatalog);
	}
}
