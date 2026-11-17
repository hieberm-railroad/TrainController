# firmware-arduino

PlatformIO firmware for RS485-connected turnout and signal nodes.
Also includes a dedicated reversing-loop power controller node.

## Environments

- `turnout_node` for turnout actuator firmware.
- `signal_node` for signal controller firmware.
- `reversing_loop_node` for automatic reversing-loop polarity control.

## Build

```bash
pio run -e turnout_node
pio run -e signal_node
pio run -e reversing_loop_node
```
