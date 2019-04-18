package com.scout24.home.automation.meross.api;

import lombok.Data;

/**
 * Initially created by Tino on 13.04.19.
 */
@Data
public class AttachedDevice {
    private Device device;
    private String token;
    private String key;
    private long userId;
    private String deviceType;
    public AttachedDevice(Device device, String token, String key, long userId) {
        this.device = device;
        this.token = token;
        this.key = key;
        this.userId = userId;
        this.deviceType = device.getDeviceType();
    }
}
