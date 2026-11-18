package am2.compass;

import org.junit.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;
import java.io.InputStream;
import static org.junit.Assert.*;

/** Both physical sides must start the logical-server request queue. */
public class AstralCompassLifecycleTest {
    private ClassNode read(String name) throws Exception {
        ClassNode node = new ClassNode();
        try (InputStream in = getClass().getResourceAsStream("/" + name + ".class")) {
            assertNotNull(in);
            new ClassReader(in).accept(node, 0);
        }
        return node;
    }

    @Test public void sharedInitializationStartsServiceAndClientCallsIt() throws Exception {
        ClassNode common = read("am2/proxy/CommonProxy");
        int instances = 0;
        for (Object raw : common.methods) {
            MethodNode method = (MethodNode) raw;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction.getOpcode() == Opcodes.NEW && instruction instanceof TypeInsnNode
                        && ((TypeInsnNode) instruction).desc.equals("am2/compass/AstralCompassService")) {
                    assertEquals("Service must start on the path shared with integrated servers", "init", method.name);
                    instances++;
                }
            }
        }
        assertEquals("Exactly one service registration", 1, instances);
        boolean callsCommon = false;
        for (Object raw : read("am2/proxy/ClientProxy").methods) {
            MethodNode method = (MethodNode) raw;
            if (!method.name.equals("init")) continue;
            for (AbstractInsnNode instruction : method.instructions.toArray()) {
                if (instruction instanceof MethodInsnNode) {
                    MethodInsnNode call = (MethodInsnNode) instruction;
                    callsCommon |= call.getOpcode() == Opcodes.INVOKESPECIAL
                            && call.owner.equals("am2/proxy/CommonProxy") && call.name.equals("init");
                }
            }
        }
        assertTrue("Client must also run the shared server-service initialization", callsCommon);
    }
}
