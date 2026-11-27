package am2.cinematics.ritual;

import am2.cinematics.network.GameMessage;
import am2.cinematics.network.Net;
import am2.cinematics.scene.Scene;
import am2.cinematics.server.CinematicServer;
import am2.cinematics.server.SessionController;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * Um ritual em andamento: para a cena em cada pausa, conduz o minigame e decide
 * se o conjurador passa. O resultado é aplicado por {@link FusionRitual} quando
 * a sessão se encerra.
 */
final class RitualRun implements SessionController {
    private enum Outcome { NONE, FAIL, INTERRUPT }

    /** Tempo para ler o resultado antes de a cena continuar. */
    static final int RESULT_TICKS = 20;
    /** Quanto o servidor espera por uma tecla atrasada pela rede. */
    private static final int GRACE_TICKS = 30;
    /** Folga para um cliente com o relógio adiantado. */
    private static final int SKEW_TICKS = 20;
    /** Se o conjurador se afastar mais que isto do altar, o ritual falha. */
    private static final int REACH = 8;

    private static final Random RANDOM = new Random();

    final FusionRitual ritual;
    final UUID caster;
    final String name;
    final int dimension, x, y, z;
    final float yaw;
    private final List<Scene.Gate> gates;
    /** Treino: sem oferta, prêmio nem castigo. */
    final boolean practice;
    int session;

    private Outcome outcome = Outcome.NONE;
    /** Venceu o último minigame: nada mais tira a fusão. */
    private boolean won;
    private boolean leaving;
    private int gate;
    private Minigame game;
    private long seed;
    private int gameAge, resultTicks, lastTick;
    private boolean passed;
    /** Teclas aceitas na partida atual, para quem chega no meio. */
    private final List<int[]> accepted = new ArrayList<int[]>();

    RitualRun(FusionRitual ritual, EntityPlayerMP player, int x, int y, int z, List<Scene.Gate> gates,
            boolean practice) {
        this.practice = practice;
        this.ritual = ritual;
        this.caster = player.getUniqueID();
        this.name = player.getCommandSenderName();
        this.dimension = player.dimension;
        this.yaw = Math.round(player.rotationYaw / 90) * 90;
        this.x = x;
        this.y = y;
        this.z = z;
        this.gates = gates;
    }

    /** O conjurador está saindo: falha, que vale quando a sessão for encerrada. */
    void leave() {
        outcome = Outcome.FAIL;
        leaving = true;
    }

    boolean leaving() {
        return leaving;
    }

    @Override
    public Step tick(int age) {
        // Depois da vitória só falta a cena: o jogador já pode estar livre no cliente dele.
        if (!won) {
            if (outcome != Outcome.NONE) return Step.STOP;
            EntityPlayerMP player = CinematicServer.find(caster);
            if (player == null || !player.isEntityAlive() || player.dimension != dimension
                    || player.getDistanceSq(x + 0.5, y + 1, z + 0.5) > REACH * REACH) {
                outcome = Outcome.FAIL;
                return Step.STOP;
            }
        }
        if (game == null) {
            if (gate >= gates.size() || age < gates.get(gate).t) return Step.ADVANCE;
            seed = RANDOM.nextLong();
            game = new Minigame(gates.get(gate).game, seed);
            gameAge = resultTicks = lastTick = 0;
            accepted.clear();
            send(start(), null);
            return Step.HOLD;
        }
        if (resultTicks > 0) {
            if (--resultTicks > 0) return Step.HOLD;
            if (!passed) {
                outcome = Outcome.FAIL;
                return Step.STOP;
            }
            game = null;
            gate++;
            return Step.ADVANCE;
        }
        gameAge++;
        if (game.advance(gameAge - game.lead() - GRACE_TICKS) != Minigame.State.RUNNING) resolve();
        return Step.HOLD;
    }

    @Override
    public void input(UUID player, int forGate, int key, int tick) {
        if (!caster.equals(player) || game == null || resultTicks > 0 || forGate != gate) return;
        if (tick < lastTick || tick > Minigame.PLAY_TICKS || tick > gameAge - game.lead() + SKEW_TICKS) return;
        lastTick = tick;
        if (game.press(key, tick) != Minigame.PENDING) {
            accepted.add(new int[] { key, tick });
            send(GameMessage.input(session, gate, key, tick), caster);
        }
        if (game.state() != Minigame.State.RUNNING) resolve();
    }

    private void resolve() {
        passed = game.state() == Minigame.State.PASSED;
        if (passed && gate == gates.size() - 1) won = true;
        resultTicks = RESULT_TICKS;
        send(GameMessage.end(session, gate, passed), null);
    }

    @Override
    public void left(UUID player) {
        if (!caster.equals(player) || won || outcome != Outcome.NONE) return;
        EntityPlayerMP online = CinematicServer.find(caster);
        // Morte é falha; sair da cena por outro motivo é um defeito, e nada se perde.
        outcome = online == null || !online.isEntityAlive() ? Outcome.FAIL : Outcome.INTERRUPT;
    }

    @Override
    public void admitted(EntityPlayerMP viewer) {
        if (game == null) return;
        Net.channel.sendTo(start(), viewer);
        for (int[] input : accepted) {
            Net.channel.sendTo(GameMessage.input(session, gate, input[0], input[1]), viewer);
        }
        if (resultTicks > 0) Net.channel.sendTo(GameMessage.end(session, gate, passed), viewer);
    }

    @Override
    public int gate() {
        return gate;
    }

    @Override
    public boolean practice() {
        return practice;
    }

    @Override
    public void closed(boolean completed) {
        // Sem pausas (cena minúscula), chegar ao fim basta.
        if (won || completed && gates.isEmpty()) {
            ritual.succeed(this);
        } else if (outcome == Outcome.FAIL) {
            ritual.failed(this);
        } else {
            ritual.interrupted(this);
        }
    }

    private GameMessage start() {
        return GameMessage.start(session, gate, game.type.ordinal(), seed, gameAge);
    }

    private void send(GameMessage message, UUID except) {
        CinematicServer.INSTANCE.sendToViewers(session, message, except);
    }
}
