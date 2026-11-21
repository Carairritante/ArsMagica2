package am2.preloader;

import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Optional component guards AFTER Playground's native call-site validation. */
public final class RecallInterventionTransformer implements Opcodes {
    private RecallInterventionTransformer() {}
    public static byte[] transform(String name, byte[] bytes) {
        if (bytes == null || !(name.equals("airburn.am2playground.spell.components.NetherIntervention")
                || name.equals("airburn.am2playground.spell.components.CrossDimensionalIntervention"))) return bytes;
        ClassNode node = new ClassNode(); new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        boolean changed = false;
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode)raw;
            if (!method.name.equals("applyEffectEntity") || !method.desc.equals("(Lnet/minecraft/item/ItemStack;Lnet/minecraft/world/World;Lnet/minecraft/entity/EntityLivingBase;Lnet/minecraft/entity/Entity;)Z")) continue;
            boolean existing = false;
            for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (call.owner.equals("am2/spell/RecallTravel") && call.name.equals("suppressIntervention")) existing = true;
            }
            if (existing) continue;
            InsnList code = new InsnList(); code.add(new VarInsnNode(ALOAD, 1));
            code.add(new MethodInsnNode(INVOKESTATIC, "am2/spell/RecallTravel", "suppressIntervention", "(Ljava/lang/Object;)Z", false));
            LabelNode proceed = new LabelNode(); code.add(new JumpInsnNode(IFEQ, proceed));
            code.add(new InsnNode(ICONST_0)); code.add(new InsnNode(IRETURN)); code.add(proceed);
            Object[] locals = {node.name, "net/minecraft/item/ItemStack", "net/minecraft/world/World", "net/minecraft/entity/EntityLivingBase", "net/minecraft/entity/Entity"};
            code.add(new FrameNode(F_NEW, locals.length, locals, 0, new Object[0]));
            method.instructions.insert(code); changed = true;
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS); node.accept(writer); return writer.toByteArray();
    }
}
