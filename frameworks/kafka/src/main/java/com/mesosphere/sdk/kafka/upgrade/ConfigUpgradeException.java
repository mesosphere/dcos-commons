package com.mesosphere.sdk.kafka.upgrade;


import java.io.IOException;

/**
 * ConfigUpgradeException
 */
public class ConfigUpgradeException extends IOException {
    public ConfigUpgradeException(String message){
        super(message);
    }
}
