package am2.cinematics;

import am2.cinematics.network.Net;
import am2.cinematics.ritual.FusionReward;
import am2.cinematics.ritual.FusionRitual;
import am2.cinematics.server.CinemaCommand;
import am2.cinematics.server.CinematicServer;
import am2.cinematics.server.FirstBookTrigger;
import am2.cinematics.server.GuardianVictoryTrigger;
import am2.cinematics.server.SceneStore;
import am2.cinematics.server.WorldFusionTrigger;
import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLPreInitializationEvent;
import cpw.mods.fml.common.event.FMLServerStartingEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.config.Configuration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.File;

/**
 * Cinemáticas do AM2.5 (antes o mod separado AMCinematics), chamadas pelo AMCore em cada
 * fase de carregamento. Os nomes "amcinematics" de configuração, idioma, texturas e dados
 * do jogador continuam os mesmos, para nada salvo se perder na troca.
 */
public final class AMCinematics {
    /** Nome usado nos arquivos, no idioma e nos dados salvos; não é mais um mod. */
    public static final String MODID = "amcinematics";
    public static final String VERSION = "0.12.0";
    /** O mod antigo, quando instalado junto, fica com as cinemáticas. */
    private static final String OLD_MOD = "amcinematics";

    public static CommonProxy proxy = new CommonProxy();
    public static Logger log = LogManager.getLogger("AM2.5 Cinematics");

    private static boolean loaded, triggersEnabled, firstBookEnabled, worldFusionEnabled, guardiansEnabled, ritualEnabled;
    private static int ritualLevel;
    private static FirstBookTrigger firstBook;
    private static WorldFusionTrigger worldFusion;
    private static GuardianVictoryTrigger guardians;
    private static FusionRitual ritual;

    private AMCinematics() {}

    /** O ritual novo, ou nulo se estiver desligado ou indisponível. */
    public static FusionRitual ritual() {
        return ritual;
    }

    public static void preInit(FMLPreInitializationEvent event) {
        if (Loader.isModLoaded(OLD_MOD)) {
            log.warn("O mod AMCinematics separado está instalado: as cinemáticas do AM2.5 ficam desligadas. "
                    + "Remova o AMCinematics, elas já fazem parte do AM2.5.");
            return;
        }
        loaded = true;
        File configDirectory = event.getModConfigurationDirectory();
        Configuration config = new Configuration(new File(configDirectory, MODID + ".cfg"));
        triggersEnabled = config.getBoolean("ativado", "geral", true,
                "Liga as cinemáticas do AM2.5 (primeiro livro, World Fusion e o ritual difícil). "
                        + "Desligado, o servidor fica como o AM2.5 sem cinemáticas.");
        firstBookEnabled = config.getBoolean("primeiroLivro", "gatilhos", true,
                "Toca a cena 'despertar' quando um jogador de nível 0 pega o Arcane Compendium");
        worldFusionEnabled = config.getBoolean("fusaoComOMundo", "gatilhos", true,
                "Toca a cena 'fusao' quando o ritual original de World Fusion dá certo pela primeira vez "
                        + "(o ritual novo sempre mostra a cena)");
        guardiansEnabled = config.getBoolean("guardioes", "gatilhos", true,
                "Toca a cena 'guardiao' na primeira vez que um jogador derrota cada Guardião");
        ritualEnabled = config.getBoolean("ativado", "ritual", true,
                "Troca o ritual de World Fusion pelo difícil: agachado, clique na esmeralda com o "
                        + "Orbe Infinito vermelho");
        ritualLevel = config.getInt("nivelMinimo", "ritual", 50, 1, 99, "Nível mágico exigido pelo ritual novo");
        if (config.hasChanged()) config.save();
        SceneStore.init(configDirectory);
        // O cliente sempre sabe tocar as cenas; quem decide se elas acontecem é o servidor.
        if (FMLCommonHandler.instance().getSide() == Side.CLIENT) {
            try {
                proxy = (CommonProxy) Class.forName("am2.cinematics.client.ClientProxy").newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cinemáticas: proxy do cliente indisponível", e);
            }
        }
        Net.init();
    }

    public static void init() {
        if (!loaded) return;
        CinematicServer server = CinematicServer.INSTANCE;
        FMLCommonHandler.instance().bus().register(server);
        MinecraftForge.EVENT_BUS.register(server);
        if (triggersEnabled && firstBookEnabled) {
            firstBook = FirstBookTrigger.create();
            if (firstBook != null) {
                FMLCommonHandler.instance().bus().register(firstBook);
                MinecraftForge.EVENT_BUS.register(firstBook);
            }
        }
        if (triggersEnabled && worldFusionEnabled) {
            worldFusion = WorldFusionTrigger.create();
            if (worldFusion != null) {
                FMLCommonHandler.instance().bus().register(worldFusion);
                MinecraftForge.EVENT_BUS.register(worldFusion);
            }
        }
        if (triggersEnabled && guardiansEnabled) {
            guardians = new GuardianVictoryTrigger();
            FMLCommonHandler.instance().bus().register(guardians);
            MinecraftForge.EVENT_BUS.register(guardians);
        }
        proxy.init();
    }

    // Depois de todos os blocos e itens do AM2 estarem registrados.
    public static void postInit() {
        if (!loaded) return;
        FusionReward reward = FusionReward.create();
        if (reward == null) return;
        // A recompensa vale para quem já ganhou, mesmo com o ritual novo desligado.
        FMLCommonHandler.instance().bus().register(reward);
        if (triggersEnabled && ritualEnabled) {
            ritual = new FusionRitual(reward, ritualLevel);
            MinecraftForge.EVENT_BUS.register(ritual);
            FMLCommonHandler.instance().bus().register(ritual);
        }
    }

    public static void serverStarting(FMLServerStartingEvent event) {
        if (!loaded) return;
        CinematicServer.INSTANCE.reset();
        event.registerServerCommand(new CinemaCommand());
    }

    public static void serverStopping() {
        if (!loaded) return;
        // Antes de limpar as sessões: os rituais em andamento precisam se encerrar.
        if (ritual != null) ritual.shutdown();
        CinematicServer.INSTANCE.reset();
        if (firstBook != null) firstBook.reset();
        if (worldFusion != null) worldFusion.reset();
        if (guardians != null) guardians.reset();
    }
}
