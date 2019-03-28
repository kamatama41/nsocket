package com.github.kamatama41.nsocket;

import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.concurrent.atomic.AtomicInteger;

class ServerConnection extends Connection {
    private static final AtomicInteger CONNECTION_ID_COUNTER = new AtomicInteger(1);

    ServerConnection(
            SocketChannel channel,
            IOProcessor.Loop belongingTo,
            CommandWorker worker,
            Context context) {
        super(channel, belongingTo, worker, context);
    }

    void register() {
        belongingTo.addEvent(() -> {
            log.trace("register");
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            int connectionId = CONNECTION_ID_COUNTER.getAndIncrement();
            setConnectionId(connectionId);

            final SelectionKey key = channel.register(belongingTo.getSelector(), SelectionKey.OP_READ);
            key.attach(this);

            sendCommand(SetConnectionIdCommand.ID, connectionId);

            listenerRegistry.fireConnectedEvent(this);
        });
    }
}
