#!/usr/bin/env python3
"""Tiny RS485 serial smoke test for TrainController Arduino nodes.

Usage examples:
  python3 tools/rs485_smoke_test.py --port /dev/ttyUSB2
  python3 tools/rs485_smoke_test.py --port /dev/ttyUSB2 --turnout-node turnout1 --signal-node signal1
"""

from __future__ import annotations

import argparse
import sys
import time

try:
    import serial  # type: ignore
except ImportError:
    print("Missing dependency: pyserial")
    print("Install with: /usr/bin/python3 -m pip install --user --break-system-packages pyserial")
    sys.exit(1)


def send_and_read(ser: serial.Serial, frame: str, wait_s: float) -> None:
    print(f"\n>> {frame}")
    ser.write((frame + "\n").encode("ascii"))
    ser.flush()

    deadline = time.time() + wait_s
    got_reply = False
    while time.time() < deadline:
        line = ser.readline()
        if not line:
            continue
        text = line.decode("ascii", errors="replace").strip()
        if text:
            got_reply = True
            print(f"<< {text}")

    if not got_reply:
        print("<< (no response)")

    time.sleep(0.1)


def main() -> int:
    parser = argparse.ArgumentParser(description="RS485 smoke test for turnout/signal nodes")
    parser.add_argument("--port", required=True, help="Serial port for USB-RS485 adapter (example: /dev/ttyUSB2)")
    parser.add_argument("--baud", type=int, default=19200, help="Serial baud rate (default: 19200)")
    parser.add_argument("--turnout-node", default="turnout1", help="Node ID used in turnout frames")
    parser.add_argument("--signal-node", default="signal1", help="Node ID used in signal frames")
    parser.add_argument("--wait", type=float, default=0.6, help="Seconds to collect replies after each frame")
    args = parser.parse_args()

    frames = [
        f"QSTATE|{args.turnout_node}",
        f"v1|{args.turnout_node}|cmd-1|TURNOUT|x|OPEN",
        f"v1|{args.turnout_node}|cmd-2|TURNOUT|x|CLOSED",
        f"QSTATE|{args.signal_node}",
        f"v1|{args.signal_node}|cmd-3|SIGNAL|x|GREEN",
        f"v1|{args.signal_node}|cmd-4|SIGNAL|x|RED",
    ]

    print(f"Opening {args.port} @ {args.baud}...")
    with serial.Serial(args.port, args.baud, timeout=0.15) as ser:
        # Give USB serial a moment to settle.
        time.sleep(0.2)
        ser.reset_input_buffer()

        for frame in frames:
            send_and_read(ser, frame, args.wait)

    print("\nDone.")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
