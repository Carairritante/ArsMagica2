package am2;

import am2.api.math.AMVector2;
import am2.buffs.BuffList;
import am2.playerextensions.ExtendedProperties;
import am2.spell.SkillManager;
import am2.spell.SkillTreeManager;
import am2.utility.EntityUtilities;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraftforge.event.entity.living.LivingEvent;

public class ShrinkHandler{

	@SubscribeEvent
	public void onEntityLiving(LivingEvent event){
		if (!(event instanceof LivingEvent.LivingUpdateEvent) || !(event.entityLiving instanceof EntityPlayer)) return;

		EntityPlayer player = (EntityPlayer) event.entityLiving;

		if (SkillTreeManager.instance.isSkillDisabled(SkillManager.instance.getSkill("Shrink")))
			return;


		ExtendedProperties exProps = null;

		try{
			exProps = ExtendedProperties.For(player);
		}catch (Throwable t){
			return;
		}

		if (exProps.originalSize == null){
			exProps.originalSize = new AMVector2(player.width, player.height);
		}

		boolean shrunk = exProps.getIsShrunk();
		boolean wasResized = exProps.shrinkAmount != 0f;
		double feetY = player.boundingBox.minY;
		double centerX = player.posX, centerZ = player.posZ;
		float previousOffset = player.yOffset;

		if (!player.worldObj.isRemote && shrunk && !player.isPotionActive(BuffList.shrink)){
			exProps.setIsShrunk(false);
			shrunk = false;
		}else if (!player.worldObj.isRemote && !shrunk && player.isPotionActive(BuffList.shrink)){
			exProps.setIsShrunk(true);
			shrunk = true;
		}

		float shrinkPct = exProps.getShrinkPct();
		if (shrunk && shrinkPct < 1f){
			shrinkPct = Math.min(1f, shrinkPct + 0.005f);
		}else if (!shrunk && shrinkPct > 0f){
			shrinkPct = Math.max(0f, shrinkPct - 0.005f);
		}
		exProps.setShrinkPct(shrinkPct);

		if (exProps.getShrinkPct() > 0f){
			if (exProps.shrinkAmount == 0f || //shrink hasn't yet been applied
					exProps.getOriginalSize().x * 0.25 != player.width || //width has changed through other means
					exProps.getOriginalSize().y * 0.25 != player.height){ //height has changed through other means
				exProps.setOriginalSize(new AMVector2(player.width, player.height));
				exProps.shrinkAmount = 0.25f;
				EntityUtilities.setSize(player, player.width * exProps.shrinkAmount, player.height * exProps.shrinkAmount);
				player.eyeHeight = player.getDefaultEyeHeight() * exProps.shrinkAmount;
				player.yOffset = 0.0f;
			}
		}else{
			if (exProps.shrinkAmount != 0f){
				AMVector2 size = ExtendedProperties.For(player).getOriginalSize();
				EntityUtilities.setSize(player, (float)(size.x), (float)(size.y));
				exProps.shrinkAmount = 0f;
				player.eyeHeight = player.getDefaultEyeHeight();
				player.yOffset = 0.0f;
				if (exProps.getIsFlipped()){
					event.entityLiving.moveEntity(0, -1, 0);
					feetY = player.boundingBox.minY;
				}
			}
		}

		if (exProps.getShrinkPct() > 0f || wasResized){
			// Remote players and server players use feet coordinates (zero offset).
			// Only the local client stores its eye offset in posY. Keep the ground
			// anchor and interpolation stable when resizing or changing that offset.
			boolean local = player.worldObj.isRemote && player == AMCore.proxy.getLocalPlayer();
			float offset = local ? (float)exProps.getOriginalSize().y * 0.9f * (1f - 0.75f * exProps.getShrinkPct()) : 0f;
			player.yOffset = offset;
			player.setPosition(centerX, feetY + offset - player.ySize, centerZ);
			double shift = offset - previousOffset;
			player.prevPosY += shift;
			player.lastTickPosY += shift;
		}
	}
}
