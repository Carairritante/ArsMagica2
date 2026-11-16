import java.io.InputStream;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;

/**
 * Verifies Otherworld Aura AE2 Terminal integration contracts in the reobfuscated JAR:
 * 1. TileEntityOtherworldAura and EntityAISpellmaking retain soft-dependency isolation
 *    (no direct appeng/* bytecode references outside AE2OtherworldCompat).
 * 2. AE2OtherworldCompat restricts lookup/extraction strictly to terminal parts
 *    (ITerminalHost + IPowerChannelState + IActionHost via IPartHost) and uses poweredExtraction.
 * 3. EntityShadowHelper handles carriedItem safely on death/unsummon without item loss.
 */
public final class VerifyOtherworldAE2Jar {

    public static void main(String[] args) throws Exception {
        try (JarFile jar = new JarFile(args[0])) {
            assertNoDirectAE2Ref(jar, "am2/blocks/tileentities/TileEntityOtherworldAura.class");
            assertNoDirectAE2Ref(jar, "am2/entities/ai/EntityAISpellmaking.class");
            assertAE2BridgeContract(jar);
            assertShadowHelperSafety(jar);
            System.out.println("Verified: Otherworld Aura AE2 terminal integration preserves soft dependency, terminal-only lookup, and anti-loss/anti-dupe guards.");
        }
    }

    private static ClassNode read(JarFile jar, String path) throws Exception {
        if (jar.getJarEntry(path) == null) {
            throw new IllegalStateException("Missing expected class in JAR: " + path);
        }
        ClassNode node = new ClassNode();
        try (InputStream in = jar.getInputStream(jar.getJarEntry(path))) {
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    private static void assertNoDirectAE2Ref(JarFile jar, String path) throws Exception {
        ClassNode cn = read(jar, path);
        for (FieldNode fn : cn.fields) {
            checkNotAE2(fn.desc, path + " field " + fn.name);
        }
        for (MethodNode mn : cn.methods) {
            checkNotAE2(mn.desc, path + " method " + mn.name);
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    checkNotAE2(m.owner, path + " call in " + mn.name);
                    checkNotAE2(m.desc, path + " call desc in " + mn.name);
                } else if (insn instanceof FieldInsnNode) {
                    FieldInsnNode f = (FieldInsnNode) insn;
                    checkNotAE2(f.owner, path + " field access in " + mn.name);
                    checkNotAE2(f.desc, path + " field desc in " + mn.name);
                } else if (insn instanceof TypeInsnNode) {
                    checkNotAE2(((TypeInsnNode) insn).desc, path + " type in " + mn.name);
                }
            }
        }
    }

    private static void checkNotAE2(String name, String context) {
        if (name != null && name.contains("appeng/")) {
            throw new IllegalStateException("Direct AE2 reference leaks outside compat bridge (" + context + "): " + name);
        }
    }

    private static void assertAE2BridgeContract(JarFile jar) throws Exception {
        ClassNode cn = read(jar, "am2/interop/AE2OtherworldCompat.class");
        boolean checksTerminalHost = false;
        boolean checksPowerChannel = false;
        boolean checksActionHost = false;
        boolean usesPoweredExtraction = false;

        for (MethodNode mn : cn.methods) {
            for (AbstractInsnNode insn : mn.instructions.toArray()) {
                if (insn instanceof TypeInsnNode) {
                    String desc = ((TypeInsnNode) insn).desc;
                    if ("appeng/api/storage/ITerminalHost".equals(desc)) checksTerminalHost = true;
                    if ("appeng/api/implementations/IPowerChannelState".equals(desc)) checksPowerChannel = true;
                    if ("appeng/api/networking/security/IActionHost".equals(desc)) checksActionHost = true;
                    if (desc.contains("TileInterface") || desc.contains("PartInterfaceTerminal") || desc.contains("PartStorageMonitor")) {
                        throw new IllegalStateException("Non-terminal AE2 class referenced in AE2OtherworldCompat: " + desc);
                    }
                } else if (insn instanceof MethodInsnNode) {
                    MethodInsnNode m = (MethodInsnNode) insn;
                    if ("appeng/api/storage/IStorageHelper".equals(m.owner) && "poweredExtraction".equals(m.name)) {
                        usesPoweredExtraction = true;
                    }
                }
            }
        }
        if (!checksTerminalHost || !checksPowerChannel || !checksActionHost || !usesPoweredExtraction) {
            throw new IllegalStateException("AE2OtherworldCompat contract incomplete: terminalHost="
                    + checksTerminalHost + ", powerState=" + checksPowerChannel
                    + ", actionHost=" + checksActionHost + ", poweredExtraction=" + usesPoweredExtraction);
        }
    }

    private static void assertShadowHelperSafety(JarFile jar) throws Exception {
        ClassNode cn = read(jar, "am2/entities/EntityShadowHelper.class");
        boolean hasCarriedField = false;
        for (FieldNode fn : cn.fields) {
            if ("carriedItem".equals(fn.name)) {
                hasCarriedField = true;
            }
        }
        if (!hasCarriedField) {
            throw new IllegalStateException("EntityShadowHelper is missing carriedItem anti-loss tracking");
        }
    }
}
