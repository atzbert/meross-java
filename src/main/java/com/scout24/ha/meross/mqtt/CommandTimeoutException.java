package com.scout24.ha.meross.mqtt;

/**
 * Initially created by Tino on 15.04.19.
 */
public class CommandTimeoutException extends Throwable {
    public CommandTimeoutException(Throwable throwable) {
        super(throwable);
    }

    public CommandTimeoutException() {

    }
}
