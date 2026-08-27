#!/usr/bin/env python3
"""Interactive serial terminal that proxies XMODEM uploads over a Unix socket."""

import argparse
import errno
import os
import select
import socket
import stat
import sys
import termios
import tty

try:
    from .serial_load import DEFAULT_BAUD, TransferError, configure_serial, write_all
except ImportError:  # Direct execution: python3 tools/serial_terminal.py
    from serial_load import DEFAULT_BAUD, TransferError, configure_serial, write_all


DEFAULT_SOCKET = "/tmp/rt68ice-serial.sock"
TERMINAL_ESCAPE = 0x1D  # Ctrl-]


def terminal_message(output_fd, message):
    write_all(output_fd, f"\r\n--- {message} ---\r\n".encode("utf-8"))


def create_server(socket_path):
    """Create the proxy socket, removing it only when it is demonstrably stale."""
    if os.path.lexists(socket_path):
        if not stat.S_ISSOCK(os.lstat(socket_path).st_mode):
            raise TransferError(f"socket path exists and is not a socket: {socket_path}")

        probe = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
        try:
            probe.connect(socket_path)
        except OSError as error:
            if error.errno not in (errno.ECONNREFUSED, errno.ENOENT):
                raise
        else:
            raise TransferError(f"another serial terminal is using {socket_path}")
        finally:
            probe.close()

        if os.path.lexists(socket_path):
            os.unlink(socket_path)

    server = socket.socket(socket.AF_UNIX, socket.SOCK_STREAM)
    server.bind(socket_path)
    os.chmod(socket_path, 0o600)
    server.listen(1)
    return server


def relay_terminal(
    serial_fd,
    server,
    input_fd=None,
    output_fd=None,
    initial_client=None,
):
    """Relay the terminal normally, giving an uploader exclusive byte access."""
    if input_fd is None:
        input_fd = sys.stdin.fileno()
    if output_fd is None:
        output_fd = sys.stdout.fileno()

    saved_settings = None
    client = initial_client
    if os.isatty(input_fd):
        saved_settings = termios.tcgetattr(input_fd)
        tty.setraw(input_fd, when=termios.TCSANOW)
    if client is not None:
        terminal_message(output_fd, "uploader connected")

    try:
        while True:
            client_fd = client.fileno() if client is not None else None
            inputs = [server.fileno(), serial_fd, input_fd]
            if client_fd is not None:
                inputs.append(client_fd)
            readable, _, _ = select.select(inputs, (), ())

            if server.fileno() in readable:
                new_client, _ = server.accept()
                if client is not None:
                    new_client.close()
                else:
                    client = new_client
                    client_fd = client.fileno()
                    terminal_message(output_fd, "uploader connected")

            # Handle disconnect before UART input, so application output after
            # `run` returns to the visible terminal instead of the old client.
            if client is not None and client_fd in readable:
                try:
                    data = client.recv(4096)
                except ConnectionResetError:
                    data = b""
                if data:
                    write_all(serial_fd, data)
                else:
                    client.close()
                    client = None
                    client_fd = None
                    terminal_message(output_fd, "uploader disconnected")

            if serial_fd in readable:
                data = os.read(serial_fd, 4096)
                if not data:
                    raise TransferError("serial port disconnected")
                if client is not None:
                    try:
                        client.sendall(data)
                    except (BrokenPipeError, ConnectionResetError):
                        client.close()
                        client = None
                        terminal_message(output_fd, "uploader disconnected")
                        write_all(output_fd, data)
                else:
                    write_all(output_fd, data)

            if input_fd in readable:
                data = os.read(input_fd, 4096)
                if not data:
                    return

                escape_index = data.find(bytes((TERMINAL_ESCAPE,)))
                if escape_index >= 0:
                    if client is None and escape_index:
                        write_all(serial_fd, data[:escape_index])
                    return

                # Keyboard input during an upload is discarded so it cannot
                # corrupt XMODEM or unexpectedly reach the program afterward.
                if client is None:
                    write_all(serial_fd, data)
    finally:
        if client is not None:
            client.close()
        if saved_settings is not None:
            termios.tcsetattr(input_fd, termios.TCSADRAIN, saved_settings)


def main():
    parser = argparse.ArgumentParser(
        description="Serial terminal with a local proxy for serial_load.py."
    )
    parser.add_argument("--port", default="/dev/ttyACM0", help="Serial port path")
    parser.add_argument("--baud", type=int, default=DEFAULT_BAUD, help="Serial baud rate")
    parser.add_argument(
        "--socket",
        dest="socket_path",
        default=DEFAULT_SOCKET,
        help=f"Uploader proxy socket (default: {DEFAULT_SOCKET})",
    )
    args = parser.parse_args()

    serial_fd = None
    server = None
    try:
        serial_fd = os.open(args.port, os.O_RDWR | os.O_NOCTTY)
        configure_serial(serial_fd, args.baud)
        server = create_server(args.socket_path)

        print(f"--- Terminal connected to {args.port} at {args.baud} baud ---")
        print(f"--- Upload socket: {args.socket_path} ---")
        print("--- Press Ctrl-] to exit ---")
        relay_terminal(serial_fd, server)
        print("\n--- Terminal closed ---")
    except (OSError, ValueError, TransferError) as error:
        print(f"Error: {error}", file=sys.stderr)
        return 1
    finally:
        if server is not None:
            server.close()
            if os.path.lexists(args.socket_path):
                try:
                    if stat.S_ISSOCK(os.lstat(args.socket_path).st_mode):
                        os.unlink(args.socket_path)
                except FileNotFoundError:
                    pass
        if serial_fd is not None:
            os.close(serial_fd)

    return 0


if __name__ == "__main__":
    raise SystemExit(main())
