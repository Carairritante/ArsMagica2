package am2.playerextensions;

import am2.network.AMDataWriter;
import org.junit.Test;
import static org.junit.Assert.*;

public class SkillDataPacketTest {
    @Test public void serverAcceptsLearnRequestsFromClients() {
        byte[] learn = new AMDataWriter().add((byte)0x01).add(7).add(1205).add(3).generate();
        assertTrue(SkillData.isClientLearnRequest(learn));
    }

    @Test public void serverRejectsFullKnowledgeSyncFromClients() {
        byte[] sync = new AMDataWriter().add((byte)0x02).add(7).add(0x1F).add(false).generate();
        assertFalse(SkillData.isClientLearnRequest(sync));
    }

    @Test public void serverRejectsEmptyOrMissingPackets() {
        assertFalse(SkillData.isClientLearnRequest(null));
        assertFalse(SkillData.isClientLearnRequest(new byte[0]));
    }
}
