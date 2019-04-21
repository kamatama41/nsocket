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
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class Connection {
    private static final AtomicInteger CONNECTION_ID_COUNTER = new AtomicInteger(1);
    protected final Logger log = LoggerFactory.getLogger(this.getClass());
    private int connectionId;
    private final TcpChannel channel;
    private final Context context;
    private final ObjectCodec codec;
    private final SyncManager syncManager;
    private final CommandRegistry commandRegistry;
    private final CommandListenerRegistry listenerRegistry;
    private final CommandWorker worker;
    private final CountDownLatch connectionTimer;
    private final Queue<ByteBuffer> writeQueue;
    private ByteBuffer contentBuffer;
    private Object attachment;
    private long lastHeartbeatTime;
    private boolean isClosed;

    Connection(TcpChannel channel, CommandWorker worker, Context context) {
        this.channel = channel;
        this.worker = worker;
        this.context = context;
        this.codec = context.getCodec();
        this.syncManager = context.getSyncManager();
        this.commandRegistry = context.getCommandRegistry();
        this.listenerRegistry = context.getListenerRegistry();
        this.writeQueue = new ConcurrentLinkedQueue<>();
        this.connectionTimer = new CountDownLatch(1);
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

    boolean waitUntilConnected(long timeoutSeconds) throws InterruptedException {
        return connectionTimer.await(timeoutSeconds, TimeUnit.SECONDS);
    }

    void notifyConnected() {
        connectionTimer.countDown();
        listenerRegistry.fireConnectedEvent(this);
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
        channel.finishConnect(this);
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
        if (doRead() == -1) {
            close();
            return;
        }

        contentBuffer.flip();
        try (MessageUnpacker unpacker = MessagePack.newDefaultUnpacker(contentBuffer)) {
            while (unpacker.hasNext()) {
                String json = unpacker.unpackString();
                worker.addRequest(new CommandRequest(json, this));
                contentBuffer.position((int) unpacker.getTotalReadBytes());
                log.trace("unpacked {}/{}\n{}", contentBuffer.position(), contentBuffer.limit(), json);
            }
            contentBuffer.clear();
        } catch (MessageInsufficientBufferException e) {
            contentBuffer.compact();
            if (!contentBuffer.hasRemaining()) {
                int currentCapacity = contentBuffer.capacity();
                expandContentBufferSize();
                log.warn("Failed to unpack content by insufficient buffer size. Expanded it ({} -> {})", currentCapacity, contentBuffer.capacity());
            }
        }
    }

    private int doRead() throws IOException {
        int read;
        try {
            do {
                read = channel.read(contentBuffer);
            } while (contentBuffer.hasRemaining() && read > 0);
        } catch (InsufficientInboundBufferException e) {
            int currentCapacity = contentBuffer.capacity();
            expandContentBufferSize();
            log.warn("Failed to read content by insufficient buffer size. Expanded it ({} -> {})", currentCapacity, contentBuffer.capacity());
            return doRead();
        }
        return read;
    }

    void sendHeartbeat() throws IOException {
        long timeout = context.getHeartbeatIntervalSeconds() * 3 * 1000;
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
        int currentPos = contentBuffer.position();
        contentBuffer.flip();
        ByteBuffer newBuffer = ByteBuffer.allocate(contentBuffer.capacity() * 2);
        newBuffer.put(contentBuffer);
        newBuffer.position(currentPos);
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
