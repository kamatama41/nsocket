package com.github.kamatama41.nsocket.integration;

import com.github.kamatama41.nsocket.Command;
import com.github.kamatama41.nsocket.CommandListener;
import com.github.kamatama41.nsocket.Connection;
import com.github.kamatama41.nsocket.SocketClient;
import com.github.kamatama41.nsocket.SocketServer;
import com.github.kamatama41.nsocket.SyncCommand;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

class IntegrationTest {
    private final Random RANDOM = new Random();
    private final InetSocketAddress ADDRESS_30000 = new InetSocketAddress("localhost", 30000);
    private final InetSocketAddress ADDRESS_30001 = new InetSocketAddress("localhost", 30001);
    private static final List<String> CONTENTS = Arrays.asList(
            // Simple string
            "Hello",
            // Includes newline char
            "I have a pen.\r\nI have an apple.",
            // Too large
            IntStream.range(0, 20000).mapToObj(i -> "a").collect(Collectors.joining())
    );

    private static final Logger log = LoggerFactory.getLogger(IntegrationTest.class);

    public static void main(String[] args) throws Exception {
        new IntegrationTest().runServersAndClients();
    }

    @Test
    void runServersAndClients() throws Exception {
        List<SocketServer> servers = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            SocketServer server = new SocketServer();
            server.setName("server" + i);
            server.setPort(30000 + i);
            server.setNumOfWorkers(2);
            server.registerCommand(new PingCommand());
            server.registerSyncCommand(new SquareCommand());
            server.registerListener(new DebugListener());
            server.setDefaultContentBufferSize(16 * 1024);
            server.setHeartbeatIntervalSeconds(1);
            servers.add(server);
        }

        try {
            for (SocketServer server : servers) {
                server.start();
            }
//            runClients(Runtime.getRuntime().availableProcessors() * 2);
            runClients(1);
        } finally {
            for (SocketServer server : servers) {
                server.stop();
            }
        }
        TimeUnit.SECONDS.sleep(1);
    }

    private void runClients(int numOfClients) throws Exception {
        ExecutorService es = Executors.newFixedThreadPool(numOfClients);
        List<Future<Void>> futures = new ArrayList<>();
        for (int i = 0; i < numOfClients; i++) {
            futures.add(runClient(es, i));
        }
        try {
            for (Future<Void> future : futures) {
                future.get();
            }
        } finally {
            es.shutdown();
        }
    }

    private Future<Void> runClient(ExecutorService es, int index) {
        return es.submit(() -> {
            SocketClient client = new SocketClient();
            Connection[] connections = new Connection[2];
            client.setName("client" + index);
            client.registerCommand(new PongCommand(index));
            client.registerSyncCommand(new SquareCommand());
            client.registerListener(new DebugListener());
            client.setDefaultContentBufferSize(16 * 1024);
            client.setHeartbeatIntervalSeconds(1);
            try {
                client.open();
                connections[0] = client.addNode(ADDRESS_30000);
                connections[1] = client.addNode(ADDRESS_30001);
                int count = 0;
                Connection conn = chooseConnection(connections, client);
                for (int _ignored = 0; _ignored < 10; _ignored++) {
                    for (int i = 0; i < 1; i++) {
                        Message message = new Message();
                        message.setId(index + "-" + count);
                        message.setContent(CONTENTS.get(RANDOM.nextInt(CONTENTS.size())));
                        conn.sendCommand(PingCommand.ID, message);
                        count++;
                    }
                    TimeUnit.SECONDS.sleep(1);
                    if (RANDOM.nextBoolean()) {
                        // Randomly shutdown to check reconnecting feature
                        conn.close();
                        conn = chooseConnection(connections, client);
                    }
                }
                System.out.println(String.format("%d * %d = %d",
                        index + 2, index + 2, conn.<Integer>sendSyncCommand(SquareCommand.ID, index + 2)
                ));
            } finally {
                client.close();
            }
            return null;
        });
    }

    private Connection chooseConnection(Connection[] connections, SocketClient client) throws IOException {
        int index = RANDOM.nextInt(1);
        Connection connection = connections[index];
        if (connection.isOpen()) {
            return connection;
        } else {
            Connection reconnected = client.reconnect(connection);
            connections[index] = reconnected;
            return reconnected;
        }
    }

    public static class Message {
        private String id;
        private String content;

        public Message() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }

        @Override
        public String toString() {
            return "Message{" +
                    "id=" + id +
                    ", content='" +
                    content.substring(0, Math.min(30, content.length())) +
                    (content.length() > 30 ? "..." : "") +
                    '\'' +
                    '}';
        }
    }

    private static class PingCommand implements Command<Message> {
        static final String ID = "ping";

        @Override
        public void execute(Message data, Connection connection) {
            connection.sendCommand(PongCommand.ID, data);
        }

        @Override
        public String getId() {
            return ID;
        }
    }

    private static class PongCommand implements Command<Message> {
        static final String ID = "pong";
        private final int index;

        PongCommand(int index) {
            this.index = index;
        }

        @Override
        public void execute(Message data, Connection connection) {
            System.out.println(String.format("index: %d, data: %s", index, data.toString()));
        }

        @Override
        public String getId() {
            return ID;
        }
    }

    private static class SquareCommand implements SyncCommand<Integer, Integer> {
        static final String ID = "square";

        @Override
        public Integer apply(Integer data, Connection connection) {
            return data * data;
        }

        @Override
        public long getTimeoutMillis() {
            return 100L;
        }

        @Override
        public String getId() {
            return ID;
        }
    }

    private static class DebugListener implements CommandListener {
        @Override
        public void onConnected(Connection connection) {
            log.debug("Connected: " + connection.toString());
        }

        @Override
        public void onDisconnected(Connection connection) {
            log.debug("Disconnected: " + connection.toString());
        }

        @Override
        public void onException(Connection connection, Exception ex) {
            log.warn("Exception", ex);
        }
    }
}
