package com.spinthechoice.privateproxy;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoopingSocketHandlerTest {
    private static class TestHandler extends SocketHandler {
        private final int runTimesBeforeClose;
        private int actualTimesRun;

        TestHandler(final int runTimesBeforeClose) {
            // won't be using any dependencies
            super(null, null);
            this.runTimesBeforeClose = runTimesBeforeClose;
        }

        @Override
        boolean isServerOpen() {
            return actualTimesRun < runTimesBeforeClose;
        }

        @Override
        public void run() {
            actualTimesRun++;
        }

        int timesRun() {
            return actualTimesRun;
        }
    }

    @Test
    void loopsUntilStopped() {
        final int limit = 5;
        final TestHandler fake = new TestHandler(limit);
        final LoopingSocketHandler handler = new LoopingSocketHandler(fake);
        assertEquals(0, fake.timesRun());
        handler.run();
        assertEquals(limit, fake.timesRun());
    }

    @Test
    void neverRuns() {
        final TestHandler fake = new TestHandler(0);
        final LoopingSocketHandler handler = new LoopingSocketHandler(fake);
        handler.run();
        assertEquals(0, fake.timesRun());
    }
}
