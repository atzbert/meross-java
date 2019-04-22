
# Meross IoT library
This is a Java based library providing an API for controlling Meross IoT devices over the internet. It is based on the work of albertogeniola who managed to reverse engineer the Meross API and provides the Python based library https://github.com/albertogeniola/MerossIot.

Basically all devices are currently supported, but only a few are actually tested, please refer to the *Currently supported devices* section. Feel free to comment and/or contribute to this repository.

This library is still work in progress, therefore use it with caution and report bugs in the issues section.

## Installation
Right now you will have to clone the repo locally in order to build the jar and use it in your project.

```
mvn clean install
```

## Usage
The library should be designed in a selfexplanatory way. But here an exctract from the main test class that I use which is also contained in this repo. 

```java
//todo

```

## Currently supported devices
The list of tested devices is the following:
- MSS110
- MSS210
- MSS310
- MSS310h
- MSS425e
- MSS425F


## Protocol details
This library was implemented by reverse-engineering the network communications between the plug and the meross network.
Anyone can do the same by simply installing a Man-In-The-Middle proxy and routing the ssl traffic of an Android emulator through the sniffer.

If you want to understand how the Meross protocol works, [have a look at the Wiki](https://github.com/albertogeniola/MerossIot/wiki). Be aware: this is still work in progress, so some pages of the wiki might still be blank/under construction.

So far, I've bought the following devices:
- MSS425E
- MSS425F
- MSS710
- MSS5X0
