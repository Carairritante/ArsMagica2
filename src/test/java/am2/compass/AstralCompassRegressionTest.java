package am2.compass;

import org.junit.Test;
import net.minecraft.nbt.*;
import static org.junit.Assert.*;

public class AstralCompassRegressionTest {
    @Test public void rejectsInvalidManaAndUntrustedIds() {
        assertFalse(AstralStructureLocator.hasEnoughMana(Float.NaN,20000));
        assertFalse(AstralStructureLocator.hasEnoughMana(Float.POSITIVE_INFINITY,0));
        assertFalse(AstralStructureLocator.validId(""));
        assertFalse(AstralStructureLocator.validId("bad\u0000id"));
        assertTrue(AstralStructureLocator.validId("orespawn:makeKingAltar"));
    }
    @Test public void distanceDoesNotOverflowAtWorldBoundary() {
        assertEquals(3600000000000000D,AstralStructureLocator.Search.squaredDistance(-30000000,0,30000000,0),1D);
    }
    @Test public void indexPersistsDeduplicatesAndRejectsCorruptCoordinates() {
        NBTTagCompound root=new NBTTagCompound();NBTTagList entries=new NBTTagList();
        for(int i=0;i<3;i++){NBTTagCompound e=new NBTTagCompound();e.setString("id","wizardry:wizard_tower");e.setInteger("x",i==2?Integer.MAX_VALUE:16);e.setInteger("y",80);e.setInteger("z",16);entries.appendTag(e);}
        root.setTag("points",entries);AstralLandmarks data=new AstralLandmarks("test");data.readFromNBT(root);
        assertEquals(1,data.points("wizardry:wizard_tower").size());
        NBTTagCompound saved=new NBTTagCompound();data.writeToNBT(saved);AstralLandmarks reloaded=new AstralLandmarks("test");reloaded.readFromNBT(saved);
        assertEquals(80,reloaded.points("wizardry:wizard_tower").get(0).y);
    }
    @Test public void indexReadIsBounded() {
        NBTTagCompound root=new NBTTagCompound();NBTTagList entries=new NBTTagList();
        for(int i=0;i<AstralLandmarks.MAX_POINTS+20;i++){NBTTagCompound e=new NBTTagCompound();e.setString("id","test:tower");e.setInteger("x",i*16);entries.appendTag(e);}
        root.setTag("points",entries);AstralLandmarks data=new AstralLandmarks("test");data.readFromNBT(root);
        assertEquals(AstralLandmarks.MAX_POINTS,data.points("test:tower").size());
    }
    @Test public void bundledStructurePreviewsResolveAndExist() {
        String[] sampleIds = {
            "Stronghold", "Village", "Mineshaft", "Temple:DesertPyramid",
            "Temple:JungleTemple", "Temple:WitchHut", "Fortress", "am2:witchwood_forest",
            "chromaticraft:PYLON", "chromaticraft:PYLON_BLUE", "chromaticraft:PYLON_RED",
            "chromaticraft:PYLON_WHITE", "chromaticraft:OCEAN", "wizardry:wizard_tower",
            "thaumcraft:mound", "thaumcraft:eldritch_ring", "thaumcraft:hilltop_stones",
            "orespawn:makeKingAltar", "orespawn:makeQueenAltar", "orespawn:makeCrystalBattleTower",
            "twilightforest:finalCastle", "twilightforest:lichTower"
        };
        for (String id : sampleIds) {
            String key = am2.compass.client.StructurePreviewRegistry.resolvePreviewKey(id);
            assertNotNull("Missing preview key for " + id, key);
            assertNotNull("Missing PNG resource for " + id,
                getClass().getResourceAsStream("/assets/arsmagica2/textures/gui/compass_previews/" + key + ".png"));
        }
        for (String oreMethod : am2.preloader.AstralStructureTransformer.ORE_METHODS) {
            String id = "orespawn:" + oreMethod;
            String key = am2.compass.client.StructurePreviewRegistry.resolvePreviewKey(id);
            assertNotNull("Missing preview key for " + id, key);
            assertNotNull("Missing PNG resource for " + id,
                getClass().getResourceAsStream("/assets/arsmagica2/textures/gui/compass_previews/" + key + ".png"));
        }
    }
}
