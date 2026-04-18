#include <Arduino.h>
#include <SoftwareSerial.h>

const uint8_t RS485_DIR_PIN = 5;
const uint8_t RS485_RX_PIN = 6;
const uint8_t RS485_TX_PIN = 7;
const uint8_t TURNOUT_PIN = 9;
const char NODE_ID[] = "turnout1";

SoftwareSerial rs485(RS485_RX_PIN, RS485_TX_PIN);

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
    if (line == String("QSTATE|") + NODE_ID)
    {
        sendReply(digitalRead(TURNOUT_PIN) ? "STATE|OPEN" : "STATE|CLOSED", viaUsb);
    }
    else if (line.startsWith("v1|"))
    {
        String nodeId = framePart(line, 1);
        String commandId = framePart(line, 2);
        String messageType = framePart(line, 3);
        String desiredState = framePart(line, 5);

        if (nodeId != NODE_ID)
        {
            return;
        }

        if (messageType == "TURNOUT" && desiredState == "OPEN")
        {
            digitalWrite(TURNOUT_PIN, HIGH);
            sendReply(buildAckFrame(NODE_ID, commandId, "ACCEPTED"), viaUsb);
        }
        else if (messageType == "TURNOUT" && desiredState == "CLOSED")
        {
            digitalWrite(TURNOUT_PIN, LOW);
            sendReply(buildAckFrame(NODE_ID, commandId, "ACCEPTED"), viaUsb);
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
    pinMode(TURNOUT_PIN, OUTPUT);
    digitalWrite(RS485_DIR_PIN, LOW);
    digitalWrite(TURNOUT_PIN, LOW);
    // Debug: also init hardware Serial for direct USB testing
    Serial.begin(19200);
    Serial.print("Turnout node started: ");
    Serial.println(NODE_ID);

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
