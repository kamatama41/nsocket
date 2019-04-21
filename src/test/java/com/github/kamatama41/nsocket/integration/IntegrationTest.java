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

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.security.KeyStore;
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
    private static final Logger log = LoggerFactory.getLogger(IntegrationTest.class);

    public static void main(String[] args) throws Exception {
        new IntegrationTest().runServersAndClients();
        System.out.println("================================================================================");
        new IntegrationTest().runServersAndClientsWithSSL();
    }

    @Test
    void runServersAndClients() throws Exception {
        new TestRunner()
                .numOfServers(2)
                .numOfClients(1)
                .run();
    }

    @Test
    void runServersAndClientsWithSSL() throws Exception {
        new TestRunner()
                .numOfServers(2)
                .numOfClients(1)
                .useTls(true)
                .run();
    }

    private static class TestRunner {
        private static final List<String> CONTENTS = Arrays.asList(
                // Simple string
                "Hello",
                // Includes newline char
                "I have a pen.\r\nI have an apple.",
                // Too large
                IntStream.range(0, 20000).mapToObj(i -> "a").collect(Collectors.joining())
        );
        private int numOfServers = 2;
        private int numOfClients = 1;
        private boolean useTls = false;
        private List<InetSocketAddress> hosts = new ArrayList<>();
        private final Random random = new Random();

        TestRunner numOfServers(int numOfServers) {
            this.numOfServers = numOfServers;
            return this;
        }

        TestRunner numOfClients(int numOfClients) {
            this.numOfClients = numOfClients;
            return this;
        }

        TestRunner useTls(boolean useTls) {
            this.useTls = useTls;
            return this;
        }

        void run() throws Exception {
            List<SocketServer> servers = new ArrayList<>();
            hosts.clear();
            for (int i = 0; i < numOfServers; i++) {
                SocketServer server = new SocketServer();
                int port = 30000 + i;
                server.setName("server" + i);
                server.setPort(port);
                server.setNumOfWorkers(2);
                server.registerCommand(new PingCommand());
                server.registerSyncCommand(new SquareCommand());
                server.registerListener(new DebugListener());
                server.setDefaultContentBufferSize(16 * 1024);
                server.setHeartbeatIntervalSeconds(1);
                if (useTls) {
                    server.setSslContext(createSSLContext("test/work/nsocket.server.p12"));
                    server.enableSslClientAuth();
                }
                servers.add(server);
                hosts.add(new InetSocketAddress("localhost", port));
            }

            try {
                for (SocketServer server : servers) {
                    server.start();
                }
                runClients(numOfClients);
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
                List<Connection> connections = new ArrayList<>(hosts.size());
                client.setName("client" + index);
                client.registerCommand(new PongCommand(index));
                client.registerSyncCommand(new SquareCommand());
                client.registerListener(new DebugListener());
                client.setDefaultContentBufferSize(16 * 1024);
                client.setHeartbeatIntervalSeconds(1);
                if (useTls) {
                    client.setSslContext(createSSLContext("test/work/nsocket.client.p12"));
                }
                try {
                    client.open();
                    for (InetSocketAddress host : hosts) {
                        connections.add(client.addNode(host));
                    }
                    int count = 0;
                    Connection conn = chooseConnection(connections, client);
                    for (int _ignored = 0; _ignored < 10; _ignored++) {
                        for (int i = 0; i < 1; i++) {
                            System.out.println(String.format("%d * %d = %d",
                                    count, count, conn.<Integer>sendSyncCommand(SquareCommand.ID, count)
                            ));

                            Message message = new Message();
                            message.setId(index + "-" + count);
                            message.setContent(CONTENTS.get(random.nextInt(CONTENTS.size())));
                            conn.sendCommand(PingCommand.ID, message);
                            count++;
                        }
                        TimeUnit.MILLISECONDS.sleep(500L);
                        if (random.nextBoolean()) {
                            // Randomly shutdown to check reconnecting feature
                            conn.close();
                            conn = chooseConnection(connections, client);
                        }
                    }
                } finally {
                    client.close();
                }
                return null;
            });
        }

        private Connection chooseConnection(List<Connection> connections, SocketClient client) throws IOException {
            int index = random.nextInt(hosts.size());
            Connection connection = connections.get(index);
            if (connection.isOpen()) {
                return connection;
            } else {
                Connection reconnected = client.reconnect(connection);
                connections.add(index, reconnected);
                return reconnected;
            }
        }

        private static SSLContext createSSLContext(String keyPath) throws Exception {
            final String password = "passw0rd";

            KeyStore keyStore = KeyStore.getInstance("PKCS12");
            try (InputStream keyStoreIS = new FileInputStream(keyPath)) {
                keyStore.load(keyStoreIS, password.toCharArray());
            }
            final KeyManagerFactory kmf =
                    KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(keyStore, password.toCharArray());

            KeyStore trustStore = KeyStore.getInstance("PKCS12");
            try (InputStream trustStoreIS = new FileInputStream("test/work/ca-chain.p12")) {
                trustStore.load(trustStoreIS, password.toCharArray());
            }
            final TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(trustStore);

            SSLContext context = SSLContext.getInstance("TLS");
            context.init(kmf.getKeyManagers(), tmf.getTrustManagers(), null);
            return context;
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
