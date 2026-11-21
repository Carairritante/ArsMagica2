package am2.shield;

import am2.preloader.ManaShieldTransformer;
import java.nio.file.*;
import java.util.*;
import java.util.jar.*;
import java.io.*;
import org.junit.Test;
import static org.junit.Assert.*;
import org.objectweb.asm.*;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;

public class ManaShieldTransformerTest {
    @Test public void realMinecraftMethodsRemainVerifiableAndIdempotent() throws Exception {
        for (String jarName : new String[]{"recompiled_minecraft-1.7.10.jar", "srg_merged_minecraft.jar"}) {
            try (JarFile jar = new JarFile("build/rfg/" + jarName)) {
                int health = 0, damage = 0, death = 0, potion = 0, actor = 0, packet = 0;
                for (String name : Arrays.asList("net.minecraft.entity.Entity", "net.minecraft.entity.EntityLivingBase",
                        "net.minecraft.entity.player.EntityPlayer", "net.minecraft.entity.player.EntityPlayerMP",
                        "net.minecraft.world.World", "net.minecraft.network.NetHandlerPlayServer")) {
                    byte[] original = read(jar, name);
                    byte[] patched = ManaShieldTransformer.transform(name, original);
                    assertFalse(name, Arrays.equals(original, patched));
                    assertArrayEquals(patched, ManaShieldTransformer.transform(name, patched));
                    ClassNode node = new ClassNode(); new ClassReader(patched).accept(node, ClassReader.EXPAND_FRAMES);
                    for (Object raw : node.methods) {
                        MethodNode method = (MethodNode)raw;
                        if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                            new Analyzer(new BasicVerifier()).analyze(node.name, method);
                        for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                            MethodInsnNode call = (MethodInsnNode)i;
                            if (!call.owner.equals("am2/shield/ManaShieldWard")) continue;
                            if (call.name.equals("filterHealth")) health++;
                            if (call.name.equals("enterDamage")) damage++;
                            if (call.name.equals("preventDeath")) death++;
                            if (call.name.equals("preventPotion")) potion++;
                            if (call.name.equals("enterActor")) actor++;
                            if (call.name.equals("enterPacket")) packet++;
                        }
                    }
                }
                assertEquals(1, health); assertEquals(2, damage); assertEquals(3, death);
                assertEquals(1, potion); assertEquals(1, actor); assertTrue(packet >= 15);
            }
        }
    }
    static byte[] read(JarFile jar, String name) throws Exception {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream in = jar.getInputStream(jar.getJarEntry(name.replace('.', '/') + ".class"))) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) > 0) out.write(b, 0, n);
        }
        return out.toByteArray();
    }
    @org.junit.Test public void optionalPackGuardsMatchRealBytecode() throws Exception {
        String path = System.getProperty("astral.pack.mods"); org.junit.Assume.assumeTrue(path != null);
        java.io.File root = new java.io.File(path); int checked = 0;
        String[] names = {"api.player.server.ServerPlayerAPI", "electroblob.wizardry.spell.Telekinesis",
            "com.emoniph.witchery.infusion.infusions.symbols.EffectRegistry$12"};
        for (java.io.File file : root.listFiles()) if (file.getName().endsWith(".jar")) try (JarFile jar = new JarFile(file)) {
            for (String name : names) if (jar.getJarEntry(name.replace('.', '/') + ".class") != null) {
                byte[] original = read(jar, name), patched = ManaShieldTransformer.transform(name, original);
                assertFalse(name, java.util.Arrays.equals(original, patched));
                assertArrayEquals(patched, ManaShieldTransformer.transform(name, patched));
                ClassNode node = new ClassNode(); new ClassReader(patched).accept(node, ClassReader.EXPAND_FRAMES);
                int guards = 0; java.util.Map<String, Integer> hooks = new java.util.HashMap<String, Integer>();
                for (Object raw : node.methods) {
                    MethodNode method = (MethodNode)raw;
                    if ((method.access & (Opcodes.ACC_ABSTRACT | Opcodes.ACC_NATIVE)) == 0)
                        new Analyzer(new BasicVerifier()).analyze(node.name, method);
                    for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode
                        && ((MethodInsnNode)i).owner.equals("am2/shield/ManaShieldWard")) {
                        guards++; String hook = ((MethodInsnNode)i).name;
                        hooks.put(hook, hooks.containsKey(hook) ? hooks.get(hook) + 1 : 1);
                    }
                }
                assertTrue(name + " guards", guards >= 2); checked++;
                if (name.endsWith("Telekinesis")) assertEquals(Integer.valueOf(1), hooks.get("filterHeldItem"));
                if (name.endsWith("EffectRegistry$12")) assertEquals(Integer.valueOf(1), hooks.get("preventTheft"));
                if (name.endsWith("ServerPlayerAPI")) {
                    assertEquals(Integer.valueOf(1), hooks.get("preventControlFrom"));
                    assertEquals(Integer.valueOf(1), hooks.get("enterDamage"));
                    assertEquals(Integer.valueOf(1), hooks.get("preventDeath"));
                    assertEquals(Integer.valueOf(1), hooks.get("preventSetDead"));
                }
            }
        }
        assertEquals("all three inspected optional APIs", 3, checked);
    }
}
