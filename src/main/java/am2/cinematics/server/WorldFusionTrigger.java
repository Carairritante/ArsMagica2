package am2.cinematics.server;

import am2.api.events.WorldFusionEvent;
import am2.cinematics.AMCinematics;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;

/**
 * "Fusão com o Mundo": toca quando o ritual dá certo pela primeira vez. Este
 * gatilho cobre o ritual do Vínculo de Mana; o ritual novo
 * ({@code am2.cinematics.ritual.FusionRitual}) chama a cena diretamente.
 *
 * O {@link WorldFusionEvent} do AM2 dispara antes de o ritual ser validado, então
 * ele só anota onde a magia caiu; o sucesso é {@code ExtendedProperties.hasRitual}
 * passar de falso para verdadeiro (ManaLink.applyEffectBlock). O AM2 não é
 * alterado, e a consagração do Playground (que exige o ritual já feito) não conta.
 */
public final class WorldFusionTrigger {
    public static final String SCENE = "fusao";
    /** Quem estiver até esta distância do altar assiste. */
    public static final int AUDIENCE_BLOCKS = 50;

    private static final class Candidate {
        final int dimension, x, y, z;
        int age;

        Candidate(int dimension, int x, int y, int z) {
            this.dimension = dimension;
            this.x = x;
            this.y = y;
            this.z = z;
        }
    }

    private final Method properties;
    private final Field hasRitual;
    private final Map<UUID, Candidate> candidates = new HashMap<UUID, Candidate>();

    private WorldFusionTrigger(Method properties, Field hasRitual) {
        this.properties = properties;
        this.hasRitual = hasRitual;
    }

    /** @return nulo se a versão do AM2 não tiver o que o gatilho precisa. */
    public static WorldFusionTrigger create() {
        try {
            ClassLoader loader = WorldFusionTrigger.class.getClassLoader();
            Class.forName("am2.api.events.WorldFusionEvent", false, loader);
            Class<?> type = Class.forName("am2.playerextensions.ExtendedProperties", false, loader);
            return new WorldFusionTrigger(type.getMethod("For", EntityLivingBase.class), type.getField("hasRitual"));
        } catch (Exception e) {
            AMCinematics.log.warn("Gatilho do ritual de World Fusion indisponível neste AM2: " + e);
            return null;
        }
    }

    private boolean fused(EntityPlayerMP player) {
        try {
            return hasRitual.getBoolean(properties.invoke(null, player));
        } catch (Exception e) {
            throw new IllegalStateException("Falha ao ler o estado do ritual", e);
        }
    }

    // LOWEST e mesmo cancelado: só observa, sem interferir em quem estende o ritual.
    @SubscribeEvent(priority = EventPriority.LOWEST, receiveCanceled = true)
    public void onFusion(WorldFusionEvent event) {
        if (!(event.caster instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.caster;
        if (fused(player)) return;
        candidates.put(player.getUniqueID(), new Candidate(player.dimension, event.x, event.y, event.z));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || candidates.isEmpty()) return;
        for (Iterator<Map.Entry<UUID, Candidate>> it = candidates.entrySet().iterator(); it.hasNext();) {
            Map.Entry<UUID, Candidate> entry = it.next();
            Candidate c = entry.getValue();
            EntityPlayerMP player = CinematicServer.find(entry.getKey());
            if (player == null || !player.isEntityAlive() || player.dimension != c.dimension) {
                it.remove();
            } else if (fused(player)) {
                it.remove();
                playScene(player, c.x, c.y, c.z);
            } else if (++c.age > 3) {
                // A magia caiu no bloco, mas o ritual não estava completo.
                it.remove();
            }
        }
    }

    /** Toca a cena do ritual no altar em (x, y, z). Só na thread do servidor. */
    public static void playScene(EntityPlayerMP player, int x, int y, int z) {
        try {
            CinematicServer.INSTANCE.playOn(player, SceneStore.read(SCENE), AUDIENCE_BLOCKS, false,
                    new double[] { x + 0.5, y, z + 0.5 });
        } catch (IOException e) {
            AMCinematics.log.error("Cena " + SCENE + " indisponível: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            AMCinematics.log.error("Cena " + SCENE + " inválida: " + e.getMessage());
        }
    }

    public void reset() {
        candidates.clear();
    }
}
