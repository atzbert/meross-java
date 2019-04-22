package com.scout24.ha.meross.mqtt;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum  Abilities {
    TOGGLE("Appliance.Control.Toggle"),
    TOGGLEX("Appliance.Control.ToggleX"),
    TRIGGER("Appliance.Control.Trigger"),
    TRIGGERX("Appliance.Control.TriggerX"),
    ELECTRICITY("Appliance.Control.Electricity"),
    CONSUMPTIONX("Appliance.Control.ConsumptionX"),
    ONLINE("Appliance.System.Online");

    private String namespace;

    public Abilities forLowercaseName(String name) {
        return Abilities.valueOf(name.toLowerCase());
    }

    public Abilities forNamespace(String name) {
        for (Abilities value : Abilities.values()) {
            if(value.namespace.equalsIgnoreCase(name)){
                return value;
            }
        }
        return null;
    }
}