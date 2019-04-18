package com.scout24.home.automation.meross.mqtt;

public enum  Abilities {
    TOGGLE("Appliance.Control.Toggle"),
    TOGGLEX("Appliance.Control.ToggleX"),
    TRIGGER("Appliance.Control.Trigger"),
    TRIGGERX("Appliance.Control.TriggerX"),
    ELECTRICITY("Appliance.Control.Electricity"),
    CONSUMPTIONX("Appliance.Control.ConsumptionX"),
    ONLINE("Appliance.System.Online");

    private String namespace;

    Abilities(String name) {
        this.namespace = name;
    }

    public String getNamespace() {
        return namespace;
    }
}