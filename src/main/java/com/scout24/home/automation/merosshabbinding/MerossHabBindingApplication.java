package com.scout24.home.automation.merosshabbinding;

import com.scout24.home.automation.meross_iot.MerossHttpClient;
import com.scout24.home.automation.meross_iot.model.AttachedDevice;
import com.scout24.home.automation.meross_iot.supported_devices.CommandTimeoutException;
import com.scout24.home.automation.meross_iot.supported_devices.MQTTException;
import com.scout24.home.automation.meross_iot.supported_devices.MerossDevice;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import java.util.List;

@SpringBootApplication
public class MerossHabBindingApplication {

	public static void main(String[] args) throws MerossHttpClient.UnauthorizedException, MerossHttpClient.AuthenticatedPostException, MqttException, InterruptedException, CommandTimeoutException, MQTTException {
		SpringApplication.run(MerossHabBindingApplication.class, args);
		final MerossHttpClient merossHttpClient = new MerossHttpClient(args[0].split(" ")[0], args[1].split(" ")[0]);
		final List<AttachedDevice> deviceList = merossHttpClient.listSupportedDevices(false);
		System.out.println(deviceList);
		for (AttachedDevice device : deviceList) {
			// Returns most of the info about the power plug
			System.out.println("device = " + device);
			MerossDevice connection = new MerossDevice(device);
			System.out.println("connection.getStatusImpl() = " + connection.getSysData());
		}
	}

}
