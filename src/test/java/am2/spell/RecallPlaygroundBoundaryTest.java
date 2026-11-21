package am2.spell;

import org.junit.Test;
import static org.junit.Assert.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;

/** Playground's released binary injects ALOAD14 at the component call. */
public class RecallPlaygroundBoundaryTest {
    @Test public void preservesExternalTransformerComponentLocal() throws Exception {
        java.io.InputStream in = getClass().getResourceAsStream("/am2/spell/SpellHelper.class");
        assertNotNull(in); ClassNode node = new ClassNode();
        try { new ClassReader(in).accept(node, 0); } finally { in.close(); }
        int calls = 0;
        for (Object raw : node.methods) {
            MethodNode method = (MethodNode)raw;
            if (!method.name.equals("applyStageToEntity")) continue;
            for (AbstractInsnNode insn : method.instructions.toArray()) if (insn instanceof MethodInsnNode) {
                MethodInsnNode call = (MethodInsnNode)insn;
                if (!call.owner.equals("am2/api/spell/component/interfaces/ISpellComponent") || !call.name.equals("applyEffectEntity")) continue;
                AbstractInsnNode component = insn;
                for (int argument = 0; argument < 5; argument++) {
                    do { component = component.getPrevious(); } while (component.getOpcode() < 0);
                }
                assertTrue(component instanceof VarInsnNode);
                assertEquals(Opcodes.ALOAD, component.getOpcode());
                assertEquals("Playground's binary slot for component", 14, ((VarInsnNode)component).var);
                calls++;
            }
        }
        assertEquals(1, calls);
    }
    @Test public void validatesOptionalComponentGuardsInRealAddon() throws Exception {
        String path = System.getProperty("astral.pack.mods"); org.junit.Assume.assumeTrue(path != null);
        int count = 0;
        for (java.io.File file : new java.io.File(path).listFiles()) if (file.getName().endsWith(".jar")) try (java.util.jar.JarFile jar = new java.util.jar.JarFile(file)) {
            for (String simple : new String[]{"NetherIntervention", "CrossDimensionalIntervention"}) {
                String name = "airburn.am2playground.spell.components." + simple;
                java.util.jar.JarEntry entry = jar.getJarEntry(name.replace('.', '/') + ".class");
                if (entry == null) continue;
                java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
                try (java.io.InputStream in = jar.getInputStream(entry)) { byte[] bytes = new byte[8192]; int n; while ((n = in.read(bytes)) > 0) out.write(bytes, 0, n); }
                byte[] original = out.toByteArray(), patched = am2.preloader.RecallInterventionTransformer.transform(name, original);
                assertFalse(java.util.Arrays.equals(original, patched));
                assertArrayEquals(patched, am2.preloader.RecallInterventionTransformer.transform(name, patched));
                ClassNode node = new ClassNode(); new ClassReader(patched).accept(node, ClassReader.EXPAND_FRAMES);
                for (Object raw : node.methods) {
                    MethodNode method = (MethodNode)raw;
                    if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                        new org.objectweb.asm.tree.analysis.Analyzer(new org.objectweb.asm.tree.analysis.BasicVerifier()).analyze(node.name, method);
                }
                count++;
            }
        }
        assertEquals(2, count);
    }
}
