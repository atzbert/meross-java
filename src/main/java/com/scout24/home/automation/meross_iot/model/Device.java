package com.scout24.home.automation.meross_iot.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
public class Device {
    private String uuid;
    private float onlineStatus;
    private String devName;
    private String devIconId;
    private float bindTime;
    private String deviceType;
    private String subType;
    private List<Map> channels = new ArrayList <> ();
    private String region;
    private String fmwareVersion;
    private String hdwareVersion;
    private String userDevIcon;
    private float iconType;
    private String skillNumber;
    private String domain;
    private String reservedDomain;

}
