package com.scout24.home.automation.meross.api;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class Device {
    private String uuid;
    private long onlineStatus;
    private String devName;
    private String devIconId;
    private long bindTime;
    private String deviceType;
    private String subType;
    private List<Map> channels = new ArrayList <> ();
    private String region;
    private String fmwareVersion;
    private String hdwareVersion;
    private String userDevIcon;
    private int iconType;
    private String skillNumber;
    private String domain;
    private String reservedDomain;

}
