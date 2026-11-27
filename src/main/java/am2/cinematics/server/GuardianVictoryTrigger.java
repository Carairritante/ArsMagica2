package am2.cinematics.server;

import am2.bosses.EntityAirGuardian;
import am2.bosses.EntityArcaneGuardian;
import am2.bosses.EntityEarthGuardian;
import am2.bosses.EntityEnderGuardian;
import am2.bosses.EntityFireGuardian;
import am2.bosses.EntityLifeGuardian;
import am2.bosses.EntityLightningGuardian;
import am2.bosses.EntityNatureGuardian;
import am2.bosses.EntityWaterGuardian;
import am2.bosses.EntityWinterGuardian;
import am2.cinematics.AMCinematics;
import am2.playerextensions.ExtendedProperties;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * Cena curta na primeira vez que um jogador derrota cada Guardião. Uma cena só
 * ({@code guardiao}), com cores, partículas, nome e um detalhe próprio de cada um.
 * "Primeira vez" é a marca do próprio AM2 (guardian1..10), lida antes de ela ser gravada.
 */
public final class GuardianVictoryTrigger {
    public static final String SCENE = "guardiao";
    /** Cores e partícula da cena base (Guardião Arcano), trocadas pelas de cada Guardião. */
    private static final String BASE_MAIN = "9A5CFF", BASE_LIGHT = "D9C2FF", BASE_PARTICLE = "witchMagic";
    /** Espera depois da morte, para o Guardião cair antes da câmera mudar. */
    private static final int DELAY_TICKS = 16;
    private static final int RADIUS = 16;

    /** Detalhe próprio de cada Guardião, além das cores e da partícula. */
    private enum Extra { NONE, LIGHTNING, FLAMES, SNOW, PORTAL }

    public enum Guardian {
        WATER("agua", "GUARDIÃO DA ÁGUA", "2E9BFF", "BFEAFF", "splash", Extra.NONE),
        EARTH("terra", "GUARDIÃO DA TERRA", "A0703C", "E6CFA0", "largesmoke", Extra.NONE),
        AIR("ar", "GUARDIÃO DO AR", "BFEFFF", "FFFFFF", "cloud", Extra.NONE),
        ARCANE("arcano", "GUARDIÃO ARCANO", BASE_MAIN, BASE_LIGHT, BASE_PARTICLE, Extra.NONE),
        LIFE("vida", "GUARDIÃO DA VIDA", "FF6FA8", "FFD1E3", "heart", Extra.NONE),
        NATURE("natureza", "GUARDIÃO DA NATUREZA", "4CD964", "CFFFD6", "happyVillager", Extra.NONE),
        LIGHTNING("raio", "GUARDIÃO DO RAIO", "FFE14D", "FFF6C2", "magicCrit", Extra.LIGHTNING),
        WINTER("inverno", "GUARDIÃO DO INVERNO", "8FE3FF", "FFFFFF", "snowshovel", Extra.SNOW),
        FIRE("fogo", "GUARDIÃO DO FOGO", "FF6A1F", "FFD08A", "flame", Extra.FLAMES),
        ENDER("ender", "GUARDIÃO DO ENDER", "B04CFF", "E6B8FF", "portal", Extra.PORTAL);

        public final String id, title, main, light, particle;
        private final Extra extra;

        Guardian(String id, String title, String main, String light, String particle, Extra extra) {
            this.id = id;
            this.title = title;
            this.main = main;
            this.light = light;
            this.particle = particle;
            this.extra = extra;
        }

        public static Guardian byId(String id) {
            for (Guardian g : values()) if (g.id.equals(id)) return g;
            return null;
        }

        static Guardian of(Entity entity) {
            if (entity instanceof EntityWaterGuardian) return ((EntityWaterGuardian) entity).isClone() ? null : WATER;
            if (entity instanceof EntityEarthGuardian) return EARTH;
            if (entity instanceof EntityAirGuardian) return AIR;
            if (entity instanceof EntityArcaneGuardian) return ARCANE;
            if (entity instanceof EntityLifeGuardian) return LIFE;
            if (entity instanceof EntityNatureGuardian) return NATURE;
            if (entity instanceof EntityLightningGuardian) return LIGHTNING;
            if (entity instanceof EntityWinterGuardian) return WINTER;
            if (entity instanceof EntityFireGuardian) return FIRE;
            if (entity instanceof EntityEnderGuardian) return ENDER;
            return null;
        }

        /** A marca do AM2 de que este jogador já venceu este Guardião. */
        boolean beaten(ExtendedProperties p) {
            switch (this) {
                case WATER: return p.guardian1;
                case EARTH: return p.guardian2;
                case AIR: return p.guardian3;
                case ARCANE: return p.guardian4;
                case LIFE: return p.guardian5;
                case NATURE: return p.guardian6;
                case LIGHTNING: return p.guardian7;
                case WINTER: return p.guardian8;
                case FIRE: return p.guardian9;
                default: return p.guardian10;
            }
        }
    }

    private static final class Pending {
        final UUID player;
        final Guardian guardian;
        final double x, z;
        int wait = DELAY_TICKS;

        Pending(UUID player, Guardian guardian, double x, double z) {
            this.player = player;
            this.guardian = guardian;
            this.x = x;
            this.z = z;
        }
    }

    private final List<Pending> pending = new ArrayList<Pending>();

    // LOWEST e sem receber cancelados: só conta se a morte valeu. O AM2 grava a marca
    // depois deste evento (no onDeath do Guardião), então aqui ela ainda é a anterior.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onDeath(LivingDeathEvent event) {
        if (event.entityLiving.worldObj.isRemote) return;
        Guardian guardian = Guardian.of(event.entityLiving);
        if (guardian == null || !(event.source.getEntity() instanceof EntityPlayerMP)) return;
        EntityPlayerMP player = (EntityPlayerMP) event.source.getEntity();
        if (guardian.beaten(ExtendedProperties.For(player))) return;
        for (Pending p : pending) if (p.player.equals(player.getUniqueID())) return;
        pending.add(new Pending(player.getUniqueID(), guardian, event.entityLiving.posX, event.entityLiving.posZ));
    }

    @SubscribeEvent
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || pending.isEmpty()) return;
        for (Iterator<Pending> it = pending.iterator(); it.hasNext();) {
            Pending p = it.next();
            if (--p.wait > 0) continue;
            it.remove();
            EntityPlayerMP player = CinematicServer.find(p.player);
            if (player == null || !player.isEntityAlive() || CinematicServer.INSTANCE.watching(player)) continue;
            play(player, p.guardian, yawTowards(player, p.x, p.z), false);
        }
    }

    /** Vira a cena para onde o Guardião caiu (se ele caiu longe o bastante para isso importar). */
    private static float yawTowards(EntityPlayerMP player, double x, double z) {
        double dx = x - player.posX, dz = z - player.posZ;
        if (dx * dx + dz * dz < 1) return player.rotationYaw;
        return (float) (Math.atan2(dz, dx) * 180 / Math.PI) - 90;
    }

    /** Toca a cena de um Guardião neste jogador; {@code onlyPlayer} para conferir sem plateia. */
    public static void play(EntityPlayerMP player, Guardian guardian, float yaw, boolean onlyPlayer) {
        try {
            String json = variant(SceneStore.read(SCENE), guardian);
            CinematicServer.INSTANCE.playFacing(player, json, onlyPlayer ? 1 : RADIUS, yaw, onlyPlayer);
        } catch (IOException e) {
            AMCinematics.log.error("Cena " + SCENE + " indisponível: " + e.getMessage());
        } catch (RuntimeException e) {
            AMCinematics.log.error("Cena " + SCENE + " inválida: " + e.getMessage());
        }
    }

    /** A cena base com os detalhes do Guardião. */
    static String variant(String base, Guardian g) {
        String text = base.replace("\"" + BASE_MAIN + "\"", "\"" + g.main + "\"")
                .replace("\"" + BASE_LIGHT + "\"", "\"" + g.light + "\"")
                .replace("\"" + BASE_PARTICLE + "\"", "\"" + g.particle + "\"")
                .replace("{guardiao}", g.title);
        if (g.extra == Extra.NONE) return text;
        JsonObject scene = new JsonParser().parse(text).getAsJsonObject();
        JsonArray events = scene.getAsJsonArray("events");
        switch (g.extra) {
            case LIGHTNING:
                // Raios só desenhados para quem assiste: não queimam nem ferem.
                events.add(lightning(2, 0, 4));
                events.add(lightning(40, 1.5, 2.5));
                events.add(lightning(84, -2.5, 1.5));
                events.add(sound(2, "ambient.weather.thunder", 1.1));
                events.add(sound(84, "ambient.weather.thunder", 1.3));
                break;
            case FLAMES:
                events.add(particles(0, "lava", 20, 2, 0.8));
                events.add(particles(84, "lava", 10, 3, 0.4));
                break;
            case SNOW:
                events.add(particles(84, "snowballpoof", 24, 6, 1.2));
                break;
            case PORTAL:
                events.add(particles(84, "portal", 30, 10, 1.0));
                events.add(sound(84, "mob.endermen.portal", 0.8));
                break;
            default:
                break;
        }
        return scene.toString();
    }

    private static JsonObject event(int t, String type) {
        JsonObject o = new JsonObject();
        o.addProperty("t", t);
        o.addProperty("type", type);
        return o;
    }

    private static JsonObject lightning(int t, double x, double z) {
        JsonObject o = event(t, "lightning");
        o.add("pos", vector(x, 0, z));
        return o;
    }

    private static JsonObject sound(int t, String name, double pitch) {
        JsonObject o = event(t, "sound");
        o.addProperty("name", name);
        o.addProperty("pitch", pitch);
        return o;
    }

    /** Partículas em volta do jogador (y do peito), espalhadas por {@code spread}. */
    private static JsonObject particles(int t, String name, int duration, int count, double spread) {
        JsonObject o = event(t, "particles");
        o.addProperty("name", name);
        o.addProperty("duration", duration);
        o.addProperty("count", count);
        o.add("pos", vector(0, 1.1, 0));
        o.add("spread", vector(spread, 0.6, spread));
        o.add("velocitySpread", vector(0.05, 0.05, 0.05));
        return o;
    }

    private static JsonArray vector(double x, double y, double z) {
        JsonArray a = new JsonArray();
        a.add(new com.google.gson.JsonPrimitive(x));
        a.add(new com.google.gson.JsonPrimitive(y));
        a.add(new com.google.gson.JsonPrimitive(z));
        return a;
    }

    public void reset() {
        pending.clear();
    }
}
