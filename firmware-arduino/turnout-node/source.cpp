#include <Arduino.h>
#include <Adafruit_PWMServoDriver.h>
#include <SoftwareSerial.h>
#include <Wire.h>

const uint8_t RS485_DIR_PIN = 5;
const uint8_t RS485_RX_PIN = 6;
const uint8_t RS485_TX_PIN = 7;
const uint8_t PCA9685_ADDR = 0x40;
const uint16_t PCA9685_FREQ_HZ = 50;
const uint16_t SERVO_MIN_PULSE = 120;
const uint16_t SERVO_MAX_PULSE = 520;
const uint16_t TURNOUT_DEFAULT_OPEN_ANGLE = 317;   // THROWN = diverging
const uint16_t TURNOUT_DEFAULT_CLOSED_ANGLE = 238; // CLOSED = straight
const uint8_t TURNOUT_COUNT = 10;
const char NODE_ID[] = "turnout1";

SoftwareSerial rs485(RS485_RX_PIN, RS485_TX_PIN);
Adafruit_PWMServoDriver pwm(PCA9685_ADDR);
bool turnoutIsOpen[TURNOUT_COUNT];
bool pca9685Present = false;

static bool detectPca9685()
{
    Wire.beginTransmission(PCA9685_ADDR);
    return Wire.endTransmission() == 0;
}

static void printI2cScan()
{
    Serial.println("I2C scan start");
    bool foundAny = false;
    for (uint8_t addr = 1; addr < 127; addr++)
    {
        Wire.beginTransmission(addr);
        uint8_t error = Wire.endTransmission();
        if (error == 0)
        {
            foundAny = true;
            Serial.print("I2C device at 0x");
            if (addr < 16)
            {
                Serial.print('0');
            }
            Serial.println(addr, HEX);
        }
    }

    if (!foundAny)
    {
        Serial.println("I2C scan found no devices");
    }
}

static int turnoutIdToIndex(const String &turnoutId)
{
    if (turnoutId == "turnout1" || turnoutId == "001")
        return 0;
    if (turnoutId == "002")
        return 1;
    if (turnoutId == "003")
        return 2;
    if (turnoutId == "004")
        return 3;
    if (turnoutId == "005")
        return 4;
    if (turnoutId == "006")
        return 5;
    if (turnoutId == "007")
        return 6;
    if (turnoutId == "008")
        return 7;
    if (turnoutId == "009")
        return 8;
    if (turnoutId == "010")
        return 9;
    return -1;
}

static uint16_t angleToPulse(uint8_t angle)
{
    return map(angle, 0, 180, SERVO_MIN_PULSE, SERVO_MAX_PULSE);
}

static bool setTurnoutState(const String &turnoutId, bool open, uint16_t openPulse, uint16_t closedPulse)
{
    if (!pca9685Present)
    {
        Serial.println("ERROR: PCA9685 not present");
        return false;
    }

    int turnoutIndex = turnoutIdToIndex(turnoutId);
    if (turnoutIndex < 0)
    {
        Serial.print("ERROR: Invalid turnoutId=");
        Serial.println(turnoutId);
        return false;
    }

    uint16_t pulseValue = open ? openPulse : closedPulse;
    turnoutIsOpen[turnoutIndex] = open;

    Serial.print("PWM: channel=");
    Serial.print(turnoutIndex);
    Serial.print(" state=");
    Serial.print(open ? "OPEN" : "CLOSED");
    Serial.print(" pulse=");
    Serial.println(pulseValue);

    pwm.setPWM(static_cast<uint8_t>(turnoutIndex), 0, pulseValue);
    return true;
}

static void rs485SendLine(const String &line)
{
    Serial.print("RS485 TX: ");
    Serial.println(line);
    // Drive DE high only while transmitting on the RS485 transceiver.
    digitalWrite(RS485_DIR_PIN, HIGH);
    delayMicroseconds(200);
    rs485.println(line);
    // Approximate transmit drain time at 19200 8N1 before returning to RX mode.
    const unsigned long txMs = ((line.length() + 2U) * 10U * 1000UL) / 19200UL + 2UL;
    delay(txMs);
    digitalWrite(RS485_DIR_PIN, LOW);
}

static String framePart(const String &frame, uint8_t index)
{
    int start = 0;
    for (uint8_t i = 0; i < index; i++)
    {
        int sep = frame.indexOf('|', start);
        if (sep < 0)
        {
            return "";
        }
        start = sep + 1;
    }

    int end = frame.indexOf('|', start);
    if (end < 0)
    {
        end = frame.length();
    }
    return frame.substring(start, end);
}

static String buildAckFrame(const String &nodeId, const String &commandId, const String &ackStatus)
{
    String payload = "v1|" + nodeId + "|" + commandId + "|ACK|" + ackStatus;
    uint8_t checksum = 0;
    for (size_t i = 0; i < payload.length(); i++)
    {
        checksum = (checksum + static_cast<uint8_t>(payload[i])) & 0xFF;
    }

    char checksumHex[3];
    snprintf(checksumHex, sizeof(checksumHex), "%02X", checksum);
    return payload + "|" + checksumHex;
}

static void sendReply(const String &line, bool viaUsb)
{
    if (viaUsb)
    {
        Serial.println(line);
        return;
    }

    rs485SendLine(line);
}

static void handleLine(const String &line, bool viaUsb)
{
    if (line.startsWith(String("QSTATE|") + NODE_ID))
    {
        String turnoutId = framePart(line, 2);
        if (!pca9685Present)
        {
            sendReply("STATE|UNKNOWN", viaUsb);
            return;
        }

        if (turnoutId.length() == 0)
        {
            sendReply("STATE|UNKNOWN", viaUsb);
            return;
        }

        int turnoutIndex = turnoutIdToIndex(turnoutId);
        if (turnoutIndex < 0)
        {
            sendReply(String("STATE|") + turnoutId + "|UNKNOWN", viaUsb);
            return;
        }

        sendReply(String("STATE|") + turnoutId + "|" + (turnoutIsOpen[turnoutIndex] ? "OPEN" : "CLOSED"), viaUsb);
    }
    else if (line.startsWith("v1|"))
    {
        String nodeId = framePart(line, 1);
        String commandId = framePart(line, 2);
        String messageType = framePart(line, 3);
        String turnoutId = framePart(line, 4);
        String desiredState = framePart(line, 5);
        String openAngleStr = framePart(line, 6);
        String closedAngleStr = framePart(line, 7);

        uint16_t openPulse = openAngleStr.length() > 0 ? (uint16_t)openAngleStr.toInt() : TURNOUT_DEFAULT_OPEN_ANGLE;
        uint16_t closedPulse = closedAngleStr.length() > 0 ? (uint16_t)closedAngleStr.toInt() : TURNOUT_DEFAULT_CLOSED_ANGLE;

        Serial.print("CMD: node=");
        Serial.print(nodeId);
        Serial.print(" type=");
        Serial.print(messageType);
        Serial.print(" turnout=");
        Serial.print(turnoutId);
        Serial.print(" state=");
        Serial.print(desiredState);
        Serial.print(" open=");
        Serial.print(openPulse);
        Serial.print(" closed=");
        Serial.println(closedPulse);

        if (nodeId != NODE_ID)
        {
            return;
        }

        if (messageType == "TURNOUT" && turnoutIdToIndex(turnoutId) < 0)
        {
            sendReply(buildAckFrame(NODE_ID, commandId, "REJECTED"), viaUsb);
        }
        else if (messageType == "TURNOUT" && desiredState == "OPEN")
        {
            sendReply(buildAckFrame(NODE_ID, commandId, setTurnoutState(turnoutId, true, openPulse, closedPulse) ? "ACCEPTED" : "REJECTED"), viaUsb);
        }
        else if (messageType == "TURNOUT" && desiredState == "CLOSED")
        {
            sendReply(buildAckFrame(NODE_ID, commandId, setTurnoutState(turnoutId, false, openPulse, closedPulse) ? "ACCEPTED" : "REJECTED"), viaUsb);
        }
        else
        {
            sendReply(buildAckFrame(NODE_ID, commandId, "REJECTED"), viaUsb);
        }
    }
}

void setup()
{
    pinMode(RS485_DIR_PIN, OUTPUT);
    digitalWrite(RS485_DIR_PIN, LOW);
    Wire.begin();
    pca9685Present = detectPca9685();

    if (pca9685Present)
    {
        pwm.begin();
        pwm.setPWMFreq(PCA9685_FREQ_HZ);

        for (uint8_t i = 0; i < TURNOUT_COUNT; i++)
        {
            turnoutIsOpen[i] = false;
            pwm.setPWM(i, 0, TURNOUT_DEFAULT_CLOSED_ANGLE);
        }
    }
    else
    {
        for (uint8_t i = 0; i < TURNOUT_COUNT; i++)
        {
            turnoutIsOpen[i] = false;
        }
    }

    // Debug: also init hardware Serial for direct USB testing
    Serial.begin(19200);
    Serial.print("Turnout node started: ");
    Serial.println(NODE_ID);
    Serial.print("PCA9685 detected at 0x40: ");
    Serial.println(pca9685Present ? "YES" : "NO");
    if (!pca9685Present)
    {
        printI2cScan();
    }

    rs485.begin(19200);
}

void loop()
{
    if (rs485.available())
    {
        String line = rs485.readStringUntil('\n');
        line.trim();
        if (line.length() > 0)
        {
            Serial.print("RS485 RX: ");
            Serial.println(line);
            handleLine(line, false);
        }
    }

    if (Serial.available())
    {
        String line = Serial.readStringUntil('\n');
        line.trim();
        if (line.length() > 0)
        {
            handleLine(line, true);
        }
    }
}
