package com.scout24.ha.meross.rest;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
@NoArgsConstructor
public class NetworkDevice {

    private All all;

    @Data
    @NoArgsConstructor
    public static class All {
        private System system;
        private Digest digest;
        private Map control;
    }
    @Data
    @NoArgsConstructor
    public static class Digest {
        private ArrayList< Map > togglex = new ArrayList <> ();
        private ArrayList < Object > triggerx = new ArrayList < Object > ();
        private ArrayList < Object > timerx = new ArrayList < Object > ();
    }
    @Data
    @NoArgsConstructor
    public static class System {
        private Firmware firmware;
        private Hardware hardware;
        private Online online;
        private Time time;

        public boolean isOnline() {
            return online.status == 1;
        }
    }
    @Data
    @NoArgsConstructor
    public static class Time {
        private String timezone;
        private long timestamp;
        private ArrayList < Integer[] > timeRule = new ArrayList<>();
    }
    @Data
    @NoArgsConstructor
    public static class Online {
        private int status;
    }
    @Data
    @NoArgsConstructor
    public static class Hardware {
        private String version;
        private String chipType;
        private String macAddress;
        private String subType;
        private String uuid;
        private String type;
    }
    @Data
    @NoArgsConstructor
    public static class Firmware {
        private String version;
        private String innerIp;
        private long userId;
        private String wifiMac;
        private String compileTime;
        private int port;
        private String server;
    }
}
