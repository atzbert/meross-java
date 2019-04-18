package com.scout24.home.automation.meross.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.hivemq.client.mqtt.MqttGlobalPublishFilter;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3PublishBuilder;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.MqttException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * Initially created by Tino on 16.04.19.
 */
public abstract class HiveMqttConnection extends MqttConnection  {

    Logger log = LoggerFactory.getLogger(getClass().getName());


    private Mqtt3Client mqtt3Client;
    private Mqtt3BlockingClient mqtt3BlockingClient;

    public HiveMqttConnection(String key, Long userid, String token, String uuid, String domain) {
        super(key, userid, token, uuid, domain);
    }

    protected HiveMqttConnection() {
    }

    public void connectToBroker() throws MqttException {
        //Password is calculated as the MD5 of USERID concatenated with KEY
        String clearpwd = this.userId + this.key;
        String hashedpassword = md5DigestAsHex(clearpwd.getBytes());

        mqtt3Client = Mqtt3Client.builder()
                .identifier(clientid)
                .serverHost(this.domain)
                .serverPort(this.port)
                .useSslWithDefaultConfig()
                .build();
        final Mqtt3ConnectBuilder.Send<Mqtt3ConnAck> send;

        if (this.userId != null) {
            send = mqtt3Client.toBlocking().connectWith()
                    .simpleAuth()
                    .username(this.userId)
                    .password(hashedpassword.getBytes())
                    .applySimpleAuth();
        } else {
            send = mqtt3Client.toBlocking().connectWith();
        }
        final Mqtt3ConnAck ack = send.send();
        if (ack.isSessionPresent()) {
            this.setStatus(ClientStatus.CONNECTED);
            log.debug("Connected");
        } else {
            this.setStatus(ClientStatus.CONNECTIONDROPPED);
        }
    }

    @Override
    protected synchronized Map executecmd(String method, String namespace, Map payload, long timeout) {
        try {
            mqttMessage(method, namespace, payload);
            final Mqtt3BlockingClient.Mqtt3Publishes publishes = mqtt3Client.toBlocking().publishes(MqttGlobalPublishFilter.ALL);

            final Mqtt3Publish receive = publishes.receive(timeout, TimeUnit.SECONDS)
                    .orElseThrow(() -> new MQTTException("Did not receive response on time"));
            Message message = mapper.readValue(receive.getPayloadAsBytes(), Message.class);
            handleMessagePayload(message);
            return message.getPayload();
        } catch (MQTTException | IOException | InterruptedException e) {
            log.error("Error executing command for " + method, e);
        }
        return null;
    }

    @Override
    @SneakyThrows
    protected void subscribeToTopics() {
        MqttConnection.WAITING_SUBSCRIBER_LOCK.lock();
        Mqtt3BlockingClient.Mqtt3Publishes publishes = mqtt3Client.toBlocking().publishes(MqttGlobalPublishFilter.ALL);
        mqtt3Client.toBlocking()
                .subscribeWith()
                .topicFilter(this.usertopic)
                .send();
        mqtt3Client.toBlocking()
                .subscribeWith()
                .topicFilter(this.clientresponsetopic)
                .send();
        this.setStatus(ClientStatus.SUBSCRIBED);
    }


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





    public void messageArrived(String topic, byte[] mqttMessage) throws Exception {
        Map message = mapper.readValue(mqttMessage, Map.class);
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

        String randomString = UUID.randomUUID().toString();
        String messageId = md5DigestAsHex(randomString.getBytes(StandardCharsets.UTF_8)).toLowerCase();
        long timestamp = System.currentTimeMillis();
        String stringToHash = String.format("%s%s%s", messageId, this.key, timestamp);
        String signature = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8)).toLowerCase();
        Message.Header header = Message.Header.builder()
                .from(clientresponsetopic)
                .messageId(messageId)
                .method(method)
                .namespace(namespace)
                .payloadVersion(1)
                .sign(signature)
                .timestamp(timestamp).build();
        Message message = Message.builder().header(header).payload(payload).build();
        //Generate a random 16 byte string
//        Map<String, Object> data = new ImmutableMap.Builder<String, Object>()
//                .put("header", new ImmutableMap.Builder<String, Object>()
//                        .put("from", this.clientresponsetopic)
//                        .put("messageId", messageId)  //Example: "122e3e47835fefcd8aaf22d13ce21859"
//                        .put("method", method)  //Example, "GET",
//                        .put("namespace", namespace)  //Example, "Appliance.System.All",
//                        .put("payloadVersion", 1)
//                        .put("sign", signature)  //Example, "b4236ac6fb399e70c3d61e98fcb68b74",
//                        .put("timestamp", timestamp).build())
//                .put("payload", payload).build();

        try {
            final Mqtt3PublishBuilder.SendVoid.Complete sendVoid = mqtt3Client.toBlocking().publishWith()
                    .topic(this.clientrequesttopic)
                    .payload(mapper.writeValueAsBytes(message.getPayload()));
            sendVoid.send();

            return "";
        } catch (JsonProcessingException e) {
            throw new MQTTException("Error sending message: " + e.getMessage());
        }
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
        return this.clientStatus;
    }

}
