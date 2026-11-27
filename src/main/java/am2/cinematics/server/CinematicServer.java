package am2.cinematics.server;

import am2.cinematics.network.InputMessage;
import am2.cinematics.network.Net;
import am2.cinematics.network.PlayMessage;
import am2.cinematics.network.StopMessage;
import am2.cinematics.scene.Scene;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.init.Blocks;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;

/**
 * Autoridade das sessões: quem assiste, por quanto tempo e com qual proteção.
 * Todo o estado é tocado apenas na thread do servidor; a rede usa a fila.
 */
public final class CinematicServer {
    public static final CinematicServer INSTANCE = new CinematicServer();

    public static final int DEFAULT_RADIUS = 32;
    public static final int MAX_RADIUS = 50;
    /** Folga para o cliente, que começa alguns ticks depois do servidor. */
    private static final int GRACE_TICKS = 20;
    private static final int LEASH_BLOCKS = 16;
    private static final int ALTAR_RANGE = 8;

    private static final class Session {
        int id, dimension, radius, age, duration;
        double x, y, z;
        float yaw;
        byte[] json;
        /** Entidade do jogador-âncora, ou -1. */
        int subject = -1;
        /** Se preenchido, só este jogador pode assistir (pré-visualização). */
        UUID only;
        final Set<UUID> viewers = new HashSet<UUID>();
        /** Já saíram: não são puxados de volta. */
        final Set<UUID> done = new HashSet<UUID>();
        /** Sessão interativa; nulo nas comuns e depois de encerrada para o controlador. */
        SessionController controller;
        boolean interactive;
    }

    private static final class Leave {
        final UUID player;
        final int session;

        Leave(UUID player, int session) {
            this.player = player;
            this.session = session;
        }
    }

    private static final class Input {
        final UUID player;
        final InputMessage message;

        Input(UUID player, InputMessage message) {
            this.player = player;
            this.message = message;
        }
    }

    private final List<Session> sessions = new ArrayList<Session>();
    private final ConcurrentLinkedQueue<Leave> leaves = new ConcurrentLinkedQueue<Leave>();
    private final ConcurrentLinkedQueue<Input> inputs = new ConcurrentLinkedQueue<Input>();
    private int nextId;

    private CinematicServer() {}

    /**
     * Inicia uma cinemática para quem estiver no raio. Chamar apenas na thread do
     * servidor. Ponto de entrada para magias, rituais e outros mods.
     *
     * @return id da sessão
     * @throws IllegalArgumentException se a cena for inválida
     */
    public int play(int dimension, double x, double y, double z, float yaw, String sceneJson, int radius, UUID only) {
        Scene scene = Scene.parse(sceneJson);
        Session s = new Session();
        s.id = ++nextId;
        s.dimension = dimension;
        s.x = x;
        s.y = y;
        s.z = z;
        s.yaw = yaw;
        s.radius = Math.max(1, Math.min(MAX_RADIUS, radius));
        s.duration = scene.duration;
        s.json = sceneJson.getBytes(Scene.UTF8);
        s.only = only;
        sessions.add(s);
        return s.id;
    }

    /**
     * Inicia uma cinemática ancorada em um jogador. Preenche {@code {player}} com o
     * nome dele e, se a cena pedir, vira cena e jogador para o lado com mais espaço.
     *
     * @param onlyAnchor se verdadeiro, só esse jogador assiste
     */
    public int playOn(EntityPlayerMP anchor, String sceneJson, int radius, boolean onlyAnchor) {
        return playOn(anchor, sceneJson, radius, onlyAnchor, null);
    }

    /**
     * Como {@link #playOn(EntityPlayerMP, String, int, boolean)}, ancorando a cena
     * em um ponto fixo (por exemplo o bloco central de um ritual).
     *
     * @param at âncora {x, y, z}, ou nulo para usar a posição do jogador
     */
    public int playOn(EntityPlayerMP anchor, String sceneJson, int radius, boolean onlyAnchor, double[] at) {
        // Só caracteres de nome de usuário, para não quebrar o JSON.
        String name = anchor.getCommandSenderName().replaceAll("[^A-Za-z0-9_ ]", "");
        String json = sceneJson.replace("{player}", name);
        Scene scene = Scene.parse(json);
        if (at == null && scene.stand != null && !scene.altar.isEmpty()) {
            at = findAltar(anchor, scene.altar);
        }
        float yaw;
        double x = at == null ? anchor.posX : at[0];
        double y = at == null ? anchor.boundingBox.minY : at[1];
        double z = at == null ? anchor.posZ : at[2];
        if (scene.stand != null) {
            // Alinha aos blocos: yaw em múltiplos de 90 e âncora no meio do bloco.
            yaw = Math.round(anchor.rotationYaw / 90) * 90;
            double[] offset = new double[3];
            Scene.rotate(scene.stand, yaw, offset);
            if (at == null) {
                // Sem ponto fixo, o jogador já está no lugar de {@code stand}.
                x = Math.floor(x - offset[0]) + 0.5;
                y -= offset[1];
                z = Math.floor(z - offset[2]) + 0.5;
            }
            if (anchor.ridingEntity != null) anchor.mountEntity(null);
            anchor.playerNetServerHandler.setPlayerLocation(x + offset[0], y + offset[1], z + offset[2], yaw, 10);
        } else {
            yaw = scene.align ? Placement.align(anchor, scene) : anchor.rotationYaw;
        }
        int id = play(anchor.dimension, x, y, z, yaw, json, radius, onlyAnchor ? anchor.getUniqueID() : null);
        sessions.get(sessions.size() - 1).subject = anchor.getEntityId();
        return id;
    }

    /**
     * Cena ancorada no jogador e virada para {@code yaw} (por exemplo, para onde um
     * Guardião caiu). O jogador também vira, para a pose e os efeitos no corpo baterem.
     */
    public int playFacing(EntityPlayerMP anchor, String sceneJson, int radius, float yaw, boolean onlyAnchor) {
        String name = anchor.getCommandSenderName().replaceAll("[^A-Za-z0-9_ ]", "");
        String json = sceneJson.replace("{player}", name);
        if (anchor.ridingEntity != null) anchor.mountEntity(null);
        anchor.playerNetServerHandler.setPlayerLocation(anchor.posX, anchor.posY, anchor.posZ, yaw, 10);
        int id = play(anchor.dimension, anchor.posX, anchor.boundingBox.minY, anchor.posZ, yaw, json, radius,
                onlyAnchor ? anchor.getUniqueID() : null);
        sessions.get(sessions.size() - 1).subject = anchor.getEntityId();
        return id;
    }

    /** Se o jogador está assistindo a alguma cinemática agora. */
    public boolean watching(EntityPlayer player) {
        return isWatching(player.getUniqueID());
    }

    /**
     * Âncora no bloco de altar mais próximo do jogador, como no ritual de verdade.
     *
     * @throws IllegalArgumentException se não houver um por perto
     */
    public static double[] findAltar(EntityPlayerMP player, String name) {
        Object block = Block.blockRegistry.getObject(name);
        // O registro devolve ar para nomes desconhecidos.
        if (block == Blocks.air) throw new IllegalArgumentException("bloco de altar desconhecido: " + name);
        int px = MathHelper.floor_double(player.posX), py = MathHelper.floor_double(player.boundingBox.minY);
        int pz = MathHelper.floor_double(player.posZ);
        double[] best = null;
        double closest = Double.MAX_VALUE;
        for (int x = px - ALTAR_RANGE; x <= px + ALTAR_RANGE; x++) {
            for (int y = Math.max(0, py - 4); y <= py + 3; y++) {
                for (int z = pz - ALTAR_RANGE; z <= pz + ALTAR_RANGE; z++) {
                    double distance = player.getDistanceSq(x + 0.5, y, z + 0.5);
                    if (distance >= closest || !player.worldObj.blockExists(x, y, z)
                            || player.worldObj.getBlock(x, y, z) != block
                            || !player.worldObj.isAirBlock(x, y + 1, z)) continue;
                    closest = distance;
                    best = new double[] { x + 0.5, y, z + 0.5 };
                }
            }
        }
        if (best == null) {
            throw new IllegalArgumentException("coloque um bloco " + name + " (livre em cima) a até "
                    + ALTAR_RANGE + " blocos de você");
        }
        return best;
    }

    /**
     * Inicia a cena de um ritual: para nas pausas, quem manda é o controlador e o
     * conjurador recebe a cena na hora, mesmo que outra o alcançasse.
     *
     * @return id da sessão
     */
    public int playRitual(EntityPlayerMP caster, String sceneJson, int radius, double[] at,
            SessionController controller) {
        int id = playOn(caster, sceneJson, radius, false, at);
        Session s = sessions.get(sessions.size() - 1);
        s.controller = controller;
        s.interactive = true;
        for (Session other : sessions) {
            if (other != s && other.viewers.remove(caster.getUniqueID())) {
                other.done.add(caster.getUniqueID());
                Net.channel.sendTo(new StopMessage(other.id), caster);
            }
        }
        admit(s, caster);
        return id;
    }

    /** Mensagem para quem está assistindo à sessão, menos {@code except} (pode ser nulo). */
    public void sendToViewers(int session, IMessage message, UUID except) {
        for (Session s : sessions) {
            if (s.id != session) continue;
            for (UUID id : s.viewers) {
                EntityPlayerMP player = id.equals(except) ? null : find(id);
                if (player != null) Net.channel.sendTo(message, player);
            }
        }
    }

    /** Encerra uma sessão agora. Não chamar de dentro de {@link SessionController#tick}. */
    public void close(int session) {
        for (Iterator<Session> it = sessions.iterator(); it.hasNext();) {
            Session s = it.next();
            if (s.id != session) continue;
            it.remove();
            stopViewers(s);
            if (s.controller != null) s.controller.closed(false);
            return;
        }
    }

    public int stopAll() {
        List<Session> stopped = new ArrayList<Session>(sessions);
        sessions.clear();
        for (Session s : stopped) {
            stopViewers(s);
            if (s.controller != null) s.controller.closed(false);
        }
        return stopped.size();
    }

    public void reset() {
        sessions.clear();
        leaves.clear();
        inputs.clear();
    }

    public void queueLeave(UUID player, int session) {
        leaves.add(new Leave(player, session));
    }

    public void queueInput(UUID player, InputMessage message) {
        inputs.add(new Input(player, message));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Input input;
        while ((input = inputs.poll()) != null) {
            InputMessage m = input.message;
            for (Session s : sessions) {
                if (s.id == m.session && s.controller != null) s.controller.input(input.player, m.gate, m.key, m.tick);
            }
        }
        Leave leave;
        while ((leave = leaves.poll()) != null) {
            for (Session s : sessions) {
                if (s.id != leave.session || !s.viewers.remove(leave.player)) continue;
                s.done.add(leave.player);
                if (s.controller != null) s.controller.left(leave.player);
            }
        }
        // O encerramento pode criar sessões, então fica para depois da varredura.
        List<SessionController> failed = new ArrayList<SessionController>();
        List<SessionController> completed = new ArrayList<SessionController>();
        for (Iterator<Session> it = sessions.iterator(); it.hasNext();) {
            Session s = it.next();
            SessionController.Step step = s.controller == null ? SessionController.Step.ADVANCE
                    : s.controller.tick(s.age);
            if (step == SessionController.Step.STOP) {
                it.remove();
                stopViewers(s);
                failed.add(s.controller);
                continue;
            }
            if (step == SessionController.Step.ADVANCE) {
                if (++s.age > s.duration + GRACE_TICKS) {
                    it.remove();
                    continue;
                }
                if (s.controller != null && s.age >= s.duration) {
                    completed.add(s.controller);
                    s.controller = null;
                }
            }
            dropInvalidViewers(s);
            if (s.age < s.duration) admitViewers(s);
        }
        for (SessionController controller : failed) controller.closed(false);
        for (SessionController controller : completed) controller.closed(true);
    }

    private void stopViewers(Session s) {
        for (UUID id : s.viewers) {
            EntityPlayerMP player = find(id);
            if (player != null) Net.channel.sendTo(new StopMessage(s.id), player);
        }
        s.viewers.clear();
    }

    private void dropInvalidViewers(Session s) {
        for (Iterator<UUID> it = s.viewers.iterator(); it.hasNext();) {
            UUID id = it.next();
            EntityPlayerMP player = find(id);
            if (player == null) {
                it.remove();
            } else if (!isNear(s, player, s.radius + LEASH_BLOCKS)) {
                it.remove();
                s.done.add(id);
                Net.channel.sendTo(new StopMessage(s.id), player);
            }
        }
    }

    private void admitViewers(Session s) {
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            UUID id = player.getUniqueID();
            if (s.only != null && !s.only.equals(id)) continue;
            // ticksExisted evita enviar antes de o cliente terminar de carregar o mundo.
            if (player.ticksExisted <= 20 || s.done.contains(id) || isWatching(id) || !isNear(s, player, s.radius)) continue;
            admit(s, player);
        }
    }

    private void admit(Session s, EntityPlayerMP player) {
        s.viewers.add(player.getUniqueID());
        player.clearItemInUse();
        // Só fecha contêineres: o pacote de fechar também derrubaria o chat ou o menu de pausa.
        if (player.openContainer != player.inventoryContainer) player.closeScreen();
        PlayMessage message = new PlayMessage(s.id, s.dimension, s.x, s.y, s.z, s.yaw, s.age, s.json, s.subject);
        message.interactive = s.interactive;
        if (s.controller != null) {
            message.gate = s.controller.gate();
            message.practice = s.controller.practice();
        }
        Net.channel.sendTo(message, player);
        if (s.controller != null) s.controller.admitted(player);
    }

    private static boolean isNear(Session s, EntityPlayerMP player, double range) {
        return player.dimension == s.dimension && player.isEntityAlive()
                && player.getDistanceSq(s.x, s.y, s.z) <= range * range;
    }

    private boolean isWatching(UUID id) {
        for (Session s : sessions) {
            if (s.viewers.contains(id)) return true;
        }
        return false;
    }

    public static EntityPlayerMP find(UUID id) {
        for (Object o : MinecraftServer.getServer().getConfigurationManager().playerEntityList) {
            EntityPlayerMP player = (EntityPlayerMP) o;
            if (player.getUniqueID().equals(id)) return player;
        }
        return null;
    }

    private boolean isProtected(Object entity) {
        if (!(entity instanceof EntityPlayerMP)) return false;
        return isWatching(((EntityPlayer) entity).getUniqueID());
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onAttacked(LivingAttackEvent event) {
        // outOfWorld cobre /kill e queda no vazio.
        if (event.source != DamageSource.outOfWorld && isProtected(event.entityLiving)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onViewerAttacks(AttackEntityEvent event) {
        if (isProtected(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onViewerInteracts(PlayerInteractEvent event) {
        if (isProtected(event.entityPlayer)) event.setCanceled(true);
    }

    @SubscribeEvent
    public void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID id = event.player.getUniqueID();
        // Reconectar durante a cena volta a assistir do ponto atual.
        for (Session s : sessions) {
            s.viewers.remove(id);
            s.done.remove(id);
        }
    }
}
