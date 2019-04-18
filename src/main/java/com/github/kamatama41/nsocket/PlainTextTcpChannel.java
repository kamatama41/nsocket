package com.github.kamatama41.nsocket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.SocketChannel;

class PlainTextTcpChannel implements TcpChannel {
    private static final Logger log = LoggerFactory.getLogger(PlainTextTcpChannel.class);
    private final SocketChannel channel;
    private final IOProcessor.Loop belongingTo;
    private final Context context;
    private SocketAddress remoteSocketAddress;

    PlainTextTcpChannel(SocketChannel channel, IOProcessor.Loop belongingTo, Context context) {
        this.channel = channel;
        this.belongingTo = belongingTo;
        this.context = context;
    }

    @Override
    public void connect(SocketAddress remote, Connection attachment) {
        belongingTo.addEvent(() -> {
            log.trace("connect");
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            SelectionKey connectKey = channel.register(belongingTo.getSelector(), SelectionKey.OP_CONNECT);
            connectKey.attach(attachment);
            channel.connect(remote);
        });
    }

    @Override
    public void finishConnect(Connection attachment) throws IOException {
        if (!channel.isConnectionPending()) {
            return;
        }
        channel.configureBlocking(false);

        if (!channel.finishConnect()) {
            throw new IOException("Failed to connect to server");
        }
        channel.socket().setTcpNoDelay(true);
        SelectionKey readKey = channel.register(belongingTo.getSelector(), SelectionKey.OP_READ);
        readKey.attach(attachment);
        updateRemoteSocketAddress();
    }

    @Override
    public void register(Connection attachment) {
        belongingTo.addEvent(() -> {
            log.trace("register");
            channel.configureBlocking(false);
            channel.socket().setTcpNoDelay(true);
            attachment.assignConnectionId();

            final SelectionKey key = channel.register(belongingTo.getSelector(), SelectionKey.OP_READ);
            key.attach(attachment);

            updateRemoteSocketAddress();
            attachment.sendCommand(SetConnectionIdCommand.ID, attachment.getConnectionId());

            context.getListenerRegistry().fireConnectedEvent(attachment);
        });
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        return channel.read(dst);
    }

    @Override
    public void write(ByteBuffer src) throws IOException {
        channel.write(src);
    }

    @Override
    public boolean isOpen() {
        return channel.isConnected() && channel.isOpen();
    }

    @Override
    public void close() throws IOException {
        if (channel.isOpen()) {
            Selector selector = belongingTo.getSelector();
            SelectionKey key = getKey();
            selector.wakeup();
            if (key != null) {
                key.cancel();
                key.attach(null);
            }
            channel.close();
        }
    }

    @Override
    public SocketAddress getRemoteSocketAddress() {
        return remoteSocketAddress;
    }

    @Override
    public void enableInterest(int ops) {
        belongingTo.addEvent(() -> {
            log.trace("enableInterest: {}", ops);
            SelectionKey key = getKey();
            if (key != null && key.isValid()) {
                int current = key.interestOps();
                if (!alreadyIncluded(current, ops)) {
                    int newOps = key.interestOps() | ops;
                    key.interestOps(newOps);
                    log.trace("Updated to {}", ops);
                }
            }
        });
    }

    @Override
    public void overrideInterest(int ops) {
        log.trace("overrideInterest: {}", ops);
        SelectionKey key = getKey();
        if (key != null && key.isValid()) {
            key.interestOps(ops);
        }
    }

    private void updateRemoteSocketAddress() {
        this.remoteSocketAddress = channel.socket().getRemoteSocketAddress();
    }

    private SelectionKey getKey() {
        return channel.keyFor(belongingTo.getSelector());
    }

    private static boolean alreadyIncluded(int current, int newOps) {
        return (current & newOps) == newOps;
    }
}
