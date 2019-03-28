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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class IntegrationTest {
    private final Random RANDOM = new Random();
    private final InetSocketAddress ADDRESS_30000 = new InetSocketAddress("localhost", 30000);
    private final InetSocketAddress ADDRESS_30001 = new InetSocketAddress("localhost", 30001);
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
            client.setName("client" + index);
            client.registerCommand(new PongCommand(index));
            client.registerSyncCommand(new SquareCommand());
            client.registerListener(new DebugListener());
            try {
                client.open(ADDRESS_30000);
                client.addNode(ADDRESS_30001);
                List<String> names = Arrays.asList("Alice", "Bob", "Char\r\nlie");
                int count = 0;
                Connection conn = chooseConnection(client);
                for (int _ignored = 0; _ignored < 10; _ignored++) {
                    for (int i = 0; i < 1; i++) {
                        User user = new User();
                        user.setId(index + "-" + count);
                        user.setName(names.get(RANDOM.nextInt(names.size())));
                        conn.sendCommand(PingCommand.ID, user);
                        count++;
                    }
                    TimeUnit.SECONDS.sleep(1);
                    if (RANDOM.nextBoolean()) {
                        // Randomly shutdown to check reconnecting feature
                        conn.close();
                        conn = chooseConnection(client);
                    }
                }
                System.out.println(String.format("%d * %d = %d",
                        index + 2, index + 2, conn.<Integer>sendSyncCommand(SquareCommand.ID, index +  2)
                ));
            } finally {
                client.close();
            }
            return null;
        });
    }

    private Connection chooseConnection(SocketClient client) {
        if (RANDOM.nextBoolean()) {
            return client.getConnection(ADDRESS_30000);
        } else {
            return client.getConnection(ADDRESS_30001);
        }
    }

    public static class User {
        private String id;
        private String name;

        public User() {
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        @Override
        public String toString() {
            return "User{" +
                    "id=" + id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    private static class PingCommand implements Command<User> {
        static final String ID = "ping";

        @Override
        public void execute(User data, Connection connection) {
            connection.sendCommand(PongCommand.ID, data);
        }

        @Override
        public String getId() {
            return ID;
        }
    }

    private static class PongCommand implements Command<User> {
        static final String ID = "pong";
        private final int index;

        PongCommand(int index) {
            this.index = index;
        }

        @Override
        public void execute(User data, Connection connection) {
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
            log.debug("Connected");
            connection.attach(connection.toString());
        }

        @Override
        public void onDisconnected(Connection connection) {
            log.debug("Disconnected: " + connection.attachment());
        }

        @Override
        public void onException(Connection connection, Exception ex) {
            log.debug("Exception: " + ex.getMessage());
        }
    }
}
