package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;
import org.msgpack.core.MessageBufferPacker;
import org.msgpack.core.MessageInsufficientBufferException;
import org.msgpack.core.MessagePack;
import org.msgpack.core.MessageUnpacker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

public abstract class Connection {
    private static final AtomicInteger CONNECTION_ID_COUNTER = new AtomicInteger(1);
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private int connectionId;
    final TcpChannel channel;
    private final Context context;
    private final ObjectCodec codec;
    private final SyncManager syncManager;
    private final CommandRegistry commandRegistry;
    private final CommandListenerRegistry listenerRegistry;
    private final CommandWorker worker;
    private Queue<ByteBuffer> writeQueue;
    private ByteBuffer contentBuffer;
    private Object attachment;
    private long lastHeartbeatTime;
    private boolean isClosed;

    Connection(
            SocketChannel channel, IOProcessor.Loop belongingTo, CommandWorker worker, Context context) {
        this.channel = TcpChannel.getInstance(channel, belongingTo, context);
        this.worker = worker;
        this.context = context;
        this.codec = context.getCodec();
        this.syncManager = context.getSyncManager();
        this.commandRegistry = context.getCommandRegistry();
        this.listenerRegistry = context.getListenerRegistry();
        this.writeQueue = new ConcurrentLinkedQueue<>();
        this.contentBuffer = ByteBuffer.allocate(context.getDefaultContentBufferSize());
        this.lastHeartbeatTime = System.currentTimeMillis();
        this.isClosed = false;
    }

    public int getConnectionId() {
        return connectionId;
    }

    void setConnectionId(int connectionId) {
        this.connectionId = connectionId;
    }

    void assignConnectionId() {
        setConnectionId(CONNECTION_ID_COUNTER.getAndIncrement());
    }

    public SocketAddress getRemoteSocketAddress() {
        return channel.getRemoteSocketAddress();
    }

    @Override
    public String toString() {
        return String.format("Connection{%d@%s}", connectionId, getRemoteSocketAddress());
    }

    public void sendCommand(String id, Object body) {
        writeCommandRequest(id, null, body);
    }

    @SuppressWarnings("unchecked")
    public <R> R sendSyncCommand(String id, Object body) {
        SyncCommand syncCommand = commandRegistry.getSyncCommand(id);
        SyncManager.Request request = syncManager.registerNewRequest();
        writeCommandRequest(id, request.getCallId(), body);
        try {
            long timeoutMillis = syncCommand.getTimeoutMillis() + 100L; // Add a buffer of networking
            boolean completed = request.waitUntilCompleted(timeoutMillis);
            if (!completed) {
                throw new SyncCommandException("A sync command could not return response");
            }
        } catch (InterruptedException e) {
            throw new SyncCommandException("A sync command is interrupted");
        }

        SyncResultData result = request.getResult();
        if (result.getStatus() == SyncResultData.Status.FAILED) {
            throw new SyncCommandException("A sync command failed: " + result.getErrorMessage());
        }

        if (result.getStatus() == SyncResultData.Status.TIMEOUT) {
            throw new SyncCommandException("A sync command was timed out");
        }
        return (R) result.getResult();
    }

    public boolean isOpen() {
        return channel.isOpen();
    }

    public void attach(Object attachment) {
        this.attachment = attachment;
    }

    public Object attachment() {
        return attachment;
    }

    public synchronized void close() throws IOException {
        log.trace("Closing connection.");
        if (isClosed) {
            log.warn("Connection already closed");
        }
        channel.close();
        isClosed = true;
        listenerRegistry.fireDisconnectedEvent(this);
    }

    void onConnectable() throws IOException {
        throw new UnsupportedOperationException("onConnectable");
    }

    void onWritable() throws IOException {
        if (writeQueue.isEmpty()) {
            channel.overrideInterest(SelectionKey.OP_READ);
            return;
        }

        ByteBuffer buffer;
        while ((buffer = writeQueue.peek()) != null) {
            channel.write(buffer);
            if (buffer.remaining() != 0) {
                return;
            } else {
                writeQueue.poll();
            }
        }
        channel.overrideInterest(SelectionKey.OP_READ);
    }

    void onReadable() throws IOException {
        if (!isOpen()) {
            close();
            return;
        }

        int read;
        ByteBuffer content = contentBuffer;
        do {
            read = channel.read(content);
        } while (content.hasRemaining() && read > 0);

        if (read == -1) {
            close();
            return;
        }

        content.flip();
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(content)) {
            while (unpacker.hasNext()) {
                worker.addRequest(new CommandRequest(unpacker.unpackString(), this));
                content.position((int) unpacker.getTotalReadBytes());
            }
            content.clear();
        } catch (MessageInsufficientBufferException e) {
            content.compact();
            if (!content.hasRemaining()) {
                log.warn("Message size larger than buffer's size ({}), will expand it.", content.capacity());
                content.flip();
                expandContentBufferSize();
            }
        }
    }

    void sendHeartbeat() throws IOException {
        long timeout = context.getHeartbeatInterval() * 3;
        long now = System.currentTimeMillis();
        if (now - lastHeartbeatTime >= timeout) {
            log.warn("Connection might be dead.");
            close();
        }
        sendCommand(HeartbeatCommand.ID, null);
    }

    void updateLastHeartbeatTime() {
        lastHeartbeatTime = System.currentTimeMillis();
        log.trace("updateLastHeartbeatTime: {}", lastHeartbeatTime);
    }

    private void expandContentBufferSize() {
        ByteBuffer newBuffer = ByteBuffer.allocate(contentBuffer.capacity() * 2);
        newBuffer.put(contentBuffer);
        contentBuffer = newBuffer;
    }

    private void write(ByteBuffer data) {
        if (isOpen()) {
            writeQueue.add(data);
            channel.enableInterest(SelectionKey.OP_WRITE);
        }
    }

    private void writeCommandRequest(String commandId, Integer callId, Object body) {
        try (MessageBufferPacker packer = MessagePack.newDefaultBufferPacker()) {
            String message = RequestMessageCodec.encode(commandId, callId, codec.encodeToJson(body));
            packer.packString(message);
            write(ByteBuffer.wrap(packer.toByteArray()));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
