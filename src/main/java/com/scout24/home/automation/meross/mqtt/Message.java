package com.scout24.home.automation.meross.mqtt;

import lombok.Builder;
import lombok.Data;

import java.util.Map;

/**
 * Initially created by Tino on 18.04.19.
 */
@Data
@Builder
public class Message {
        private Header header;
        private Map payload;

    @Data
    @Builder
    public static class Header {
        private String messageId;
        private String namespace;
        private String method;
        private double payloadVersion;
        private String from;
        private long timestamp;
        private long timestampMs;
        private String sign;
    }
}
