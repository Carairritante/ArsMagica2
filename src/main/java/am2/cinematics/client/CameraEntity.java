package am2.cinematics.client;

import com.mojang.authlib.GameProfile;
import java.util.UUID;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;

/**
 * Ponto de vista que nunca entra no mundo. Estende EntityPlayer porque o jogo e
 * outros mods fazem cast de renderViewEntity para EntityPlayer.
 */
final class CameraEntity extends EntityPlayer {
    CameraEntity(World world) {
        super(world, new GameProfile(UUID.randomUUID(), "amcinematics_camera"));
    }

    /** Posição já interpolada para o frame: anterior e atual são iguais. */
    void setPose(double x, double y, double z, float yaw, float pitch) {
        posX = prevPosX = lastTickPosX = x;
        posY = prevPosY = lastTickPosY = y;
        posZ = prevPosZ = lastTickPosZ = z;
        rotationYaw = prevRotationYaw = rotationYawHead = prevRotationYawHead = yaw;
        rotationPitch = prevRotationPitch = pitch;
        boundingBox.setBounds(x - 0.1, y - 0.1, z - 0.1, x + 0.1, y + 0.1, z + 0.1);
    }

    @Override
    public void addChatMessage(IChatComponent message) {}

    @Override
    public boolean canCommandSenderUseCommand(int level, String command) {
        return false;
    }

    @Override
    public ChunkCoordinates getPlayerCoordinates() {
        return new ChunkCoordinates(MathHelper.floor_double(posX), MathHelper.floor_double(posY), MathHelper.floor_double(posZ));
    }
}
