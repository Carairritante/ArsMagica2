package am2.compass;

import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;
import java.util.*;

public class AstralDimensionMetadataTest {
    private static Map<String,Integer> remappedIds() {
        Map<String,Integer> ids=new HashMap<String,Integer>();
        String[] groups={"overworld","end","utopia","danger","village","islands","crystal","chaos"};
        int[] values={0,1,800,810,820,830,840,850};for(int i=0;i<groups.length;i++)ids.put(groups[i],values[i]);return ids;
    }
    @Test public void naturalOreSpawnDimensionsUseRegisteredIdsAndMultipleWorlds() {
        Map<String,Integer> ids=remappedIds();
        assertArrayEquals(new int[]{800},OptionalStructureDimensions.oreDimensions("makeKingAltar",ids,true));
        assertArrayEquals(new int[]{800},OptionalStructureDimensions.oreDimensions("makeQueenAltar",ids,true));
        assertArrayEquals(new int[]{840},OptionalStructureDimensions.oreDimensions("makeCrystalBattleTower",ids,true));
        assertArrayEquals(new int[]{1,830},OptionalStructureDimensions.oreDimensions("makeEnderCastle",ids,true));
        assertArrayEquals(new int[]{0,800},OptionalStructureDimensions.oreDimensions("makeHauntedHouse",ids,true));
    }
    @Test public void surfaceConfigDoesNotDisableUtopiaAltarsOrMoveThemToOverworld() {
        assertArrayEquals(new int[0],OptionalStructureDimensions.oreDimensions("makeHauntedHouse",remappedIds(),false));
        assertArrayEquals(new int[]{800},OptionalStructureDimensions.oreDimensions("makeKingAltar",remappedIds(),false));
        assertArrayEquals(new int[]{840},OptionalStructureDimensions.oreDimensions("makeRoundRotator",remappedIds(),false));
    }
    @Test public void metadataDoesNotClaimTheCurrentWorldWhenRulesAreUnknown() {
        StructureTargetInfo unknown=new StructureTargetInfo("chromaticraft:BURROW","Burrow","modded",42,false,1);
        assertFalse(unknown.hasKnownDimensions());assertTrue(unknown.canSearchIn(42));assertTrue(unknown.canSearchIn(-1));
        assertFalse(unknown.listsDimension(0));assertEquals(42,unknown.preferredDimension);
    }
    @Test public void multiDimensionFilterAndObservedSpawnerLocationsStayIndependent() {
        StructureTargetInfo natural=new StructureTargetInfo("orespawn:makeEnderCastle","Castle","modded",new int[]{830,1,830},1);
        assertArrayEquals(new int[]{1,830},natural.getSearchDimensions());assertTrue(natural.canSearchIn(830));assertFalse(natural.canSearchIn(0));
        StructureTargetInfo observed=natural.withObservedDimension(0);
        assertTrue(observed.listsDimension(0));assertFalse(natural.listsDimension(0));
        int[] copy=natural.getSearchDimensions();copy[0]=0;assertFalse(natural.canSearchIn(0));
    }
    @Test public void boundedExtensionRoundTripsWithoutChangingLegacyCatalogFormat() throws Exception {
        StructureTargetInfo info=new StructureTargetInfo("orespawn:makeEnderCastle","Castle","modded",new int[]{1,830},1);
        List<StructureTargetInfo> base=Arrays.asList(new StructureTargetInfo(info.id,info.rawName,info.category,info.preferredDimension,false,1));
        Map<Integer,String> names=Collections.singletonMap(830,"OreSpawn: Islands");
        AstralDimensionMetadata.Decoded decoded=AstralDimensionMetadata.read(new DataInputStream(new ByteArrayInputStream(AstralDimensionMetadata.encode(Arrays.asList(info),names))),base);
        assertArrayEquals(new int[]{1,830},decoded.catalog.get(0).getSearchDimensions());assertEquals("OreSpawn: Islands",decoded.names.get(830));
    }
    @Test public void extensionRejectsMismatchTruncationAndOversizedDimensionCounts() throws Exception {
        StructureTargetInfo info=new StructureTargetInfo("Village","Village","vanilla",0,true,1);List<StructureTargetInfo> base=Arrays.asList(info);
        byte[] valid=AstralDimensionMetadata.encode(base,Collections.<Integer,String>emptyMap());
        try{AstralDimensionMetadata.read(new DataInputStream(new ByteArrayInputStream(Arrays.copyOf(valid,valid.length-1))),base);fail("truncation accepted");}catch(IOException expected){}
        try{AstralDimensionMetadata.read(new DataInputStream(new ByteArrayInputStream(valid)),Arrays.asList(new StructureTargetInfo("Fortress","Fortress","vanilla",-1,true,1)));fail("mismatch accepted");}catch(IOException expected){}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();DataOutputStream out=new DataOutputStream(bytes);out.writeInt(0x414d3344);out.writeInt(1);out.writeUTF("Village");out.writeInt(Integer.MAX_VALUE);
        try{AstralDimensionMetadata.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray())),base);fail("unbounded allocation accepted");}catch(IOException expected){}
    }
    @Test public void unknownOrIncompletePreviewsNeverBorrowAnUnrelatedStructurePhoto() {
        assertNull(am2.compass.client.StructurePreviewRegistry.resolvePreviewKey("orespawn:unknown_structure"));
        assertNull(am2.compass.client.StructurePreviewRegistry.resolvePreviewKey("twilightforest:unknown_structure"));
        assertNull(am2.compass.client.StructurePreviewRegistry.resolvePreviewKey("chromaticraft:BURROW"));
        assertNotNull(am2.compass.client.StructurePreviewRegistry.resolvePreviewKey("chromaticraft:PYLON_BLUE"));
    }

}
