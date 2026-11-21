package am2.preloader;

import am2.LogHelper;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Soft client integration: Futura replaces the vanilla body render entirely. */
public final class FuturaShrinkTransformer implements Opcodes {
    private FuturaShrinkTransformer() {}

    public static byte[] transform(byte[] bytes) {
        if (bytes == null) return null;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        if (!node.name.equals("com/akashic/futura/client/ThirdPersonRenderer")) return bytes;
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode)raw;
            if (!method.name.equals("placement") || (method.access & ACC_STATIC) == 0
                    || !method.desc.equals("(Lnet/minecraft/entity/player/EntityPlayer;DDDFFF[F)V")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (!(instruction instanceof MethodInsnNode)) continue;
                MethodInsnNode call = (MethodInsnNode)instruction;
                if (call.owner.equals("am2/utility/RenderUtilities") && call.name.equals("scalePlayerModelMatrix")) return bytes;
            }
            int patched = 0;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != RETURN) continue;
                InsnList hook = new InsnList();
                hook.add(new VarInsnNode(ALOAD, 0));
                hook.add(new VarInsnNode(ALOAD, 10));
                hook.add(new MethodInsnNode(INVOKESTATIC, "am2/utility/RenderUtilities", "scalePlayerModelMatrix",
                        "(Lnet/minecraft/entity/EntityLivingBase;[F)V", false));
                method.instructions.insertBefore(instruction, hook);
                patched++;
            }
            if (patched == 0) break;
            ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
            node.accept(writer);
            LogHelper.info("Core: Optional Futura3D player shrink placement applied (" + patched + " exits).");
            return writer.toByteArray();
        }
        LogHelper.warn("Core: Optional Futura3D placement signature differs; shrink adapter was not applied.");
        return bytes;
    }
}
