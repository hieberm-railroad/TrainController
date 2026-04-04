#include <Arduino.h>

const uint8_t RS485_DIR_PIN = 8;
const uint8_t TURNOUT_PIN = 9;

void setup()
{
    pinMode(RS485_DIR_PIN, OUTPUT);
    pinMode(TURNOUT_PIN, OUTPUT);
    digitalWrite(RS485_DIR_PIN, LOW);
    digitalWrite(TURNOUT_PIN, LOW);
    Serial.begin(19200);
}

void loop()
{
    // Placeholder: parse framed RS485 command and actuate turnout.
    if (Serial.available())
    {
        String line = Serial.readStringUntil('\n');
        if (line.indexOf("TURNOUT") >= 0 && line.indexOf("OPEN") >= 0)
        {
            digitalWrite(TURNOUT_PIN, HIGH);
            Serial.println("ACK|OPEN");
        }
        else if (line.indexOf("TURNOUT") >= 0 && line.indexOf("CLOSED") >= 0)
        {
            digitalWrite(TURNOUT_PIN, LOW);
            Serial.println("ACK|CLOSED");
        }
        else if (line.startsWith("QSTATE"))
        {
            Serial.println(digitalRead(TURNOUT_PIN) ? "STATE|OPEN" : "STATE|CLOSED");
        }
    }
}
