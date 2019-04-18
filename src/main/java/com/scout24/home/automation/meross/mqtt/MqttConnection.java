package com.scout24.home.automation.meross.mqtt;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.scout24.home.automation.meross.api.AttachedDevice;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static org.springframework.util.DigestUtils.md5DigestAsHex;

/**
 * Initially created by Tino on 16.04.19.
 */
@Slf4j
public abstract class MqttConnection {
    static final long SHORT_TIMEOUT = 5;
    static final long LONG_TIMEOUT = 30;
    static final ReentrantLock WAITING_MESSAGE_ACL_LOCK = new ReentrantLock();
    static final ReentrantLock WAITING_SUBSCRIBER_LOCK = new ReentrantLock();
    static ObjectMapper mapper = new ObjectMapper();
    protected Lock statuslock = new ReentrantLock();
    protected AttachedDevice device;
    protected byte[] ackresponse = null;

    String key = null;
    String userId = null;
    String token;
    String uuid = null;
    String appid = null;
    String domain = null;
    int port = 2001;
    String clientid = null;

    //Topic name where the client should publish to its commands. Every client should have a dedicated one.
    String clientrequesttopic = null;
    //Topic name in which the client retrieves its own responses from server.
    String clientresponsetopic = null;
    //Topic where important notifications are pushed (needed if any other client is dealing with the same device)
    String usertopic = null;
    //Waiting condition used to wait for command ACKs
    Condition waitingmessageackqueue = WAITING_MESSAGE_ACL_LOCK.newCondition();
    Condition waitingsubscribersqueue = WAITING_SUBSCRIBER_LOCK.newCondition();
    String waitingmessageid = null;
    ClientStatus clientStatus = null;
    private volatile AtomicInteger subscriptioncount = new AtomicInteger(0);

    public MqttConnection(String key, Long userId, String token, String uuid, String domain) {
        this.key = key;
        this.userId = userId + "";
        this.token = token;
        this.uuid = uuid;
        this.usertopic = "/app/" + this.userId + "/subscribe";
        this.domain = domain!=null? domain : "eu-iot.meross.com";
    }

    protected abstract void subscribeToTopics() throws MqttException;

    protected abstract void connectToBroker() throws MqttException;

    protected void generateFClientandAppId() {
        String stringToHash = String.format("%s%s", "API", this.uuid);
        this.appid = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8));
        this.clientid = "app:" + appid;
    }

    void waitForStatus(ClientStatus status) throws MQTTException {
        boolean ok = false;
        while (!ok) {
            try {
                if (!this.statuslock.tryLock(SHORT_TIMEOUT, TimeUnit.SECONDS)) {
                    throw new MQTTException("TimeoutError");
                }
                ok = (status == this.clientStatus);
                this.statuslock.unlock();
            } catch (InterruptedException e) {
                throw new MQTTException(e);
            }
        }
    }

    protected void setStatus(ClientStatus status) {
        this.clientStatus = status;
    }

    protected void handleMessagePayload(Message message) throws MQTTException {
        Message.Header header = message.getHeader();

        String stringToHash = String.format("%s%s%s", header.getMessageId(), this.key, header.getTimestamp());
        String expected_signature = md5DigestAsHex(stringToHash.getBytes(StandardCharsets.UTF_8)).toLowerCase();

        if (!header.getSign().equals(expected_signature)) {
            throw new MQTTException("The signature did not match!");
        }

        //If the message is the RESP for some previous action, process return the control to the "stopped" method.
        if (this.messageFromSelf(message)) {
            if (header.getMethod().equals("PUSH") && header.getNamespace() != null) {
                this.handleNamespacePayload(header.getNamespace(), (Map) message.getPayload());
            } else {
                log.warn("The following message was unhandled: " + message);
            }
        }
    }

    protected abstract Map executecmd(String method, String namespace, Map payload, long timeout);

    protected abstract void handleNamespacePayload(String namespace, Map payload);

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
