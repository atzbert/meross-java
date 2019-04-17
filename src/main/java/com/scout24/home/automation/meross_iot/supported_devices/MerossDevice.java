package com.scout24.home.automation.meross_iot.supported_devices;

import com.google.common.collect.ImmutableMap;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.scout24.home.automation.meross_iot.model.AttachedDevice;
import com.scout24.home.automation.meross_iot.model.Device;
import com.scout24.home.automation.meross_iot.model.NetworkDevice;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.io.Serializable;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.CONSUMPTIONX;
import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.ELECTRICITY;
import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.TOGGLE;
import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.TOGGLEX;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

public class MerossDevice extends PahoMqttConnection {
    private static final int CHANNEL_0 = 0;
    protected Lock statuslock = new ReentrantLock();

    private volatile AtomicInteger subscriptioncount = new AtomicInteger(0);

    private String token;
    private List<Map> channels;

    private String uuid = null;
    private String appid = null;

    private Device device;

    //hive mqtt client
    private Mqtt3Client hiveClient;


    //Cached list of abilities
    List<?> abilities = new ArrayList<>();

    //Dictionary {channel->status}
    int[] state;

    public MerossDevice(String token, String key, Long userid, Device device) throws MqttException {
        super(key, userid);
        this.token = token;
        this.device = device;
        this.uuid = device.getUuid();
        this.channels = device.getChannels();
        this.state = new int[channels.size()];

        this.generateFClientandAppId();
        this.connectToPahoBroker();

        this.clientrequesttopic = "/appliance/" + this.uuid + "/subscribe";
        this.clientresponsetopic = "/app/" + this.uuid + "-" + this.appid + "/subscribe";
        subscribeToTopics();

    }

    public MerossDevice(AttachedDevice device) throws MqttException {
        this(device.getToken(), device.getKey(), device.getUser_id(), device.getDevice());
    }


    private void connectToHiveMQBroker() throws MqttException {
        //Password is calculated as the MD5 of USERID concatenated with KEY
        String clearpwd = this.userid + this.key;
        String hashedpassword = md5DigestAsHex(clearpwd.getBytes());

        hiveClient = Mqtt3Client.builder()
                .identifier(clientid)
                .serverHost(this.domain)
                .serverPort(this.port)
                .useSslWithDefaultConfig()
                .build();
        final Mqtt3ConnectBuilder.Send<Mqtt3ConnAck> send;

        if (this.userid != null) {
            send = hiveClient.toBlocking().connectWith()
                    .simpleAuth()
                    .username(this.userid)
                    .password(hashedpassword.getBytes())
                    .applySimpleAuth();
        } else {
            send = hiveClient.toBlocking().connectWith();
        }
        final Mqtt3ConnAck ack = send.send();
        if (ack.isSessionPresent()) {
            this.setStatus(ClientStatus.CONNECTED);
            log.debug("Connected");
        } else {
            this.setStatus(ClientStatus.CONNECTIONDROPPED);
        }
    }



    private void generateFClientandAppId() {
        String stringToHash = String.format("%s%s", "API", this.uuid);
        this.appid = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8));
        this.clientid = "app:" + appid;
    }

    void waitforstatus(ClientStatus status) throws MQTTException {
        boolean ok = false;
        while (!ok) {
            try {
                if (!this.statuslock.tryLock(SHORT_TIMEOUT, TimeUnit.SECONDS)) {
                    throw new MQTTException("TimeoutError");
                }
                ok = (status == this.clientstatus);
                this.statuslock.unlock();
            } catch (InterruptedException e) {
                throw new MQTTException(e);
            }
        }
    }


    private Map executecmd(String method, Abilities namespace, Map payload, long timeout) throws InterruptedException, CommandTimeoutException {
        return this.executecmd(method, namespace.getNamespace(), payload, timeout);
    }

    private Map executecmd(String method, String namespace, Map payload, long timeout) throws InterruptedException, CommandTimeoutException {
        WAITING_SUBSCRIBER_LOCK.lock();
        //Before executing any command, we need to be subscribed to the MQTT topics where to listen for ACKS.
        while (this.clientstatus != ClientStatus.SUBSCRIBED) {
            this.waitingsubscribersqueue.await();
            //Execute the command and retrieve the message-id
        }
        try {
            this.waitingmessageid = this.mqttMessage(method, namespace, payload);
            //Wait synchronously until we get the ACK.
            WAITING_MESSAGE_ACL_LOCK.lock();
            System.out.println("Waiting for ACK...");
            if (!this.waitingmessageackqueue.await(timeout, TimeUnit.SECONDS)) {
                //Timeout expired.
                WAITING_MESSAGE_ACL_LOCK.unlock();
                throw new CommandTimeoutException();
            }
            Thread.sleep(3000);
            WAITING_MESSAGE_ACL_LOCK.unlock();
            WAITING_SUBSCRIBER_LOCK.unlock();
            return mapper.convertValue(this.ackresponse.getMessage().getPayload(), Map.class);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }


    }


    public Map getConsumptionx() throws InterruptedException, CommandTimeoutException, MQTTException {
        return this.executecmd("GET", CONSUMPTIONX, ImmutableMap.of(), SHORT_TIMEOUT);
    }

    public Map getElectricity() throws InterruptedException, CommandTimeoutException, MQTTException {
        return this.executecmd("GET", ELECTRICITY, ImmutableMap.of(), SHORT_TIMEOUT);
    }

    public Map toggle(int status) throws MQTTException, CommandTimeoutException, InterruptedException {
        ImmutableMap<String, Serializable> payload = ImmutableMap.of("channel", 0, "toggle", ImmutableMap.of("onoff", status));
        return this.executecmd("SET", TOGGLE, payload, SHORT_TIMEOUT);
    }

    public Map togglex(int channel, int status) throws MQTTException, CommandTimeoutException, InterruptedException {
        Map<String, Map<String, Integer>> payload = ImmutableMap.of("togglex", ImmutableMap.of("onoff", status, "channel", channel));
        return this.executecmd("SET", TOGGLEX, payload, SHORT_TIMEOUT);
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

    public Map getStatusImpl() throws InterruptedException, CommandTimeoutException, MQTTException {
        Map result = new HashMap();
        NetworkDevice data = this.getSysData();
        final NetworkDevice.Digest digest = data.getAll().getDigest();
        if (digest != null) {
            for (Object c : digest.getTogglex()) {
                result.put(((Map) c).get("channel"), ((Map) c).get("channel"));
            }
        } else {
            final Map control = data.getAll().getControl();
            if (control != null) {
                result.put(0, ((Map) ((Map) control).get("toggle")).get("'onoff'"));
            }
        }
        return result;
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
        return mapper.convertValue(this.executecmd("GET", "Appliance.System.All", ImmutableMap.of(), SHORT_TIMEOUT), NetworkDevice.class);
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

    Map getdebug() throws InterruptedException, CommandTimeoutException, MQTTException {
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

    int getChannelStatus(String channel) throws MQTTException {
        int c = this.getChannelId(channel);
        return this.state[c];
    }

    Map turnonchannel(String channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 1);
    }

    Map turnoffchannel(String channel) throws MQTTException, CommandTimeoutException, InterruptedException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 0);
    }

    Map turnon(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 1);
    }

    Map turnoff(String channel) throws InterruptedException, CommandTimeoutException, MQTTException {
        int c = this.getChannelId(channel);
        return this.channelControlImpl(c, 0);
    }

    boolean supportsconsumptionreading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(CONSUMPTIONX);
    }

    boolean supportselectricityreading() throws InterruptedException, CommandTimeoutException, MQTTException {
        return getAbilities().contains(ELECTRICITY);
    }

    Map getpowerconsumption() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(CONSUMPTIONX)) {
            return this.getConsumptionx();
        } else return null;
    }

    Map getelectricity() throws InterruptedException, CommandTimeoutException, MQTTException {
        if (getAbilities().contains(ELECTRICITY)) {
            return this.getElectricity();
        } else return null;
    }


    int getstatus(String channel) throws MQTTException {
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

        //todo understand
//        if (this.state == null){
//            this.state = this.getStatusImpl().
//
//        }
        this.statuslock.unlock();
        return this.state[c];
    }


    @Override
    protected void handleNamespacePayload(String namespace, Map payload) {
        this.statuslock.lock();
        if (namespace.equals(TOGGLE.getNamespace())) {
            final Map<String, ?> toggle = (Map<String, ?>) payload.get("toggle");
            this.state[MerossDevice.CHANNEL_0] = Integer.parseInt(toggle.get("onoff").toString());
        } else if (namespace.equals(TOGGLEX.getNamespace())) {
            if (payload.get("togglex") instanceof List) {
                final List<Map> togglex = (List) payload.get("togglex");
                for (Map map : togglex) {
                    int channelindex = Integer.parseInt(map.get("channel").toString());
                    this.state[channelindex] = Integer.parseInt(map.get("onoff").toString());
                }
            } else if (payload.get("togglex") instanceof Map) {
                final Map togglex = (Map) payload.get("togglex");
                int channelindex = Integer.parseInt(togglex.get("channel").toString());
                this.state[channelindex] = Integer.parseInt(togglex.get("onoff").toString());
            }
        } else if (namespace.equals(Abilities.ONLINE.getNamespace())) {
            log.info("Online keep alive received: " + payload);
        } else {
            log.error("Unknown/Unsupported namespace/command: " + namespace);
        }
        this.statuslock.unlock();
    }
}