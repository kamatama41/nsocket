package com.github.kamatama41.socket;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

class InteractionTest {
    private static final Random RANDOM = new Random();

    public static void main(String[] args) throws Exception {
        SocketServer server = new SocketServer();
        server.setNumOfWorkers(2);
        server.registerCommand(new PingCommand());
        server.registerSyncCommand(new SquareCommand());
        try {
            server.start();
//            runClients(Runtime.getRuntime().availableProcessors() * 2);
            runClients(1);
        } finally {
            server.stop();
        }
    }

    private static void runClients(int numOfClients) {
        ExecutorService es = Executors.newFixedThreadPool(numOfClients);
        List<Future<SocketClient>> futures = new ArrayList<>();
        for (int i = 0; i < numOfClients; i++) {
            futures.add(runClient(es, i));
        }
        for (Future<SocketClient> future : futures) {
            try {
                future.get().close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        es.shutdown();
    }

    private static Future<SocketClient> runClient(ExecutorService es, int index) {
        return es.submit(() -> {
            SocketClient client = new SocketClient();
            client.registerCommand(new PongCommand(index));
            client.registerSyncCommand(new SquareCommand());
            try {
                client.open();
                List<String> names = Arrays.asList("Alice", "Bob", "Charlie");
                int count = 0;
                for (int _ignored = 0; _ignored < 20; _ignored++) {
                    for (int i = 0; i < 1; i++) {
                        User user = new User();
                        user.setId(index + "-" + count);
                        user.setName(names.get(RANDOM.nextInt(names.size())));
                        client.sendCommand("ping", user);
                        count++;
                    }
                    TimeUnit.SECONDS.sleep(1);
                }
                System.out.println(String.format("%d * %d = %d",
                        index + 2, index + 2, client.<Integer>sendSyncCommand("square", index +  2)
                ));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return client;
        });
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
        @Override
        public void execute(User data, Connection connection) {
            connection.sendCommand("pong", data);
        }
    }

    private static class PongCommand implements Command<User> {
        private final int index;

        PongCommand(int index) {
            this.index = index;
        }

        @Override
        public void execute(User data, Connection connection) {
            System.out.println(String.format("index: %d, data: %s", index, data.toString()));
        }
    }

    private static class SquareCommand implements SyncCommand<Integer, Integer> {
        @Override
        public Integer apply(Integer data, Connection connection) {
            return data * data;
        }

        @Override
        public long getTimeoutMillis() {
            return 100L;
        }
    }
}
