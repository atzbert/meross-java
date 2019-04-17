package com.scout24.home.automation.meross_iot.supported_devices;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.SneakyThrows;
import org.eclipse.paho.client.mqttv3.MqttException;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Initially created by Tino on 16.04.19.
 */
public abstract class MqttConnection {
    static final long SHORT_TIMEOUT = 5;
    static final long LONG_TIMEOUT = 30;
    static final ReentrantLock WAITING_MESSAGE_ACL_LOCK = new ReentrantLock();
    static final ReentrantLock WAITING_SUBSCRIBER_LOCK = new ReentrantLock();
    public static ObjectMapper mapper = new ObjectMapper();
    protected String key = null;
    protected String userid = null;
    protected String domain = null;
    protected int port = 2001;
    protected String clientid = null;
    //Topic name where the client should publish to its commands. Every client should have a dedicated one.
    protected String clientrequesttopic = null;
    //Topic name in which the client retrieves its own responses from server.
    protected String clientresponsetopic = null;
    //Topic where important notifications are pushed (needed if any other client is dealing with the same device)
    protected String usertopic = null;
    //Waiting condition used to wait for command ACKs
    protected Condition waitingmessageackqueue = WAITING_MESSAGE_ACL_LOCK.newCondition();
    protected Condition waitingsubscribersqueue = WAITING_SUBSCRIBER_LOCK.newCondition();
    String waitingmessageid = null;

    public MqttConnection(String key, Long userid) {
        this.key = key;
        this.userid = userid + "";
        this.usertopic = "/app/" + this.userid + "/subscribe";
    }

    @SneakyThrows
    protected abstract void subscribeToTopics() throws MqttException;

    protected abstract void connectToPahoBroker() throws MqttException;

}
