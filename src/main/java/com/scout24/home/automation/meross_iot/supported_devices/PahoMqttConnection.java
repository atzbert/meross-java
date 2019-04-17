package com.scout24.home.automation.meross_iot.supported_devices;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import lombok.Getter;
import lombok.Setter;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.TOGGLE;
import static com.scout24.home.automation.meross_iot.supported_devices.Abilities.TOGGLEX;
import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1_1;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * Initially created by Tino on 16.04.19.
 */
public abstract class PahoMqttConnection extends MqttConnection implements MqttCallback {

    protected Logger log = LoggerFactory.getLogger(getClass().getName());
    @Getter
    @Setter
    protected ClientStatus status;
    ClientStatus clientstatus = null;
    //Paho mqtt client object
    private MqttClient mqttclient = null;

    IMqttDeliveryToken ackresponse = null;

    public PahoMqttConnection(String key, Long userid) {
        super(key, userid, domain, port);
        this.domain = "eu-iot.meross.com";
    }

    @Override
    @SneakyThrows
    protected void subscribeToTopics() throws MqttException {

        MqttConnection.WAITING_SUBSCRIBER_LOCK.lock();
        //Subscribe to the relevant topics
        log.debug("Subscribing to topics...");
        mqttclient.subscribe(this.usertopic);
        if (!this.waitingsubscribersqueue.await(5, TimeUnit.SECONDS)) {
            //Timeout expired.
            MqttConnection.WAITING_MESSAGE_ACL_LOCK.unlock();
            throw new CommandTimeoutException();
        }

        mqttclient.subscribe(this.clientresponsetopic);
        if (!this.waitingsubscribersqueue.await(5, TimeUnit.SECONDS)) {
            //Timeout expired.
            MqttConnection.WAITING_MESSAGE_ACL_LOCK.unlock();
            throw new CommandTimeoutException();
        }

        this.setStatus(ClientStatus.SUBSCRIBED);

        //Starts a new thread that handles mqtt protocol and calls us back via callbacks
//
//        try {
//            waitingsubscribersqueue.await();
//            if (!getStatus().equals(ClientStatus.SUBSCRIBED)) {
//                throw new MqttException(MqttException.REASON_CODE_CLIENT_NOT_CONNECTED);
//            }
//        } catch (InterruptedException e) {
//            throw new MqttException(e);
//        }
//        WAITING_SUBSCRIBER_LOCK.unlock();

    }

    @Override
    protected void connectToPahoBroker() throws MqttException {
        //Password is calculated as the MD5 of USERID concatenated with KEY
        String clearpwd = this.userid + this.key;
        String hashedpassword = md5DigestAsHex(clearpwd.getBytes());
        mqttclient = new MqttClient("ssl://" + this.domain + ":" + this.port, clientid);
        mqttclient.setCallback(this);
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MQTT_VERSION_3_1_1);
        //Avoid login if userid is None
        if (this.userid != null) {
            options.setUserName(this.userid);
            options.setPassword(hashedpassword.toCharArray());
        }
        this.setStatus(ClientStatus.CONNECTING);
        this.mqttclient.connect(options);
        log.debug("Connected");
        this.setStatus(ClientStatus.CONNECTED);
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        this.ackresponse = iMqttDeliveryToken;
        log.debug("Received ACK...");
        System.out.println("Received ACK...");
        if(!status.equals(ClientStatus.SUBSCRIBED)){
            this.waitingsubscribersqueue.signal();
        } else {
            this.waitingmessageackqueue.signal();
        }
    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        Map message = mapper.readValue(mqttMessage.getPayload(), Map.class);
        Map<String, String> header = (Map) message.get("header");

        String stringToHash = String.format("%s%s%s", header.get("messageId"), this.key, header.get("timestamp"));
        String expected_signature = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8)).toLowerCase();

        if (!header.get("sign").equals(expected_signature)) {
            throw new MQTTException("The signature did not match!");
        }

        //If the message is the RESP for some previous action, process return the control to the "stopped" method.
        if (this.messageFromSelf(message)) {
            if (header.get("method").equals("PUSH") && header.containsKey("namespace")) {
                this.handleNamespacePayload(header.get("namespace"), (Map) message.get("payload"));
            } else {
                log.warn("The following message was unhandled: " + message);
            }
        }

    }

    String mqttMessage(String method, String namespace, Map payload) throws MQTTException {

        //Generate a random 16 byte string
        String randomString = UUID.randomUUID().toString();
        String messageId = md5DigestAsHex(randomString.getBytes(StandardCharsets.UTF_8)).toLowerCase();
        long timestamp = System.currentTimeMillis();
        String stringToHash = String.format("%s%s%s", messageId, this.key, timestamp);
        String signature = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8)).toLowerCase();
        Map<String, Object> data = new ImmutableMap.Builder<String, Object>()
                .put("header", new ImmutableMap.Builder<String, Object>()
                        .put("from", this.clientresponsetopic)
                        .put("messageId", messageId)  //Example: "122e3e47835fefcd8aaf22d13ce21859"
                        .put("method", method)  //Example, "GET",
                        .put("namespace", namespace)  //Example, "Appliance.System.All",
                        .put("payloadVersion", 1)
                        .put("sign", signature)  //Example, "b4236ac6fb399e70c3d61e98fcb68b74",
                        .put("timestamp", timestamp).build())
                .put("payload", payload).build();

        try {
            String strdata = mapper.writeValueAsString(data);
            log.debug("--> " + strdata);
            final MqttMessage message = new MqttMessage(strdata.getBytes(Charset.forName("UTF-8")));
            this.mqttclient.publish(this.clientrequesttopic, message);
            return messageId;
        } catch (JsonProcessingException | MqttException e) {
            throw new MQTTException("Error sending message: " + messageId);
        }
    }

    protected void setStatus(ClientStatus status) {
        this.clientstatus = status;
    }

    protected abstract void handleNamespacePayload(String namespace, Map payload);

    protected boolean messageFromSelf(Map message) {
        try {
            if (message.containsKey("header")) {
                final String header = (String) message.get("header");
                return header.split("/")[2].equals(this.uuid);
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return false;
    }

    public ClientStatus getStatus() {
        return this.status;
    }

    public enum ClientStatus {
        INITIALIZED(1),
        CONNECTING(2),
        CONNECTED(3),
        SUBSCRIBED(4),
        CONNECTIONDROPPED(5);
        private int status;

        ClientStatus(int status) {

            this.status = status;
        }
    }
}
