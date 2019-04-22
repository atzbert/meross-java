package com.scout24.ha.meross;

import com.google.common.collect.Maps;
import com.scout24.ha.meross.mqtt.MQTTException;
import com.scout24.ha.meross.mqtt.MerossDevice;
import com.scout24.ha.meross.mqtt.MqttConnection;
import com.scout24.ha.meross.rest.AttachedDevice;
import com.scout24.ha.meross.rest.MerossHttpClient;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Builder
@Slf4j
public class DeviceManager {

	private String email;
	private String password;

	private final Map<String, MerossDevice> merossDeviceList = Maps.newLinkedHashMap();


	public DeviceManager(String email, String password) {
		this.email = email;
		this.password = password;
	}
	public DeviceManager initialize() throws MQTTException {
		final MerossHttpClient merossHttpClient = new MerossHttpClient(email, password);
		final Map<String, AttachedDevice> deviceList = merossHttpClient.mapSupportedDevices(false);
		MqttConnection connection = new MqttConnection(merossHttpClient.getKey(), merossHttpClient.getUserId(),
					merossHttpClient.getToken(), "eu-iot.meross.com");
		for (String deviceUuid : deviceList.keySet()) {
			final AttachedDevice attachedDevice = deviceList.get(deviceUuid);
			log.info("Found device with uuid = " + deviceUuid + " and name = " + attachedDevice.getDevName() + " with channels = " + attachedDevice.getChannels());
			MerossDevice device = new MerossDevice(attachedDevice, connection);
			merossDeviceList.put(deviceUuid, device);
		}
		return this;
	}

	public Map<String, MerossDevice> getSupportedDevices() {
		return merossDeviceList;
	}
}
