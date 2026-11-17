# Desktop Runtime Requirements for TrainController

This document describes the minimum requirements for the machine that will actually run the TrainController stack in normal operation. This is separate from the developer workstation where you edit code, build firmware, and commit changes.

## Purpose

The runtime desktop is responsible for:

- running MySQL
- running Mosquitto MQTT broker
- running the Java interceptor service
- exposing the serial/RS485 connection to the Arduino hardware
- receiving turnout intent commands and reconciling them with hardware state

## Recommended Operating System

- Ubuntu 22.04 LTS or Ubuntu 24.04 LTS
- A dedicated desktop or headless Linux box is preferred
- The runtime host should be stable and left running continuously when the layout is active

## Required Software

Install the following packages:

```bash
sudo apt update
sudo apt install -y mysql-server mysql-client mosquitto openjdk-21-jdk maven python3 curl
```

### Minimum runtime components

- MySQL server
- Mosquitto broker
- Java 21 JDK
- Maven
- Python 3
- curl (for quick health checks and basic validation)

## Required Services

Enable and start the services:

```bash
sudo systemctl enable --now mysql
sudo systemctl enable --now mosquitto
```

## Required Database Setup

Create the database and application user:

```bash
sudo mysql -uroot -e "CREATE DATABASE IF NOT EXISTS train_controller;"
sudo mysql -uroot -e "CREATE USER IF NOT EXISTS 'train'@'localhost' IDENTIFIED BY 'train';"
sudo mysql -uroot -e "GRANT ALL PRIVILEGES ON train_controller.* TO 'train'@'localhost';"
sudo mysql -uroot -e "FLUSH PRIVILEGES;"
```

Then apply the schema:

```bash
cd ~/Arduino/TrainController
for f in migrations/V*.sql; do
  mysql -u train -ptrain train_controller < "$f"
done
```

## Required Java Build

Build the interceptor jar before starting it:

```bash
cd ~/Arduino/TrainController/interceptor-java
mvn -q -DskipTests package
```

## Required Hardware

The runtime desktop must have access to the physical train hardware:

- RS485 serial adapter connected to the desktop
- Arduino or controller nodes on the RS485 bus
- stable USB device naming, ideally via udev rules

Typical naming patterns:

- `/dev/ttyRS485`
- `/dev/ttyArduino`
- `/dev/ttyUSB0` or `/dev/ttyACM0`

Create udev rules if needed:

```bash
sudo nano /etc/udev/rules.d/50-traincontroller.rules
```

Example:

```udev
SUBSYSTEMS=="usb", ATTRS{idVendor}=="1a86", ATTRS{idProduct}=="7523", SYMLINK+="ttyRS485"
SUBSYSTEMS=="usb", ATTRS{idVendor}=="2341", ATTRS{idProduct}=="6001", SYMLINK+="ttyArduino"
```

Then reload:

```bash
sudo udevadm control --reload-rules
sudo udevadm trigger
```

## Required Network Access

The runtime host should be able to reach:

- the local MySQL instance on `localhost:3306`
- the local MQTT broker on `localhost:1883`
- the serial device connected to the hardware
- any external control system such as JMRI or other MQTT producers

## Required Startup Flow

Use the native startup script:

```bash
source ~/Arduino/TrainController/ops/start-native.sh
start_traincontroller
```

This handles:

- MySQL readiness
- schema import
- servo-default seeding
- Mosquitto startup
- Java interceptor startup
- health/status output

## Required Operational Assumptions

Before you can trust the desktop to run the layout, you need:

- the hardware connected and discoverable
- a valid serial path configured
- MySQL credentials and database available
- MQTT broker reachable locally
- the JAR built successfully
- the device calibration values loaded for the turnout/servo configuration

## Not Required for the Desktop Runtime

These are not required on the runtime desktop itself:

- Docker is optional for the native setup
- a full IDE is optional for runtime operation
- a Git client is not required to run the stack, though it is useful for development

## Dev Machine vs Runtime Desktop

This is a clean split:

- Dev machine: Git, IDE, compiler, firmware tooling, code changes, tests
- Runtime desktop: MySQL, Mosquitto, Java runtime, USB hardware access, 24/7 layout operation

The dev machine can be a laptop or workstation; the runtime desktop should be a stable host dedicated to running the system.

## Quick Checklist

Before you can run the system on the runtime desktop, confirm all of the following:

- [ ] Ubuntu installed
- [ ] MySQL installed and running
- [ ] Mosquitto installed and running
- [ ] Java 21 installed
- [ ] Maven installed
- [ ] Python 3 installed
- [ ] MySQL database created
- [ ] schema applied
- [ ] JAR built successfully
- [ ] hardware serial device present
- [ ] udev rules applied if needed
- [ ] MQTT broker reachable at localhost:1883
- [ ] service startup script runs without error

If any one of these is missing, the system is not ready to run on that desktop.
