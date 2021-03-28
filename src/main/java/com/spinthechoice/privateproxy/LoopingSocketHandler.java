package com.spinthechoice.privateproxy;

/**
 * Runs a {@link SocketHandler} continuously until the server is closed.
 */
class LoopingSocketHandler implements Runnable {
    private final SocketHandler delegate;

    LoopingSocketHandler(final SocketHandler delegate) {
        this.delegate = delegate;
    }

    @Override
    public void run() {
        while (delegate.isServerOpen()) {
            delegate.run();
        }
    }
}
