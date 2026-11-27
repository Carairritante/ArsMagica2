package am2.cinematics.client;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.block.Block;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Altura do chão por coluna, perto de uma altura de referência, calculada uma vez. */
final class GroundMap {
    private static final int ABOVE = 4, BELOW = 8;

    private final Map<Long, Double> heights = new HashMap<Long, Double>();
    private World world;
    private double reference;

    void reset(World newWorld, double referenceY) {
        heights.clear();
        world = newWorld;
        reference = referenceY;
    }

    /** @return altura da superfície na coluna, ou a de referência se não houver chão por perto */
    double at(double x, double z) {
        int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z);
        long column = (long) bx << 32 | bz & 0xFFFFFFFFL;
        Double known = heights.get(column);
        if (known == null) {
            known = find(bx, bz);
            heights.put(column, known);
        }
        return known;
    }

    /** A superfície livre mais próxima da referência (subindo ou descendo). */
    private double find(int x, int z) {
        int base = MathHelper.floor_double(reference + 0.01);
        double best = reference;
        double distance = Double.MAX_VALUE;
        // Terreno ainda não carregado: mantém a referência.
        if (!world.blockExists(x, base, z)) return reference;
        for (int y = base + ABOVE; y >= base - BELOW; y--) {
            Block block = world.getBlock(x, y, z);
            if (!block.getMaterial().blocksMovement() || world.getBlock(x, y + 1, z).getMaterial().blocksMovement()) {
                continue;
            }
            block.setBlockBoundsBasedOnState(world, x, y, z);
            double top = y + block.getBlockBoundsMaxY();
            if (Math.abs(top - reference) < distance) {
                distance = Math.abs(top - reference);
                best = top;
            }
        }
        return best;
    }
}
