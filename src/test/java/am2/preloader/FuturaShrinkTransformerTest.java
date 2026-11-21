package am2.preloader;

import java.io.*;
import java.util.*;
import java.util.jar.*;
import org.junit.Test;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class FuturaShrinkTransformerTest {
    @Test public void validatesBothInstalledFuturaBinaries() throws Exception {
        String paths = System.getenv("FUTURA_SHRINK_TEST_JARS");
        org.junit.Assume.assumeTrue(paths != null && !paths.isEmpty());
        for (String path : paths.split(java.util.regex.Pattern.quote(File.pathSeparator))) {
            byte[] original;
            try (JarFile jar = new JarFile(path); InputStream in = jar.getInputStream(
                    jar.getJarEntry("com/akashic/futura/client/ThirdPersonRenderer.class"))) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192]; int n;
                while ((n = in.read(buffer)) > 0) out.write(buffer, 0, n);
                original = out.toByteArray();
            }
            byte[] patched = FuturaShrinkTransformer.transform(original);
            assertFalse(path, Arrays.equals(original, patched));
            assertArrayEquals(patched, FuturaShrinkTransformer.transform(patched));
            ClassNode node = new ClassNode(); new ClassReader(patched).accept(node, ClassReader.EXPAND_FRAMES);
            int exits = 0, calls = 0;
            for (Object raw : node.methods) {
                MethodNode method = (MethodNode)raw;
                if (!method.name.equals("placement")) continue;
                new Analyzer(new BasicVerifier()).analyze(node.name, method);
                for (AbstractInsnNode insn : method.instructions.toArray()) {
                    if (insn.getOpcode() == Opcodes.RETURN) {
                        exits++;
                        assertTrue(insn.getPrevious() instanceof MethodInsnNode);
                        assertEquals("scalePlayerModelMatrix", ((MethodInsnNode)insn.getPrevious()).name);
                    }
                    if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).name.equals("scalePlayerModelMatrix")) {
                        calls++;
                        assertEquals(10, ((VarInsnNode)insn.getPrevious()).var);
                        assertEquals(0, ((VarInsnNode)insn.getPrevious().getPrevious()).var);
                    }
                }
            }
            assertTrue(exits > 0); assertEquals(exits, calls);
        }
    }

    @Test public void ignoresUnrelatedClasses() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/am2/preloader/FuturaShrinkTransformer.class")) {
            ByteArrayOutputStream out = new ByteArrayOutputStream(); byte[] buf = new byte[4096]; int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            byte[] bytes = out.toByteArray(); assertSame(bytes, FuturaShrinkTransformer.transform(bytes));
        }
        assertNull(FuturaShrinkTransformer.transform(null));
    }
}
