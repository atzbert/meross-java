package com.scout24.ha.meross.mqtt;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.Wither;

import java.util.Map;

/**
 *
 * {"header":{"messageId":"eb873fadf67b98baf9759b1822d689a6","namespace":"Appliance.Control.ToggleX","method":"PUSH","payloadVersion":1.0,"from":"/app/1903115105716739080834298f1c9c6e-341a6e6b37908270653d5d37a9af2e3f/subscribe","timestamp":1555614846,"timestampMs":123,"sign":"7d0929742957164c1d60d4be7f13f76b"},"payload":{"togglex":[{"onoff":1,"channel":0}]}}
 * Initially created by Tino on 18.04.19.
 * { "header": {
 * 	"messageId": "a8fd756033c7a8b0d16494719e7a7dee",
 * 	"namespace": "Appliance.Control.ToggleX",
 * 	"method": "PUSH",
 * 	"payloadVersion": 1,
 * 	"from": "/appliance/1903115105716739080834298f1c9c6e/publish",
 * 	"timestamp": 1555613748,
 * 	"timestampMs": 635,
 * 	"sign": "ac3482ede0243265e9f79166e94d2b1c"
 *   },
 *   "payload": {
 * 	"togglex": [
 *            {
 * 		"channel": 0,
 * 		"onoff": 1,
 * 		"lmTime": 1555613747
 *      }
 * 	]
 *   }
 * }
 */
@Data
@Wither
@NoArgsConstructor
@AllArgsConstructor
public class Message {
        private Header header;
        private Map payload;

    @Data
    @NoArgsConstructor
    @Wither
    @AllArgsConstructor
    public static class Header {
        private String messageId;
        private String namespace;
        private String method;
        private int payloadVersion;
        private String from;
        private long timestamp;
        private long timestampMs;
        private String sign;
    }
}
