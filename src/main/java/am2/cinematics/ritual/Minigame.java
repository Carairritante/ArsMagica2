package am2.cinematics.ritual;

import java.util.Random;

/**
 * Regras de um minigame do ritual. Cliente e servidor criam a mesma partida a
 * partir da semente e aplicam as mesmas teclas; o servidor dá a palavra final.
 * Sem dependência do Minecraft.
 *
 * Os ticks de {@link #press} e {@link #advance} contam do início dos 5 segundos,
 * depois da preparação de {@link #lead()}.
 */
public final class Minigame {
    public enum Type { CANDLES, LIGHTNING, PULSE }
    public enum State { RUNNING, PASSED, FAILED }

    /** Tempo para jogar: 5 segundos. */
    public static final int PLAY_TICKS = 100;
    /** Teclas. As quatro primeiras são as direções das velas. */
    public static final int UP = 0, RIGHT = 1, DOWN = 2, LEFT = 3, ACTION = 4;
    public static final int PENDING = 0, HIT = 1, MISS = 2;
    /** Demonstração das velas: início, intervalo e quanto cada chama fica acesa. */
    static final int DEMO_START = 20, DEMO_STEP = 14, DEMO_LIT = 10;
    private static final int SEQUENCE = 3;
    /** Distância em que um aperto vale para uma batida; fora dela é ignorado. */
    private static final int RANGE = 10;

    public final Type type;
    /** Velas: a sequência de direções. Raios e pulso: o tick de cada acerto. */
    private final int[] notes;
    private final int[] judged;
    private int step, hits, misses;
    private State state = State.RUNNING;

    public Minigame(Type type, long seed) {
        this.type = type;
        Random random = new Random(seed);
        // A primeira saída de Random varia pouco com sementes próximas.
        random.nextInt();
        if (type == Type.CANDLES) {
            notes = new int[SEQUENCE];
            for (int i = 0; i < SEQUENCE; i++) {
                // Sem repetir a anterior: a demonstração fica clara.
                notes[i] = i == 0 ? random.nextInt(4) : (notes[i - 1] + 1 + random.nextInt(3)) % 4;
            }
        } else if (type == Type.LIGHTNING) {
            notes = new int[] { 26, 56, 84 };
            for (int i = 0; i < notes.length; i++) notes[i] += random.nextInt(11) - 5;
        } else {
            notes = new int[] { 22, 44, 66, 88 };
        }
        judged = new int[notes.length];
    }

    /** Ticks de preparação antes de os 5 segundos começarem. */
    public int lead() {
        return type == Type.CANDLES ? DEMO_START + SEQUENCE * DEMO_STEP + 6 : 40;
    }

    public int count() {
        return notes.length;
    }

    public int note(int index) {
        return notes[index];
    }

    public int judged(int index) {
        return judged[index];
    }

    /** Quantos acertos são necessários. */
    public int needed() {
        return type == Type.CANDLES ? notes.length : notes.length - 1;
    }

    /** Acertos até agora (nas velas, passos certos em sequência). */
    public int progress() {
        return type == Type.CANDLES ? step : hits;
    }

    /** Folga em ticks para um acerto. */
    public int window() {
        return type == Type.LIGHTNING ? 6 : 5;
    }

    public State state() {
        return state;
    }

    /**
     * @param tick ticks desde o início do minigame (preparação incluída)
     * @return a direção que a demonstração das velas mostra, ou -1
     */
    public int demo(double tick) {
        if (type != Type.CANDLES || tick < DEMO_START) return -1;
        int index = (int) ((tick - DEMO_START) / DEMO_STEP);
        if (index >= notes.length || tick - DEMO_START - index * DEMO_STEP >= DEMO_LIT) return -1;
        return notes[index];
    }

    /**
     * Aplica uma tecla.
     *
     * @return {@link #HIT} se acertou, {@link #MISS} se errou, {@link #PENDING} se não contou
     */
    public int press(int key, double tick) {
        if (tick < 0 || tick > PLAY_TICKS || advance(tick) != State.RUNNING) return PENDING;
        if (type == Type.CANDLES) {
            if (key < UP || key > LEFT) return PENDING;
            if (key != notes[step]) {
                // Errou: recomeça a sequência, enquanto houver tempo.
                step = 0;
                return MISS;
            }
            if (++step == notes.length) state = State.PASSED;
            return HIT;
        }
        if (key != ACTION) return PENDING;
        for (int i = 0; i < notes.length; i++) {
            double distance = Math.abs(tick - notes[i]);
            if (judged[i] != PENDING || distance > RANGE) continue;
            // Apertar cedo demais gasta a batida: não adianta martelar a tecla.
            int result = distance <= window() ? HIT : MISS;
            judge(i, result);
            return result;
        }
        return PENDING;
    }

    /** Avança o relógio: marca o que passou sem aperto e o fim do tempo. */
    public State advance(double tick) {
        if (type != Type.CANDLES) {
            for (int i = 0; i < notes.length && state == State.RUNNING; i++) {
                if (judged[i] == PENDING && tick > notes[i] + window()) judge(i, MISS);
            }
        }
        if (state == State.RUNNING && tick > PLAY_TICKS) state = State.FAILED;
        return state;
    }

    private void judge(int index, int result) {
        judged[index] = result;
        if (result == HIT) hits++;
        else misses++;
        if (hits >= needed()) state = State.PASSED;
        else if (misses > notes.length - needed()) state = State.FAILED;
    }
}
