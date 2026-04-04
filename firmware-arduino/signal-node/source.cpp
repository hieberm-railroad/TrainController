#include <Arduino.h>

const uint8_t RS485_DIR_PIN = 8;
const uint8_t SIGNAL_PIN = 10;

void setup()
{
    pinMode(RS485_DIR_PIN, OUTPUT);
    pinMode(SIGNAL_PIN, OUTPUT);
    digitalWrite(RS485_DIR_PIN, LOW);
    digitalWrite(SIGNAL_PIN, LOW);
    Serial.begin(19200);
}

void loop()
{
    // Placeholder: parse framed RS485 signal commands.
    if (Serial.available())
    {
        String line = Serial.readStringUntil('\n');
        if (line.indexOf("SIGNAL") >= 0 && line.indexOf("GREEN") >= 0)
        {
            digitalWrite(SIGNAL_PIN, HIGH);
            Serial.println("ACK|GREEN");
        }
        else if (line.indexOf("SIGNAL") >= 0 && line.indexOf("RED") >= 0)
        {
            digitalWrite(SIGNAL_PIN, LOW);
            Serial.println("ACK|RED");
        }
    }
}
