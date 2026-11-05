package am2.items.renderers;

import am2.bosses.EntityArcaneGuardian;
import am2.items.ItemBoxOfIllusions;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.EntityDragon;
import net.minecraft.entity.boss.EntityWither;
import net.minecraft.entity.monster.*;
import net.minecraft.entity.passive.*;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Gerenciador central de entidades simuladas, animacoes continuas e sons dos mobs da Caixa de Ilusoes.
 */
public class IllusionEntityHelper {

    private static final Map<String, EntityLivingBase> cachedEntities = Collections.synchronizedMap(new HashMap<String, EntityLivingBase>());
    private static final Map<String, String> cachedEntityTypes = Collections.synchronizedMap(new HashMap<String, String>());

    private static final Map<String, Long> lastIdleSoundTime = Collections.synchronizedMap(new HashMap<String, Long>());
    private static final Map<String, Long> lastHurtSoundTime = Collections.synchronizedMap(new HashMap<String, Long>());
    private static final Map<String, Long> lastWingSoundTime = Collections.synchronizedMap(new HashMap<String, Long>());

    public static boolean isValidMob(String model) {
        if (model == null || model.isEmpty()) {
            return false;
        }
        if (model.startsWith("maid")) {
            return model.length() == 5 && model.charAt(4) >= '0' && model.charAt(4) <= '7';
        }
        return model.equalsIgnoreCase("spider")
                || model.equalsIgnoreCase("zombie")
                || model.equalsIgnoreCase("witch")
                || model.equalsIgnoreCase("snowman")
                || model.equalsIgnoreCase("creeper")
                || model.equalsIgnoreCase("chicken")
                || model.equalsIgnoreCase("cow")
                || model.equalsIgnoreCase("pig")
                || model.equalsIgnoreCase("sheep")
                || model.equalsIgnoreCase("wolf")
                || model.equalsIgnoreCase("ocelot")
                || model.equalsIgnoreCase("horse")
                || model.equalsIgnoreCase("bat")
                || model.equalsIgnoreCase("squid")
                || model.equalsIgnoreCase("villager")
                || model.equalsIgnoreCase("iron_golem")
                || model.equalsIgnoreCase("skeleton")
                || model.equalsIgnoreCase("enderman")
                || model.equalsIgnoreCase("blaze")
                || model.equalsIgnoreCase("slime")
                || model.equalsIgnoreCase("magma_cube")
                || model.equalsIgnoreCase("ghast")
                || model.equalsIgnoreCase("dragon")
                || model.equalsIgnoreCase("ender_dragon")
                || model.equalsIgnoreCase("wither")
                || model.equalsIgnoreCase("ender");
    }

    public static boolean isMiniatureDragon(String model) {
        return model != null && (model.equalsIgnoreCase("dragon") || model.equalsIgnoreCase("ender_dragon"));
    }

    public static boolean isMiniatureWither(String model) {
        return model != null && model.equalsIgnoreCase("wither");
    }

    public static String getModelForCatalyst(ItemStack stack, EntityPlayer player) {
        if (stack == null || stack.getItem() == null) {
            return null;
        }
        Item item = stack.getItem();
        Item eggItem = Item.getItemFromBlock(Blocks.dragon_egg);
        Item ironBlockItem = Item.getItemFromBlock(Blocks.iron_block);
        Item woolItem = Item.getItemFromBlock(Blocks.wool);
        Item hayItem = Item.getItemFromBlock(Blocks.hay_block);

        // Dragao em miniatura com ovo de dragao
        if (eggItem != null && item == eggItem) {
            return "dragon";
        }
        if (item == Items.nether_star) {
            return "wither";
        }
        if (item == Items.feather) {
            return "chicken";
        }
        if (item == Items.leather) {
            return "cow";
        }
        if (item == Items.porkchop || item == Items.cooked_porkchop || item == Items.carrot) {
            return "pig";
        }
        if (woolItem != null && item == woolItem) {
            return "sheep";
        }
        if (item == Items.bone) {
            return "wolf";
        }
        if (item == Items.arrow) {
            return "skeleton";
        }
        if (item == Items.fish || item == Items.cooked_fished) {
            return "ocelot";
        }
        if (item == Items.saddle || (hayItem != null && item == hayItem)) {
            return "horse";
        }
        if (item == Items.flint) {
            return "bat";
        }
        if (item == Items.dye && stack.getItemDamage() == 0) {
            return "squid";
        }
        if (ironBlockItem != null && item == ironBlockItem) {
            return "iron_golem";
        }
        if (item == Items.book || item == Items.wheat) {
            return "villager";
        }
        if (item == Items.snowball) {
            return "snowman";
        }
        if (item == Items.gunpowder) {
            return "creeper";
        }
        if (item == Items.rotten_flesh) {
            return "zombie";
        }
        if (item == Items.spider_eye) {
            return "spider";
        }
        if (item == Items.redstone) {
            return "witch";
        }
        if (item == Items.ender_pearl) {
            return "enderman";
        }
        if (item == Items.blaze_rod || item == Items.blaze_powder) {
            return "blaze";
        }
        if (item == Items.slime_ball) {
            return "slime";
        }
        if (item == Items.magma_cream) {
            return "magma_cube";
        }
        if (item == Items.ghast_tear) {
            return "ghast";
        }
        if (item == Items.ender_eye) {
            return "ender"; // Guardiao Arcano AM2 mantido intocado
        }
        if (item == Items.emerald) {
            int idx = (player.worldObj != null && player.worldObj.rand != null) ? player.worldObj.rand.nextInt(8) : 0;
            return "maid" + idx;
        }
        return null;
    }

    public static EntityLivingBase getEntityForPlayer(EntityPlayer player, String mobType, World world) {
        if (player == null || mobType == null || world == null) {
            return null;
        }
        String playerName = player.getCommandSenderName();
        EntityLivingBase existing = cachedEntities.get(playerName);
        String existingType = cachedEntityTypes.get(playerName);

        if (existing != null && existingType != null && existingType.equalsIgnoreCase(mobType) && existing.worldObj == world) {
            return existing;
        }

        EntityLivingBase created = createEntityForMob(mobType, world);
        if (created != null) {
            cachedEntities.put(playerName, created);
            cachedEntityTypes.put(playerName, mobType);
        }
        return created;
    }

    public static void clearPlayerCache(String playerName) {
        if (playerName != null) {
            cachedEntities.remove(playerName);
            cachedEntityTypes.remove(playerName);
            lastIdleSoundTime.remove(playerName);
            lastHurtSoundTime.remove(playerName);
            lastWingSoundTime.remove(playerName);
        }
    }

    private static EntityLivingBase createEntityForMob(String mobType, World world) {
        String m = mobType.toLowerCase();
        if (m.equals("chicken")) return new EntityChicken(world);
        if (m.equals("cow")) return new EntityCow(world);
        if (m.equals("pig")) return new EntityPig(world);
        if (m.equals("sheep")) return new EntitySheep(world);
        if (m.equals("wolf")) return new EntityWolf(world);
        if (m.equals("ocelot")) return new EntityOcelot(world);
        if (m.equals("horse")) return new EntityHorse(world);
        if (m.equals("bat")) return new EntityBat(world);
        if (m.equals("squid")) return new EntitySquid(world);
        if (m.equals("villager")) return new EntityVillager(world);
        if (m.equals("iron_golem")) return new EntityIronGolem(world);
        if (m.equals("snowman")) return new EntitySnowman(world);
        if (m.equals("zombie")) return new EntityZombie(world);
        if (m.equals("skeleton")) return new EntitySkeleton(world);
        if (m.equals("spider")) return new EntitySpider(world);
        if (m.equals("creeper")) return new EntityCreeper(world);
        if (m.equals("witch")) return new EntityWitch(world);
        if (m.equals("enderman")) return new EntityEnderman(world);
        if (m.equals("blaze")) return new EntityBlaze(world);
        if (m.equals("slime")) {
            EntitySlime s = new EntitySlime(world);
            s.getDataWatcher().updateObject(16, Byte.valueOf((byte)2));
            return s;
        }
        if (m.equals("magma_cube")) {
            EntityMagmaCube mc = new EntityMagmaCube(world);
            mc.getDataWatcher().updateObject(16, Byte.valueOf((byte)2));
            return mc;
        }
        if (m.equals("ghast")) return new EntityGhast(world);
        if (m.equals("dragon") || m.equals("ender_dragon")) return new EntityDragon(world);
        if (m.equals("wither")) return new EntityWither(world);
        if (m.equals("ender")) return new EntityArcaneGuardian(world);
        return new EntityZombie(world);
    }

    public static void updateEntityAnimation(EntityPlayer player, EntityLivingBase elb, String mobType) {
        if (player == null || elb == null) {
            return;
        }
        ItemBoxOfIllusions.Copy(player, elb);

        boolean inAir = !player.onGround;
        double horizSpeed = Math.sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ);

        if (elb instanceof EntityChicken) {
            EntityChicken chicken = (EntityChicken) elb;
            chicken.field_70888_h = chicken.field_70886_e;
            chicken.field_70884_g = chicken.destPos;

            if (inAir) {
                chicken.destPos = Math.min(1.0F, chicken.destPos + 0.25F);
                chicken.field_70886_e += 0.75F;
            } else {
                chicken.destPos = Math.max(0.0F, chicken.destPos - 0.15F);
                if (horizSpeed > 0.05D) {
                    chicken.field_70886_e += 0.25F;
                } else {
                    chicken.field_70886_e += 0.03F;
                }
            }
        } else if (elb instanceof EntityDragon) {
            EntityDragon dragon = (EntityDragon) elb;
            dragon.prevAnimTime = dragon.animTime;
            float wingSpeed = inAir ? (0.08F + (float) horizSpeed * 0.15F) : 0.03F;
            dragon.animTime += wingSpeed;
        } else if (elb instanceof EntityBat) {
            EntityBat bat = (EntityBat) elb;
            bat.setIsBatHanging(player.isSneaking() && player.onGround);
        } else if (elb instanceof EntityWolf) {
            EntityWolf wolf = (EntityWolf) elb;
            wolf.setSitting(player.isSneaking());
            wolf.setTamed(true);
            float healthRatio = player.getMaxHealth() > 0 ? (player.getHealth() / player.getMaxHealth()) : 1.0F;
            wolf.setHealth(Math.max(1.0F, wolf.getMaxHealth() * healthRatio));
        } else if (elb instanceof EntityIronGolem) {
            EntityIronGolem golem = (EntityIronGolem) elb;
            if (player.isSwingInProgress) {
                golem.setAttackTarget(null);
            }
        } else if (elb instanceof EntitySlime) {
            EntitySlime slime = (EntitySlime) elb;
            slime.prevSquishFactor = slime.squishFactor;
            if (inAir) {
                slime.squishFactor = Math.min(0.6F, slime.squishFactor + 0.1F);
            } else {
                slime.squishFactor = Math.max(0.0F, slime.squishFactor - 0.12F);
            }
        } else if (elb instanceof EntitySquid) {
            EntitySquid squid = (EntitySquid) elb;
            squid.squidPitch = player.rotationPitch;
            squid.prevSquidPitch = player.prevRotationPitch;
            squid.tentacleAngle = MathHelper.sin(player.ticksExisted * 0.18F) * 0.45F;
            squid.lastTentacleAngle = MathHelper.sin((player.ticksExisted - 1) * 0.18F) * 0.45F;
        } else if (elb instanceof EntityHorse) {
            EntityHorse horse = (EntityHorse) elb;
            horse.setHorseSaddled(true);
        } else if (elb instanceof EntitySheep) {
            EntitySheep sheep = (EntitySheep) elb;
            sheep.setSheared(false);
        }
    }

    public static void handleLivingSound(EntityPlayer player, String mob) {
        if (player == null || player.worldObj == null || mob == null) {
            return;
        }
        String sound = getIdleSound(mob);
        if (sound == null) {
            return;
        }
        String name = player.getCommandSenderName();
        long now = System.currentTimeMillis();
        Long last = lastIdleSoundTime.get(name);

        // Cooldown minimo estrito de 30 segundos (garante silencio sem incomodar conversas)
        if (last != null && (now - last < 30000L)) {
            return;
        }

        // Apos 30s, chance moderada (10% a cada tick que cai no teste de segundo)
        if (player.worldObj.rand.nextInt(100) < 10) {
            lastIdleSoundTime.put(name, now);
            float volume = getSoundVolume(mob);
            float pitch = getSoundPitch(mob, player.worldObj.rand.nextFloat());
            player.worldObj.playSoundAtEntity(player, sound, volume, pitch);
        }

        // Bater de asas suave para galinha e dragao em quedas prolongadas
        if (isWingMob(mob) && !player.onGround && player.motionY < -0.25D && player.fallDistance > 0.8F) {
            Long lastWing = lastWingSoundTime.get(name);
            if (lastWing == null || (now - lastWing > 8000L)) {
                lastWingSoundTime.put(name, now);
                if (mob.equalsIgnoreCase("chicken")) {
                    player.worldObj.playSoundAtEntity(player, "mob.chicken.step", 0.45F, 1.3F);
                } else if (isMiniatureDragon(mob)) {
                    player.worldObj.playSoundAtEntity(player, "mob.enderdragon.wings", 0.5F, 1.6F);
                }
            }
        }
    }

    public static void handleHurtSound(EntityPlayer player, String mob) {
        if (player == null || player.worldObj == null || mob == null) {
            return;
        }
        String sound = getHurtSound(mob);
        if (sound == null) {
            return;
        }
        String name = player.getCommandSenderName();
        long now = System.currentTimeMillis();
        Long last = lastHurtSoundTime.get(name);

        if (last != null && (now - last < 500L)) {
            return;
        }
        lastHurtSoundTime.put(name, now);
        float volume = Math.min(0.8F, getSoundVolume(mob) * 1.1F);
        float pitch = getSoundPitch(mob, player.worldObj.rand.nextFloat());
        player.worldObj.playSoundAtEntity(player, sound, volume, pitch);
    }

    public static void handleDeathSound(EntityPlayer player, String mob) {
        if (player == null || player.worldObj == null || mob == null) {
            return;
        }
        String sound = getDeathSound(mob);
        if (sound == null) {
            sound = getHurtSound(mob);
        }
        if (sound != null) {
            float pitch = getSoundPitch(mob, 0.5F);
            player.worldObj.playSoundAtEntity(player, sound, 0.9F, pitch);
        }
    }

    private static boolean isWingMob(String mob) {
        return mob.equalsIgnoreCase("chicken") || isMiniatureDragon(mob) || mob.equalsIgnoreCase("bat");
    }

    private static float getSoundVolume(String mob) {
        if (mob.equalsIgnoreCase("chicken")) return 0.55F;
        if (mob.equalsIgnoreCase("bat")) return 0.50F;
        if (mob.equalsIgnoreCase("ocelot")) return 0.60F;
        if (mob.equalsIgnoreCase("wolf")) return 0.65F;
        if (isMiniatureDragon(mob)) return 0.70F;
        if (isMiniatureWither(mob)) return 0.70F;
        if (mob.equalsIgnoreCase("ghast")) return 0.70F;
        return 0.65F;
    }

    private static float getSoundPitch(String mob, float rand) {
        if (isMiniatureDragon(mob)) {
            // Dragao miniatura: pitch agudo para parecer um dragaozinho/filhote elegante
            return 1.35F + (rand - 0.5F) * 0.15F;
        }
        if (mob.equalsIgnoreCase("chicken")) {
            return 1.0F + (rand - 0.5F) * 0.15F;
        }
        if (mob.equalsIgnoreCase("bat")) {
            return 1.2F + (rand - 0.5F) * 0.2F;
        }
        return 0.95F + (rand - 0.5F) * 0.15F;
    }

    public static String getIdleSound(String mob) {
        if (mob == null) return null;
        String m = mob.toLowerCase();
        if (m.equals("chicken")) return "mob.chicken.say";
        if (m.equals("cow")) return "mob.cow.say";
        if (m.equals("pig")) return "mob.pig.say";
        if (m.equals("sheep")) return "mob.sheep.say";
        if (m.equals("wolf")) return "mob.wolf.bark";
        if (m.equals("ocelot")) return "mob.cat.meow";
        if (m.equals("horse")) return "mob.horse.idle";
        if (m.equals("bat")) return "mob.bat.idle";
        if (m.equals("villager")) return "mob.villager.idle";
        if (m.equals("zombie")) return "mob.zombie.say";
        if (m.equals("skeleton")) return "mob.skeleton.say";
        if (m.equals("spider")) return "mob.spider.say";
        if (m.equals("creeper")) return "mob.creeper.say";
        if (m.equals("witch")) return "mob.witch.idle";
        if (m.equals("enderman")) return "mob.endermen.idle";
        if (m.equals("blaze")) return "mob.blaze.breathe";
        if (m.equals("slime")) return "mob.slime.small";
        if (m.equals("magma_cube")) return "mob.magmacube.small";
        if (m.equals("ghast")) return "mob.ghast.moan";
        if (m.equals("dragon") || m.equals("ender_dragon")) return "mob.enderdragon.growl";
        if (m.equals("wither")) return "mob.wither.idle";
        return null;
    }

    public static String getHurtSound(String mob) {
        if (mob == null) return null;
        String m = mob.toLowerCase();
        if (m.equals("chicken")) return "mob.chicken.hurt";
        if (m.equals("cow")) return "mob.cow.hurt";
        if (m.equals("pig")) return "mob.pig.say";
        if (m.equals("sheep")) return "mob.sheep.say";
        if (m.equals("wolf")) return "mob.wolf.hurt";
        if (m.equals("ocelot")) return "mob.cat.hitt";
        if (m.equals("horse")) return "mob.horse.hit";
        if (m.equals("bat")) return "mob.bat.hurt";
        if (m.equals("villager")) return "mob.villager.hit";
        if (m.equals("iron_golem")) return "mob.irongolem.hit";
        if (m.equals("zombie")) return "mob.zombie.hurt";
        if (m.equals("skeleton")) return "mob.skeleton.hurt";
        if (m.equals("spider")) return "mob.spider.say";
        if (m.equals("witch")) return "mob.witch.hurt";
        if (m.equals("enderman")) return "mob.endermen.hit";
        if (m.equals("blaze")) return "mob.blaze.hit";
        if (m.equals("slime")) return "mob.slime.small";
        if (m.equals("magma_cube")) return "mob.magmacube.small";
        if (m.equals("ghast")) return "mob.ghast.scream";
        if (m.equals("dragon") || m.equals("ender_dragon")) return "mob.enderdragon.hit";
        if (m.equals("wither")) return "mob.wither.hurt";
        return null;
    }

    public static String getDeathSound(String mob) {
        if (mob == null) return null;
        String m = mob.toLowerCase();
        if (m.equals("chicken")) return "mob.chicken.hurt";
        if (m.equals("cow")) return "mob.cow.hurt";
        if (m.equals("pig")) return "mob.pig.death";
        if (m.equals("wolf")) return "mob.wolf.death";
        if (m.equals("horse")) return "mob.horse.death";
        if (m.equals("bat")) return "mob.bat.death";
        if (m.equals("villager")) return "mob.villager.death";
        if (m.equals("iron_golem")) return "mob.irongolem.death";
        if (m.equals("zombie")) return "mob.zombie.death";
        if (m.equals("skeleton")) return "mob.skeleton.death";
        if (m.equals("spider")) return "mob.spider.death";
        if (m.equals("creeper")) return "mob.creeper.death";
        if (m.equals("witch")) return "mob.witch.death";
        if (m.equals("enderman")) return "mob.endermen.death";
        if (m.equals("blaze")) return "mob.blaze.death";
        if (m.equals("slime")) return "mob.slime.small";
        if (m.equals("magma_cube")) return "mob.magmacube.small";
        if (m.equals("ghast")) return "mob.ghast.death";
        if (m.equals("dragon") || m.equals("ender_dragon")) return "mob.enderdragon.end";
        if (m.equals("wither")) return "mob.wither.death";
        return null;
    }
}
