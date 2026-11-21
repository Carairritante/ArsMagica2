package am2.entities;

import am2.damage.DamageSources;
import am2.preloader.TornadoLandingTransformer;
import am2.spell.components.Tornado;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.junit.Test;
import java.lang.reflect.Method;
import java.io.InputStream;
import java.util.jar.JarFile;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.tree.*;
import org.objectweb.asm.tree.analysis.*;
import static org.junit.Assert.*;

public class TornadoSafetyTest {
    @Test public void fallingBlockLandingKeepsItsStackAndIsPatchedOnce() throws Exception {
        String entry = "net/minecraft/entity/item/EntityFallingBlock.class";
        for (String name : new String[]{"recompiled_minecraft-1.7.10.jar", "srg_merged_minecraft.jar"}) {
            try (JarFile jar = new JarFile("build/rfg/" + name);
                 InputStream in = jar.getInputStream(jar.getJarEntry(entry))) {
                byte[] patched = TornadoLandingTransformer.transform(org.apache.commons.io.IOUtils.toByteArray(in));
                assertArrayEquals(patched, TornadoLandingTransformer.transform(patched));
                ClassNode node = new ClassNode();
                new ClassReader(patched).accept(node, ClassReader.EXPAND_FRAMES);
                int landings = 0;
                for (Object raw : node.methods) {
                    MethodNode method = (MethodNode)raw;
                    for (AbstractInsnNode i : method.instructions.toArray()) if (i instanceof MethodInsnNode) {
                        MethodInsnNode call = (MethodInsnNode)i;
                        if (!call.owner.equals("am2/entities/TornadoLanding")) continue;
                        assertEquals("place", call.name);
                        new Analyzer(new BasicVerifier()).analyze(node.name, method);
                        landings++;
                    }
                }
                assertEquals(name, 1, landings);
            }
        }
    }
    @Test public void refreshingDoesNotTruncateLongLunarDuration() {
        EntitySpellEffect effect = new EntitySpellEffect(null);
        effect.setTornado(null, null, 0, 80, 0, 32, 800, 7, 0, 0, 8.5f, 0, 0,
                Tornado.ELEMENT_AIR, 5, Tornado.CELESTIAL_LUNAR);
        effect.ticksExisted = 5;
        effect.refreshTornado(6, 7, 0, 0, 1.8f, Tornado.ELEMENT_AIR);
        assertEquals(800, effect.getTicksToExist());
        effect.ticksExisted = 790;
        effect.refreshTornado(6, 7, 0, 0, 1.8f, Tornado.ELEMENT_AIR);
        assertEquals(845, effect.getTicksToExist());
    }

    @Test public void armorPenetrationAppliesOnlyToTheExplicitMagicPortion() throws Exception {
        Method method = EntitySpellEffect.class.getDeclaredMethod("tornadoDamageSource", EntityLivingBase.class, byte.class);
        method.setAccessible(true);
        for(byte element = Tornado.ELEMENT_AIR; element <= Tornado.ELEMENT_WATER; element++) {
            DamageSource main = (DamageSource) method.invoke(null, null, element);
            assertFalse(main.isUnblockable());
            assertEquals(element == Tornado.ELEMENT_FIRE, main.isFireDamage());
        }
        assertTrue(DamageSources.causeEntityMagicDamage(null).isUnblockable());
        // Existing non-Tornado spells retain their original sources and behavior.
        assertTrue(DamageSources.causeEntityLightningDamage(null).isUnblockable());
        assertTrue(DamageSources.causeEntityWindDamage(null).isUnblockable());
    }
}
