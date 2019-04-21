package com.scout24.home.automation.meross;

import com.google.common.collect.Maps;
import com.scout24.home.automation.meross.api.AttachedDevice;
import com.scout24.home.automation.meross.api.MerossHttpClient;
import com.scout24.home.automation.meross.mqtt.CommandTimeoutException;
import com.scout24.home.automation.meross.mqtt.MerossDevice;
import com.scout24.home.automation.meross.mqtt.MqttConnection;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.Map;

@SpringBootApplication
public class MerossHabBindingApplication {

	protected static final String SEILZUG = "18112014534925258h0234298f142e7f";
	protected static final String SCHLAFZIMMER_BETT = "1810238195273429085234298f17018a";
	protected static final String SCHLAFZIMMER_LICHT = "1903115105716739080834298f1c9c6e";

	public static void main(String[] args) throws Exception, CommandTimeoutException {
		SpringApplication.run(MerossHabBindingApplication.class, args);
		final MerossHttpClient merossHttpClient = new MerossHttpClient(args[0].split(" ")[0], args[1].split(" ")[0]);
		final Map<String, AttachedDevice> deviceList = merossHttpClient.mapSupportedDevices(false);
		final Map<String, MerossDevice> merossDeviceList = Maps.newLinkedHashMap();
		MqttConnection connection = new MqttConnection(merossHttpClient.getKey(), merossHttpClient.getUserId(),
					merossHttpClient.getToken(), "eu-iot.meross.com");
		System.out.println(deviceList);
		for (String deviceUuid : deviceList.keySet()) {
			// Returns most of the info about the power plug
			final AttachedDevice attachedDevice = deviceList.get(deviceUuid);
			System.out.println("deviceUuid = " + deviceUuid + " name = " + attachedDevice.getDevName() + " Channels = " + attachedDevice.getChannels());
			MerossDevice device = new MerossDevice(attachedDevice, connection);
			merossDeviceList.put(deviceUuid, device);
//			System.out.println("connection.getStatusImpl() = " + connection.getSysData());
		}
		merossDeviceList.get(SCHLAFZIMMER_LICHT).turnOnChannel(0);
//		merossDeviceList.get(SEILZUG).getAbilities();
		merossDeviceList.get(SEILZUG).getSysData();
//		merossDeviceList.get(SEILZUG).togglex(0, true);
//		do {
//			Map map = connection.receiveMessage();
//			System.out.println("result = " + map);
//		} while (true);
		//{"header":{"messageId":"c9ebd0bf3321612554318f3aed67e9c7","namespace":"Appliance.Control.ToggleX","method":"SET","payloadVersion":1,"from":"/app/147176-341a6e6b37908270653d5d37a9af2e3f/subscribe","timestamp":1555833193,"timestampMs":123,"sign":"fcb2c6dc0b152d90be5ae45d4c083dc2"},"payload":{"togglex":[{"onoff":1,"channel":0,"lmTime":1555833193}]}}
		//{"header":{"messageId":"41605dca1e33a9a9d2405c9a4311d54e","namespace":"Appliance.Control.ToggleX","method":"SET","payloadVersion":1,"from":"/app/147176-254a5505b39dc6b3872e21aa1695be6b/subscribe","timestamp":1555834157,"sign": "da02a9fc19644c5f587859a63de885a9", }, "payload": {"togglex": {"onoff": 1, "channel": 0}}}
	}

}
