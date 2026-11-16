package com.blackout.game.systems;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ActionLockTest {

    @Test
    void conflictingActionsAreRefusedWhileBusy() {
        ActionLock l = new ActionLock();
        assertTrue(l.start(ActionLock.Type.SWAP, 0.4, 0.5, null, null));
        assertFalse(l.start(ActionLock.Type.DOOR, 0.5, 0.5, null, null));
        assertEquals(ActionLock.Type.SWAP, l.type());
    }

    @Test
    void contactFiresOnceMidwayAndEndFiresAtTheEnd() {
        ActionLock l = new ActionLock();
        int[] n = new int[2];
        l.start(ActionLock.Type.DOOR, 1.0, 0.4, () -> n[0]++, () -> n[1]++);
        l.update(0.3);
        assertEquals(0, n[0]);
        l.update(0.2);                                      // t = 0.5 > 0.4
        assertEquals(1, n[0]);
        l.update(0.2);
        assertEquals(1, n[0]);                              // never twice
        assertEquals(0, n[1]);
        assertEquals(0.7, l.progress(), 1e-9);
        l.update(0.4);                                      // t = 1.1 >= 1.0
        assertEquals(1, n[1]);
        assertFalse(l.busy());
        assertEquals(0, l.progress());
    }

    @Test
    void cancelFiresNothingAndFreesTheLock() {
        ActionLock l = new ActionLock();
        int[] n = new int[1];
        l.start(ActionLock.Type.SWAP, 1, 0.5, () -> n[0]++, () -> n[0]++);
        l.cancel();
        l.update(5);
        assertEquals(0, n[0]);
        assertTrue(l.start(ActionLock.Type.DOOR, 1, 0.5, null, null));
    }

    @Test
    void contactCallbackMayCancelTheAction() {
        ActionLock l = new ActionLock();
        int[] ended = new int[1];
        l.start(ActionLock.Type.DOOR, 1, 0.2, l::cancel, () -> ended[0]++);   // e.g. player walked away
        l.update(0.5);
        assertFalse(l.busy());
        assertEquals(0, ended[0]);
    }

    @Test
    void zeroDurationAndNoneAreRejected() {
        ActionLock l = new ActionLock();
        assertFalse(l.start(ActionLock.Type.NONE, 1, 0, null, null));
        assertFalse(l.start(ActionLock.Type.SWAP, 0, 0, null, null));
    }

    @Test
    void contactAtTheVeryEndStillFiresBeforeEnd() {
        ActionLock l = new ActionLock();
        StringBuilder order = new StringBuilder();
        l.start(ActionLock.Type.SWAP, 1, 1.0, () -> order.append('c'), () -> order.append('e'));
        l.update(1.5);
        assertEquals("ce", order.toString());
    }
}
