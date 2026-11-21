package am2.preloader;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** In-place guards: preserve vanilla method identities for other coremods. */
public final class ManaShieldTransformer implements Opcodes {
    private static final String HOOK = "am2/shield/ManaShieldWard";
    private ManaShieldTransformer() {}

    public static boolean accepts(String name) {
        return "net.minecraft.entity.EntityLivingBase".equals(name)
            || "net.minecraft.entity.Entity".equals(name)
            || "net.minecraft.entity.player.EntityPlayer".equals(name)
            || "net.minecraft.entity.player.EntityPlayerMP".equals(name)
            || "net.minecraft.world.World".equals(name)
            || "net.minecraft.network.NetHandlerPlayServer".equals(name)
            || "am2.spell.SpellHelper".equals(name)
            || "com.emoniph.witchery.infusion.infusions.symbols.EffectRegistry$12".equals(name)
            || "electroblob.wizardry.spell.Telekinesis".equals(name)
            || "api.player.server.ServerPlayerAPI".equals(name);
    }

    public static byte[] transform(String name, byte[] bytes) {
        if (bytes == null || !accepts(name)) return bytes;
        ClassNode node = new ClassNode();
        new ClassReader(bytes).accept(node, ClassReader.EXPAND_FRAMES);
        boolean changed = false;
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode)raw;
            if ((method.access & (ACC_ABSTRACT | ACC_NATIVE)) != 0) continue;
            boolean patched = false;
            for (AbstractInsnNode i : method.instructions.toArray())
                if (i instanceof MethodInsnNode && HOOK.equals(((MethodInsnNode)i).owner)) patched = true;
            if (patched) continue;
            String m = method.name;
            String descriptor = method.desc;
            if (name.equals("api.player.server.ServerPlayerAPI")) {
                // PlayerAPI adds MP overrides AFTER this transformer sees vanilla
                // classes. Guard its public static dispatchers before any override.
                if ((method.access & ACC_STATIC) == 0 || !method.desc.startsWith("(Lapi/player/server/IServerPlayerAPI;")) continue;
                if (m.equals("damageEntity") && method.desc.equals("(Lapi/player/server/IServerPlayerAPI;Lnet/minecraft/util/DamageSource;F)V")) {
                    scope(node, method, "Damage", 0, 1); changed = true;
                } else if (m.equals("knockBack") && method.desc.equals("(Lapi/player/server/IServerPlayerAPI;Lnet/minecraft/entity/Entity;FDD)V")) {
                    guard(node, method, "preventControlFrom", "(Ljava/lang/Object;Ljava/lang/Object;)Z", args(0, 1)); changed = true;
                } else if (m.equals("onDeath") && method.desc.equals("(Lapi/player/server/IServerPlayerAPI;Lnet/minecraft/util/DamageSource;)V")) {
                    guard(node, method, "preventDeath", "(Ljava/lang/Object;Ljava/lang/Object;)Z", args(0, 1)); changed = true;
                } else if (m.equals("setDead") && method.desc.equals("(Lapi/player/server/IServerPlayerAPI;)V")) {
                    guard(node, method, "preventSetDead", "(Ljava/lang/Object;)Z", args(0)); changed = true;
                }
                continue;
            }
            if ((method.access & ACC_STATIC) != 0) continue;
            // This coremod can run BEFORE FML's deobfuscation transformer.
            // Match raw vanilla bytecode through FML's real mappings, retaining
            // the original method names/descriptors and stack-map types in output.
            if (name.startsWith("net.minecraft.") && !node.name.startsWith("net/minecraft/")) {
                cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper remapper =
                    cpw.mods.fml.common.asm.transformers.deobf.FMLDeobfuscatingRemapper.INSTANCE;
                m = remapper.mapMethodName(node.name, m, method.desc);
                descriptor = remapper.mapMethodDesc(method.desc);
            }
            if (is(m, "setHealth", "func_70606_j") && method.desc.equals("(F)V")) {
                InsnList code = args(0); code.add(new VarInsnNode(FLOAD, 1));
                call(code, "filterHealth", "(Ljava/lang/Object;F)F"); code.add(new VarInsnNode(FSTORE, 1));
                method.instructions.insert(code); changed = true;
            } else if (is(m, "damageEntity", "func_70665_d")) {
                // SRG name in 1.7.10 is func_70665_d; MCP source parameters retain the old suffix.
                if (Type.getArgumentTypes(method.desc).length == 2 && method.desc.endsWith(";F)V")) {
                    scope(node, method, "Damage", 0, 1); changed = true;
                }
            } else if (is(m, "onDeath", "func_70645_a") && method.desc.endsWith(";)V")) {
                guard(node, method, "preventDeath", "(Ljava/lang/Object;Ljava/lang/Object;)Z", args(0, 1)); changed = true;
            } else if (is(m, "setDead", "func_70106_y") && method.desc.equals("()V")) {
                guard(node, method, "preventSetDead", "(Ljava/lang/Object;)Z", args(0)); changed = true;
            } else if (is(m, "setFire", "func_70015_d") && method.desc.equals("(I)V")) {
                InsnList code = args(0); code.add(new VarInsnNode(ILOAD, 1));
                guard(node, method, "preventFire", "(Ljava/lang/Object;I)Z", code); changed = true;
            } else if (is(m, "getActivePotionEffects", "func_70651_bq") && method.desc.equals("()Ljava/util/Collection;")) {
                for (AbstractInsnNode i : method.instructions.toArray()) if (i.getOpcode() == ARETURN) {
                    InsnList code = args(0); call(code, "guardEffects", "(Ljava/util/Collection;Ljava/lang/Object;)Ljava/util/Collection;");
                    method.instructions.insertBefore(i, code);
                }
                changed = true;
            } else if (is(m, "addPotionEffect", "func_70690_d") && method.desc.endsWith(";)V")) {
                guard(node, method, "preventPotion", "(Ljava/lang/Object;Ljava/lang/Object;)Z", args(0, 1)); changed = true;
            } else if (is(m, "clearActivePotions", "func_70674_bp") && method.desc.equals("()V")) {
                guard(node, method, "preventClear", "(Ljava/lang/Object;)Z", args(0)); changed = true;
            } else if ((is(m, "removePotionEffect", "func_82170_o") || is(m, "removePotionEffectClient", "func_70618_n")) && method.desc.equals("(I)V")) {
                InsnList code = args(0); code.add(new VarInsnNode(ILOAD, 1));
                guard(node, method, "preventRemove", "(Ljava/lang/Object;I)Z", code); changed = true;
            } else if (is(m, "knockBack", "func_70653_a") && method.desc.endsWith(";FDD)V")) {
                guard(node, method, "preventControlFrom", "(Ljava/lang/Object;Ljava/lang/Object;)Z", args(0, 1)); changed = true;
            } else if ((is(m, "setPositionAndUpdate", "func_70634_a") || is(m, "addVelocity", "func_70024_g")) && method.desc.equals("(DDD)V")) {
                guard(node, method, "preventControl", "(Ljava/lang/Object;)Z", args(0)); changed = true;
            } else if (name.equals("net.minecraft.world.World") && is(m, "updateEntityWithOptionalForce", "func_72866_a") && method.desc.endsWith(";Z)V")) {
                scope(node, method, "Actor", 1); changed = true;
            } else if (name.equals("net.minecraft.network.NetHandlerPlayServer") && descriptor.startsWith("(Lnet/minecraft/network/play/client/") && descriptor.endsWith(";)V")) {
                scope(node, method, "Packet", 0); changed = true;
            } else if (name.equals("am2.spell.SpellHelper") && (m.equals("applyStageToEntity") || m.equals("applyStageToGround"))) {
                scope(node, method, "Actor", 2); changed = true;
            } else if (name.equals("com.emoniph.witchery.infusion.infusions.symbols.EffectRegistry$12")) {
                if (m.equals("disarm") && method.desc.equals("(Lnet/minecraft/entity/EntityLivingBase;)V")) {
                    guard(node, method, "preventTheft", "(Ljava/lang/Object;)Z", args(1)); changed = true;
                } else if (m.equals("onCollision")) { scope(node, method, "Actor", 2); changed = true; }
            } else if (name.equals("electroblob.wizardry.spell.Telekinesis") && m.equals("cast")) {
                // Verified player overload: World, EntityPlayer, four float modifiers.
                // Cancel its held-item test, BEFORE spawning the stolen copy or clearing the slot.
                if (!method.desc.startsWith("(Lnet/minecraft/world/World;Lnet/minecraft/entity/player/EntityPlayer;")) continue;
                for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                    MethodInsnNode invoke = (MethodInsnNode)i;
                    if (!is(invoke.name, "getHeldItem", "func_70694_bm") || i.getNext().getOpcode() != IFNULL) continue;
                    method.instructions.insertBefore(i, new InsnNode(DUP));
                    InsnList filter = new InsnList(); call(filter, "filterHeldItem", "(Ljava/lang/Object;Ljava/lang/Object;)Ljava/lang/Object;");
                    filter.add(new TypeInsnNode(CHECKCAST, Type.getReturnType(invoke.desc).getInternalName()));
                    method.instructions.insert(i, filter); changed = true;
                }
                scope(node, method, "Actor", 2); changed = true;
            }
        }
        if (!changed) return bytes;
        ClassWriter writer = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        node.accept(writer); return writer.toByteArray();
    }

    private static boolean is(String actual, String mcp, String srg) { return actual.equals(mcp) || actual.equals(srg); }
    private static InsnList args(int... slots) {
        InsnList code = new InsnList();
        for (int slot : slots) code.add(new VarInsnNode(ALOAD, slot));
        return code;
    }
    private static void call(InsnList code, String method, String desc) {
        code.add(new MethodInsnNode(INVOKESTATIC, HOOK, method, desc, false));
    }
    private static Object[] locals(ClassNode owner, MethodNode method) {
        List<Object> locals = new ArrayList<Object>();
        if ((method.access & ACC_STATIC) == 0) locals.add(owner.name);
        for (Type type : Type.getArgumentTypes(method.desc)) {
            switch (type.getSort()) {
                case Type.FLOAT: locals.add(FLOAT); break;
                case Type.DOUBLE: locals.add(DOUBLE); break;
                case Type.LONG: locals.add(LONG); break;
                case Type.OBJECT: locals.add(type.getInternalName()); break;
                case Type.ARRAY: locals.add(type.getDescriptor()); break;
                default: locals.add(INTEGER);
            }
        }
        return locals.toArray();
    }
    private static FrameNode frame(ClassNode owner, MethodNode method, Object[] stack) {
        Object[] local = locals(owner, method);
        return new FrameNode(F_NEW, local.length, local, stack.length, stack);
    }
    private static void guard(ClassNode owner, MethodNode method, String hook, String desc, InsnList code) {
        call(code, hook, desc);
        LabelNode original = new LabelNode(); code.add(new JumpInsnNode(IFEQ, original));
        code.add(new InsnNode(RETURN)); code.add(original); code.add(frame(owner, method, new Object[0]));
        method.instructions.insert(code);
    }
    /** Finally scopes are in-place, including exceptional exits; no reflection or method renaming. */
    private static void scope(ClassNode owner, MethodNode method, String kind, int... slots) {
        String desc = slots.length == 2 ? "(Ljava/lang/Object;Ljava/lang/Object;)V" : "(Ljava/lang/Object;)V";
        AbstractInsnNode[] original = method.instructions.toArray();
        InsnList begin = args(slots); call(begin, "enter" + kind, desc);
        LabelNode start = new LabelNode(), end = new LabelNode(), handler = new LabelNode();
        begin.add(start); method.instructions.insert(begin);
        for (AbstractInsnNode i : original) if (i.getOpcode() >= IRETURN && i.getOpcode() <= RETURN) {
            InsnList exit = args(slots); call(exit, "exit" + kind, desc); method.instructions.insertBefore(i, exit);
        }
        int exception = method.maxLocals++;
        method.instructions.add(end); method.instructions.add(handler);
        Object[] locals = locals(owner, method);
        method.instructions.add(new FrameNode(F_NEW, locals.length, locals, 1, new Object[]{"java/lang/Throwable"}));
        method.instructions.add(new VarInsnNode(ASTORE, exception));
        InsnList exit = args(slots); call(exit, "exit" + kind, desc); method.instructions.add(exit);
        method.instructions.add(new VarInsnNode(ALOAD, exception)); method.instructions.add(new InsnNode(ATHROW));
        method.tryCatchBlocks.add(new TryCatchBlockNode(start, end, handler, null));
    }
}
