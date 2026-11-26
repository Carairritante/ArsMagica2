package am2.playerextensions;

import net.minecraft.nbt.NBTTagCompound;
import org.junit.Test;
import static org.junit.Assert.*;

public class ExtendedPropertiesManaTest {

    @Test
    public void levelUpWithWorldFusionDoesNotDoubleCountRitualMana() {
        ExtendedProperties props = new ExtendedProperties();
        props.hasRitual = true;
        props.setMagicLevelWithMana(50);

        float base50 = ExtendedProperties.calculateBaseMaxManaForLevel(50);
        assertEquals(base50 + 23000f, props.getMaxMana(), 0.01f);

        // Leveling up repeatedly must keep exactly +23,000 from hasRitual (never +46,000).
        props.setMagicLevelWithMana(51);
        props.setMagicLevelWithMana(52);
        props.setMagicLevelWithMana(99);
        float base99 = ExtendedProperties.calculateBaseMaxManaForLevel(99);
        assertEquals(base99 + 23000f, props.getMaxMana(), 0.01f);
    }

    @Test
    public void tenGuardiansWithoutRitualDoNotTriggerPhantomRitualBonus() {
        ExtendedProperties props = new ExtendedProperties();
        props.guardian1 = props.guardian2 = props.guardian3 = props.guardian4 = props.guardian5 = true;
        props.guardian6 = props.guardian7 = props.guardian8 = props.guardian9 = props.guardian10 = true;

        // 10 guardians = +20,000 (> old 17,000 threshold), still must NOT add +23,000 without hasRitual.
        props.setMagicLevelWithMana(50);
        props.setMagicLevelWithMana(51);
        float base51 = ExtendedProperties.calculateBaseMaxManaForLevel(51);
        assertEquals(base51 + 20000f, props.getMaxMana(), 0.01f);
    }

    @Test
    public void loadNbtRestoresCurrentManaAboveBaseWhenRitualAndGuardiansArePresent() {
        ExtendedProperties saved = new ExtendedProperties();
        saved.guardian1 = true;
        saved.guardian2 = true;
        saved.hasRitual = true;
        saved.setMagicLevelWithMana(50);
        float expectedMax = ExtendedProperties.calculateBaseMaxManaForLevel(50) + 4000f + 23000f;
        saved.setCurrentMana(expectedMax - 500f);

        NBTTagCompound tag = new NBTTagCompound();
        tag.setShort("magicLevel", (short) 50);
        tag.setFloat("curMana", expectedMax - 500f);
        tag.setFloat("curFatigue", 0f);
        tag.setBoolean("guardian1", true);
        tag.setBoolean("guardian2", true);
        tag.setBoolean("hasRitual", true);

        ExtendedProperties loaded = new ExtendedProperties();
        loaded.loadNBTData(tag);
        assertEquals(expectedMax, loaded.getMaxMana(), 0.01f);
        assertEquals(expectedMax - 500f, loaded.getCurrentMana(), 0.01f);
    }
}
