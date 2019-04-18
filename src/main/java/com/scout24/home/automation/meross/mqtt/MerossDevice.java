package com.scout24.home.automation.meross.mqtt;

import com.google.common.collect.ImmutableMap;
import com.scout24.home.automation.meross.api.AttachedDevice;
import com.scout24.home.automation.meross.api.NetworkDevice;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.scout24.home.automation.meross.mqtt.Abilities.CONSUMPTIONX;
import static com.scout24.home.automation.meross.mqtt.Abilities.ELECTRICITY;
import static com.scout24.home.automation.meross.mqtt.Abilities.TOGGLE;
import static com.scout24.home.automation.meross.mqtt.Abilities.TOGGLEX;

public class MerossDevice {
    private static final int CHANNEL_0 = 0;
    private final AttachedDevice device;
    private final MqttConnection connection;

    private List<Map> channels;
    private boolean[] state;


    //Cached list of abilities
    List<?> abilities = new ArrayList<>();

    public MerossDevice(String token, String key, Long userid, AttachedDevice device) throws MqttException {
        super(key, userid, token, device.getDevice().getUuid(), device.getDevice().getDomain());
        this.device = device;
        this.channels = device.getDevice().getChannels();
        this.state = new boolean[channels.size()];

        this.generateFClientandAppId();
        this.connectToBroker();

        this.clientrequesttopic = "/appliance/" + this.uuid + "/subscribe";
        this.clientresponsetopic = "/app/" + this.uuid + "-" + this.appid + "/subscribe";
        subscribeToTopics();
    }

    public MerossDevice(AttachedDevice device) throws MqttException {
        this(device.getToken(), device.getKey(), device.getUserId(), device);
    }

    public MerossDevice(AttachedDevice device, MqttConnection connection)  {
        this.device = device;
        this.connection = connection;
    }


    public Map toggle(int status) throws MQTTException, CommandTimeoutException, InterruptedException {
        ImmutableMap<String, Serializable> payload = ImmutableMap.of("channel", 0, "toggle", ImmutableMap.of("onoff", status));
        return this.executecmd("SET", TOGGLE.getNamespace(), payload, SHORT_TIMEOUT);
    }

    public Map togglex(int channel, int status) throws MQTTException, CommandTimeoutException, InterruptedException {
        Map<String, Map<String, Integer>> payload = ImmutableMap.of("togglex", ImmutableMap.of("onoff", status, "channel", channel));
        return this.executecmd("SET", TOGGLEX.getNamespace(), payload, SHORT_TIMEOUT);
    }

    private Map channelControlImpl(int channel, int status) throws MQTTException, CommandTimeoutException, InterruptedException {
        if (this.abilities.contains(TOGGLE)) {

            return this.toggle(status);
        } else if (this.abilities.contains(TOGGLEX)) {
            return this.togglex(channel, status);

        } else {
            throw new MQTTException("The current device does not support neither TOGGLE nor TOGGLEX.");
        }
    }

    public void initializeStatus() throws InterruptedException, CommandTimeoutException, MQTTException {
        NetworkDevice data = this.getSysData();
        final NetworkDevice.Digest digest = data.getAll().getDigest();
        if (digest != null) {
            for (Object c : digest.getTogglex()) {
//                state[(int) ((Map) c).get("channel")] = ((Map) c).get("channel"));
            }
        } else {
            final Map control = data.getAll().getControl();
            if (control != null) {
                state[0] = Boolean.parseBoolean(((Map) control.get("toggle")).get("'onoff'").toString());
            }
        }
    }


    Object getChannelId(Map channel) throws MQTTException {
        //Otherwise, if the passed channel looks like the channel spec, lookup its array indexindex
        if (this.channels.contains(channel)) {
            return this.channels.indexOf(channel);
        }
        //In other cases return an error
        throw new MQTTException("Invalid channel specified.");
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

    public String getDeviceid() {
        return this.uuid;
    }

    public NetworkDevice getSysData() throws InterruptedException, CommandTimeoutException, MQTTException {
        return mapper.convertValue(this.executecmd("GET", "Appliance.System.All", ImmutableMap.of(), LONG_TIMEOUT), NetworkDevice.class);
    }

    List<Map> getChannels() {
        return this.channels;
    }

    Map getWifilist() throws InterruptedException, CommandTimeoutException, MQTTException {
        return this.executecmd("GET", "Appliance.Config.WifiList", ImmutableMap.of(), LONG_TIMEOUT);
    }

    Map gettrace() throws InterruptedException, CommandTimeoutException, MQTTException {
        return this.executecmd("GET", "Appliance.Config.Trace", ImmutableMap.of(), SHORT_TIMEOUT);
    }

    Map getdebug() throws InterruptedException, CommandTimeoutException {
        return this.executecmd("GET", "Appliance.System.Debug", ImmutableMap.of(), SHORT_TIMEOUT);
    }

    List<?> getAbilities() throws InterruptedException, CommandTimeoutException, MQTTException {
        //TODO: Make this cached value expire after a bit...
        if (this.abilities == null) {
            this.abilities = (List<?>)
                    this.executecmd("GET", "Appliance.System.Ability", ImmutableMap.of(), SHORT_TIMEOUT).get("ability");
        }
        return this.abilities;
    }

    Map getReport() throws InterruptedException, CommandTimeoutException {
        return this.executecmd("GET", "Appliance.System.Report", ImmutableMap.of(), SHORT_TIMEOUT);
    }

    boolean getChannelStatus(String channel) throws MQTTException {
        int c = this.getChannelId(channel);
        return this.state[c];
    }

    Map turnOnChannel(String channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 1);
    }

    Map turnOffChannel(String channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 0);
    }

    Map turnOn(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 1);
    }

    Map turnOff(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 0);
    }

    boolean supportsConsumptionReading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(CONSUMPTIONX);
    }

    boolean supportsElectricityReading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(ELECTRICITY);
    }

    Map getPowerConsumption() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(CONSUMPTIONX)) {
            return this.executecmd("GET", CONSUMPTIONX.getNamespace(), ImmutableMap.of(), SHORT_TIMEOUT);
        } else return null;
    }

    Map getElectricity() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(ELECTRICITY)) {
            return this.executecmd("GET", ELECTRICITY.getNamespace(), ImmutableMap.of(), SHORT_TIMEOUT);
        } else return null;
    }


    boolean getState(String channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        //In order to optimize the network traffic, we don't call the getstatus() api at every request.
        //On the contrary, we only call it the first time. Then, the rest of the API will silently listen
        //for state changes and will automatically update the this.state structure listening for
        //messages of the device.
        //Such approach, however, has a side effect. If we call TOGGLE/TOGGLEX and immediately after we call
        //getstatus(), the reported status will be still the old one. This is a race condition because the
        //"status" RESPONSE will be delivered some time after the TOGGLE REQUEST. It's not a big issue for now,
        //and synchronizing the two things would be inefficient and probably not very useful.
        //Just remember to wait some time before testing the status of the item after a toggle.
        this.statuslock.lock();
        int c = this.getChannelId(channel);

        if (this.state == null){
            initializeStatus();
        }
        this.statuslock.unlock();
        return this.state[c];
    }


    @Override
    protected void handleNamespacePayload(String namespace, Map payload) {
        this.statuslock.lock();
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
        this.statuslock.unlock();
    }
}