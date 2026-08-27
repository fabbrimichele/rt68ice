#!/usr/bin/env python3
"""Upload a monitor-headered image using XMODEM-CRC."""

import argparse
import binascii
import os
import select
import socket
import struct
import sys
import termios
import time


SOH = 0x01
STX = 0x02
EOT = 0x04
ACK = 0x06
NAK = 0x15
CAN = 0x18
CRC_REQUEST = ord("C")
PAD = 0x1A

DEFAULT_BAUD = 57600
DEFAULT_BLOCK_SIZE = 1024
DEFAULT_RETRIES = 10
DEFAULT_TIMEOUT = 3.0
LOAD_MIN = 0x00010000
LOAD_END = 0x00800000


class TransferError(RuntimeError):
    """Raised when the XMODEM transfer cannot be completed."""


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
        if written == 0:
            raise TransferError("serial port stopped accepting data")
        view = view[written:]


def drain_output(fd):
    """Wait for a direct TTY transport; a terminal proxy owns its TTY."""
    if os.isatty(fd):
        termios.tcdrain(fd)


def crc16_xmodem(data):
    """Return CRC16-XMODEM (polynomial 0x1021, initial value zero)."""
    return binascii.crc_hqx(data, 0)


def make_packet(block_number, data, block_size=DEFAULT_BLOCK_SIZE):
    if block_size not in (128, 1024):
        raise ValueError("XMODEM block size must be 128 or 1024 bytes")
    if not 0 < len(data) <= block_size:
        raise ValueError("packet data must contain between 1 and block_size bytes")

    padded = data.ljust(block_size, bytes((PAD,)))
    control = STX if block_size == 1024 else SOH
    number = block_number & 0xFF
    crc = crc16_xmodem(padded)
    return bytes((control, number, 0xFF - number)) + padded + struct.pack(">H", crc)


def read_control(fd, accepted, timeout):
    """Read until an accepted control byte arrives or the timeout expires."""
    deadline = time.monotonic() + timeout
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return None
        readable, _, _ = select.select((fd,), (), (), remaining)
        if not readable:
            return None
        data = os.read(fd, 1)
        if not data:
            return None
        if data[0] in accepted:
            return data[0]


def wait_for_prompt(fd, timeout):
    """Wait for the monitor's next command prompt."""
    deadline = time.monotonic() + timeout
    while True:
        remaining = deadline - time.monotonic()
        if remaining <= 0:
            return False
        readable, _, _ = select.select((fd,), (), (), remaining)
        if not readable:
            return False
        data = os.read(fd, 1)
        if not data:
            return False
        if data == b">":
            return True


def send_packet(fd, packet, timeout, retries):
    for _ in range(retries):
        write_all(fd, packet)
        drain_output(fd)
        response = read_control(fd, {ACK, NAK, CAN}, timeout)
        if response == ACK:
            return
        if response == CAN:
            raise TransferError("receiver cancelled the transfer")
    raise TransferError("packet was not acknowledged")


def finish_transfer(fd, timeout, retries):
    """Support both EOT/ACK and the classic EOT/NAK/EOT/ACK ending."""
    for _ in range(retries):
        write_all(fd, bytes((EOT,)))
        drain_output(fd)
        response = read_control(fd, {ACK, NAK, CAN}, timeout)
        if response == ACK:
            return
        if response == CAN:
            raise TransferError("receiver cancelled the transfer")
        # NAK and timeout both cause EOT to be sent again.
    raise TransferError("end of transfer was not acknowledged")


def send_xmodem(fd, image, block_size, timeout, retries):
    block_number = 1
    packet_count = (len(image) + block_size - 1) // block_size

    try:
        for offset in range(0, len(image), block_size):
            chunk = image[offset : offset + block_size]
            packet = make_packet(block_number, chunk, block_size)
            send_packet(fd, packet, timeout, retries)
            block_number = (block_number + 1) & 0xFF

            packet_index = offset // block_size + 1
            print(
                f"\r--- Sent block {packet_index}/{packet_count} ---",
                end="",
                flush=True,
            )

        print()
        finish_transfer(fd, timeout, retries)
    except TransferError:
        write_all(fd, bytes((CAN, CAN)))
        drain_output(fd)
        raise


def read_image(path):
    with open(path, "rb") as file:
        image = file.read()

    if len(image) < 8:
        raise ValueError(f"{path} is too small for a monitor header")

    program_address, payload_len = struct.unpack(">II", image[:8])
    expected_size = payload_len + 8
    if len(image) != expected_size:
        raise ValueError(
            f"header length is {payload_len} bytes, but file size is "
            f"{len(image)} bytes ({expected_size} expected)"
        )
    if payload_len == 0:
        raise ValueError("image payload is empty")
    if program_address & 1:
        raise ValueError("load address must be even")
    image_end = program_address + payload_len
    if program_address < LOAD_MIN or image_end > LOAD_END:
        raise ValueError(
            f"load range 0x{program_address:08X}..0x{image_end - 1:08X} "
            f"is outside 0x{LOAD_MIN:08X}..0x{LOAD_END - 1:08X}"
        )

    return image, program_address, payload_len


def main():
    parser = argparse.ArgumentParser(
        description="Load a monitor-headered binary over XMODEM-CRC and optionally run it."
    )
    parser.add_argument("bin_file", help="Binary file with 8-byte monitor header")
    parser.add_argument("--port", default="/dev/ttyACM0", help="Serial port path")
    parser.add_argument(
        "--socket",
        dest="socket_path",
        help="Use the Unix socket exposed by serial_terminal.py instead of opening the port",
    )
    parser.add_argument("--baud", type=int, default=DEFAULT_BAUD, help="Serial baud rate")
    parser.add_argument(
        "--block-size",
        type=int,
        choices=(128, 1024),
        default=DEFAULT_BLOCK_SIZE,
        help="XMODEM data block size (default: 1024)",
    )
    parser.add_argument(
        "--timeout",
        type=float,
        default=DEFAULT_TIMEOUT,
        help="Response timeout in seconds (default: 3)",
    )
    parser.add_argument(
        "--start-timeout",
        type=float,
        default=15.0,
        help="Time to wait for the monitor's initial CRC request (default: 15)",
    )
    parser.add_argument(
        "--retries",
        type=int,
        default=DEFAULT_RETRIES,
        help="Maximum attempts for each block (default: 10)",
    )
    parser.add_argument("--no-run", action="store_true", help="Load without sending run")
    args = parser.parse_args()

    if args.timeout <= 0 or args.start_timeout <= 0 or args.retries <= 0:
        parser.error("timeouts and retries must be greater than zero")

    try:
        image, program_address, payload_len = read_image(args.bin_file)
    except (OSError, ValueError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1

    fd = None
    transport_socket = None
    try:
        if args.socket_path:
            transport_socket = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
            try:
                transport_socket.connect(args.socket_path)
            except OSError as error:
                raise TransferError(
                    f"cannot connect to {args.socket_path}; start `make serial-open` first"
                ) from error
            fd = transport_socket.fileno()
            destination = f"terminal at {args.socket_path}"
        else:
            fd = os.open(args.port, os.O_RDWR | os.O_NOCTTY)
            configure_serial(fd, args.baud)
            termios.tcflush(fd, termios.TCIFLUSH)
            destination = args.port

        protocol_name = "XMODEM-1K" if args.block_size == 1024 else "XMODEM-CRC"
        print(
            f"--- Loading {payload_len} bytes at 0x{program_address:08X} "
            f"to {destination} using {protocol_name} ---"
        )
        write_all(fd, b"load\r")
        drain_output(fd)

        response = read_control(fd, {CRC_REQUEST, CAN}, args.start_timeout)
        if response == CAN:
            raise TransferError("monitor cancelled before the transfer started")
        if response != CRC_REQUEST:
            raise TransferError("monitor did not request an XMODEM-CRC transfer")

        send_xmodem(fd, image, args.block_size, args.timeout, args.retries)

        if not wait_for_prompt(fd, args.timeout):
            raise TransferError("transfer completed, but the monitor prompt was not received")

        if not args.no_run:
            run_cmd = f"run {program_address:08X}\r".encode("ascii")
            print(f"--- Running application at 0x{program_address:08X} ---")
            write_all(fd, run_cmd)
            drain_output(fd)
    except (OSError, ValueError, TransferError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    finally:
        if transport_socket is not None:
            transport_socket.close()
        elif fd is not None:
            os.close(fd)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
