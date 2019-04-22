package com.scout24.ha.meross;

import com.google.common.collect.Maps;
import com.scout24.ha.meross.mqtt.MQTTException;
import com.scout24.ha.meross.mqtt.MerossDevice;
import com.scout24.ha.meross.mqtt.MqttConnection;
import com.scout24.ha.meross.rest.AttachedDevice;
import com.scout24.ha.meross.rest.MerossHttpClient;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;

@Slf4j
public class DeviceManager {

	protected static final String MEROSS_BROKER_DOMAIN = "eu-iot.meross.com";
	private String email;
	private String password;

	private final Map<String, MerossDevice> merossDeviceList = Maps.newLinkedHashMap();
	private MerossHttpClient merossHttpClient;
	private Map<String, AttachedDevice> deviceList;
	private MqttConnection connection;


	public DeviceManager(String email, String password) {
		this.email = email;
		this.password = password;
	}

	public DeviceManager initializeDevices() throws MQTTException {
		for (String deviceUuid : deviceList.keySet()) {
			final AttachedDevice attachedDevice = deviceList.get(deviceUuid);
			log.info("Found device with uuid = " + deviceUuid + " and name = " +
					attachedDevice.getDevName() + " with channels = " + attachedDevice.getChannels());
			if (attachedDevice.isOnline()) {
				MerossDevice device = new MerossDevice(attachedDevice, connection);
				device.initialize();
				merossDeviceList.put(deviceUuid, device);
			} else {
				log.info("Did not connect to broker for offline device " + deviceUuid);
			}
		}
		return this;
	}

	public Map<String, MerossDevice> getSupportedDevices() {
		return merossDeviceList;
	}

	public boolean connect()  {
		try {
			merossHttpClient = new MerossHttpClient(email, password);
			deviceList = merossHttpClient.mapSupportedDevices(false);
			connection = new MqttConnection(merossHttpClient.getKey(), merossHttpClient.getUserId(),
					merossHttpClient.getToken(), MEROSS_BROKER_DOMAIN);
		} catch (MQTTException e) {
			log.error("Error connecting ");
		}
		return true;
	}
}
