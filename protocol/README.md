# protocol

This directory contains message contracts and examples shared between Java interceptor and Arduino firmware.

## Current Contracts

- `schemas/turnout-intent.schema.json` for inbound turnout intents.
- RS485 frame draft for firmware dispatch:

`v1|<nodeId>|<commandId>|TURNOUT|<turnoutId>|<OPEN|CLOSED>|<checksumHex>\n`

- Addressed state query frame:

`QSTATE|<nodeId>\n`

- State response frame:

`STATE|<actualState>\n`

## Notes

- Keep frame size under firmware parser limits.
- Include command IDs for idempotency and stale-response rejection.
- Nodes should ignore commands and queries addressed to other node IDs.
