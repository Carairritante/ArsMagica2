import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.*;

/** The integrated-server worker must be initialized before either sided proxy. */
public final class VerifyRitualArchitectJar {
 public static void main(String[] args) throws Exception {
  try (JarFile jar = new JarFile(args[0])) {
   ClassNode core = read(jar, "am2/AMCore.class");
   boolean commonRegistration = false;
   for (MethodNode method : core.methods) if (method.name.equals("preInit")) {
    boolean worker = false;
    for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
     MethodInsnNode call = (MethodInsnNode)instruction;
     if (call.owner.equals("am2/ritual/RitualArchitectServer") && call.name.equals("register")) worker = true;
     if (call.name.equals("InitializeAndRegisterHandlers") && worker) commonRegistration = true;
    }
   }
   if (!commonRegistration) throw new IllegalStateException("Ritual worker is not registered before sided proxy initialization: single-player GUI actions will never run");
   ClassNode server = read(jar, "am2/ritual/RitualArchitectServer.class");
   boolean registeredOnFmlBus = false, tickDrains = false, guarded = false;
   for (MethodNode method : server.methods) {
    if (method.name.equals("register")) {
     guarded = (method.access & Opcodes.ACC_SYNCHRONIZED) != 0;
     for (AbstractInsnNode instruction : method.instructions.toArray()) if (instruction instanceof MethodInsnNode) {
      MethodInsnNode call = (MethodInsnNode)instruction;
      if (call.owner.equals("cpw/mods/fml/common/eventhandler/EventBus") && call.name.equals("register")) registeredOnFmlBus = true;
     }
    }
    if (method.name.equals("onServerTick")) for (AbstractInsnNode instruction : method.instructions.toArray())
     if (instruction instanceof MethodInsnNode && ((MethodInsnNode)instruction).name.equals("drain")) tickDrains = true;
   }
   if (!registeredOnFmlBus || !tickDrains || !guarded) throw new IllegalStateException("Ritual worker registration or tick handler is incomplete");
   System.out.println("Verified: ritual actions run from the common initialization path on integrated and dedicated servers.");
  }
 }
 static ClassNode read(JarFile jar, String path) throws Exception {
  ClassNode node = new ClassNode(); new ClassReader(jar.getInputStream(jar.getJarEntry(path))).accept(node, 0); return node;
 }
}
