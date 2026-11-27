package am2.cinematics.client;

import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.Loader;
import java.lang.reflect.Method;

/**
 * "Dublê" para quem usa o Futura3D: enquanto o jogador-âncora é desenhado, o
 * mod de modelos fica desligado e o próprio Minecraft desenha o corpo original,
 * que a cena sabe animar. Os outros jogadores continuam com os seus modelos.
 *
 * Quando desligado, {@code PlayerModels.render} devolve falso e o mixin do
 * Futura3D chama o desenho vanilla (conferido em futura3d-90c7159). Usa só
 * métodos públicos, por reflexão: o Futura3D não é dependência.
 */
final class ModelModSwitch {
    private boolean looked, suspended;
    private Object models;
    private Method active, setEnabled;

    /** Chamar antes do desenho do jogador-âncora; sempre seguido de {@link #restore()}. */
    void suspend() {
        if (suspended || !find()) return;
        try {
            if ((Boolean) active.invoke(models)) {
                setEnabled.invoke(models, false);
                suspended = true;
            }
        } catch (Exception e) {
            giveUp(e);
        }
    }

    /** Seguro chamar a qualquer momento. */
    void restore() {
        if (!suspended) return;
        suspended = false;
        try {
            setEnabled.invoke(models, true);
        } catch (Exception e) {
            giveUp(e);
        }
    }

    /** Procura só no primeiro uso, quando o Futura3D já terminou de carregar. */
    private boolean find() {
        if (!looked) {
            looked = true;
            if (Loader.isModLoaded("futura3d")) {
                try {
                    Class<?> type = Class.forName("com.akashic.futura.client.PlayerModels");
                    active = type.getMethod("active");
                    setEnabled = type.getMethod("setEnabled", boolean.class);
                    models = type.getField("INSTANCE").get(null);
                } catch (Exception e) {
                    giveUp(e);
                }
            }
        }
        return models != null;
    }

    private void giveUp(Exception e) {
        models = null;
        AMCinematics.log.warn("Esta versão do Futura3D não permite o dublê; o corpo não será animado: " + e);
    }
}
