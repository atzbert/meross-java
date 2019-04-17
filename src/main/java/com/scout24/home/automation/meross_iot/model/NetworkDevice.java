package com.scout24.home.automation.meross_iot.model;


import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.Map;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class NetworkDevice {
    All all;


    @Data
    public class All {
        private System system;
        private Digest digest;
        private Map control;
    }
    @Data
    public class Digest {
        private ArrayList< Object > togglex = new ArrayList < Object > ();
        private ArrayList < Object > triggerx = new ArrayList < Object > ();
        private ArrayList < Object > timerx = new ArrayList < Object > ();
    }
    @Data
    public class System {
        private Firmware firmware;
        private Hardware hardware;
        private Online online;
        private Time time;

        public boolean isOnline() {
            return online.status == 1;
        }
    }
    @Data
    public class Time {
        private String timezone;
        private long timestamp;
        private ArrayList < Integer[] > timeRule = new ArrayList<>();

    }
    @Data
    public class Online {
        private int status;
    }
    @Data
    public class Hardware {
        private String version;
        private String chipType;
        private String macAddress;
        private String subType;
        private String uuid;
        private String type;
    }
    @Data
    public class Firmware {
        private String version;
        private String innerIp;
        private long userId;
        private String wifiMac;
        private String compileTime;
        private int port;
        private String server;


    }
}
