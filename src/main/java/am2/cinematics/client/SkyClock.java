package am2.cinematics.client;

import am2.cinematics.scene.Scene;
import net.minecraft.world.World;

/**
 * Anoitece e faz chover só na tela de quem assiste: muda a hora e o clima do mundo
 * do cliente e devolve no fim. O servidor não é tocado (e a sincronização dele
 * corrige qualquer resto).
 */
final class SkyClock {
    private static final long DAY = 24000, MIDNIGHT = 18000;

    private final Scene.ActorKey key = new Scene.ActorKey();
    private long original;
    private int since;
    private boolean cycle, changed, stormy;
    private float rain, thunder;

    void remember(World world, int elapsed) {
        original = world.getWorldTime();
        cycle = world.getGameRules().getGameRuleBooleanValue("doDaylightCycle");
        since = elapsed;
        rain = world.getRainStrength(1);
        // A força de trovão lida já vem multiplicada pela da chuva.
        thunder = rain > 0 ? Math.min(1, world.getWeightedThunderStrength(1) / rain) : 0;
    }

    /** Chamar a cada frame, antes de o mundo ser desenhado. */
    void apply(World world, Scene scene, double time, int elapsed) {
        storm(world, scene, time);
        for (Scene.Actor actor : scene.actors) {
            if (actor.type != Scene.ActorType.NIGHT) continue;
            double night = actor.sample(time, key) ? key.alpha : 0;
            if (night <= 0 && !changed) return;
            long now = original + (cycle ? elapsed - since : 0);
            // Avança até a próxima meia-noite, passando pelo pôr do sol.
            long ahead = ((MIDNIGHT - now % DAY) % DAY + DAY) % DAY;
            set(world, now + Math.round(ahead * night));
            changed = true;
            return;
        }
    }

    private void storm(World world, Scene scene, double time) {
        for (Scene.Actor actor : scene.actors) {
            if (actor.type != Scene.ActorType.RAIN) continue;
            double strength = actor.sample(time, key) ? key.alpha : 0;
            if (strength <= 0 && !stormy) return;
            world.setRainStrength((float) Math.max(rain, strength));
            world.setThunderStrength((float) Math.max(thunder, strength));
            stormy = true;
            return;
        }
    }

    void restore(World world, int elapsed) {
        if (stormy) {
            stormy = false;
            world.setRainStrength(rain);
            world.setThunderStrength(thunder);
        }
        if (!changed) return;
        changed = false;
        set(world, original + (cycle ? elapsed - since : 0));
    }

    private void set(World world, long time) {
        // No cliente, hora negativa significa "ciclo parado" (WorldClient.setWorldTime).
        world.setWorldTime(cycle || time == 0 ? time : -time);
    }
}
