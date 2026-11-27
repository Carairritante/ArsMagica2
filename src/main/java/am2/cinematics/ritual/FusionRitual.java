package am2.cinematics.ritual;

import am2.api.events.WorldFusionEvent;
import am2.cinematics.AMCinematics;
import am2.cinematics.network.Net;
import am2.cinematics.network.RitualReportMessage;
import am2.cinematics.network.RitualReportMessage.Line;
import am2.cinematics.scene.Scene;
import am2.cinematics.server.CinematicServer;
import am2.cinematics.server.SceneStore;
import am2.cinematics.server.WorldFusionTrigger;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * O ritual de World Fusion difícil: agachado, clique no bloco de esmeralda com
 * o Orbe Infinito vermelho. Substitui o ritual do Vínculo de Mana para quem
 * ainda não se fundiu.
 *
 * A oferta sai no começo; a cena para três vezes para os minigames
 * ({@link RitualRun}). Passando por todos, o jogador se funde e ganha a
 * recompensa ({@link FusionReward}); falhando, perde os reagentes (não o Orbe) e
 * fica em Silêncio. Quem já fez o ritual antigo pode fazer este uma vez.
 */
public final class FusionRitual {
    /** A oferta de um ritual em andamento ({@link Offering}). */
    private static final String OFFERING_TAG = "amcinematicsOferta";
    /** Tick do mundo a partir do qual se pode tentar de novo. */
    private static final String RETRY_TAG = "amcinematicsFalhaAte";
    private static final String FAILURE_SCENE = "fusao_falha";
    private static final int SILENCE_TICKS = 20 * 30;
    private static final int ESSENCE_PURE = 10, ESSENCE_HIGH = 11, ESSENCE_BASE = 12;
    /** Valor do AM2 para "qualquer meta" ({@code AMCore.ANY_META}). */
    private static final int ANY_META = 32767;
    private static final int NOTICE_TICKS = 20;
    /** Dois rituais não usam altares mais próximos que isto ao mesmo tempo. */
    private static final int ALTAR_SPACING = 8;

    private final Am2Ritual am2;
    private final FusionReward reward;
    private final int minimumLevel;
    private final Map<UUID, Integer> noticed = new HashMap<UUID, Integer>();
    private final Map<UUID, RitualRun> runs = new HashMap<UUID, RitualRun>();
    private boolean stopping;

    public FusionRitual(FusionReward reward, int minimumLevel) {
        this.am2 = reward.am2;
        this.reward = reward;
        this.minimumLevel = minimumLevel;
    }

    // LOW: proteções de área e a própria cinemática cancelam antes.
    @SubscribeEvent(priority = EventPriority.LOW)
    public void onInteract(PlayerInteractEvent event) {
        if (event.world.isRemote || event.action != PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK
                || !(event.entityPlayer instanceof EntityPlayerMP) || event.entityPlayer instanceof FakePlayer
                || !Activation.matches(event.world, event.entityPlayer, event.x, event.y, event.z)) return;
        // O clique é do ritual: o Orbe não é usado de outro jeito.
        event.setCanceled(true);
        attempt((EntityPlayerMP) event.entityPlayer, event.x, event.y, event.z);
    }

    /** O ritual do Vínculo de Mana deixa de fundir quem ainda não se fundiu. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onOldRitual(WorldFusionEvent event) {
        if (!(event.caster instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.caster;
        if (am2.fused(player)) return;
        event.setCanceled(true);
        event.completed = false;
        am2.resync(player);
        if (mayNotify(player)) tell(player, EnumChatFormatting.LIGHT_PURPLE, "amcinematics.ritual.antigo");
    }

    /** Sair no meio é falhar: o Orbe volta antes de o jogador ser salvo. */
    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        RitualRun run = runs.get(event.player.getUniqueID());
        if (run != null) abandon(run);
    }

    /** Uma oferta sem ritual em andamento só sobra se o servidor caiu: devolve tudo. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.player instanceof EntityPlayerMP && !runs.containsKey(event.player.getUniqueID())) {
            refundLeftover((EntityPlayerMP) event.player);
        }
    }

    /** Evita repetir avisos enquanto o botão ou a magia continuam acionados. */
    private boolean mayNotify(EntityPlayerMP player) {
        Integer last = noticed.get(player.getUniqueID());
        // ticksExisted recomeça quando o jogador renasce ou reconecta.
        if (last != null && player.ticksExisted >= last && player.ticksExisted - last < NOTICE_TICKS) return false;
        noticed.put(player.getUniqueID(), player.ticksExisted);
        return true;
    }

    private void attempt(EntityPlayerMP player, int x, int y, int z) {
        World world = player.worldObj;
        if (runs.containsKey(player.getUniqueID())) return;
        refundLeftover(player);
        // Quem só fez o ritual antigo segue em frente: este é feito uma vez.
        if (reward.completed(player) && am2.fused(player)) {
            if (mayNotify(player)) tell(player, EnumChatFormatting.GRAY, "amcinematics.ritual.feito");
            return;
        }
        long wait = persisted(player).getLong(RETRY_TAG) - now();
        if (wait > 0) {
            if (mayNotify(player)) {
                tell(player, EnumChatFormatting.GRAY, "amcinematics.ritual.silencio", (wait + 19) / 20);
            }
            return;
        }
        for (RitualRun run : runs.values()) {
            if (run.dimension == player.dimension && Math.abs(run.x - x) < ALTAR_SPACING
                    && Math.abs(run.z - z) < ALTAR_SPACING) {
                if (mayNotify(player)) tell(player, EnumChatFormatting.GRAY, "amcinematics.ritual.ocupado");
                return;
            }
        }

        List<Line> missing = new ArrayList<Line>();
        int level = am2.magicLevel(player);
        if (level < minimumLevel) missing.add(Line.text("amcinematics.ritual.falta.nivel", minimumLevel, level));
        if (!am2.beatWaterGuardian(player)) missing.add(Line.text("amcinematics.ritual.falta.agua"));
        if (!am2.beatEarthGuardian(player)) missing.add(Line.text("amcinematics.ritual.falta.terra"));
        if (!world.canBlockSeeTheSky(x, y + 1, z)) missing.add(Line.text("amcinematics.ritual.falta.ceu"));
        int soil = OpenArea.count(ground(world), x, y, z);
        if (soil < OpenArea.NEEDED) {
            missing.add(Line.text("amcinematics.ritual.falta.terreno", soil, OpenArea.NEEDED));
        }
        if (!am2.circleComplete(world, x, y, z)) missing.add(Line.text("amcinematics.ritual.falta.circulo"));
        Map<EntityItem, Integer> offering = new IdentityHashMap<EntityItem, Integer>();
        for (ItemStack lacking : gather(world, x, y, z, offering)) missing.add(Line.item(lacking));

        if (!missing.isEmpty()) {
            if (mayNotify(player)) Net.channel.sendTo(new RitualReportMessage(missing), player);
            return;
        }
        begin(player, x, y, z, offering);
    }

    private void begin(EntityPlayerMP player, int x, int y, int z, Map<EntityItem, Integer> reagents) {
        // A cena é conferida antes de qualquer coisa ser consumida.
        String json;
        Scene scene;
        try {
            json = SceneStore.read(WorldFusionTrigger.SCENE);
            scene = Scene.parse(json.replace("{player}", "x"));
        } catch (IOException e) {
            unavailable(player, e);
            return;
        } catch (IllegalArgumentException e) {
            unavailable(player, e);
            return;
        }
        RitualRun run = new RitualRun(this, player, x, y, z, scene.ritualGates(), false);
        store(player, OFFERING_TAG, Offering.take(am2, player, player.worldObj, x, y, z, reagents,
                !player.capabilities.isCreativeMode));
        runs.put(run.caster, run);
        run.session = CinematicServer.INSTANCE.playRitual(player, json, WorldFusionTrigger.AUDIENCE_BLOCKS,
                altar(run), run);
    }

    /**
     * Treino (comando de operador): a cena do ritual com os minigames, num bloco de
     * altar por perto, sem requisitos, custo, prêmio ou castigo.
     *
     * @throws IllegalArgumentException se não der para começar
     */
    public void practice(EntityPlayerMP player) {
        if (runs.containsKey(player.getUniqueID())) {
            throw new IllegalArgumentException(player.getCommandSenderName() + " já está num ritual");
        }
        String json;
        try {
            json = SceneStore.read(WorldFusionTrigger.SCENE);
        } catch (IOException e) {
            throw new IllegalArgumentException(e.getMessage());
        }
        Scene scene = Scene.parse(json.replace("{player}", "x"));
        double[] at = CinematicServer.findAltar(player,
                scene.altar.isEmpty() ? "minecraft:emerald_block" : scene.altar);
        RitualRun run = new RitualRun(this, player, MathHelper.floor_double(at[0]), MathHelper.floor_double(at[1]),
                MathHelper.floor_double(at[2]), scene.ritualGates(), true);
        runs.put(run.caster, run);
        run.session = CinematicServer.INSTANCE.playRitual(player, json, WorldFusionTrigger.AUDIENCE_BLOCKS, at, run);
    }

    private void unavailable(EntityPlayerMP player, Exception e) {
        AMCinematics.log.error("Cena do ritual indisponível: " + e.getMessage());
        tell(player, EnumChatFormatting.RED, "amcinematics.ritual.cena");
    }

    /** Todos os minigames vencidos e a cena terminada. */
    void succeed(RitualRun run) {
        runs.remove(run.caster);
        EntityPlayerMP player = CinematicServer.find(run.caster);
        if (player == null) return;
        if (run.practice) {
            tell(player, EnumChatFormatting.GREEN, "amcinematics.ritual.treino.passou");
            return;
        }
        NBTTagCompound data = persisted(player);
        data.removeTag(OFFERING_TAG);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        grant(player);
        tell(player, EnumChatFormatting.GREEN, "amcinematics.ritual.sucesso");
        tell(player, EnumChatFormatting.AQUA, "amcinematics.ritual.recompensa");
    }

    /** Funde o jogador e entrega a recompensa (também para o comando de operador). */
    public void grant(EntityPlayer player) {
        am2.setFused(player, true);
        reward.setCompleted(player, true);
        reward.update(player);
    }

    /** Errou um minigame, morreu, saiu do altar ou do jogo. */
    void failed(RitualRun run) {
        runs.remove(run.caster);
        EntityPlayerMP player = CinematicServer.find(run.caster);
        if (player != null && run.practice) {
            tell(player, EnumChatFormatting.RED, "amcinematics.ritual.treino.falhou");
        } else if (player != null) {
            NBTTagCompound data = persisted(player);
            if (data.hasKey(OFFERING_TAG)) {
                // Os reagentes se perdem; o círculo continua montado e o Orbe volta.
                Offering.restoreCircle(data.getCompoundTag(OFFERING_TAG));
                Offering.returnOrb(player, data.getCompoundTag(OFFERING_TAG));
            }
            data.removeTag(OFFERING_TAG);
            data.setLong(RETRY_TAG, now() + SILENCE_TICKS);
            player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
            if (player.isEntityAlive()) am2.silence(player, SILENCE_TICKS);
            tell(player, EnumChatFormatting.RED, "amcinematics.ritual.falhou");
        }
        showFailure(run, player);
    }

    /** Parado sem culpa do jogador (comando ou defeito): nada se perde. */
    void interrupted(RitualRun run) {
        runs.remove(run.caster);
        EntityPlayerMP player = CinematicServer.find(run.caster);
        // Ausente, recebe de volta quando entrar.
        if (player != null && !run.practice) refundLeftover(player);
    }

    private void refundLeftover(EntityPlayerMP player) {
        NBTTagCompound data = persisted(player);
        if (!data.hasKey(OFFERING_TAG)) return;
        Offering.refund(player, data.getCompoundTag(OFFERING_TAG));
        data.removeTag(OFFERING_TAG);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        tell(player, EnumChatFormatting.YELLOW, "amcinematics.ritual.interrompido");
    }

    /** O jogador está saindo (ou o servidor fechando): se ainda não venceu, falha agora. */
    private void abandon(RitualRun run) {
        run.leave();
        CinematicServer.INSTANCE.close(run.session);
        if (runs.containsKey(run.caster)) run.closed(false);
    }

    private void showFailure(RitualRun run, EntityPlayerMP player) {
        if (stopping) return;
        try {
            String json = SceneStore.read(FAILURE_SCENE);
            boolean present = player != null && !run.leaving() && player.isEntityAlive()
                    && player.dimension == run.dimension;
            if (present) {
                CinematicServer.INSTANCE.playOn(player, json, WorldFusionTrigger.AUDIENCE_BLOCKS, false, altar(run));
            } else {
                // Sem o conjurador, a cena toca no altar para quem está perto.
                CinematicServer.INSTANCE.play(run.dimension, run.x + 0.5, run.y, run.z + 0.5, run.yaw,
                        json.replace("{player}", run.name.replaceAll("[^A-Za-z0-9_ ]", "")),
                        WorldFusionTrigger.AUDIENCE_BLOCKS, null);
            }
        } catch (IOException e) {
            AMCinematics.log.error("Cena " + FAILURE_SCENE + " indisponível: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            AMCinematics.log.error("Cena " + FAILURE_SCENE + " inválida: " + e.getMessage());
        }
    }

    private static double[] altar(RitualRun run) {
        return new double[] { run.x + 0.5, run.y, run.z + 0.5 };
    }

    private static long now() {
        return MinecraftServer.getServer().worldServers[0].getTotalWorldTime();
    }

    public int minimumLevel() {
        return minimumLevel;
    }

    /** O que precisa estar no chão (cópias novas a cada chamada); o Orbe é o que está na mão. */
    public ItemStack[] groundReagents() {
        List<ItemStack> required = new ArrayList<ItemStack>();
        for (ItemStack stack : am2.reagents()) required.add(stack.copy());
        required.add(new ItemStack(am2.essence(), 4, ESSENCE_BASE));
        required.add(new ItemStack(am2.essence(), 2, ESSENCE_HIGH));
        required.add(new ItemStack(am2.essence(), 1, ESSENCE_PURE));
        return required.toArray(new ItemStack[required.size()]);
    }

    /**
     * Separa, entre os itens soltos junto do altar (mesma caixa do AM2), os que o
     * ritual vai consumir. Itens a mais ficam onde estão.
     *
     * @param offering preenchido com quanto tirar de cada item solto
     * @return o que falta
     */
    private List<ItemStack> gather(World world, int x, int y, int z, Map<EntityItem, Integer> offering) {
        int r = am2.reagentRadius();
        List<?> dropped = world.getEntitiesWithinAABB(EntityItem.class,
                AxisAlignedBB.getBoundingBox(x - r, y, z - r, x + r + 1, y + 1, z + r + 1));
        List<ItemStack> missing = new ArrayList<ItemStack>();
        for (ItemStack wanted : groundReagents()) {
            int left = wanted.stackSize;
            for (Object o : dropped) {
                EntityItem entity = (EntityItem) o;
                if (left == 0) break;
                if (entity.isDead || !fits(entity.getEntityItem(), wanted)) continue;
                Integer taken = offering.get(entity);
                int take = Math.min(left, entity.getEntityItem().stackSize - (taken == null ? 0 : taken));
                if (take <= 0) continue;
                offering.put(entity, take + (taken == null ? 0 : taken));
                left -= take;
            }
            if (left > 0) {
                ItemStack lacking = wanted.copy();
                lacking.stackSize = left;
                missing.add(lacking);
            }
        }
        return missing;
    }

    private static boolean fits(ItemStack offered, ItemStack wanted) {
        return offered != null && offered.getItem() == wanted.getItem()
                && (offered.getItemDamage() == wanted.getItemDamage() || wanted.getItemDamage() == ANY_META
                        || offered.getItemDamage() == ANY_META)
                && ItemStack.areItemStackTagsEqual(offered, wanted);
    }

    private static OpenArea.Ground ground(final World world) {
        return new OpenArea.Ground() {
            @Override
            public int top(int x, int z) {
                return world.getTopSolidOrLiquidBlock(x, z);
            }

            @Override
            public boolean isSoil(int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                return block == Blocks.dirt || block == Blocks.grass;
            }

            @Override
            public boolean isOpaque(int x, int y, int z) {
                return world.getBlock(x, y, z).isOpaqueCube();
            }

            @Override
            public boolean isFree(int x, int y, int z) {
                Block block = world.getBlock(x, y, z);
                return block == Blocks.air
                        || block.getMaterial() != Material.water && block.getMobilityFlag() == 1;
            }
        };
    }

    private static NBTTagCompound persisted(EntityPlayer player) {
        return player.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }

    private static void store(EntityPlayer player, String key, NBTTagCompound value) {
        NBTTagCompound data = persisted(player);
        data.setTag(key, value);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
    }

    /** Desfaz o ritual de um jogador, para testar de novo (comando de operador). */
    public void forget(EntityPlayer player) {
        am2.setFused(player, false);
        reward.setCompleted(player, false);
        NBTTagCompound data = persisted(player);
        data.removeTag(RETRY_TAG);
        player.getEntityData().setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
        reward.update(player);
    }

    /** Antes de o servidor salvar e fechar: rituais em andamento contam como falha. */
    public void shutdown() {
        stopping = true;
        for (RitualRun run : new ArrayList<RitualRun>(runs.values())) abandon(run);
        runs.clear();
        noticed.clear();
        stopping = false;
    }

    private static void tell(EntityPlayer player, EnumChatFormatting color, String key, Object... args) {
        ChatComponentTranslation text = new ChatComponentTranslation(key, args);
        text.getChatStyle().setColor(color);
        player.addChatMessage(text);
    }
}
