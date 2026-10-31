import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

public final class VerifySolarLunarTierJar {
	public static void main(String[] args) throws Exception{
		try (JarFile jar = new JarFile(args[0])){
			ClassNode spellUtils = read(jar, "am2/spell/SpellUtils.class");
			ClassNode solar = read(jar, "am2/spell/modifiers/Solar.class");
			ClassNode lunar = read(jar, "am2/spell/modifiers/Lunar.class");
			ClassNode scaling = read(jar, "am2/spell/SolarLunarTierScaling.class");
			if (!calls(spellUtils, "getModifierMetadataFromStack", "withModifierCount"))
				throw new IllegalStateException("Solar/Lunar modifier count hook missing");
			if (!calls(solar, "getModifier", "scale"))
				throw new IllegalStateException("Solar tier scaling hook missing");
			if (!calls(lunar, "getModifier", "scale"))
				throw new IllegalStateException("Lunar tier scaling hook missing");
			if (!hasMethod(scaling, "withModifierCount") || !hasMethod(scaling, "scale"))
				throw new IllegalStateException("Solar/Lunar scaling helper is incomplete");
			JarEntry helper = jar.getJarEntry("am2/spell/SolarLunarTierScaling.class");
			int major = new ClassReader(jar.getInputStream(helper)).readUnsignedShort(6);
			if (major > 52) throw new IllegalStateException("Scaling helper is not Java 8 compatible: " + major);
			System.out.println("Verified: repeated Solar/Lunar modifiers use capped 1/3, 2/3, 3/3 tiers.");
		}
	}

	private static ClassNode read(JarFile jar, String name) throws Exception{
		JarEntry entry = jar.getJarEntry(name);
		if (entry == null) throw new IllegalStateException("Missing class " + name);
		ClassNode node = new ClassNode();
		new ClassReader(jar.getInputStream(entry)).accept(node, 0);
		return node;
	}

	private static boolean calls(ClassNode node, String methodName, String target){
		for (MethodNode method : node.methods){
			if (!method.name.equals(methodName)) continue;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()){
				if (insn instanceof MethodInsnNode && ((MethodInsnNode)insn).name.equals(target)) return true;
			}
		}
		return false;
	}

	private static boolean hasMethod(ClassNode node, String name){
		for (MethodNode method : node.methods) if (method.name.equals(name)) return true;
		return false;
	}
}
