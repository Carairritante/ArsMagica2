package am2.armor;

import am2.AMCore;
import am2.LogHelper;
import am2.api.items.armor.ArmorTextureEvent;
import am2.api.items.armor.IArmorImbuement;
import am2.api.items.armor.ImbuementApplicationTypes;
import am2.bosses.EntityAirGuardian;
import am2.bosses.EntityArcaneGuardian;
import am2.bosses.EntityEnderGuardian;
import am2.bosses.EntityNatureGuardian;
import am2.items.ItemBoxOfIllusions;
import am2.items.ItemSoulspike;
import am2.items.ItemsCommonProxy;
import am2.particles.AMParticle;
import am2.particles.ParticleFadeOut;
import am2.playerextensions.ExtendedProperties;
import cpw.mods.fml.common.eventhandler.Event;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import net.minecraft.inventory.IInventory;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.ModifiableAttributeInstance;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.item.ItemTossEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent;
import net.minecraftforge.event.entity.living.LivingEvent.LivingUpdateEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.world.BlockEvent;
import net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka;

import java.util.*;

public class ArmorEventHandler{
	@SubscribeEvent
	public void onEntityLiving(LivingUpdateEvent event){
		if (!(event.entityLiving instanceof EntityPlayer))
			return;

		doInfusions(ImbuementApplicationTypes.ON_TICK, event, (EntityPlayer)event.entityLiving);

		EntityPlayer livingP = (EntityPlayer) event.entityLiving;
		String mob = net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka.playerModelMap.get(livingP.getCommandSenderName());
		if (mob != null && am2.items.renderers.IllusionEntityHelper.isValidMob(mob)) {
			if (!livingP.worldObj.isRemote) {
				am2.items.renderers.IllusionEntityHelper.handleLivingSound(livingP, mob);
			}
			am2.items.renderers.IllusionEntityHelper.applyDisguisePhysics(livingP, mob);
		} else {
			am2.items.renderers.IllusionEntityHelper.cleanupDisguisePhysics(livingP);
		}

		EntityPlayer player = (EntityPlayer)event.entityLiving;
		if (player.inventory.armorInventory[0] != null && player.inventory.armorInventory[0].getItem() == ItemsCommonProxy.archmageBoots){
			ModifiableAttributeInstance instance = (ModifiableAttributeInstance)player.getAttributeMap().getAttributeInstance(SharedMonsterAttributes.movementSpeed);

			ArrayList<AttributeModifier> toRemove = new ArrayList<AttributeModifier>();

			Collection c = instance.func_111122_c();
			ArrayList arraylist = new ArrayList(c);
			Iterator iterator = arraylist.iterator();

			while (iterator.hasNext()){
				AttributeModifier attributemodifier = (AttributeModifier)iterator.next();
				if (attributemodifier.getOperation() == 2 && attributemodifier.getAmount() < 0.0f){
					toRemove.add(attributemodifier);
				}
			}

			for (AttributeModifier modifier : toRemove){
				instance.removeModifier(modifier);
			}

			float speedInAirLast = ReflectionHelper.getPrivateValue(EntityPlayer.class, player, "speedInAir", "field_71102_ce");
			if (speedInAirLast < 0.1F){
				ReflectionHelper.setPrivateValue(EntityPlayer.class, player, 0.1F, "speedInAir", "field_71102_ce");
				setSpeedInAir.put(player, speedInAirLast);
			}

			if (player.worldObj.isRemote) {
				if (AMCore.proxy.utils.isJumpKeyPressed()) {
					player.motionY += 0.135;
				}
			}
		} else {
			if (setSpeedInAir.get(player) != null) {
				ReflectionHelper.setPrivateValue(EntityPlayer.class, player, setSpeedInAir.get(player), "speedInAir", "field_71102_ce");
			}
		}
	}

	private Map<EntityPlayer, Float> setSpeedInAir = new HashMap<EntityPlayer, Float>();

	@SubscribeEvent
	public void onEntityHurt(LivingHurtEvent event){
		if (!(event.entityLiving instanceof EntityPlayer))
			return;

		doInfusions(ImbuementApplicationTypes.ON_HIT, event, (EntityPlayer)event.entityLiving);
		EntityPlayer hurtP = (EntityPlayer) event.entityLiving;
		if (!hurtP.worldObj.isRemote) {
			String mob = net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka.playerModelMap.get(hurtP.getCommandSenderName());
			if (mob != null && am2.items.renderers.IllusionEntityHelper.isValidMob(mob)) {
				am2.items.renderers.IllusionEntityHelper.handleHurtSound(hurtP, mob);
			}
		}

		if (event.entityLiving instanceof EntityPlayer)
			doXPInfusion((EntityPlayer)event.entityLiving, 0.01f, Math.max(0.05f, Math.min(event.ammount, 5)));
	}

	@SubscribeEvent
	public void onEntityJump(LivingJumpEvent event){
		if (!(event.entityLiving instanceof EntityPlayer))
			return;

		doInfusions(ImbuementApplicationTypes.ON_JUMP, event, (EntityPlayer)event.entityLiving);

		EntityPlayer player = (EntityPlayer) event.entityLiving;
		String mob = net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka.playerModelMap.get(player.getCommandSenderName());
		if (mob != null && am2.items.renderers.IllusionEntityHelper.isValidMob(mob)) {
			am2.items.renderers.IllusionEntityHelper.handleJumpSound(player, mob);
		}
	}

	@SubscribeEvent
	public void onEntityFall(LivingFallEvent event){
		if (event.entityLiving instanceof EntityPlayer){
			EntityPlayer player = (EntityPlayer) event.entityLiving;
			String mob = net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka.playerModelMap.get(player.getCommandSenderName());
			if (mob != null && (mob.equalsIgnoreCase("chicken") || mob.equalsIgnoreCase("blaze"))){
				event.setCanceled(true);
				player.fallDistance = 0.0F;
			}
		}
	}

	@SubscribeEvent
	public void onMiningSpeed(BreakSpeed event){
		doInfusions(ImbuementApplicationTypes.ON_MINING_SPEED, event, (EntityPlayer)event.entityPlayer);
	}

	@SubscribeEvent
	public void onEntityDeath(LivingDeathEvent event){
		if (event.source.getSourceOfDamage() instanceof EntityPlayer)
			doXPInfusion((EntityPlayer)event.source.getSourceOfDamage(), 1, Math.min(20, event.entityLiving.getMaxHealth()));

		if (!(event.entityLiving instanceof EntityPlayer))
			return;

		doInfusions(ImbuementApplicationTypes.ON_DEATH, event, (EntityPlayer)event.entityLiving);
		EntityPlayer deadP = (EntityPlayer) event.entityLiving;
		if (!deadP.worldObj.isRemote) {
			String mob = net.tclproject.mysteriumlib.asm.fixes.MysteriumPatchesFixesMagicka.playerModelMap.get(deadP.getCommandSenderName());
			if (mob != null && am2.items.renderers.IllusionEntityHelper.isValidMob(mob)) {
				am2.items.renderers.IllusionEntityHelper.handleDeathSound(deadP, mob);
			}
		}
	}

	private void doInfusions(ImbuementApplicationTypes type, Event event, EntityPlayer player){
		ExtendedProperties props = ExtendedProperties.For(player);

		for (int i = 0; i < 4; ++i){
			IArmorImbuement[] infusions = ArmorHelper.getInfusionsOnArmor(player, i);
			int cd = props.armorProcCooldowns[i];
			for (IArmorImbuement inf : infusions){
				if (inf == null)
					continue;
				if (inf.getApplicationTypes().contains(type)){
					if (cd == 0 || inf.canApplyOnCooldown()){
						if (inf.applyEffect(player, player.worldObj, player.getCurrentArmor(i), type, event)){
							if (inf.getCooldown() > 0){
								if (props.armorProcCooldowns[i] < inf.getCooldown()){
									props.armorProcCooldowns[i] = inf.getCooldown();
									if (player instanceof EntityPlayerMP)
										AMCore.proxy.blackoutArmorPiece((EntityPlayerMP)player, i, inf.getCooldown());
								}
							}
						}
					}
				}
			}
		}
	}

	private void doXPInfusion(EntityPlayer player, float xpMin, float xpMax){
		float amt = (float)((player.worldObj.rand.nextFloat() * xpMin + (xpMax - xpMin)) * AMCore.config.getArmorXPInfusionFactor());
		ArmorHelper.addXPToArmor(amt, player);
	}

	@SubscribeEvent
	public void onArmorTexture(ArmorTextureEvent event){
		if (event.renderIndex == ArmorHelper.getArmorRenderIndex("mage")){
			if (event.slot == 2){
				event.texture = "arsmagica2:textures/models/mage_2.png";
			}else{
				event.texture = "arsmagica2:textures/models/mage_1.png";
			}
		}else if (event.renderIndex == ArmorHelper.getArmorRenderIndex("battlemage")){
			if (event.slot == 2){
				event.texture = "arsmagica2:textures/models/battlemage_2.png";
			}else{
				event.texture = "arsmagica2:textures/models/battlemage_1.png";
			}
		}else if (event.renderIndex == ArmorHelper.getArmorRenderIndex("archmage")){
			if (event.slot == 2){
				event.texture = "arsmagica2:textures/models/archmage_2.png";
			}else{
				event.texture = "arsmagica2:textures/models/archmage_1.png";
			}
		}else if (event.renderIndex == ArmorHelper.getArmorRenderIndex("bound")){
			if (event.slot == 2){
				event.texture = "arsmagica2:textures/models/bound_2.png";
			}else{
				event.texture = "arsmagica2:textures/models/bound_1.png";
			}
		}else if (event.renderIndex == ArmorHelper.getArmorRenderIndex("ender")){
			event.texture = "arsmagica2:textures/models/ender_1.png";
		}else if (event.renderIndex == ArmorHelper.getArmorRenderIndex("magitech")){
			event.texture = "arsmagica2:textures/models/magitech_1.png";
		}
	}



	@SubscribeEvent
	public void onInteract(PlayerInteractEvent event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.entityPlayer)) {
			if(event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK)
				event.setCanceled(true);
			else {
				if(!event.world.blockExists(event.x, event.y, event.z)) {
					return;
				}
				Block block = event.world.getBlock(event.x, event.y, event.z);
				int meta = event.world.getBlockMetadata(event.x, event.y, event.z);
				if(!block.hasTileEntity(meta) || !(event.world.getTileEntity(event.x, event.y, event.z) instanceof IInventory)) {
					event.setCanceled(true);
				}
			}
		}
	}

	@SubscribeEvent
	public void onInteract(BlockEvent.PlaceEvent event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.player)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onItemPickup(EntityItemPickupEvent event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.entityPlayer)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent
	public void onInteract(AttackEntityEvent event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.entityPlayer)) {
			event.setCanceled(true);
		}
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void onPlayerTick(TickEvent.PlayerTickEvent event) {
		if(event.phase == TickEvent.Phase.START) {
			if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.player)) {
				event.player.noClip = true;
				event.player.onGround = false;
				event.player.setInvisible(true);
			}
		}
	}

	@SubscribeEvent
	public void breakSpeed(PlayerEvent.BreakSpeed event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.entityPlayer))
			event.newSpeed = 0;
	}

	@SubscribeEvent(priority = EventPriority.LOWEST)
	public void itemToss(ItemTossEvent event) {
		if(MysteriumPatchesFixesMagicka.isPlayerEthereal(event.player)) {
			event.setCanceled(true);
			ItemStack item = event.entityItem.getEntityItem();
			event.player.inventory.addItemStackToInventory(item);
		}
	}


}
