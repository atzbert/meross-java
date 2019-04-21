package com.scout24.home.automation.meross.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.scout24.home.automation.meross.api.AttachedDevice;
import com.scout24.home.automation.meross.api.NetworkDevice;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.scout24.home.automation.meross.mqtt.Abilities.CONSUMPTIONX;
import static com.scout24.home.automation.meross.mqtt.Abilities.ELECTRICITY;
import static com.scout24.home.automation.meross.mqtt.Abilities.TOGGLE;
import static com.scout24.home.automation.meross.mqtt.Abilities.TOGGLEX;

@Slf4j
public class MerossDevice {
    private static final int CHANNEL_0 = 0;
    private final AttachedDevice device;
    private final MqttConnection connection;
    private final String clientRequestTopic;

    private List<Map> channels;
    private boolean[] state;

    static ObjectMapper mapper = new ObjectMapper();


    //Cached list of abilities
    List<String> abilities = null;
    private NetworkDevice networkDevice;

    public MerossDevice(AttachedDevice device, MqttConnection connection) throws MQTTException {
        this.device = device;
        this.connection = connection;
        this.clientRequestTopic = "/appliance/" + device.getUuid() + "/subscribe";
    }

    public Map toggle(boolean enabled) {
        ImmutableMap<String, Serializable> payload = ImmutableMap.of(
                "channel", 0,
                "toggle", ImmutableMap.of("onoff", enabled? 1 : 0)
        );
        return connection.executecmd("SET", TOGGLE.getNamespace(), payload, clientRequestTopic);
    }

    public Map togglex(int channel, boolean enabled) throws MQTTException, CommandTimeoutException, InterruptedException {
        Map<String, Serializable> payload = ImmutableMap.of(
                "togglex", ImmutableMap.of(
                        "onoff", enabled? 1:0,
                        "channel", channel,
                        "lmTime", System.currentTimeMillis()/1000)
        );
        return connection.executecmd("SET", TOGGLEX.getNamespace(), payload, clientRequestTopic);
    }

    private Map toggleChannel(int channel, boolean status) throws MQTTException, CommandTimeoutException, InterruptedException {
        if (this.getAbilities().contains(TOGGLE.getNamespace())) {
            return this.toggle(status);
        } else if (this.getAbilities().contains(TOGGLEX.getNamespace())) {
            return this.togglex(channel, status);

        } else {
            throw new MQTTException("The current device does not support neither TOGGLE nor TOGGLEX.");
        }
    }

    private int getChannelId(String channel) throws MQTTException {
        //Otherwise, if the passed channel looks like the channel spec, lookup its array indexindex
        //if a channel name is given, lookup the channel id from the name
        int i = 0;
        for (Map map : channels) {
            if (map.get("devName").equals(channel)) {
                return i;
            }
            i++;
        }
        throw new MQTTException("Invalid channel specified.");
    }


    public NetworkDevice getSysData() {
        if (networkDevice==null) {
            connection.executecmd("GET", "Appliance.System.All", ImmutableMap.of(), clientRequestTopic);
            try {
                final Map map = connection.receiveMessage();
                networkDevice = mapper.convertValue(map, NetworkDevice.class);
            } catch (Exception e) {
                log.error("error retrieving abilities: ", e);
            }
        }
        return networkDevice;
    }

    List<Map> getChannels() {
        return this.channels;
    }

    public List<String> getAbilities() {
        if (this.abilities == null) {
            connection.executecmd("GET", "Appliance.System.Ability", ImmutableMap.of(), clientRequestTopic);
            try {
                final Map map = connection.receiveMessage();
                abilities = Lists.newArrayList(((Map)map.get("ability")).keySet());
            } catch (Exception e) {
                log.error("error retrieving abilities: ", e);
            }

        }
        return this.abilities;
    }

    Map getReport() throws InterruptedException, CommandTimeoutException {
        return connection.executecmd("GET", "Appliance.System.Report", ImmutableMap.of(), clientRequestTopic);
    }

    boolean getChannelStatus(String channel) throws MQTTException {
        int c = this.getChannelId(channel);
        return this.state[c];
    }

    public Map turnOnChannel(int channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        return this.toggleChannel(channel, true);
    }

    public Map turnOffChannel(int channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        return this.toggleChannel(channel, true);
    }

    Map turnOn(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.toggleChannel(c, true);
    }

    Map turnOff(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.toggleChannel(c, false);
    }

    boolean supportsConsumptionReading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(CONSUMPTIONX.getNamespace());
    }

    boolean supportsElectricityReading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(ELECTRICITY.getNamespace());
    }

    Map getPowerConsumption() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(CONSUMPTIONX.getNamespace())) {
            return connection.executecmd("GET", CONSUMPTIONX.getNamespace(), ImmutableMap.of(), clientRequestTopic);
        } else return null;
    }

    Map getElectricity() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(ELECTRICITY.getNamespace())) {
            return connection.executecmd("GET", ELECTRICITY.getNamespace(), ImmutableMap.of(), clientRequestTopic);
        } else return null;
    }

    protected void handleNamespacePayload(String namespace, Map payload) {
        if (namespace.equals(TOGGLE.getNamespace())) {
            final Map<String, ?> toggle = (Map<String, ?>) payload.get("toggle");
            this.state[MerossDevice.CHANNEL_0] = Boolean.parseBoolean(toggle.get("onoff").toString());
        } else if (namespace.equals(TOGGLEX.getNamespace())) {
            if (payload.get("togglex") instanceof List) {
                final List<Map> togglex = (List) payload.get("togglex");
                for (Map map : togglex) {
                    int channelindex = Integer.parseInt(map.get("channel").toString());
                    this.state[channelindex] = Boolean.parseBoolean(map.get("onoff").toString());
                }
            } else if (payload.get("togglex") instanceof Map) {
                final Map togglex = (Map) payload.get("togglex");
                int channelindex = Integer.parseInt(togglex.get("channel").toString());
                this.state[channelindex] = Boolean.parseBoolean(togglex.get("onoff").toString());
            }
        } else if (namespace.equals(Abilities.ONLINE.getNamespace())) {
            log.info("Online keep alive received: " + payload);
        } else {
            log.error("Unknown/Unsupported namespace/command: " + namespace);
        }
    }

    public void consumeMessage(Map payload) {

        this.handleNamespacePayload("", payload);

    }
    //
//    Map getWifilist() throws InterruptedException, CommandTimeoutException, MQTTException {
//        return executecmd("GET", "Appliance.Config.WifiList", ImmutableMap.of(), LONG_TIMEOUT, clientresponsetopic, clientRequestTopic);
//    }
//
//    Map getTrace() throws InterruptedException, CommandTimeoutException, MQTTException {
//        return executecmd("GET", "Appliance.Config.Trace", ImmutableMap.of(), SHORT_TIMEOUT, clientresponsetopic, clientRequestTopic);
//    }
//
//    Map getDebug() throws InterruptedException, CommandTimeoutException {
//        return executecmd("GET", "Appliance.System.Debug", ImmutableMap.of(), SHORT_TIMEOUT, clientresponsetopic, clientRequestTopic);
//    }

}