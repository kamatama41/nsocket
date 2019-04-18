package com.github.kamatama41.nsocket;

import java.nio.channels.SocketChannel;

class ServerConnection extends Connection {
    ServerConnection(
            SocketChannel channel,
            IOProcessor.Loop belongingTo,
            CommandWorker worker,
            Context context) {
        super(channel, belongingTo, worker, context);
    }

    void register() {
        channel.register(this);
    }
}
