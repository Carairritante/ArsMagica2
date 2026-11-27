package am2.cinematics.scene;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Desenho das veias que se espalham pelo terreno: ramos que saem do centro e se
 * dividem. Coordenadas em um disco de raio 1; a mesma semente gera sempre o mesmo
 * desenho, então todos os espectadores veem igual.
 */
public final class VeinMap {
    private static final double STEP = 0.022;

    public static final class Segment {
        public final double x1, z1, x2, z2;
        /** Distância percorrida desde o centro em cada ponta (1 = a ponta mais longa). */
        public final double from, to;
        /** Espessura relativa, de 0 a 1. */
        public final double width;

        private Segment(double x1, double z1, double x2, double z2, double from, double to, double width) {
            this.x1 = x1;
            this.z1 = z1;
            this.x2 = x2;
            this.z2 = z2;
            this.from = from;
            this.to = to;
            this.width = width;
        }
    }

    private VeinMap() {}

    public static List<Segment> generate(long seed) {
        Random random = new Random(seed);
        List<Segment> raw = new ArrayList<Segment>();
        int trunks = 17;
        for (int i = 0; i < trunks; i++) {
            double angle = Math.PI * 2 * (i + 0.35 * random.nextDouble()) / trunks;
            grow(raw, random, Math.cos(angle) * 0.035, Math.sin(angle) * 0.035, angle, 0.035, 1, 0);
        }
        double longest = 0;
        for (Segment s : raw) longest = Math.max(longest, s.to);
        List<Segment> result = new ArrayList<Segment>(raw.size());
        for (Segment s : raw) {
            result.add(new Segment(s.x1, s.z1, s.x2, s.z2, s.from / longest, s.to / longest, s.width));
        }
        return result;
    }

    private static void grow(List<Segment> out, Random random, double x, double z, double heading, double travelled,
            double width, int depth) {
        double angle = heading;
        int steps = depth == 0 ? 80 : depth == 1 ? 7 + random.nextInt(14) : depth == 2 ? 4 + random.nextInt(7)
                : 2 + random.nextInt(3);
        for (int step = 0; step < steps; step++) {
            double outward = Math.atan2(z, x);
            double drift = Math.atan2(Math.sin(outward - angle), Math.cos(outward - angle));
            // Serpenteia, sempre puxada para fora.
            angle += (random.nextDouble() - 0.5) * 0.85 + drift * 0.22;
            double nx = x + Math.cos(angle) * STEP, nz = z + Math.sin(angle) * STEP;
            if (nx * nx + nz * nz > 1) return;
            double thickness = width * Math.max(0.16, 1 - travelled * 0.85);
            out.add(new Segment(x, z, nx, nz, travelled, travelled + STEP, thickness));
            x = nx;
            z = nz;
            travelled += STEP;
            if (depth < 3 && step > 1 && random.nextDouble() < (depth == 0 ? 0.22 : depth == 1 ? 0.16 : 0.09)) {
                double side = random.nextBoolean() ? 1 : -1;
                grow(out, random, x, z, angle + side * (0.6 + random.nextDouble() * 0.7), travelled, width * 0.62,
                        depth + 1);
            }
        }
    }
}
