package com.github.kamatama41.nsocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

class ClientConnection extends Connection {
    private final CountDownLatch timer;

    ClientConnection(
            SocketChannel channel,
            IOProcessor.Loop belongingTo,
            CommandWorker worker,
            Context context) {
        super(channel, belongingTo, worker, context);
        this.timer = new CountDownLatch(1);
    }

    void connect(SocketAddress address, long connectionTimeoutSeconds) throws IOException {
        channel.connect(address, this);
        try {
            if (!timer.await(connectionTimeoutSeconds, TimeUnit.SECONDS)) {
                throw new IOException("Connection timed out");
            }
        } catch (InterruptedException e) {
            throw new IOException("Connecting to server failed by interruption");
        }

        if (!channel.isOpen()) {
            throw new IOException(String.format("Failed to connect server (%s)", address));
        }
    }

    @Override
    void onConnectable() throws IOException {
        try {
            channel.finishConnect(this);
        } catch (IOException e) {
            close();
            throw e;
        }
        // Notify connected
        timer.countDown();
    }
}
