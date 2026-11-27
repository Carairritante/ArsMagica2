package am2.cinematics.scene;

import java.util.List;

/**
 * Pose roteirizada do jogador-âncora: cabeça, ombros e cotovelos. Não depende do
 * Minecraft. Ângulos em graus, com os eixos do ModelBiped: X negativo leva o
 * braço para a frente e, no cotovelo, dobra o antebraço para a frente.
 */
public final class Pose {
    /** Índices de um braço. */
    public static final int X = 0, Y = 1, Z = 2, ELBOW = 3;
    /** Ângulo do braço direito parado segurando um item (ModelBiped). */
    private static final double HOLDING_X = -18;
    /** Distância do cotovelo até a palma, em pixels do modelo. */
    private static final double FOREARM = 5.5;

    public static final class Key {
        public int t;
        /** 0 = pose normal do jogo, 1 = pose do roteiro. */
        public double weight;
        /** Inclinação da cabeça (negativo olha para cima) e tremor em graus. */
        public double head, tremor;
        /** Quantos blocos o corpo flutua (só no desenho). */
        public double lift;
        public final double[] right = new double[4], left = new double[4];

        void copy(Key from) {
            weight = from.weight;
            head = from.head;
            tremor = from.tremor;
            lift = from.lift;
            System.arraycopy(from.right, 0, right, 0, 4);
            System.arraycopy(from.left, 0, left, 0, 4);
        }
    }

    private Pose() {}

    /** Fora do intervalo vale o primeiro ou o último ponto. @return falso se não há pose */
    public static boolean sample(List<Key> keys, double time, Key out) {
        if (keys.isEmpty()) return false;
        int i = 0;
        while (i + 2 < keys.size() && keys.get(i + 1).t <= time) i++;
        Key a = keys.get(i), b = keys.get(Math.min(i + 1, keys.size() - 1));
        double u = b.t == a.t ? 0 : Math.max(0, Math.min(1, (time - a.t) / (b.t - a.t)));
        u = u * u * (3 - 2 * u);
        out.weight = a.weight + (b.weight - a.weight) * u;
        out.head = a.head + (b.head - a.head) * u;
        out.tremor = a.tremor + (b.tremor - a.tremor) * u;
        out.lift = a.lift + (b.lift - a.lift) * u;
        for (int n = 0; n < 4; n++) {
            out.right[n] = a.right[n] + (b.right[n] - a.right[n]) * u;
            out.left[n] = a.left[n] + (b.left[n] - a.left[n]) * u;
        }
        return true;
    }

    /**
     * Posição da palma no espaço local da cena (direita, cima, frente), com o
     * jogador na âncora olhando para a frente. {@code key} pode ser nulo.
     */
    public static void hand(Key key, boolean right, double[] out) {
        double weight = key == null ? 0 : key.weight;
        double[] arm = key == null ? new double[4] : right ? key.right : key.left;
        double idle = right ? HOLDING_X : 0;
        double x = idle + (arm[X] - idle) * weight;
        double[] p = { 0, FOREARM, 0 };
        rotateX(p, arm[ELBOW] * weight);
        p[0] += right ? -1 : 1;
        p[1] += 4;
        rotateX(p, x);
        rotateY(p, arm[Y] * weight);
        rotateZ(p, arm[Z] * weight);
        toLocal(p[0] + (right ? -5 : 5), p[1] + 2, p[2], out);
        if (key != null) out[1] += key.lift;
    }

    /** Posição do coração no mesmo espaço de {@link #hand}. {@code key} pode ser nulo. */
    public static void heart(Key key, double[] out) {
        toLocal(1.4, 3.6, -2.2, out);
        if (key != null) out[1] += key.lift;
    }

    /** Converte um ponto do modelo do jogador (pixels, Y para baixo, frente -Z) em local. */
    public static void toLocal(double x, double y, double z, double[] out) {
        out[0] = -0.9375 * x / 16;
        out[1] = 0.9375 * (1.5078125 - y / 16);
        out[2] = -0.9375 * z / 16;
    }

    // Mesma convenção de glRotatef; ModelRenderer aplica X, depois Y, depois Z ao vértice.
    public static void rotateX(double[] p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        double y = p[1] * c - p[2] * s, z = p[1] * s + p[2] * c;
        p[1] = y;
        p[2] = z;
    }

    private static void rotateY(double[] p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        double x = p[0] * c + p[2] * s, z = -p[0] * s + p[2] * c;
        p[0] = x;
        p[2] = z;
    }

    private static void rotateZ(double[] p, double degrees) {
        double c = Math.cos(Math.toRadians(degrees)), s = Math.sin(Math.toRadians(degrees));
        double x = p[0] * c - p[1] * s, y = p[0] * s + p[1] * c;
        p[0] = x;
        p[1] = y;
    }
}
