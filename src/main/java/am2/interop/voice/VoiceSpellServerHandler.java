package am2.interop.voice;

import am2.LogHelper;
import am2.api.spell.ItemSpellBase;
import am2.api.spell.component.interfaces.ISpellShape;
import am2.api.spell.enums.SpellCastResult;
import am2.buffs.BuffList;
import am2.items.ItemSpellBook;
import am2.network.AMDataReader;
import am2.network.AMNetHandler;
import am2.playerextensions.VoiceVaultStorage;
import am2.skills.VocalResonance;
import am2.soulbound.TrueSoulboundHelper;
import am2.spell.SpellHelper;
import am2.spell.SpellUtils;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;

import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Authoritative server-side handler for Vocal Resonance vault updates, instant & channeled voice spell casts,
 * and multiplayer resonance audio-visual feedback.
 */
public final class VoiceSpellServerHandler {

	public static final int VAULT_CAST_MARKER = -2;
	private static final long MIN_CAST_INTERVAL_MS = 450L;
	private static final int CHANNELED_BURST_TICKS = 18; // ~0.9s burst for Beam / Wave / Channel spells

	private static final class ChanneledBurst {
		final UUID playerId;
		final int dimensionId;
		final ItemStack spellStack;
		int ticksElapsed;

		ChanneledBurst(UUID playerId, int dimensionId, ItemStack spellStack) {
			this.playerId = playerId;
			this.dimensionId = dimensionId;
			this.spellStack = spellStack;
			this.ticksElapsed = 0;
		}
	}

	private static final Map<UUID, Long> LAST_CAST_MS = new ConcurrentHashMap<UUID, Long>();
	private static final Map<UUID, ChanneledBurst> ACTIVE_BURSTS = new ConcurrentHashMap<UUID, ChanneledBurst>();
	private static final AtomicBoolean TICKER_REGISTERED = new AtomicBoolean(false);

	public static final class BurstTicker {
		@SubscribeEvent
		public void onServerTick(TickEvent.ServerTickEvent event) {
			if (event.phase != TickEvent.Phase.END || ACTIVE_BURSTS.isEmpty()) {
				return;
			}
			MinecraftServer srv = MinecraftServer.getServer();
			if (srv == null || srv.getConfigurationManager() == null) {
				ACTIVE_BURSTS.clear();
				return;
			}
			Iterator<Map.Entry<UUID, ChanneledBurst>> it = ACTIVE_BURSTS.entrySet().iterator();
			while (it.hasNext()) {
				Map.Entry<UUID, ChanneledBurst> entry = it.next();
				ChanneledBurst burst = entry.getValue();
				EntityPlayerMP player = findOnlinePlayer(srv, burst.playerId);
				if (player == null || player.isDead || player.worldObj == null
						|| player.worldObj.provider.dimensionId != burst.dimensionId
						|| (BuffList.silence != null && player.isPotionActive(BuffList.silence.id))) {
					it.remove();
					continue;
				}
				burst.ticksElapsed++;
				SpellCastResult res = SpellHelper.instance.applyStackStageOnUsing(
						burst.spellStack, player, player,
						player.posX, player.posY, player.posZ,
						player.worldObj, true, true, burst.ticksElapsed);
				if (burst.ticksElapsed >= CHANNELED_BURST_TICKS
						|| (res != SpellCastResult.SUCCESS && res != SpellCastResult.SUCCESS_REDUCE_MANA)) {
					it.remove();
				}
			}
		}

		@SuppressWarnings("unchecked")
		private static EntityPlayerMP findOnlinePlayer(MinecraftServer srv, UUID id) {
			for (Object obj : srv.getConfigurationManager().playerEntityList) {
				if (obj instanceof EntityPlayerMP) {
					EntityPlayerMP p = (EntityPlayerMP) obj;
					if (id.equals(p.getUniqueID())) {
						return p;
					}
				}
			}
			return null;
		}
	}

	private VoiceSpellServerHandler() {
	}

	private static void ensureTickerRegistered() {
		if (TICKER_REGISTERED.compareAndSet(false, true)) {
			FMLCommonHandler.instance().bus().register(new BurstTicker());
		}
	}

	public static void handleVaultAction(byte[] data, EntityPlayerMP player) {
		if (player == null || player.isDead || data == null) {
			return;
		}
		if (!VocalResonance.isUnlocked(player)) {
			player.addChatMessage(new ChatComponentTranslation("am2.vocal_aura.locked"));
			return;
		}
		try {
			AMDataReader rdr = new AMDataReader(data, false);
			int slot = rdr.getInt();
			String word = rdr.getString();
			if (slot < 0 || slot >= VoiceVaultStorage.NUM_SLOTS) {
				return;
			}
			VoiceVaultStorage vault = VoiceVaultStorage.For(player);
			vault.setWord(slot, word);
			vault.syncToClient(player);
		} catch (Throwable t) {
			LogHelper.warn("Malformed VOICE_VAULT_ACTION packet from %s", player.getCommandSenderName());
		}
	}

	public static void handlePacket(byte[] data, EntityPlayerMP player) {
		if (player == null || player.isDead || player.worldObj == null || data == null) {
			return;
		}

		if (!VocalResonance.isUnlocked(player)) {
			player.addChatMessage(new ChatComponentTranslation("am2.vocal_aura.locked"));
			return;
		}

		long now = System.currentTimeMillis();
		UUID id = player.getUniqueID();
		Long previous = LAST_CAST_MS.get(id);
		if (previous != null && (now - previous.longValue()) < MIN_CAST_INTERVAL_MS) {
			return;
		}
		LAST_CAST_MS.put(id, Long.valueOf(now));

		if (BuffList.silence != null && player.isPotionActive(BuffList.silence.id)) {
			player.addChatMessage(new ChatComponentText(
					EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + "Silenciado: sua voz não consegue conjurar magias."));
			return;
		}

		int slotIndex;
		int spellBookSlot;
		String spokenWord;
		try {
			AMDataReader rdr = new AMDataReader(data, false);
			slotIndex = rdr.getInt();
			spellBookSlot = rdr.getInt();
			spokenWord = rdr.getString();
		} catch (Throwable t) {
			LogHelper.warn("Malformed VOICE_SPELL_CAST packet from %s", player.getCommandSenderName());
			return;
		}

		ItemStack spellStack;
		if (spellBookSlot == VAULT_CAST_MARKER) {
			if (slotIndex < 0 || slotIndex >= VoiceVaultStorage.NUM_SLOTS) {
				return;
			}
			ItemStack stored = VoiceVaultStorage.For(player).getStackInSlot(slotIndex);
			spellStack = (stored != null && stored.getItem() instanceof ItemSpellBase) ? stored.copy() : null;
		} else {
			if (slotIndex < 0 || slotIndex >= player.inventory.mainInventory.length) {
				return;
			}
			ItemStack containerStack = player.inventory.mainInventory[slotIndex];
			spellStack = containerStack != null ? resolveInventorySpellStack(containerStack, spellBookSlot, player) : null;
		}

		if (spellStack == null || !(spellStack.getItem() instanceof ItemSpellBase)) {
			return;
		}

		ItemStack classicStack = SpellUtils.instance.constructSpellStack(spellStack);
		ISpellShape shape = SpellUtils.instance.getShapeForStage(classicStack, 0);
		if (shape == null) {
			return;
		}

		SpellCastResult result;
		if (!shape.isChanneled()) {
			result = SpellHelper.instance.applyStackStage(
					spellStack, player, null,
					player.posX, player.posY, player.posZ, 0,
					player.worldObj, true, true, 0);
		} else {
			ensureTickerRegistered();
			result = SpellHelper.instance.applyStackStageOnUsing(
					spellStack, player, player,
					player.posX, player.posY, player.posZ,
					player.worldObj, true, true, 0);
			if (result == SpellCastResult.SUCCESS || result == SpellCastResult.SUCCESS_REDUCE_MANA) {
				ACTIVE_BURSTS.put(id, new ChanneledBurst(id, player.worldObj.provider.dimensionId, spellStack.copy()));
			}
		}

		if (result == SpellCastResult.SUCCESS || result == SpellCastResult.SUCCESS_REDUCE_MANA) {
			player.swingItem();
			player.worldObj.playSoundEffect(player.posX, player.posY + 1.5, player.posZ,
					"arsmagica2:misc.craftingaltar.create_item", 0.35F, 1.55F);
			AMNetHandler.INSTANCE.sendVoiceCastFx(
					player.worldObj.provider.dimensionId,
					player.posX, player.posY + player.getEyeHeight() - 0.15, player.posZ);
		} else if (result == SpellCastResult.NOT_ENOUGH_MANA) {
			player.addChatMessage(new ChatComponentText(
					EnumChatFormatting.AQUA + "[AM2 Voice] " + EnumChatFormatting.RED + "Mana insuficiente para '" + sanitizeWord(spokenWord) + "'."));
		} else if (result == SpellCastResult.REAGENTS_MISSING) {
			player.addChatMessage(new ChatComponentText(
					EnumChatFormatting.AQUA + "[AM2 Voice] " + EnumChatFormatting.RED + "Reagentes ausentes para '" + sanitizeWord(spokenWord) + "'."));
		} else if (result == SpellCastResult.SILENCED) {
			player.addChatMessage(new ChatComponentText(
					EnumChatFormatting.RED + "[AM2 Voice] " + EnumChatFormatting.GRAY + "Você está silenciado."));
		}
	}

	private static ItemStack resolveInventorySpellStack(ItemStack containerStack, int spellBookSlot, EntityPlayerMP player) {
		if (spellBookSlot < 0) {
			return (containerStack.getItem() instanceof ItemSpellBase) ? containerStack : null;
		}
		if (spellBookSlot >= 8 || !(containerStack.getItem() instanceof ItemSpellBook)) {
			return null;
		}
		if (!TrueSoulboundHelper.isOwnerOrUnbound(containerStack, player)) {
			return null;
		}
		ItemStack[] scrolls = ((ItemSpellBook) containerStack.getItem()).getActiveScrollInventory(containerStack);
		if (scrolls == null || spellBookSlot >= scrolls.length) {
			return null;
		}
		ItemStack scroll = scrolls[spellBookSlot];
		return (scroll != null && scroll.getItem() instanceof ItemSpellBase) ? scroll.copy() : null;
	}

	private static String sanitizeWord(String word) {
		if (word == null) return "";
		String trimmed = word.trim();
		return trimmed.length() > 32 ? trimmed.substring(0, 32) : trimmed;
	}
}
