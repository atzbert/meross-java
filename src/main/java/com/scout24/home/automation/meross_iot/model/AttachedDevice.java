package com.scout24.home.automation.meross_iot.model;

import lombok.Data;

/**
 * Initially created by Tino on 13.04.19.
 */
@Data
public class AttachedDevice {
    private Device device;
    private String token;
    private String key;
    private long user_id;
    private String device_type;
    public AttachedDevice(Device device, String token, String key, long user_id) {
        this.device = device;
        this.token = token;
        this.key = key;
        this.user_id = user_id;
        this.device_type = device.getDeviceType();
    }
}
