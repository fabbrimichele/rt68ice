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


def upload_image(fd, image):
    total = len(image)
    interactive = sys.stdout.isatty()
    # Update in place on a terminal; keep redirected output to about ten lines.
    progress_step = 1 if interactive else 10
    last_bucket = 0

    def show_progress(sent):
        percent = sent * 100.0 / total
        status = (
            f"Upload: {percent:5.1f}% | {sent:,}/{total:,} bytes | "
            f"{100.0 - percent:5.1f}% remaining"
        )
        print(("\r" if interactive else "") + status,
              end="" if interactive else "\n", flush=True)

    show_progress(0)
    try:
        for offset in range(0, total, 1024):
            chunk = image[offset:offset + 1024]
            write_all(fd, chunk)
            # Count drained chunks, rather than bytes merely queued in the OS.
            # As before, this tracks serial transmission, not monitor CRC acceptance.
            termios.tcdrain(fd)
            sent = offset + len(chunk)
            bucket = (sent * 100 // total) // progress_step
            if bucket != last_bucket or sent == total:
                show_progress(sent)
                last_bucket = bucket
    finally:
        if interactive:
            print(flush=True)


def wire_time_seconds(byte_count, baud):
    # 8N1 serial sends 1 start bit, 8 data bits, and 1 stop bit per byte.
    return byte_count * 10.0 / baud


def main():
    parser = argparse.ArgumentParser(
        description="Send an image to the monitor over UART and optionally run it."
    )
    parser.add_argument("bin_file", help="Binary file with a monitor load header")
    parser.add_argument("--port", default="/dev/ttyACM0", help="Serial port path")
    parser.add_argument("--baud", type=int, default=57600, help="Serial baud rate")
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

    # The monitor owns all header validation.  The sender only needs the new
    # header's load-address field for the optional automatic RUN command.
    if len(image) < 8:
        print(f"Error: {args.bin_file} is too small to contain a load address", file=sys.stderr)
        return 1

    program_address = struct.unpack(">I", image[4:8])[0]

    fd = os.open(args.port, os.O_RDWR | os.O_NOCTTY)
    try:
        configure_serial(fd, args.baud)

        print(f"--- Loading {args.bin_file} to {args.port} ---")
        write_all(fd, b"load\r")
        termios.tcdrain(fd)
        time.sleep(0.5)

        upload_image(fd, image)

        if args.no_run:
            return 0

        load_wait = wire_time_seconds(len(image), args.baud) + args.wire_margin
        if load_wait > 1:
            load_wait = 1

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
