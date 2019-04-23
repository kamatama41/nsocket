package com.github.kamatama41.nsocket;

import javax.net.ssl.SSLEngine;
import javax.net.ssl.SSLEngineResult;
import javax.net.ssl.SSLEngineResult.HandshakeStatus;
import javax.net.ssl.SSLSession;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

class SslTcpChannel extends PlaintextTcpChannel {
    private final SSLEngine sslEngine;
    private ByteBuffer inboundBuffer;
    private final Queue<ByteBuffer> outboundBuffers;
    private final ByteBuffer unwrapBuffer;
    private final ByteBuffer wrapBuffer;
    private final Object outboundLock = new Object();

    SslTcpChannel(SocketChannel channel, IOProcessor.Loop belongingTo, Context context) {
        super(channel, belongingTo);
        this.sslEngine = context.getSslContext().createSSLEngine();

        SSLSession session = sslEngine.getSession();
        int applicationBufferSize = session.getApplicationBufferSize();
        this.inboundBuffer = ByteBuffer.allocate(applicationBufferSize);
        this.unwrapBuffer = ByteBuffer.allocate(applicationBufferSize);
        this.outboundBuffers = new ConcurrentLinkedQueue<>();
        this.wrapBuffer = ByteBuffer.allocate(0);
    }

    @Override
    public void finishConnect(Connection connection) throws IOException {
        super.finishConnect(connection);
        beginHandshake();
    }

    @Override
    protected void doRegister(Connection connection) throws IOException {
        super.doRegister(connection);
        beginHandshake();
    }

    @Override
    public int read(ByteBuffer dst) throws IOException {
        int initialPosition = dst.position();
        int read = unwrap(dst);
        if (read == -1) {
            return read;
        }
        return dst.position() - initialPosition;
    }

    @Override
    public int write(ByteBuffer src) throws IOException {
        return wrap(src);
    }

    @Override
    public void close() throws IOException {
        super.close();
        sslEngine.closeOutbound();
    }

    private int unwrap(ByteBuffer dst) throws IOException {
        // Read from unwrapBuffer first
        int totalUnwrapped = 0;
        if (unwrapBuffer.position() != 0) {
            unwrapBuffer.flip();
            while (unwrapBuffer.hasRemaining() && dst.hasRemaining()) {
                dst.put(unwrapBuffer.get());
                totalUnwrapped++;
            }
            unwrapBuffer.compact();
        }

        int unwrapped, wrapped;
        do {
            totalUnwrapped += unwrapped = doUnwrap(dst);
            wrapped = doWrap(wrapBuffer);
        } while (unwrapped > 0 || wrapped > 0 && (!outboundBuffers.isEmpty() && inboundBuffer.hasRemaining()));

        return totalUnwrapped;
    }

    private int doUnwrap(ByteBuffer dst) throws IOException {
        int totalRead = 0;
        loop:
        do {
            // Read data from channel
            int read;
            do {
                totalRead += read = super.read(inboundBuffer);
                if (read == -1) {
                    return read;
                }
            } while (inboundBuffer.hasRemaining() && read > 0);

            inboundBuffer.flip();
            if (!inboundBuffer.hasRemaining()) {
                // This block means inboundBuffer's position = limit = 0, which is no read from channel.
                inboundBuffer.compact();
                return totalRead;
            }

            try {
                SSLEngineResult result = sslEngine.unwrap(inboundBuffer, dst);
                logSSLEngineResult("unwrap:", result);
                switch (result.getStatus()) {
                    case OK:
                        switch (result.getHandshakeStatus()) {
                            case NEED_WRAP:
                                prepareWrapping();
                                break loop;
                            case NEED_TASK:
                                HandshakeStatus taskResult = runHandshakeTasks();
                                switch (taskResult) {
                                    case NEED_WRAP:
                                        prepareWrapping();
                                        break loop;
                                    case NEED_UNWRAP:
                                        break;
                                    default:
                                        throw new IllegalStateException("Unexpected handshake status: " + taskResult);
                                }
                                break;
                            case FINISHED:
                                log.debug("unwrap: Handshaking finished.");
                            case NEED_UNWRAP:
                            case NOT_HANDSHAKING:
                                break;
                            default:
                                throw new IllegalStateException("Unexpected handshake status: " + result.getHandshakeStatus());
                        }
                        break;
                    case BUFFER_OVERFLOW:
                        throw new InsufficientInboundBufferException();
                    case CLOSED:
                        return totalRead == 0 ? -1 : totalRead;
                    case BUFFER_UNDERFLOW:  // should not happen because sufficient buffer size is allocated
                    default:
                        throw new IllegalStateException("Unexpected status: " + result.getStatus());
                }
            } finally {
                inboundBuffer.compact();
            }
        } while (dst.hasRemaining());

        return totalRead;
    }

    private int wrap(ByteBuffer src) throws IOException {
        int wrapped = doWrap(src);
        try {
            doUnwrap(unwrapBuffer);
        } catch (InsufficientInboundBufferException e) {
            log.warn("Unexpectedly failed to unwrap into unwrapBuffer.", e);
        }
        return wrapped;
    }

    private int doWrap(ByteBuffer src) throws IOException {
        // Send data if wrapped data exists
        int totalWritten = flush();

        // Wrap application data (src) and send it.
        loop:
        while (true) {
            SSLEngineResult result;
            synchronized (outboundLock) {
                ByteBuffer outboundBuffer = newPacketBuffer();
                result = sslEngine.wrap(src, outboundBuffer);
                logSSLEngineResult("wrap:", result);
                if (result.bytesProduced() > 0) {
                    outboundBuffer.flip();
                    outboundBuffers.add(outboundBuffer);
                }
            }
            totalWritten += flush();

            switch (result.getStatus()) {
                case OK:
                    switch (result.getHandshakeStatus()) {
                        case NEED_UNWRAP:
                            prepareUnwrapping();
                            break loop;
                        case NEED_TASK:
                            HandshakeStatus taskResult = runHandshakeTasks();
                            switch (taskResult) {
                                case NEED_UNWRAP:
                                    prepareUnwrapping();
                                    break loop;
                                case NEED_WRAP:
                                    break;
                                default:
                                    throw new IllegalStateException("Unexpected handshake status: " + taskResult);
                            }
                            break;
                        case NOT_HANDSHAKING:
                            if (src.hasRemaining()) {
                                break;
                            }
                            break loop;
                        case NEED_WRAP:
                            break;
                        case FINISHED:
                            log.debug("wrap: Handshaking finished.");
                            break;
                        default:
                            throw new IllegalStateException("Unexpected handshake status: " + result.getHandshakeStatus());
                    }
                    break;
                case CLOSED:
                    break loop;
                case BUFFER_OVERFLOW:   // should not happen because sufficient buffer size is allocated
                case BUFFER_UNDERFLOW:  // happen only on unwrapping
                default:
                    throw new IllegalStateException("Unexpected status: " + result.getStatus());
            }
        }
        return totalWritten;
    }

    private HandshakeStatus runHandshakeTasks() {
        log.trace("Running handshake tasks..");
        Runnable task;
        while ((task = sslEngine.getDelegatedTask()) != null) {
            task.run();
        }
        HandshakeStatus status = sslEngine.getHandshakeStatus();
        log.trace("Ran handshake tasks. Current status is {}", status);
        return status;
    }

    private int flush() throws IOException {
        if (!isOpen()) {
            return 0;
        }
        int totalWritten = 0;
        ByteBuffer buffer;
        while ((buffer = outboundBuffers.peek()) != null) {
            totalWritten += super.write(buffer);
            if (buffer.remaining() != 0) {
                break;
            } else {
                outboundBuffers.poll();
            }
        }
        return totalWritten;
    }

    private void beginHandshake() throws IOException {
        log.debug("Start handshaking..");
        sslEngine.beginHandshake();
        doWrap(wrapBuffer);
        doUnwrap(unwrapBuffer);
    }

    private void prepareUnwrapping() {
        if (outboundBuffers.isEmpty()) {
            overrideInterest(SelectionKey.OP_READ);
        } else {
            enableInterest(SelectionKey.OP_READ);
        }
    }

    private void prepareWrapping() {
        overrideInterest(SelectionKey.OP_WRITE);
    }

    private void logSSLEngineResult(String message, SSLEngineResult result) {
        if (result.bytesConsumed() != 0 || result.bytesProduced() != 0) {
            log.trace("{}\t{}/{}\t{}/{} bytes",
                    message,
                    result.getStatus(), result.getHandshakeStatus(),
                    result.bytesConsumed(), result.bytesProduced()
            );
        }
    }

    private ByteBuffer newPacketBuffer() {
        return ByteBuffer.allocate(sslEngine.getSession().getPacketBufferSize());
    }
}

