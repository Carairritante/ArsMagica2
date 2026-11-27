package am2.cinematics.ritual;

/**
 * Área aberta ao redor do altar, com a mesma contagem do ritual da Runa de
 * Divisão do Extra Utilities ({@code ActivationRitual.naturalEarth}): colunas
 * com terra ou grama exposta numa esfera ao redor do bloco central.
 * Sem dependência do Minecraft, para poder ser verificada fora do jogo.
 */
public final class OpenArea {
    public static final int RANGE = 8;
    public static final int NEEDED = 21;

    /** O que a contagem precisa saber do mundo. */
    public interface Ground {
        /** Primeira altura livre acima do chão da coluna ({@code getTopSolidOrLiquidBlock}). */
        int top(int x, int z);

        /** Terra ou grama. */
        boolean isSoil(int x, int y, int z);

        /** Cubo opaco: esconde o que está abaixo. */
        boolean isOpaque(int x, int y, int z);

        /** Ar, planta ou outro bloco que não cobre a terra. */
        boolean isFree(int x, int y, int z);
    }

    private OpenArea() {}

    /** @return quantas colunas contam ao redor do altar em (x, y, z). */
    public static int count(Ground ground, int x, int y, int z) {
        int count = 0;
        for (int dx = -RANGE; dx <= RANGE; dx++) {
            for (int dz = -RANGE; dz <= RANGE; dz++) {
                if (dx * dx + dz * dz >= RANGE * RANGE) continue;
                for (int dy = Math.min(3 + ground.top(x + dx, z + dz) - y, RANGE); dy >= -RANGE; dy--) {
                    if (dx * dx + dy * dy + dz * dz > RANGE * RANGE) {
                        // Acima da esfera ainda há o que descer; abaixo, não.
                        if (dy < 0) break;
                        continue;
                    }
                    if (ground.isSoil(x + dx, y + dy, z + dz)) {
                        if (ground.isFree(x + dx, y + dy + 1, z + dz)) count++;
                        break;
                    }
                    if (ground.isOpaque(x + dx, y + dy, z + dz)) break;
                }
            }
        }
        return count;
    }
}
