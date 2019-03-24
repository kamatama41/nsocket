package com.github.kamatama41.nsocket;

import java.util.Arrays;
import java.util.stream.Collectors;

class RequestMessageCodec {
    private RequestMessageCodec() {
    }

    static String encode(String commandId, Integer callId, String bodyJson) {
        /*
         * CommandID + CRLF
         * CallID + CRLF
         * BodyJSON
         */
        return commandId + "\r\n" +
                (callId == null ? "" : callId) + "\r\n" +
                bodyJson;
    }

    static Decoded decode(String message) {
        String[] split = message.split("\r\n");
        if (split.length < 3) {
            throw new IllegalStateException(String.format("Request message must be 3 lines, but %d", split.length));
        }
        String commandId = split[0];
        Integer callId = split[1].isEmpty() ? null : Integer.parseInt(split[1]);
        String bodyJson = Arrays.stream(split, 2, split.length)
                .collect(Collectors.joining());
        return new Decoded(commandId, callId, bodyJson);
    }

    static class Decoded {
        private final String commandId;
        private final Integer callId;
        private final String bodyJson;

        Decoded(String commandId, Integer callId, String bodyJson) {
            this.commandId = commandId;
            this.callId = callId;
            this.bodyJson = bodyJson;
        }

        String getCommandId() {
            return commandId;
        }

        Integer getCallId() {
            return callId;
        }

        String getBodyJson() {
            return bodyJson;
        }
    }
}
