package com.github.kamatama41.nsocket;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.channels.SelectionKey;
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

    void connect(SocketAddress address) throws IOException {
        belongingTo.addEvent(() -> {
            log.trace("connect");
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            SelectionKey connectKey = channel.register(belongingTo.getSelector(), SelectionKey.OP_CONNECT);
            connectKey.attach(this);
            channel.connect(address);
        });

        try {
            if (!timer.await(10, TimeUnit.SECONDS)) {
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
            if (!channel.isConnectionPending()) {
                return;
            }
            channel.configureBlocking(false);

            if (!channel.finishConnect()) {
                throw new IOException("Failed to connect to server");
            }
            channel.socket().setTcpNoDelay(true);
            SelectionKey readKey = channel.register(belongingTo.getSelector(), SelectionKey.OP_READ);
            readKey.attach(this);
            updateRemoteSocketAddress();
        } catch (IOException e) {
            close();
            throw e;
        }

        // Notify connected
        timer.countDown();
    }
}
