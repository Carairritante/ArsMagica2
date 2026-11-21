package am2.entities;

import am2.damage.DamageSources;
import am2.spell.components.Tornado;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.DamageSource;
import org.junit.Test;
import java.lang.reflect.Method;
import static org.junit.Assert.*;

public class TornadoSafetyTest {
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
