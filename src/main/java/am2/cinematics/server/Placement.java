package am2.cinematics.server;

import am2.cinematics.Space;
import am2.cinematics.scene.Scene;
import net.minecraft.entity.player.EntityPlayerMP;

/** Escolhe para que lado a cena fica virada, evitando paredes. */
final class Placement {
    /** Olhando um pouco para baixo, para a volta à primeira pessoa ficar natural. */
    private static final float PITCH = 10;
    private static final float[] TURNS = { 0, 180, 90, -90 };

    private Placement() {}

    /** Vira o jogador-âncora para o lado com mais espaço e devolve essa direção. */
    static float align(EntityPlayerMP player, Scene scene) {
        float best = player.rotationYaw;
        int bestScore = Integer.MAX_VALUE;
        // Em empate vale o primeiro: a direção em que o jogador já olha.
        for (float turn : TURNS) {
            int score = blocked(player, scene, player.rotationYaw + turn);
            if (score < bestScore) {
                bestScore = score;
                best = player.rotationYaw + turn;
            }
        }
        if (player.ridingEntity == null) {
            player.playerNetServerHandler.setPlayerLocation(player.posX, player.posY, player.posZ, best, PITCH);
        }
        return best;
    }

    private static int blocked(EntityPlayerMP player, Scene scene, float yaw) {
        int score = 0;
        Scene.Sample sample = new Scene.Sample();
        for (int t = 0; t <= scene.duration; t += 5) {
            scene.sample(t, sample);
            if (!isClear(player, sample.pos, yaw)) score++;
        }
        // Um ator dentro da parede estraga mais que a câmera, que sabe recuar.
        for (Scene.Actor actor : scene.actors) {
            // Círculos ficam no chão e efeitos sem posição não contam.
            if (actor.type == Scene.ActorType.CIRCLE || !actor.type.placed()) continue;
            for (Scene.ActorKey key : actor.keys) {
                if (key.attach == Scene.Attach.NONE && !isClear(player, key.pos, yaw)) score += 3;
            }
        }
        return score;
    }

    private static boolean isClear(EntityPlayerMP player, double[] local, float yaw) {
        double[] offset = new double[3];
        Scene.rotate(local, yaw, offset);
        return Space.isClear(player.worldObj, player.posX + offset[0], player.boundingBox.minY + offset[1],
                player.posZ + offset[2]);
    }
}
