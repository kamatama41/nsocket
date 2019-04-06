package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class SocketClient {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final IOProcessor processor;
    private final CommandWorker worker;
    private final Context context;
    private final ConcurrentMap<String, ClientConnection> connections;
    private final Object lock = new Object();
    private int connectionTimeoutSeconds;

    public SocketClient() {
        this.context = new Context("client");
        this.worker = CommandWorker.client(context);
        this.processor = IOProcessor.client(context);
        this.connections = new ConcurrentHashMap<>();
        this.connectionTimeoutSeconds = 10;
        Thread shutdownHook = new Thread(this::shutdownHook);
        shutdownHook.setName("shutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized void open() throws IOException {
        log.info("Opening connection..");
        registerCommand(new SetConnectionIdCommand(context.getListenerRegistry()));
        registerCommand(new HeartbeatCommand());
        registerCommand(new SyncResultCommand(context));
        registerCommand(new ErrorCommand());
        registerListener(new DisconnectedListener());

        worker.start();
        processor.start();
    }

    public synchronized void close() throws IOException {
        processor.stop();
        worker.stop();
        for (ClientConnection connection : connections.values()) {
            connection.close();
        }
        connections.clear();
    }

    public void setName(String name) {
        this.context.setName(name);
    }

    public void setDefaultContentBufferSize(int defaultContentBufferSize) {
        this.context.setDefaultContentBufferSize(defaultContentBufferSize);
    }

    public void addNode(InetSocketAddress address) throws IOException {
        if (connections.containsKey(address.toString())) {
            log.warn("{} is already added.", address.toString());
            return;
        }
        openConnection(address);
    }

    public Connection getConnection(InetSocketAddress address) {
        try {
            return ensureConnection(address);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public void registerCommand(Command command) {
        this.context.getCommandRegistry().registerCommand(command);
    }

    public void registerSyncCommand(SyncCommand syncCommand) {
        this.context.getCommandRegistry().registerSyncCommand(syncCommand);
    }

    public void registerListener(CommandListener listener) {
        this.context.getListenerRegistry().registerListener(listener);
    }

    public void setCodec(ObjectCodec codec) {
        this.context.setCodec(codec);
    }

    public void setConnectionTimeoutSeconds(int connectionTimeoutSeconds) {
        this.connectionTimeoutSeconds = connectionTimeoutSeconds;
    }

    private void shutdownHook() {
        try {
            log.info("Shutdown detected. Closing client..");
            close();
        } catch (IOException e) {
            log.warn("Failed to stop server.", e);
        }
    }

    private ClientConnection openConnection(InetSocketAddress address) throws IOException {
        SocketChannel channel = SocketChannel.open();
        ClientConnection connection = new ClientConnection(channel, processor.selectProcessor(), worker, context);
        connection.connect(address, connectionTimeoutSeconds);
        connections.put(address.toString(), connection);
        return connection;
    }

    private ClientConnection ensureConnection(InetSocketAddress address) throws IOException {
        ClientConnection connection = connections.get(address.toString());
        if (connection == null) {
            synchronized (lock) {
                connection = connections.get(address.toString());
                if (connection != null) {
                    return connection;
                }
                log.warn("Connection has not been established yet. Try connecting..");
                return openConnection(address);
            }
        }

        if (connection.isOpen()) {
            return connection;
        } else {
            synchronized (lock) {
                if (connection.isOpen()) {
                    return connection;
                }
                int attempts = 0;
                int maxAttempts = 60;
                while (attempts++ < maxAttempts) {
                    log.warn("Connection is already closed. Try reconnecting.. {}/{}", attempts, maxAttempts);
                    connection = openConnection(address);
                    if (connection.isOpen()) {
                        return connection;
                    } else {
                        try {
                            TimeUnit.SECONDS.sleep(1);
                        } catch (InterruptedException e) {
                            throw new IOException(e);
                        }
                    }
                }
                throw new IOException(String.format("Connection to %s could not be established.", address.toString()));
            }
        }
    }

    private class DisconnectedListener implements CommandListener {
        @Override
        public void onDisconnected(Connection connection) {
            InetSocketAddress address = (InetSocketAddress) connection.getRemoteSocketAddress();
            connections.remove(address.toString());
        }
    }
}
