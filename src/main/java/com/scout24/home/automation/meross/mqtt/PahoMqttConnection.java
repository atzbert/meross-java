package com.scout24.home.automation.meross.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.google.common.collect.ImmutableMap;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.IMqttDeliveryToken;
import org.eclipse.paho.client.mqttv3.MqttCallback;
import org.eclipse.paho.client.mqttv3.MqttClient;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.eclipse.paho.client.mqttv3.MqttMessage;

import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.eclipse.paho.client.mqttv3.MqttConnectOptions.MQTT_VERSION_3_1_1;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * Initially created by Tino on 16.04.19.
 */
@Slf4j
public abstract class PahoMqttConnection extends MqttConnection implements MqttCallback {

    //Paho mqtt client object
    private MqttClient mqttclient = null;

    public PahoMqttConnection(String key, Long userid, String token, String uuid, String domain) {
        super(key, userid, token, uuid, domain);
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
    protected void connectToBroker() throws MqttException {
        //Password is calculated as the MD5 of USERID concatenated with KEY
        String clearpwd = this.userId + this.key;
        String hashedpassword = md5DigestAsHex(clearpwd.getBytes());
        mqttclient = new MqttClient("ssl://" + this.domain + ":" + this.port, clientid);
        mqttclient.setCallback(this);
        final MqttConnectOptions options = new MqttConnectOptions();
        options.setMqttVersion(MQTT_VERSION_3_1_1);
        //Avoid login if userId is None
        if (this.userId != null) {
            options.setUserName(this.userId);
            options.setPassword(hashedpassword.toCharArray());
        }
        this.setStatus(ClientStatus.CONNECTING);
        this.mqttclient.connect(options);
        log.debug("Connected");
        this.setStatus(ClientStatus.CONNECTED);
    }

    @Override
    protected Map executecmd(String method, String namespace, Map payload, long timeout) {
        return null;
    }

    @Override
    public void connectionLost(Throwable throwable) {

    }

    @Override
    public void deliveryComplete(IMqttDeliveryToken iMqttDeliveryToken) {
        try {
            log.debug("Received ACK...");
            System.out.println("Received ACK...");
            this.ackresponse = iMqttDeliveryToken.getResponse().getPayload();
            if(!clientStatus.equals(ClientStatus.SUBSCRIBED)){
                this.waitingsubscribersqueue.signal();
            } else {
                this.waitingmessageackqueue.signal();
            }
        } catch (MqttException e) {
            e.printStackTrace();
        }

    }

    @Override
    public void messageArrived(String topic, MqttMessage mqttMessage) throws Exception {
        Message message = mapper.readValue(mqttMessage.getPayload(), Message.class);
        handleMessagePayload(message);

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

    public ClientStatus getStatus() {
        return this.clientStatus;
    }

}
