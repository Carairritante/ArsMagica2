import java.util.jar.JarFile;
import java.util.jar.JarEntry;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.FieldInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.AbstractInsnNode;

public final class VerifyLightningJar {
	public static void main(String[] args) throws Exception{
		try (JarFile jar = new JarFile(args[0])){
			ClassNode lightning = read(jar, "am2/spell/components/LightningStrike.class");
			ClassNode zone = read(jar, "am2/spell/shapes/Zone.class");
			ClassNode effect = read(jar, "am2/entities/EntitySpellEffect.class");
			ClassNode spellHelper = read(jar, "am2/spell/SpellHelper.class");
			ClassNode limiter = read(jar, "am2/spell/components/LightningStrikeRateLimiter.class");
			ClassNode worldState = read(jar, "am2/spell/components/LightningStrikeRateLimiter$WorldState.class");
			ClassNode blockPosition = read(jar, "am2/spell/components/LightningStrikeRateLimiter$BlockPosition.class");
			ClassNode castWindow = read(jar, "am2/spell/components/LightningStrikeRateLimiter$CastWindow.class");
			ClassNode channelUse = read(jar, "am2/spell/components/LightningStrikeRateLimiter$ChannelUse.class");
			JarEntry helper = jar.getJarEntry("am2/spell/components/LightningStrikeRateLimiter.class");
			if (helper == null) throw new IllegalStateException("Limiter helper missing");
			int helperMajor = new ClassReader(jar.getInputStream(helper)).readUnsignedShort(6);
			if (helperMajor > 52) throw new IllegalStateException("Helper is not Java 8 compatible: " + helperMajor);
			boolean threeSecondInterval = false;
			for (org.objectweb.asm.tree.FieldNode field : limiter.fields){
			if (field.name.equals("ZONE_TICK_RATE") && Integer.valueOf(30).equals(field.value)) threeSecondInterval = true;
			}
		if (!threeSecondInterval) throw new IllegalStateException("Lightning Zone interval is not 1.5 seconds");
			if (!calls(lightning, "applyEffectBlock", "spawnBlockBolt")) throw new IllegalStateException("Block limiter call missing");
			if (!calls(zone, "beginStackStage", "configureZone")) throw new IllegalStateException("Zone limiter call missing");
			if (!calls(spellHelper, "applyStackStage", "applyCastLimit", "(Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;Ljava/lang/Object;)V")) throw new IllegalStateException("Player cast limiter call missing");
			method(worldState, "allow", "(Ljava/lang/Object;Ljava/lang/Object;III)Z");
			method(blockPosition, "equals", "(Ljava/lang/Object;)Z");
			method(blockPosition, "hashCode", "()I");
			method(limiter, "applyCastLimit");
			method(limiter, "getComponentNameViaSpellUtils");
			method(limiter, "getCastLimitedComponentName");
			method(limiter, "containsZoneLimitedComponent");
			method(limiter, "componentNameForId");
			method(limiter, "isSpellChanneled");
			method(limiter, "sendLimitMessage", "(Ljava/lang/Object;Ljava/lang/String;I)V");
			method(castWindow, "<init>", "(J)V");
			method(channelUse, "matches", "(Ljava/lang/Object;I)Z");
			boolean maxThree = false, tenSeconds = false;
			for (org.objectweb.asm.tree.FieldNode field : limiter.fields){
				if (field.name.equals("MAX_PLAYER_CASTS") && Integer.valueOf(3).equals(field.value)) maxThree = true;
				if (field.name.equals("PLAYER_CAST_WINDOW_TICKS") && Long.valueOf(200L).equals(field.value)) tenSeconds = true;
			}
			if (!maxThree || !tenSeconds) throw new IllegalStateException("3 casts per 10-second window constants missing");
			boolean explosionId = false, digId = false;
			for (org.objectweb.asm.tree.FieldNode field : limiter.fields){
				if (field.name.equals("EXPLOSION_COMPONENT_ID") && Integer.valueOf(1092).equals(field.value)) explosionId = true;
				if (field.name.equals("DIG_COMPONENT_ID") && Integer.valueOf(1008).equals(field.value)) digId = true;
			}
			if (!explosionId || !digId) throw new IllegalStateException("Explosion/Dig component IDs missing from cast limiter");
			MethodNode rate = method(effect, "setTickRate");
			boolean current = false, maximum = false;
			for (AbstractInsnNode insn = rate.instructions.getFirst(); insn != null; insn = insn.getNext()){
				if (insn instanceof FieldInsnNode){
					FieldInsnNode field = (FieldInsnNode)insn;
					if (field.name.equals("ticksToEffect")) current = true;
					if (field.name.equals("maxTicksToEffect")) maximum = true;
				}
			}
			if (!current || !maximum) throw new IllegalStateException("Zone tick interval does not update both counters");
		System.out.println("Verified: per-block limiter, 3-pulse base Zone interval, player cast limiter, and Java 8 helper classes.");
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
		return calls(node, methodName, target, null);
	}
	private static boolean calls(ClassNode node, String methodName, String target, String targetDescriptor){
		for (MethodNode method : node.methods){
			if (!method.name.equals(methodName)) continue;
			for (AbstractInsnNode insn = method.instructions.getFirst(); insn != null; insn = insn.getNext()){
				if (insn instanceof MethodInsnNode){
					MethodInsnNode call = (MethodInsnNode)insn;
					if (call.name.equals(target) && (targetDescriptor == null || call.desc.equals(targetDescriptor))) return true;
				}
			}
		}
		return false;
	}
	private static MethodNode method(ClassNode node, String name){
		for (MethodNode method : node.methods) if (method.name.equals(name)) return method;
		throw new IllegalStateException("Method missing: " + name);
	}
	private static MethodNode method(ClassNode node, String name, String descriptor){
		for (MethodNode method : node.methods) if (method.name.equals(name) && method.desc.equals(descriptor)) return method;
		throw new IllegalStateException("Method missing: " + name + descriptor);
	}
}
