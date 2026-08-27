import os
import socket
import struct
import tempfile
import unittest
from unittest import mock

from tools import serial_load


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

    def test_terminal_relays_output_input_and_honors_escape(self):
        serial_side, device_side = socket.socketpair()
        input_read, input_write = os.pipe()
        output_read, output_write = os.pipe()
        try:
            device_side.sendall(b"board output\r\n")
            os.write(input_write, b"key" + bytes((serial_load.TERMINAL_ESCAPE,)))

            serial_load.terminal_session(
                serial_side.fileno(),
                input_fd=input_read,
                output_fd=output_write,
            )

            self.assertEqual(device_side.recv(3), b"key")
            os.close(output_write)
            output_write = None
            self.assertEqual(os.read(output_read, 4096), b"board output\r\n")
        finally:
            serial_side.close()
            device_side.close()
            os.close(input_read)
            os.close(input_write)
            os.close(output_read)
            if output_write is not None:
                os.close(output_write)

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


if __name__ == "__main__":
    unittest.main()
