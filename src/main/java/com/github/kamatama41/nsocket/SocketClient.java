package com.github.kamatama41.nsocket;

import com.github.kamatama41.nsocket.codec.ObjectCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLContext;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

public class SocketClient {
    private final Logger log = LoggerFactory.getLogger(this.getClass());
    private final IOProcessor processor;
    private final CommandWorker worker;
    private final Context context;
    private final ConcurrentMap<String, Connection> activeConnections;
    private final Object lock = new Object();
    private int connectionTimeoutSeconds;

    public SocketClient() {
        this.context = Context.client();
        this.worker = CommandWorker.client(context);
        this.processor = IOProcessor.client(context);
        this.activeConnections = new ConcurrentHashMap<>();
        this.connectionTimeoutSeconds = 10;
        Thread shutdownHook = new Thread(this::shutdownHook);
        shutdownHook.setName("shutdownHook");
        Runtime.getRuntime().addShutdownHook(shutdownHook);
    }

    public synchronized void open() throws IOException {
        log.info("Opening connection..");
        registerCommand(new SetConnectionIdCommand());
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
        for (Connection connection : activeConnections.values()) {
            connection.close();
        }
        activeConnections.clear();
    }

    public void setName(String name) {
        this.context.setName(name);
    }

    public void setDefaultContentBufferSize(int defaultContentBufferSize) {
        this.context.setDefaultContentBufferSize(defaultContentBufferSize);
    }

    public Connection addNode(InetSocketAddress address) throws IOException {
        synchronized (lock) {
            Connection connection = activeConnections.get(address.toString());
            if (connection != null) {
                log.info("{} is already connected.", address.toString());
                return connection;
            }
            return openConnection(address);
        }
    }

    public Connection reconnect(Connection connection) throws IOException {
        return ensureConnection((InetSocketAddress) connection.getRemoteSocketAddress());
    }

    public List<Connection> getActiveConnections() {
        return new ArrayList<>(activeConnections.values());
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

    public void setHeartbeatIntervalSeconds(int heartbeatIntervalSeconds) {
        this.context.setHeartbeatIntervalSeconds(heartbeatIntervalSeconds);
    }

    public void setSslContext(SSLContext sslContext) {
        this.context.getTlsContext().setSslContext(sslContext);
    }

    private void shutdownHook() {
        try {
            log.info("Shutdown detected. Closing client..");
            close();
        } catch (IOException e) {
            log.warn("Failed to stop server.", e);
        }
    }

    private Connection openConnection(InetSocketAddress address) throws IOException {
        TcpChannel channel = TcpChannel.open(SocketChannel.open(), processor.selectProcessor(), context);
        Connection connection = new Connection(channel, worker, context);
        channel.connect(address, connectionTimeoutSeconds, connection);
        activeConnections.put(address.toString(), connection);
        return connection;
    }

    private Connection ensureConnection(InetSocketAddress address) throws IOException {
        Connection connection = activeConnections.get(address.toString());
        if (connection != null && connection.isOpen()) {
            return connection;
        }
        synchronized (lock) {
            connection = activeConnections.get(address.toString());
            if (connection != null && connection.isOpen()) {
                return connection;
            }
            int attempts = 0;
            int maxAttempts = 60;
            while (attempts++ < maxAttempts) {
                log.warn("Try reconnecting.. {}/{}", attempts, maxAttempts);
                try {
                    connection = openConnection(address);
                    if (connection.isOpen()) {
                        return connection;
                    }
                } catch (IOException e) {
                    log.warn(String.format("Failed to connect to %s", address.toString()), e);
                }
                try {
                    TimeUnit.SECONDS.sleep(1);
                } catch (InterruptedException e) {
                    throw new IOException(e);
                }
            }
            throw new IOException(String.format("Connection to %s could not be established.", address.toString()));
        }
    }

    private class DisconnectedListener implements CommandListener {
        @Override
        public void onDisconnected(Connection connection) {
            InetSocketAddress address = (InetSocketAddress) connection.getRemoteSocketAddress();
            if (!activeConnections.remove(address.toString(), connection)) {
                log.warn("The connection seems to be reconnected or deleted.");
            }
        }
    }
}
