package am2.cinematics.client;

import am2.cinematics.ritual.Minigame;

/** Um minigame como este cliente o vê: relógio, teclas e o resultado na tela. */
final class MinigameView {
    private static final int FLASH_TICKS = 5, RESULT_TICKS = 20;
    /** Quem assiste só dá por perdida uma batida depois que a tecla teve tempo de chegar. */
    private static final int WATCH_DELAY = 30;

    final Minigame game;
    final int gate;
    /** Este jogador é quem joga. */
    final boolean mine;
    final String caster;
    /** Ticks desde o começo, preparação incluída. */
    int age;

    private boolean ended, passed, flashGood;
    private int resultTicks, flashKey = -1, flashAge = FLASH_TICKS;

    MinigameView(Minigame game, int gate, int age, boolean mine, String caster) {
        this.game = game;
        this.gate = gate;
        this.age = age;
        this.mine = mine;
        this.caster = caster;
    }

    /** Ticks dentro dos 5 segundos (negativo durante a preparação). */
    int playTick() {
        return age - game.lead();
    }

    void tick() {
        age++;
        flashAge++;
        if (ended) resultTicks--;
        else game.advance(playTick() - (mine ? 0 : WATCH_DELAY));
    }

    int press(int key, int tick) {
        int result = game.press(key, tick);
        if (result != Minigame.PENDING) {
            flashKey = key;
            flashAge = 0;
            flashGood = result == Minigame.HIT;
        }
        return result;
    }

    /** Resultado vindo do servidor. */
    void end(boolean success) {
        ended = true;
        passed = success;
        resultTicks = RESULT_TICKS;
    }

    /** O servidor aprovou e o resultado já foi mostrado: a cena pode continuar. */
    boolean cleared() {
        return ended && passed && resultTicks <= 0;
    }

    /** @return 1 se passou, -1 se falhou, 0 se ainda joga */
    int verdict() {
        if (ended) return passed ? 1 : -1;
        Minigame.State state = game.state();
        return state == Minigame.State.RUNNING ? 0 : state == Minigame.State.PASSED ? 1 : -1;
    }

    boolean over() {
        return ended;
    }

    /** Direção acesa agora (demonstração ou tecla recente), ou -1. */
    int lit() {
        int demo = game.demo(age);
        if (demo >= 0) return demo;
        return game.type == Minigame.Type.CANDLES && flashAge < FLASH_TICKS ? flashKey : -1;
    }

    /** Aperto recente: 1 certo, -1 errado, 0 nenhum. */
    int flash() {
        return flashAge >= FLASH_TICKS ? 0 : flashGood ? 1 : -1;
    }
}
