package com.scout24.ha.meross.rest;

import lombok.Data;
import lombok.experimental.Delegate;

/**
 * Initially created by Tino on 13.04.19.
 */
@Data
public class AttachedDevice {
    @Delegate
    private Device device;
    private String token;
    private String key;
    private long userId;
    public AttachedDevice(Device device, String token, String key, long userId) {
        this.device = device;
        this.token = token;
        this.key = key;
        this.userId = userId;
    }

    public boolean isOnline() {
        return device.getOnlineStatus() == 1;
    }
}
