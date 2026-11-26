package am2.network;

import org.junit.Test;
import static org.junit.Assert.*;

public class CompendiumBookSyncTest {
    @Test public void openAndCloseAreRelayedOnce() {
        CompendiumBookSync.Reader reader = new CompendiumBookSync.Reader();
        assertTrue(reader.accept(true, 0));
        assertTrue(reader.accept(false, 10));
        assertFalse(reader.accept(false, 20));
    }

    @Test public void closingWithoutOpeningSendsNothing() {
        assertFalse(new CompendiumBookSync.Reader().accept(false, 1000));
    }

    @Test public void rapidTogglingIsBounded() {
        CompendiumBookSync.Reader reader = new CompendiumBookSync.Reader();
        int relayed = 0;
        for (int ms = 0; ms < 10000; ms++) {
            if (reader.accept(true, ms)) relayed++;
            if (reader.accept(false, ms)) relayed++;
        }
        assertTrue(relayed <= 2 * (10000 / CompendiumBookSync.MIN_OPEN_INTERVAL_MS));
    }

    @Test public void periodicRepeatKeepsBeingRelayed() {
        CompendiumBookSync.Reader reader = new CompendiumBookSync.Reader();
        assertTrue(reader.accept(true, 0));
        assertTrue(reader.accept(true, 5000));
        assertTrue(reader.accept(true, 10000));
    }
}
