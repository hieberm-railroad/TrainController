#include <Arduino.h>

enum class LoopPolarity
{
    NORMAL,
    REVERSED
};

enum class OccupancyScenario
{
    NONE_OCCUPIED,
    ENTRY_ONLY,
    EXIT_ONLY,
    BOTH_OCCUPIED
};

// Relay outputs for the two poles of the track feed.
const uint8_t RELAY_A_PIN = 8;
const uint8_t RELAY_B_PIN = 9;
const bool RELAY_ACTIVE_HIGH = true;

// Adjust these 4 booleans for your specific relay board wiring.
const bool RELAY_NORMAL_A = false;
const bool RELAY_NORMAL_B = false;
const bool RELAY_REVERSED_A = true;
const bool RELAY_REVERSED_B = true;

const uint8_t ENTRY_DETECTOR_PIN = 2;
const uint8_t EXIT_DETECTOR_PIN = 3;

// Occupancy detector outputs are active-low and require pull-up inputs.
const bool DETECTOR_ACTIVE_LOW = true;
const unsigned long DETECTOR_DEBOUNCE_MS = 15;
const unsigned long DETECTOR_HOLD_MS = 250;

const uint8_t STATUS_LED_PIN = LED_BUILTIN;

struct OccupancyFilter
{
    uint8_t pin;
    bool activeLow;
    bool stableRawOccupied;
    bool lastRawOccupied;
    bool occupied;
    unsigned long lastRawChangeMs;
    unsigned long lastStableOccupiedMs;

    void begin(uint8_t detectorPin, bool isActiveLow)
    {
        pin = detectorPin;
        activeLow = isActiveLow;
        pinMode(pin, INPUT_PULLUP);

        bool raw = readRaw();
        stableRawOccupied = raw;
        lastRawOccupied = raw;
        occupied = raw;
        lastRawChangeMs = millis();
        lastStableOccupiedMs = raw ? millis() : 0;
    }

    bool readRaw() const
    {
        bool levelHigh = digitalRead(pin) == HIGH;
        return activeLow ? !levelHigh : levelHigh;
    }

    bool update(unsigned long nowMs)
    {
        bool raw = readRaw();
        if (raw != lastRawOccupied)
        {
            lastRawOccupied = raw;
            lastRawChangeMs = nowMs;
        }

        if ((nowMs - lastRawChangeMs) >= DETECTOR_DEBOUNCE_MS)
        {
            stableRawOccupied = raw;
        }

        if (stableRawOccupied)
        {
            occupied = true;
            lastStableOccupiedMs = nowMs;
        }
        else if (occupied && (nowMs - lastStableOccupiedMs) >= DETECTOR_HOLD_MS)
        {
            occupied = false;
        }

        return occupied;
    }
};

OccupancyFilter entryDetector;
OccupancyFilter exitDetector;

LoopPolarity currentPolarity = LoopPolarity::NORMAL;

static void writeRelayPin(uint8_t pin, bool on)
{
    bool levelHigh = RELAY_ACTIVE_HIGH ? on : !on;
    digitalWrite(pin, levelHigh ? HIGH : LOW);
}

static void setPolarity(LoopPolarity newPolarity)
{
    if (newPolarity == currentPolarity)
    {
        return;
    }

    if (newPolarity == LoopPolarity::NORMAL)
    {
        writeRelayPin(RELAY_A_PIN, RELAY_NORMAL_A);
        writeRelayPin(RELAY_B_PIN, RELAY_NORMAL_B);
        Serial.println("Polarity -> NORMAL (red left, black right)");
    }
    else
    {
        writeRelayPin(RELAY_A_PIN, RELAY_REVERSED_A);
        writeRelayPin(RELAY_B_PIN, RELAY_REVERSED_B);
        Serial.println("Polarity -> REVERSED (red right, black left)");
    }

    currentPolarity = newPolarity;
}

static void updateStatusLed(unsigned long nowMs, bool entryOccupied, bool exitOccupied)
{
    static bool ledIsOn = false;
    static unsigned long lastToggleMs = 0;
    static OccupancyScenario lastScenario = OccupancyScenario::NONE_OCCUPIED;

    OccupancyScenario scenario = OccupancyScenario::NONE_OCCUPIED;
    uint8_t blinksPerSecond = 1;
    if (entryOccupied && exitOccupied)
    {
        scenario = OccupancyScenario::BOTH_OCCUPIED;
        blinksPerSecond = 4;
    }
    else if (entryOccupied && !exitOccupied)
    {
        scenario = OccupancyScenario::ENTRY_ONLY;
        blinksPerSecond = 2;
    }
    else if (!entryOccupied && exitOccupied)
    {
        scenario = OccupancyScenario::EXIT_ONLY;
        blinksPerSecond = 3;
    }

    if (scenario != lastScenario)
    {
        if (scenario == OccupancyScenario::NONE_OCCUPIED)
        {
            Serial.println("Scenario: none occupied -> LED 1/sec");
        }
        else if (scenario == OccupancyScenario::ENTRY_ONLY)
        {
            Serial.println("Scenario: entry occupied only -> LED 2/sec");
        }
        else if (scenario == OccupancyScenario::EXIT_ONLY)
        {
            Serial.println("Scenario: exit occupied only -> LED 3/sec");
        }
        else
        {
            Serial.println("Scenario: entry and exit occupied -> LED 4/sec");
        }

        lastScenario = scenario;
    }

    const unsigned long halfPeriodMs = 1000UL / (2UL * blinksPerSecond);
    if ((nowMs - lastToggleMs) >= halfPeriodMs)
    {
        lastToggleMs = nowMs;
        ledIsOn = !ledIsOn;
        digitalWrite(STATUS_LED_PIN, ledIsOn ? HIGH : LOW);
    }
}

void setup()
{
    Serial.begin(19200);

    pinMode(RELAY_A_PIN, OUTPUT);
    pinMode(RELAY_B_PIN, OUTPUT);
    pinMode(STATUS_LED_PIN, OUTPUT);
    digitalWrite(STATUS_LED_PIN, LOW);

    entryDetector.begin(ENTRY_DETECTOR_PIN, DETECTOR_ACTIVE_LOW);
    exitDetector.begin(EXIT_DETECTOR_PIN, DETECTOR_ACTIVE_LOW);

    writeRelayPin(RELAY_A_PIN, RELAY_NORMAL_A);
    writeRelayPin(RELAY_B_PIN, RELAY_NORMAL_B);
    currentPolarity = LoopPolarity::NORMAL;

    Serial.println("Reversing loop controller started");
    Serial.println("Startup polarity = NORMAL (red left, black right)");
}

void loop()
{
    unsigned long nowMs = millis();

    bool entryOccupied = entryDetector.update(nowMs);
    bool exitOccupied = exitDetector.update(nowMs);

    updateStatusLed(nowMs, entryOccupied, exitOccupied);

    // Simplified rule set:
    // - Entry occupied -> loop/exit polarity NORMAL.
    // - Exit occupied -> loop/exit polarity REVERSED.
    // - If both are occupied, exit rule takes priority.
    LoopPolarity desiredPolarity = currentPolarity;
    if (exitOccupied)
    {
        desiredPolarity = LoopPolarity::REVERSED;
    }
    else if (entryOccupied)
    {
        desiredPolarity = LoopPolarity::NORMAL;
    }

    if (desiredPolarity != currentPolarity)
    {
        if (desiredPolarity == LoopPolarity::NORMAL)
        {
            Serial.println("Polarity demand: NORMAL (entry occupied)");
        }
        else if (entryOccupied && exitOccupied)
        {
            Serial.println("Polarity demand: REVERSED (exit occupied, both occupied)");
        }
        else
        {
            Serial.println("Polarity demand: REVERSED (exit occupied)");
        }

        setPolarity(desiredPolarity);
    }
}
