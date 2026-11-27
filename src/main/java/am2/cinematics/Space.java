package am2.cinematics;

import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/** Consultas de espaço usadas pelo servidor e pelo cliente. */
public final class Space {
    private Space() {}

    /** Verdadeiro se um cubo pequeno ao redor do ponto não toca bloco opaco. */
    public static boolean isClear(World world, double x, double y, double z) {
        // Os cantos impedem que o plano próximo da câmera corte uma parede.
        for (int corner = 0; corner < 8; corner++) {
            if (world.getBlock(
                    MathHelper.floor_double(x + ((corner & 1) == 0 ? -0.2 : 0.2)),
                    MathHelper.floor_double(y + ((corner & 2) == 0 ? -0.2 : 0.2)),
                    MathHelper.floor_double(z + ((corner & 4) == 0 ? -0.2 : 0.2))).isOpaqueCube()) return false;
        }
        return true;
    }
}
