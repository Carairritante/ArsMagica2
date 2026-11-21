package am2.preloader;

import cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** Route just the final block placement in the falling-block update through the landing check. */
public final class TornadoLandingTransformer {
    private TornadoLandingTransformer() {}

    public static byte[] transform(byte[] bytes) {
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, 0);
        boolean raw = !node.name.startsWith("net/minecraft/");
        for (Object entry : node.methods) {
            MethodNode method = (MethodNode)entry;
            if (!method.desc.equals("()V")) continue;
            String name = raw ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(node.name, method.name, method.desc) : method.name;
            if (!name.equals("onUpdate") && !name.equals("func_70071_h_")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() != Opcodes.INVOKEVIRTUAL) continue;
                MethodInsnNode call = (MethodInsnNode)instruction;
                String owner = raw ? FMLDeobfuscatingRemapper.INSTANCE.map(call.owner) : call.owner;
                if (!owner.equals("net/minecraft/world/World")) continue;
                String called = raw ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodName(call.owner, call.name, call.desc) : call.name;
                String desc = raw ? FMLDeobfuscatingRemapper.INSTANCE.mapMethodDesc(call.desc) : call.desc;
                if (!(called.equals("setBlock") || called.equals("func_147465_d")) || !desc.equals("(IIILnet/minecraft/block/Block;II)Z")) continue;
                method.instructions.insertBefore(call, new VarInsnNode(Opcodes.ALOAD, 0));
                method.instructions.set(call, new MethodInsnNode(Opcodes.INVOKESTATIC, "am2/entities/TornadoLanding",
                        "place", "(Ljava/lang/Object;IIILjava/lang/Object;IILjava/lang/Object;)Z", false));
                ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                node.accept(writer);
                return writer.toByteArray();
            }
        }
        return bytes;
    }
}
