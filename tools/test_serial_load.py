import os
import socket
import struct
import tempfile
import threading
import time
import unittest
from unittest import mock

from tools import serial_load, serial_terminal


class SerialLoadTest(unittest.TestCase):
    def test_crc16_xmodem_reference_vector(self):
        self.assertEqual(serial_load.crc16_xmodem(b"123456789"), 0x31C3)

    def test_1k_packet_framing_and_padding(self):
        packet = serial_load.make_packet(1, b"abc")

        self.assertEqual(packet[0], serial_load.STX)
        self.assertEqual(packet[1:3], bytes((1, 0xFE)))
        self.assertEqual(packet[3:6], b"abc")
        self.assertEqual(packet[6 : 3 + 1024], bytes((serial_load.PAD,)) * 1021)
        expected_crc = serial_load.crc16_xmodem(packet[3 : 3 + 1024])
        self.assertEqual(packet[-2:], struct.pack(">H", expected_crc))

    def test_128_byte_packet_uses_soh(self):
        packet = serial_load.make_packet(255, b"x", block_size=128)
        self.assertEqual(packet[:3], bytes((serial_load.SOH, 255, 0)))
        self.assertEqual(len(packet), 3 + 128 + 2)

    @mock.patch.object(serial_load.termios, "tcdrain")
    @mock.patch.object(serial_load, "read_control", side_effect=(serial_load.NAK, serial_load.ACK))
    @mock.patch.object(serial_load, "write_all")
    def test_packet_is_retried_after_nak(self, write_all, _read_control, _tcdrain):
        packet = serial_load.make_packet(1, b"abc")
        serial_load.send_packet(7, packet, timeout=1, retries=2)

        self.assertEqual(write_all.call_count, 2)
        write_all.assert_has_calls((mock.call(7, packet), mock.call(7, packet)))

    @mock.patch.object(serial_load.termios, "tcdrain")
    @mock.patch.object(serial_load, "read_control", side_effect=(serial_load.NAK, serial_load.ACK))
    @mock.patch.object(serial_load, "write_all")
    def test_classic_two_eot_termination(self, write_all, _read_control, _tcdrain):
        serial_load.finish_transfer(7, timeout=1, retries=2)

        eot_call = mock.call(7, bytes((serial_load.EOT,)))
        self.assertEqual(write_all.call_args_list, [eot_call, eot_call])

    def test_read_image_validates_header(self):
        image = struct.pack(">II", 0x00010000, 3) + b"abc"
        with tempfile.NamedTemporaryFile() as file:
            file.write(image)
            file.flush()
            actual, address, length = serial_load.read_image(file.name)

        self.assertEqual(actual, image)
        self.assertEqual(address, 0x00010000)
        self.assertEqual(length, 3)

    def test_read_image_rejects_bad_length(self):
        image = struct.pack(">II", 0x00010000, 4) + b"abc"
        with tempfile.NamedTemporaryFile() as file:
            file.write(image)
            file.flush()
            with self.assertRaisesRegex(ValueError, "header length"):
                serial_load.read_image(file.name)

    def test_read_image_rejects_odd_load_address(self):
        image = struct.pack(">II", 0x00010001, 3) + b"abc"
        with tempfile.NamedTemporaryFile() as file:
            file.write(image)
            file.flush()
            with self.assertRaisesRegex(ValueError, "must be even"):
                serial_load.read_image(file.name)


class SerialTerminalTest(unittest.TestCase):
    @staticmethod
    def read_until(fd, expected, timeout=1.0):
        deadline = time.monotonic() + timeout
        received = bytearray()
        while expected not in received:
            remaining = deadline - time.monotonic()
            if remaining <= 0:
                break
            readable, _, _ = serial_terminal.select.select((fd,), (), (), remaining)
            if not readable:
                break
            received.extend(os.read(fd, 4096))
        return bytes(received)

    def test_terminal_proxies_upload_then_resumes_interactive_io(self):
        uart_terminal, uart_device = socket.socketpair()
        proxy_terminal, uploader = socket.socketpair()
        idle_server_read, idle_server_write = os.pipe()
        input_read, input_write = os.pipe()
        output_read, output_write = os.pipe()
        errors = []

        class IdleServer:
            def fileno(self):
                return idle_server_read

        try:
            def run_terminal():
                try:
                    serial_terminal.relay_terminal(
                        uart_terminal.fileno(),
                        IdleServer(),
                        input_fd=input_read,
                        output_fd=output_write,
                        initial_client=proxy_terminal,
                    )
                except Exception as error:  # Preserve thread failures for the test.
                    errors.append(error)

            thread = threading.Thread(target=run_terminal, daemon=True)
            thread.start()
            uart_device.settimeout(1.0)
            uploader.settimeout(1.0)
            try:
                uploader.sendall(b"load\r")
                self.assertEqual(uart_device.recv(5), b"load\r")

                uart_device.sendall(b"C")
                self.assertEqual(uploader.recv(1), b"C")
                uploader.close()

                status = self.read_until(output_read, b"uploader disconnected")
                self.assertIn(b"uploader connected", status)
                self.assertIn(b"uploader disconnected", status)

                uart_device.sendall(b"program output\r\n")
                self.assertIn(
                    b"program output\r\n",
                    self.read_until(output_read, b"program output\r\n"),
                )

                os.write(input_write, b"key")
                self.assertEqual(uart_device.recv(3), b"key")
                os.write(input_write, bytes((serial_terminal.TERMINAL_ESCAPE,)))
                thread.join(timeout=1.0)
                self.assertFalse(thread.is_alive())
                self.assertEqual(errors, [])
            finally:
                uploader.close()
                if thread.is_alive():
                    os.write(input_write, bytes((serial_terminal.TERMINAL_ESCAPE,)))
                    thread.join(timeout=1.0)
        finally:
            uart_terminal.close()
            uart_device.close()
            proxy_terminal.close()
            uploader.close()
            os.close(idle_server_read)
            os.close(idle_server_write)
            os.close(input_read)
            os.close(input_write)
            os.close(output_read)
            os.close(output_write)


if __name__ == "__main__":
    unittest.main()
