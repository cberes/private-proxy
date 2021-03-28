package com.spinthechoice.privateproxy;

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
