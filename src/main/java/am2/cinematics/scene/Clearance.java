package am2.cinematics.scene;

/**
 * Suaviza o recuo da câmera quando o caminho passa por dentro de um bloco: ela
 * começa a se aproximar antes de chegar nele e volta aos poucos depois. Depende
 * só do tempo da cena, então é contínuo e igual para todos, em qualquer FPS.
 */
public final class Clearance {
    /** Ticks de antecipação e de retorno. */
    public static final int WINDOW = 12;

    /** Quanto a câmera precisa recuar (0 = nada, 1 = até o ponto observado) em um tick. */
    public interface Needed {
        double at(int tick);
    }

    private Clearance() {}

    /**
     * @param first primeiro tick do plano atual
     * @param last  último tick do plano atual; um plano não influencia o outro
     */
    public static double pull(Needed needed, double time, int first, int last) {
        int from = Math.max(first, (int) Math.floor(time) - WINDOW);
        int to = Math.min(last, (int) Math.ceil(time) + WINDOW);
        double result = 0;
        for (int tick = from; tick <= to; tick++) {
            double u = 1 - Math.abs(tick - time) / WINDOW;
            if (u > 0) result = Math.max(result, needed.at(tick) * u * u * (3 - 2 * u));
        }
        return result;
    }
}
