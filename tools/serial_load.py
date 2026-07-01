#!/usr/bin/env python3
import argparse
import os
import struct
import sys
import termios
import time


def baud_constant(baud):
    name = f"B{baud}"
    if not hasattr(termios, name):
        raise ValueError(f"Unsupported baud rate: {baud}")
    return getattr(termios, name)


def configure_serial(fd, baud):
    attrs = termios.tcgetattr(fd)
    speed = baud_constant(baud)

    attrs[0] = 0
    attrs[1] = 0
    attrs[2] &= ~(termios.CSIZE | termios.PARENB | termios.CSTOPB)
    if hasattr(termios, "CRTSCTS"):
        attrs[2] &= ~termios.CRTSCTS
    attrs[2] |= termios.CS8 | termios.CREAD | termios.CLOCAL
    attrs[3] = 0
    attrs[4] = speed
    attrs[5] = speed
    attrs[6][termios.VMIN] = 0
    attrs[6][termios.VTIME] = 0

    termios.tcsetattr(fd, termios.TCSANOW, attrs)


def write_all(fd, data):
    view = memoryview(data)
    while view:
        written = os.write(fd, view)
        view = view[written:]


def wire_time_seconds(byte_count, baud):
    # 8N1 serial sends 1 start bit, 8 data bits, and 1 stop bit per byte.
    return byte_count * 10.0 / baud


def main():
    parser = argparse.ArgumentParser(
        description="Load a monitor-headered binary over UART and optionally run it."
    )
    parser.add_argument("bin_file", help="Binary file with 8-byte monitor header")
    parser.add_argument("--port", default="/dev/ttyACM0", help="Serial port path")
    parser.add_argument("--baud", type=int, default=19200, help="Serial baud rate")
    parser.add_argument(
        "--prompt-delay",
        type=float,
        default=0.5,
        help="Delay after UART wire-time wait so the monitor can print Done/prompt",
    )
    parser.add_argument(
        "--wire-margin",
        type=float,
        default=0.1,
        help="Extra safety margin added to the computed UART wire time",
    )
    parser.add_argument("--no-run", action="store_true", help="Load without sending run")
    args = parser.parse_args()

    with open(args.bin_file, "rb") as file:
        image = file.read()

    if len(image) < 8:
        print(f"Error: {args.bin_file} is too small for a monitor header", file=sys.stderr)
        return 1

    program_address, payload_len = struct.unpack(">II", image[:8])
    expected_size = payload_len + 8
    if len(image) != expected_size:
        print(
            f"Error: header length is {payload_len} bytes, "
            f"but file size is {len(image)} bytes ({expected_size} expected)",
            file=sys.stderr,
        )
        return 1

    fd = os.open(args.port, os.O_RDWR | os.O_NOCTTY)
    try:
        configure_serial(fd, args.baud)

        print(f"--- Loading {args.bin_file} to {args.port} ---")
        write_all(fd, b"load\r")
        termios.tcdrain(fd)
        time.sleep(0.5)

        write_all(fd, image)
        termios.tcdrain(fd)

        if args.no_run:
            return 0

        load_wait = wire_time_seconds(len(image), args.baud) + args.wire_margin
        print(f"--- Waiting {load_wait:.2f}s for UART transfer to finish ---")
        time.sleep(load_wait)
        time.sleep(args.prompt_delay)

        run_cmd = f"run {program_address:08X}\r".encode("ascii")
        print(f"--- Running application at 0x{program_address:08X} ---")
        write_all(fd, run_cmd)
        termios.tcdrain(fd)
    finally:
        os.close(fd)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
