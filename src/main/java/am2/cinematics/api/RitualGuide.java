package am2.cinematics.api;

import am2.cinematics.AMCinematics;
import am2.cinematics.ritual.Activation;
import am2.cinematics.ritual.FusionRitual;
import net.minecraft.item.ItemStack;
import net.minecraft.util.StatCollector;

/**
 * Consulta para livros e guias (por exemplo o Compendium do AM2.5) mostrarem o
 * ritual difícil. Pensada para ser chamada por reflexão: nomes e assinaturas
 * devem se manter. Só vale quando {@link #active()} é verdadeiro.
 */
public final class RitualGuide {
    private RitualGuide() {}

    /** Verdadeiro quando o ritual difícil substitui o do Vínculo de Mana. */
    public static boolean active() {
        return AMCinematics.ritual() != null;
    }

    /**
     * Reagentes soltos junto da esmeralda, uma pilha por item com a quantidade em
     * {@code stackSize}. Itens a mais no chão não atrapalham. Vazio se inativo.
     */
    public static ItemStack[] groundReagents() {
        FusionRitual ritual = AMCinematics.ritual();
        return ritual == null ? new ItemStack[0] : ritual.groundReagents();
    }

    /** O item usado na mão para ativar (Orbe Infinito vermelho), ou nulo se inativo. */
    public static ItemStack catalyst() {
        return active() ? new ItemStack(Activation.orb(), 1, Activation.RED_ORB_META) : null;
    }

    /** Instrução curta de ativação, no idioma do jogo. Vazia se inativo. */
    public static String activation() {
        ItemStack orb = catalyst();
        return orb == null ? "" : StatCollector.translateToLocalFormatted(
                "amcinematics.ritual.livro.ativacao", orb.getDisplayName());
    }

    /**
     * Texto completo do ritual no idioma do jogo, com requisitos e reagentes,
     * usando as quebras {@code !l} do Compendium. Vazio se inativo.
     */
    public static String description() {
        FusionRitual ritual = AMCinematics.ritual();
        if (ritual == null) return "";
        StringBuilder text = new StringBuilder(StatCollector.translateToLocalFormatted(
                "amcinematics.ritual.livro.texto", ritual.minimumLevel()));
        for (ItemStack reagent : ritual.groundReagents()) {
            text.append(" !l ").append(reagent.stackSize).append("x ").append(reagent.getDisplayName());
        }
        return text.append(" !l !l ").append(activation()).toString();
    }
}
