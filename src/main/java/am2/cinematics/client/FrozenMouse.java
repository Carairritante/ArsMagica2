package am2.cinematics.client;

import net.minecraft.util.MouseHelper;
import org.lwjgl.input.Mouse;

/** Consome o movimento do mouse sem repassar ao jogador (evita salto no retorno). */
final class FrozenMouse extends MouseHelper {
    @Override
    public void mouseXYChange() {
        Mouse.getDX();
        Mouse.getDY();
        deltaX = 0;
        deltaY = 0;
    }
}
