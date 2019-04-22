package com.scout24.ha.meross.mqtt;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.hivemq.client.mqtt.datatypes.MqttQos;
import com.hivemq.client.mqtt.mqtt3.Mqtt3AsyncClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3BlockingClient;
import com.hivemq.client.mqtt.mqtt3.Mqtt3Client;
import com.hivemq.client.mqtt.mqtt3.message.connect.Mqtt3ConnectBuilder;
import com.hivemq.client.mqtt.mqtt3.message.connect.connack.Mqtt3ConnAck;
import com.hivemq.client.mqtt.mqtt3.message.publish.Mqtt3Publish;
import com.hivemq.client.mqtt.mqtt3.message.subscribe.Mqtt3Subscribe;
import lombok.Getter;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.Charset;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.ALL;
import static com.hivemq.client.mqtt.MqttGlobalPublishFilter.SUBSCRIBED;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * Initially created by Tino on 16.04.19.
 */
@Slf4j
public class MqttConnection {

    static final long SHORT_TIMEOUT = 5;
    static final long LONG_TIMEOUT = 30;
    static ObjectMapper mapper = new ObjectMapper();
    protected Lock statuslock = new ReentrantLock();
    protected byte[] ackresponse = null;
    //Topic where important notifications are pushed (needed if any other client is dealing with the same device)
    String usertopic = null;


//    private Mqtt3Client mqtt3Client;
    private Mqtt3BlockingClient blockingClient;
    private Mqtt3AsyncClient asyncClient;
    private String key = null;
    private String userId = null;
    private String token;
    private String uuid = null;
    @Getter
    private String appId = null;
    private String domain = null;
    private int port = 2001;
    private String clientid = null;
    private String clientResponseTopic;
    private Consumer<Map> globalMessageConsumer;

    public MqttConnection(String key, Long userid, String token, String domain) throws MQTTException {
        this.key = key;
        this.userId = userid + "";
        this.token = token;
        this.usertopic = "/app/" + this.userId + "/subscribe";
        this.domain = domain;
        this.generateFClientandAppId();
        this.clientResponseTopic = "/app/" + userId+ "-" + appId + "/subscribe";
        this.connectToBroker();
        this.subscribeToTopic(this.usertopic);
        this.subscribeToTopic(this.clientResponseTopic);
    }

    protected MqttConnection() {

    }

    public void connectToBroker() throws MQTTException {
        //Password is calculated as the MD5 of USERID concatenated with KEY
        String clearpwd = this.userId + this.key;
        String hashedpassword = md5DigestAsHex(clearpwd.getBytes());

        Mqtt3Client mqtt3Client = Mqtt3Client.builder()
                .identifier(clientid)
                .serverHost(this.domain)
                .serverPort(this.port)
                .useSslWithDefaultConfig()
                .build();
        final Mqtt3ConnectBuilder.Send<Mqtt3ConnAck> send;

        if (this.userId != null) {
            blockingClient = mqtt3Client.toBlocking();
            asyncClient = mqtt3Client.toAsync();
            send = blockingClient.connectWith()
                    .simpleAuth()
                    .username(this.userId)
                    .password(hashedpassword.getBytes())
                    .applySimpleAuth();
        } else {
            send = mqtt3Client.toBlocking().connectWith();
        }
        final Mqtt3ConnAck ack = send.send();
    }

    protected synchronized Map executecmd(String method, String namespace, Map payload,
                                          String clientRequestTopic) {
        try {
            mqttMessage(method, namespace, clientRequestTopic, payload);
        } catch (Exception e) {
            log.error("Error executing command for " + method, e);
        }
        return null;
    }


    public void subscribeToDeviceTopic(MerossDevice merossDevice) throws MQTTException {
        try {
            final String topic = merossDevice.clientRequestTopic;
            Mqtt3Subscribe subscribeMessage = Mqtt3Subscribe.builder()
                    .topicFilter(topic)
                    .qos(MqttQos.AT_LEAST_ONCE)
                    .build();

            asyncClient.subscribe(subscribeMessage, publishMessage ->
            {
                try {
                    merossDevice.consumeMessage(this.messageArrived(topic, publishMessage.getPayloadAsBytes()));
                } catch (MQTTException e) {
                    log.error("Error receiving update for " + topic);
                }
            });
        } catch (Exception e) {
            throw new MQTTException(e.getMessage());
        }
    }
    public void subscribeToTopic(String topicFilter) throws MQTTException {
        try {
            blockingClient
                        .subscribeWith()
                        .topicFilter(topicFilter)
                        .send();
                receiveMessageAsync(payload -> {
                    log.info(String.format("Global message for %s arrived: %s", topicFilter, payload));
                    if (globalMessageConsumer != null) {
                        globalMessageConsumer.accept(payload);
                    }
                }, topicFilter);
        } catch (Exception e) {
            throw new MQTTException(e.getMessage());
        }
    }



    public Map messageArrived(String topic, byte[] mqttMessage)  {
        try {
            Message message = mapper.readValue(mqttMessage, Message.class);
            Message.Header header = message.getHeader();

            String stringToHash = String.format("%s%s%s", header.getMessageId(), this.key, header.getTimestamp());
            String expected_signature = md5DigestAsHex(stringToHash.getBytes(UTF_8)).toLowerCase();

            if (!header.getSign().equals(expected_signature)) {
                throw new MQTTException("The signature did not match!");
            }

            //If the message is the RESP for some previous action, process return the control to the "stopped" method.
            if (this.messageFromSelf(message)) {
                if (header.getMethod().equals("PUSH") && header.getNamespace() != null) {
    //                this.handleNamespacePayload(header.getNamespace(), message.getPayload());
                } else {
                    log.warn("The following message was unhandled: " + message);
                }
            } else {
                return message.getPayload();
            }
        } catch (Exception e) {
            log.error(e.getMessage(), e);
        }

        return null;
    }

    @SneakyThrows
    public String mqttMessage(String method, String namespace, String clientRequestTopic, Map payload) throws MQTTException {

//        blockingClient.connect();
        byte[] array = new byte[16]; // length is bounded by 16
        new Random().nextBytes(array);
        String randomString = new String(array, Charset.forName("UTF-8"));
        String messageId = md5DigestAsHex(randomString.getBytes(UTF_8)).toLowerCase();
        long timestamp = System.currentTimeMillis() / 1000;
        String stringToHash = String.format("%s%s%s", messageId, this.key, timestamp);
        String signature = md5DigestAsHex(stringToHash.getBytes(UTF_8)).toLowerCase();
        Message.Header header = new Message.Header()
                .withFrom(this.clientResponseTopic)
                .withMessageId(messageId)
                .withMethod(method)
                .withNamespace(namespace)
                .withPayloadVersion(1)
                .withSign(signature)
                .withTimestamp(timestamp)
                .withTimestampMs(123);
        Message message = new Message().withHeader(header).withPayload(payload);
        try {
            final String valueAsString = mapper.writeValueAsString(message);
            log.debug("Sending message: " + valueAsString);
            blockingClient.publishWith()
                    .topic(clientRequestTopic)
                    .payload(valueAsString.getBytes(UTF_8))
                    .send();

            return "";
        } catch (JsonProcessingException e) {
            throw new MQTTException("Error sending message: " + e.getMessage());
        }
    }


    protected void generateFClientandAppId() {
        String stringToHash = String.format("%s%s", "API", this.uuid);
        this.appId = md5DigestAsHex(stringToHash.getBytes(UTF_8));
        this.clientid = "app:" + appId;
    }

    void handleMessagePayload(Message message) throws MQTTException {
        Message.Header header = message.getHeader();

        String stringToHash = String.format("%s%s%s", header.getMessageId(), this.key, header.getTimestamp());
        String expected_signature = md5DigestAsHex(stringToHash.getBytes(UTF_8)).toLowerCase();

        if (!header.getSign().equals(expected_signature)) {
            throw new MQTTException("The signature did not match!");
        }

        //If the message is the RESP for some previous action, process return the control to the "stopped" method.
        if (this.messageFromSelf(message)) {
            if (header.getMethod().equals("PUSH") && header.getNamespace() != null) {
                //todo
//                this.handleNamespacePayload(header.getNamespace(), (Map) message.getPayload());
            } else {
                log.warn("The following message was unhandled: " + message);
            }
        }
    }

    protected boolean messageFromSelf(Message message) {
        try {
            final String from = message.getHeader().getFrom();
            if (from != null) {
                return from.split("/")[2].equals(this.uuid);
            }
        } catch (Exception e) {
            log.warn(e.getMessage());
        }
        return false;
    }

    public Map receiveMessage() throws Exception {
        try (Mqtt3BlockingClient.Mqtt3Publishes publishes = blockingClient.publishes(SUBSCRIBED)) {
            Mqtt3Publish publishMessage = publishes.receive();
            System.out.println("receivedMessage = " + publishMessage);
            return this.messageArrived(publishMessage.getTopic().toString(), publishMessage.getPayloadAsBytes());
        } catch (InterruptedException e) {
            log.error(e.getMessage(),e);
        }
        return null;
    }
    public void onGlobalMessage(Consumer<Map> consumer) throws Exception {
        globalMessageConsumer = consumer;
    }
    public void receiveMessageAsync(Consumer<Map> consumer, String consumedTopic) throws Exception {
        asyncClient.publishes((consumedTopic == null) ? ALL : SUBSCRIBED, publishMessage -> {
            try {
                final String topic = publishMessage.getTopic().toString();
                if (consumedTopic == null || topic.equalsIgnoreCase(consumedTopic)) {
                    consumer.accept(this.messageArrived(topic, publishMessage.getPayloadAsBytes()));
                }
            } catch (Exception e) {
                log.error(e.getMessage(),e);
            }
        } );
    }
}
