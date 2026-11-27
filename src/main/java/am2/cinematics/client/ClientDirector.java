package am2.cinematics.client;

import am2.cinematics.AMCinematics;
import am2.cinematics.Space;
import am2.cinematics.network.GameMessage;
import am2.cinematics.network.InputMessage;
import am2.cinematics.network.LeaveMessage;
import am2.cinematics.network.Net;
import am2.cinematics.network.PlayMessage;
import am2.cinematics.network.StopMessage;
import am2.cinematics.ritual.Minigame;
import am2.cinematics.scene.Clearance;
import am2.cinematics.scene.Scene;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.InputEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.relauncher.ReflectionHelper;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ConcurrentLinkedQueue;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.client.entity.EntityClientPlayerMP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.client.gui.GuiGameOver;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.settings.GameSettings;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.Entity;
import net.minecraft.entity.effect.EntityLightningBolt;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MouseHelper;
import net.minecraft.util.MovementInput;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.client.IRenderHandler;
import net.minecraftforge.client.event.DrawBlockHighlightEvent;
import net.minecraftforge.client.event.EntityViewRenderEvent;
import net.minecraftforge.client.event.FOVUpdateEvent;
import net.minecraftforge.client.event.GuiOpenEvent;
import net.minecraftforge.client.event.MouseEvent;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

/**
 * Toca uma cinemática neste cliente. Tudo o que é alterado no jogo é guardado em
 * {@link Playback} e devolvido em {@link #end(boolean)}.
 */
public final class ClientDirector {
    /** Quanto tempo uma cena espera o mundo ficar pronto antes de ser descartada. */
    private static final int PENDING_TIMEOUT = 100;
    /** Atraso tolerado para um som ainda tocar (a cena sempre chega alguns ticks depois). */
    private static final int LATE_SOUND_TICKS = 10;
    private static final Object DISCONNECTED = new Object();

    final KeyBinding skipKey = new KeyBinding("key.amcinematics.skip", Keyboard.KEY_BACK, "key.categories.amcinematics");

    private static final class Playback {
        int session, elapsed, lastSoundTick, subject;
        double x, y, z;
        float yaw;
        Scene scene;
        World world;
        EntityClientPlayerMP player;
        CameraEntity camera;
        MovementInput savedInput, idleInput;
        MouseHelper savedMouse, frozenMouse;
        int savedPerspective;
        float userFovScale = 1;
        /** Recuo necessário por tick, calculado sob demanda (negativo = ainda não). */
        double[] blocked;
        final SkyClock sky = new SkyClock();
        /** Nuvens escondidas durante uma vista do alto; guarda o que havia antes. */
        boolean cloudsHidden;
        IRenderHandler savedClouds;
        /** Ritual de verdade: a cena para nas pausas até o minigame ser vencido. */
        boolean interactive, practice;
        List<Scene.Gate> gates;
        int resolved;
        /** Ticks parados no total (o ambiente continua se mexendo) e na pausa atual. */
        int held, holdAge;
        MinigameView game;
        int demo = -1;

        boolean holding() {
            return interactive && resolved < gates.size() && elapsed >= gates.get(resolved).t;
        }

        /** Este jogador faz o ritual: joga os minigames. */
        boolean locked() {
            return interactive && player.getEntityId() == subject;
        }

        /** Só quem faz um ritual de verdade não pode pular. */
        boolean canSkip() {
            return practice || !locked();
        }
    }

    private static final IRenderHandler NO_CLOUDS = new IRenderHandler() {
        @Override
        public void render(float partialTicks, WorldClient world, Minecraft minecraft) {}
    };

    private final Minecraft mc = Minecraft.getMinecraft();
    private final ConcurrentLinkedQueue<Object> inbox = new ConcurrentLinkedQueue<Object>();
    private final Scene.Sample sample = new Scene.Sample();
    private final Scene.Sample probe = new Scene.Sample();
    private final ActorRenderer actors = new ActorRenderer();
    private final BodyPerformer performer = new BodyPerformer();
    private final ModelModSwitch modelMod = new ModelModSwitch();
    private final Random random = new Random();
    private Playback active;
    private PlayMessage pending;
    private int pendingAge;
    /** Mensagens de minigame que chegaram antes de a cena começar. */
    private final List<GameMessage> waiting = new ArrayList<GameMessage>();

    private boolean lookBorrowed;
    private float realYaw, realPitch;
    private Field camRoll, prevCamRoll;
    private boolean rollUnavailable;

    /** Chamado pela thread de rede. */
    void enqueue(Object message) {
        inbox.add(message);
    }

    // ---------------------------------------------------------------- ciclo

    @SubscribeEvent
    public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
        inbox.add(DISCONNECTED);
    }

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.START) return;
        Object message;
        while ((message = inbox.poll()) != null) {
            if (message == DISCONNECTED) {
                pending = null;
                end(false);
            } else if (message instanceof PlayMessage) {
                pending = (PlayMessage) message;
                pendingAge = 0;
                waiting.clear();
            } else if (message instanceof StopMessage) {
                int session = ((StopMessage) message).session;
                if (pending != null && pending.session == session) pending = null;
                if (active != null && active.session == session) end(false);
            } else if (message instanceof GameMessage) {
                GameMessage game = (GameMessage) message;
                if (pending != null && pending.session == game.session) waiting.add(game);
                else apply(game);
            }
        }
        if (pending != null) startPending();
        if (active == null) return;
        if (!isStillValid()) {
            end(true);
            return;
        }
        // Reafirma os bloqueios caso o jogo ou outro mod os tenha trocado.
        active.player.movementInput = active.idleInput;
        mc.gameSettings.thirdPersonView = 0;
        if (!mc.isGamePaused()) {
            playDueEvents();
            spawnParticles();
            if (active.holding()) {
                hold();
            } else {
                active.holdAge = 0;
                if (++active.elapsed >= active.scene.duration) end(false);
            }
        }
    }

    // ---------------------------------------------------------------- minigames

    private void hold() {
        active.held++;
        active.holdAge++;
        MinigameView game = active.game;
        if (game == null) return;
        game.tick();
        int demo = game.game.demo(game.age);
        if (demo >= 0 && demo != active.demo) sound("note.harp", new float[] { 1.19F, 1.0F, 0.84F, 0.94F }[demo]);
        active.demo = demo;
        if (game.cleared()) {
            active.resolved = game.gate + 1;
            active.game = null;
        }
    }

    private void apply(GameMessage message) {
        if (active == null || active.session != message.session || !active.interactive) return;
        if (message.kind == GameMessage.START) {
            Minigame.Type[] types = Minigame.Type.values();
            if (message.gate < 0 || message.gate >= active.gates.size() || message.type < 0
                    || message.type >= types.length) return;
            active.resolved = message.gate;
            active.elapsed = active.gates.get(message.gate).t;
            Entity caster = active.world.getEntityByID(active.subject);
            active.game = new MinigameView(new Minigame(types[message.type], message.seed), message.gate,
                    message.age, active.locked(), caster == null ? "?" : caster.getCommandSenderName());
            active.demo = -1;
        } else if (active.game != null && active.game.gate == message.gate) {
            if (message.kind == GameMessage.INPUT) {
                if (!active.game.mine) feedback(active.game.press(message.key, message.tick));
            } else {
                active.game.end(message.passed);
                if (message.passed) sound("random.levelup", 1.4F);
            }
        } else if (message.kind == GameMessage.END && message.passed) {
            active.resolved = Math.max(active.resolved, message.gate + 1);
        }
    }

    /** Tecla ou botão do mouse de quem está jogando. */
    private void playKey(int code) {
        MinigameView game = active.game;
        if (game == null || game.over()) return;
        GameSettings settings = mc.gameSettings;
        int key = -1;
        if (game.game.type != Minigame.Type.CANDLES) {
            if (code == settings.keyBindJump.getKeyCode() || code == Keyboard.KEY_SPACE) key = Minigame.ACTION;
        } else if (code == settings.keyBindForward.getKeyCode() || code == Keyboard.KEY_UP) {
            key = Minigame.UP;
        } else if (code == settings.keyBindRight.getKeyCode() || code == Keyboard.KEY_RIGHT) {
            key = Minigame.RIGHT;
        } else if (code == settings.keyBindBack.getKeyCode() || code == Keyboard.KEY_DOWN) {
            key = Minigame.DOWN;
        } else if (code == settings.keyBindLeft.getKeyCode() || code == Keyboard.KEY_LEFT) {
            key = Minigame.LEFT;
        }
        int tick = game.playTick();
        if (key < 0 || tick < 0) return;
        int result = game.press(key, tick);
        if (result == Minigame.PENDING) return;
        Net.channel.sendToServer(new InputMessage(active.session, game.gate, key, tick));
        feedback(result);
    }

    private void feedback(int result) {
        Minigame game = active.game.game;
        if (result == Minigame.MISS) {
            sound("note.bass", 0.6F);
        } else if (result != Minigame.HIT) {
            return;
        } else if (game.type == Minigame.Type.CANDLES) {
            sound("note.pling", 0.7F + 0.2F * game.progress());
        } else if (game.type == Minigame.Type.PULSE) {
            sound("note.bd", 0.7F);
            sound("random.orb", 0.6F);
        } else {
            // O acerto chama um raio de verdade na frente do altar.
            Scene.Event bolt = new Scene.Event();
            bolt.pos[0] = jitter(14);
            bolt.pos[2] = 12 + jitter(6);
            strike(bolt);
            sound("ambient.weather.thunder", 1.1F);
        }
    }

    private void sound(String name, float pitch) {
        mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation(name), pitch));
    }

    private void startPending() {
        if (mc.theWorld != null && mc.thePlayer != null && mc.thePlayer.dimension == pending.dimension
                && mc.thePlayer.getHealth() > 0) {
            PlayMessage message = pending;
            pending = null;
            start(message, message.elapsed + pendingAge);
            for (GameMessage game : waiting) apply(game);
            waiting.clear();
        } else if (++pendingAge > PENDING_TIMEOUT) {
            Net.channel.sendToServer(new LeaveMessage(pending.session));
            pending = null;
        }
    }

    private void start(PlayMessage message, int elapsed) {
        end(false);
        Playback p = new Playback();
        try {
            p.scene = Scene.parse(new String(message.json, Scene.UTF8));
            if (elapsed < 0 || elapsed >= p.scene.duration) return;
            p.camera = new CameraEntity(mc.theWorld);
        } catch (RuntimeException e) {
            AMCinematics.log.error("Cinemática recusada: " + e);
            Net.channel.sendToServer(new LeaveMessage(message.session));
            return;
        }
        p.session = message.session;
        p.subject = message.subject;
        p.interactive = message.interactive;
        p.practice = message.practice;
        p.gates = p.scene.ritualGates();
        p.resolved = Math.max(0, message.gate);
        // Nunca passa de uma pausa por resolver, mesmo que o mundo tenha demorado a carregar.
        if (p.interactive && p.resolved < p.gates.size()) elapsed = Math.min(elapsed, p.gates.get(p.resolved).t);
        p.elapsed = elapsed;
        p.blocked = new double[p.scene.duration + 1];
        Arrays.fill(p.blocked, -1);
        p.lastSoundTick = Math.max(-1, elapsed - LATE_SOUND_TICKS - 1);
        p.x = message.x;
        p.y = message.y;
        p.z = message.z;
        p.yaw = message.yaw;
        p.world = mc.theWorld;
        p.player = mc.thePlayer;
        p.sky.remember(p.world, elapsed);
        actors.prepare(p.world, p.y);
        if (p.scene.looksFromAbove()) {
            p.savedClouds = p.world.provider.getCloudRenderer();
            p.world.provider.setCloudRenderer(NO_CLOUDS);
            p.cloudsHidden = true;
        }
        if (mc.currentScreen instanceof GuiContainer) p.player.closeScreen();
        double fromAnchorX = p.player.posX - p.x, fromAnchorZ = p.player.posZ - p.z;
        if ((p.scene.align || p.scene.stand != null) && p.player.getEntityId() == p.subject
                && fromAnchorX * fromAnchorX + fromAnchorZ * fromAnchorZ < 1) {
            // O servidor virou o jogador-âncora; sem isto o corpo demora a acompanhar a cabeça.
            p.player.renderYawOffset = p.player.prevRenderYawOffset = p.yaw;
            p.player.rotationYawHead = p.player.prevRotationYawHead = p.yaw;
        }

        p.savedInput = p.player.movementInput;
        p.savedMouse = mc.mouseHelper;
        p.savedPerspective = mc.gameSettings.thirdPersonView;
        p.idleInput = new MovementInput();
        p.frozenMouse = new FrozenMouse();

        active = p;
        KeyBinding.unPressAllKeys();
        p.player.clearItemInUse();
        mc.playerController.resetBlockRemoving();
        p.player.movementInput = p.idleInput;
        mc.mouseHelper = p.frozenMouse;
        mc.gameSettings.thirdPersonView = 0;
        try {
            pose(p, p.elapsed);
            mc.renderViewEntity = p.camera;
        } catch (RuntimeException e) {
            AMCinematics.log.error("Cinemática interrompida por erro", e);
            end(true);
        }
    }

    private boolean isStillValid() {
        return mc.theWorld == active.world && mc.thePlayer == active.player && active.player.getHealth() > 0
                && mc.renderViewEntity == active.camera;
    }

    /** Devolve o jogo ao jogador. Seguro chamar a qualquer momento. */
    private void end(boolean tellServer) {
        Playback p = active;
        if (p == null) return;
        active = null;
        returnLook();
        performer.restore();
        modelMod.restore();
        // Se outro código já trocou a câmera (ex.: descarregar o mundo), respeita.
        if (mc.renderViewEntity == p.camera) mc.renderViewEntity = mc.thePlayer;
        if (p.player.movementInput == p.idleInput) p.player.movementInput = p.savedInput;
        if (mc.mouseHelper == p.frozenMouse) mc.mouseHelper = p.savedMouse;
        mc.gameSettings.thirdPersonView = p.savedPerspective;
        setRoll(0);
        if (mc.theWorld == p.world) p.sky.restore(p.world, p.elapsed);
        if (p.cloudsHidden) p.world.provider.setCloudRenderer(p.savedClouds);
        KeyBinding.unPressAllKeys();
        if (tellServer && mc.getNetHandler() != null && mc.theWorld != null) {
            Net.channel.sendToServer(new LeaveMessage(p.session));
        }
    }

    /** Sons e raios que acontecem em um instante exato. */
    private void playDueEvents() {
        for (Scene.Event e : active.scene.events) {
            if (e.t <= active.lastSoundTick || e.t > active.elapsed) continue;
            if (e.type == Scene.Type.SOUND) {
                mc.getSoundHandler().playSound(PositionedSoundRecord.func_147674_a(new ResourceLocation(e.sound), (float) e.pitch));
            } else if (e.type == Scene.Type.LIGHTNING) {
                strike(e);
            }
        }
        active.lastSoundTick = active.elapsed;
    }

    /** Raio só visual: criado no cliente, não incendeia nem machuca. */
    private void strike(Scene.Event e) {
        double[] offset = new double[3];
        Scene.rotate(e.pos, active.yaw, offset);
        int x = MathHelper.floor_double(active.x + offset[0]), z = MathHelper.floor_double(active.z + offset[2]);
        int y = active.world.getPrecipitationHeight(x, z);
        if (y <= 0) y = MathHelper.floor_double(active.y);
        EntityLightningBolt bolt = new EntityLightningBolt(active.world, x + 0.5, y, z + 0.5);
        // Sem isto os raios distantes não seriam desenhados.
        bolt.renderDistanceWeight = 12;
        active.world.addWeatherEffect(bolt);
    }

    // Vista do alto: afasta a neblina para o terreno continuar visível.
    @SubscribeEvent
    public void onFogDistance(EntityViewRenderEvent.RenderFogEvent event) {
        if (active == null || !active.scene.looksFromAbove() || event.fogMode < 0) return;
        double high = Math.max(0, Math.min(1, (active.camera.posY - active.y - 12) / 30));
        if (high <= 0) return;
        GL11.glFogf(GL11.GL_FOG_START, (float) (event.farPlaneDistance * (0.75 + 0.5 * high)));
        GL11.glFogf(GL11.GL_FOG_END, (float) (event.farPlaneDistance * (1 + 0.9 * high)));
    }

    private void spawnParticles() {
        double[] local = new double[3], motion = new double[3], offset = new double[3], speed = new double[3];
        for (Scene.Event e : active.scene.events) {
            if (e.type != Scene.Type.PARTICLES || active.elapsed < e.t || active.elapsed >= e.t + e.duration) continue;
            for (int i = 0; i < e.count; i++) {
                for (int axis = 0; axis < 3; axis++) {
                    local[axis] = e.pos[axis] + jitter(e.spread[axis]);
                    motion[axis] = e.velocity[axis] + jitter(e.velocitySpread[axis]);
                }
                Scene.rotate(local, active.yaw, offset);
                Scene.rotate(motion, active.yaw, speed);
                // Nomes desconhecidos são ignorados pelo próprio jogo.
                active.world.spawnParticle(e.particle, active.x + offset[0], active.y + offset[1],
                        active.z + offset[2], speed[0], speed[1], speed[2]);
            }
        }
    }

    private double jitter(double range) {
        return (random.nextDouble() * 2 - 1) * range;
    }

    @SubscribeEvent
    public void onRenderWorld(RenderWorldLastEvent event) {
        if (active == null) return;
        try {
            int flare = active.game == null ? -1 : active.game.lit();
            actors.draw(mc, active.scene, time(event.partialTicks), animation(event.partialTicks), flare, active.x,
                    active.y, active.z, active.yaw);
        } catch (RuntimeException e) {
            AMCinematics.log.error("Cinemática interrompida por erro", e);
            end(true);
        }
    }

    // ---------------------------------------------------------------- corpo

    private boolean isSubject(EntityPlayer player) {
        return active != null && player.getEntityId() == active.subject;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerBefore(RenderPlayerEvent.Pre event) {
        // Se o Post anterior não veio, não deixa o próximo jogador sem o modelo dele.
        modelMod.restore();
        if (!isSubject(event.entityPlayer)) return;
        try {
            // O Futura3D desenha depois deste evento: aqui ainda dá para usar o dublê.
            if (active.scene.actsOnBody()) modelMod.suspend();
            performer.before(active.scene, time(event.partialRenderTick), event.entityPlayer, event.renderer,
                    active.yaw);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerGear(RenderPlayerEvent.Specials.Pre event) {
        if (!isSubject(event.entityPlayer) || !active.scene.hideGear) return;
        event.renderItem = false;
        event.renderHelmet = false;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onPlayerArmor(RenderPlayerEvent.SetArmorModel event) {
        // 0 faz o renderizador pular a peça; nada muda no inventário.
        if (isSubject(event.entityPlayer) && active.scene.hideGear) event.result = 0;
    }

    @SubscribeEvent
    public void onPlayerDrawn(RenderPlayerEvent.Specials.Post event) {
        if (!isSubject(event.entityPlayer)) return;
        try {
            performer.during(mc, active.scene, time(event.partialRenderTick), animation(event.partialRenderTick),
                    event.entityPlayer, event.renderer);
        } catch (RuntimeException e) {
            fail(e);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAfter(RenderPlayerEvent.Post event) {
        performer.land();
        performer.restore();
        modelMod.restore();
    }

    private void fail(RuntimeException e) {
        AMCinematics.log.error("Cinemática interrompida por erro", e);
        end(true);
    }

    // ---------------------------------------------------------------- câmera

    @SubscribeEvent
    public void onRenderTick(TickEvent.RenderTickEvent event) {
        if (active == null) {
            returnLook();
            return;
        }
        try {
            if (event.phase == TickEvent.Phase.START) {
                if (!isStillValid()) {
                    end(true);
                    return;
                }
                mc.gameSettings.thirdPersonView = 0;
                pose(active, time(event.renderTickTime));
                active.sky.apply(active.world, active.scene, time(event.renderTickTime), active.elapsed);
                borrowLook(active.camera);
            } else {
                returnLook();
                // Rede de segurança caso o evento Post do jogador não tenha vindo.
                performer.restore();
                modelMod.restore();
                if (mc.currentScreen == null) {
                    CinematicOverlay.draw(mc, active.scene, time(event.renderTickTime), active.canSkip()
                            ? GameSettings.getKeyDisplayString(skipKey.getKeyCode()) : null);
                    if (active.game != null) MinigameOverlay.draw(mc, active.game, event.renderTickTime);
                }
            }
        } catch (RuntimeException e) {
            AMCinematics.log.error("Cinemática interrompida por erro", e);
            end(true);
        }
    }

    /** Posição na linha do tempo; parada durante uma pausa. */
    private double time(float partial) {
        if (active.holding()) return active.gates.get(active.resolved).t;
        return Math.min(active.scene.duration, active.elapsed + (double) partial);
    }

    /** Relógio do que se mexe sozinho (chamas, chuva, batidas): não para nas pausas. */
    private double animation(float partial) {
        return time(partial) + active.held + (active.holding() ? partial : 0);
    }

    private void pose(Playback p, double time) {
        p.scene.sample(time, sample);
        Vec3 pos = toWorld(p, sample.pos);
        Vec3 look = toWorld(p, sample.look);
        if (p.scene.collide) pos = lerp(pos, look, pullAt(p, time));

        double dx = look.xCoord - pos.xCoord, dy = look.yCoord - pos.yCoord, dz = look.zCoord - pos.zCoord;
        double yaw = Math.toDegrees(Math.atan2(-dx, dz));
        double pitch = -Math.toDegrees(Math.atan2(dy, Math.sqrt(dx * dx + dz * dz)));
        double roll = sample.roll;

        for (Scene.Event e : p.scene.events) {
            if (e.type != Scene.Type.SHAKE || time < e.t || time >= e.t + e.duration) continue;
            // Tremor determinístico (igual para todos), enfraquecendo até o fim.
            double power = e.strength * (1 - (time - e.t) / e.duration);
            yaw += power * Math.sin(time * 2.9);
            pitch += power * Math.sin(time * 3.7 + 1.3);
            roll += power * 0.6 * Math.sin(time * 2.3 + 2.1);
        }
        if (p.holding()) {
            // Um leve balanço para a pausa não parecer travamento.
            double clock = time + p.held, ramp = Math.min(1, p.holdAge / 20.0);
            yaw += 0.35 * ramp * Math.sin(clock * 0.07);
            pitch += 0.2 * ramp * Math.sin(clock * 0.05 + 1.3);
        }
        p.camera.setPose(pos.xCoord, pos.yCoord, pos.zCoord, (float) yaw,
                MathHelper.clamp_float((float) pitch, -90, 90));
        setRoll((float) roll);

        float userFov = Math.max(1, mc.gameSettings.fovSetting);
        p.userFovScale = (float) (sample.fov / userFov);
    }

    private static Vec3 toWorld(Playback p, double[] local) {
        double[] offset = new double[3];
        Scene.rotate(local, p.yaw, offset);
        return Vec3.createVectorHelper(p.x + offset[0], p.y + offset[1], p.z + offset[2]);
    }

    // Blocos entre a câmera e o ponto observado são ignorados de propósito: a câmera
    // só recua quando o próprio caminho dela entra em um bloco opaco.
    private double pullAt(final Playback p, double time) {
        return Clearance.pull(new Clearance.Needed() {
            @Override
            public double at(int tick) {
                if (p.blocked[tick] < 0) p.blocked[tick] = blockedFraction(p, tick);
                return p.blocked[tick];
            }
        }, time, p.scene.shotStart(time), p.scene.shotEnd(time));
    }

    /** Menor fração em direção ao ponto observado em que a câmera fica fora de blocos. */
    private double blockedFraction(Playback p, int tick) {
        p.scene.sample(tick, probe);
        Vec3 pos = toWorld(p, probe.pos);
        Vec3 look = toWorld(p, probe.look);
        double length = pos.distanceTo(look);
        for (double step = 0; step <= length - 1; step += 0.25) {
            double fraction = step / length;
            Vec3 point = lerp(pos, look, fraction);
            if (Space.isClear(p.world, point.xCoord, point.yCoord, point.zCoord)) return fraction;
        }
        // Sem ponto livre no caminho: é melhor manter o enquadramento da cena.
        return 0;
    }

    private static Vec3 lerp(Vec3 from, Vec3 to, double fraction) {
        return Vec3.createVectorHelper(
                from.xCoord + (to.xCoord - from.xCoord) * fraction,
                from.yCoord + (to.yCoord - from.yCoord) * fraction,
                from.zCoord + (to.zCoord - from.zCoord) * fraction);
    }

    private void setRoll(float roll) {
        if (rollUnavailable) return;
        try {
            if (camRoll == null) {
                camRoll = ReflectionHelper.findField(EntityRenderer.class, "camRoll", "field_78495_O");
                prevCamRoll = ReflectionHelper.findField(EntityRenderer.class, "prevCamRoll", "field_78505_P");
            }
            camRoll.setFloat(mc.entityRenderer, roll);
            prevCamRoll.setFloat(mc.entityRenderer, roll);
        } catch (Exception e) {
            rollUnavailable = true;
            AMCinematics.log.warn("Rotação lateral da câmera indisponível: " + e);
        }
    }

    // As partículas são orientadas pela rotação do jogador (ActiveRenderInfo),
    // então ele a empresta da câmera até o primeiro ajuste de neblina do frame,
    // que acontece antes de as entidades serem desenhadas.
    private void borrowLook(CameraEntity camera) {
        returnLook();
        realYaw = mc.thePlayer.rotationYaw;
        realPitch = mc.thePlayer.rotationPitch;
        mc.thePlayer.rotationYaw = camera.rotationYaw;
        mc.thePlayer.rotationPitch = camera.rotationPitch;
        lookBorrowed = true;
    }

    private void returnLook() {
        if (!lookBorrowed) return;
        lookBorrowed = false;
        if (mc.thePlayer != null) {
            mc.thePlayer.rotationYaw = realYaw;
            mc.thePlayer.rotationPitch = realPitch;
        }
    }

    @SubscribeEvent(receiveCanceled = true)
    public void onFogSetup(EntityViewRenderEvent.FogDensity event) {
        returnLook();
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onFov(FOVUpdateEvent event) {
        if (active != null) event.newfov = active.userFovScale;
    }

    // ---------------------------------------------------------------- bloqueios

    @SubscribeEvent
    public void onKey(InputEvent.KeyInputEvent event) {
        if (active == null) return;
        boolean pressed = Keyboard.getEventKeyState();
        int code = Keyboard.getEventKey();
        KeyBinding.unPressAllKeys();
        if (pressed) handle(code);
    }

    /** Tecla ou botão do mouse apertado durante a cena. */
    private void handle(int code) {
        if (code == skipKey.getKeyCode() && active.canSkip()) end(true);
        else if (active.locked()) playKey(code);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onMouse(MouseEvent event) {
        // Com uma tela aberta, o evento precisa chegar até ela.
        if (active == null || mc.currentScreen != null) return;
        event.setCanceled(true);
        // Botões do mouse usam códigos negativos (botão - 100).
        if (event.buttonstate && event.button >= 0) handle(event.button - 100);
    }

    @SubscribeEvent
    public void onGui(GuiOpenEvent event) {
        if (active == null) return;
        if (event.gui instanceof GuiContainer && active.locked()) event.setCanceled(true);
        else if (event.gui instanceof GuiGameOver || event.gui instanceof GuiContainer) end(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHud(RenderGameOverlayEvent.Pre event) {
        if (active == null || event.type != RenderGameOverlayEvent.ElementType.ALL) return;
        event.setCanceled(true);
        // É o HUD que prepara a projeção 2D. Sem isto, o menu aberto com Esc seria
        // desenhado na projeção 3D e apareceria só como uma mancha preta.
        mc.entityRenderer.setupOverlayRendering();
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onHand(RenderHandEvent event) {
        if (active != null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onBlockOutline(DrawBlockHighlightEvent event) {
        if (active != null) event.setCanceled(true);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onNameTag(RenderLivingEvent.Specials.Pre event) {
        if (active != null) event.setCanceled(true);
    }
}
