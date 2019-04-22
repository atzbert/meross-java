package com.scout24.ha.meross;

import com.scout24.ha.meross.mqtt.CommandTimeoutException;
import com.scout24.ha.meross.mqtt.MerossDevice;
import lombok.Builder;

import java.util.Map;

@Builder
public class DeviceManagerTest {

	private String email;
	private String password;

	public DeviceManagerTest(String email, String password) {
		this.email = email;
		this.password = password;
	}

	protected static final String SEILZUG = "18112014534925258h0234298f142e7f";
	protected static final String SCHLAFZIMMER_BETT = "1810238195273429085234298f17018a";
	protected static final String SCHLAFZIMMER_LICHT = "1903115105716739080834298f1c9c6e";
	protected static final String TERRASSE = "1810238132684529085234298f170019";

	public static void main(String[] args) throws Exception, CommandTimeoutException {
		final String email = args[0].split(" ")[0];
		final String password = args[1].split(" ")[0];
		DeviceManager deviceManager = DeviceManager.builder()
				.email(email)
				.password(password)
				.build();
		deviceManager.initialize();

		final Map<String, MerossDevice> deviceList = deviceManager.getSupportedDevices();

		System.out.println(deviceList);
		for (String deviceUuid : deviceList.keySet()) {
			final MerossDevice attachedDevice = deviceList.get(deviceUuid);
			System.out.println("deviceUuid = " + deviceUuid + " device = " + attachedDevice);
		}
		deviceList.get(TERRASSE).turnOnChannel(3);
	}

}
