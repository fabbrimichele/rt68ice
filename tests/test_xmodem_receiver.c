#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>

#include "../sw/fw/c/xmodem.h"

enum {
    SOH = 0x01,
    STX = 0x02,
    EOT = 0x04,
    ACK = 0x06,
    NAK = 0x15,
    CAN = 0x18,
    CRC_REQUEST = 'C',
    PAD = 0x1a,
    INPUT_CAPACITY = 4096,
    OUTPUT_CAPACITY = 32,
    MEMORY_CAPACITY = 1024,
};

typedef struct {
    uint8_t input[INPUT_CAPACITY];
    uint16_t input_length;
    uint16_t input_index;
    uint8_t output[OUTPUT_CAPACITY];
    uint8_t output_length;
    uint8_t memory[MEMORY_CAPACITY];
    uint16_t store_count;
} FakeTransport;

#define CHECK(condition) \
    do { \
        if (!(condition)) { \
            fprintf(stderr, "%s:%d: %s\n", __FILE__, __LINE__, #condition); \
            return false; \
        } \
    } while (0)

static uint16_t crc16_xmodem_byte(uint16_t crc, uint8_t byte) {
    uint8_t bit;

    crc ^= (uint16_t)byte << 8;
    for (bit = 0; bit < 8; ++bit) {
        crc = (crc & 0x8000u) != 0u
            ? (uint16_t)((crc << 1) ^ 0x1021u)
            : (uint16_t)(crc << 1);
    }
    return crc;
}

static void append_byte(FakeTransport *transport, uint8_t byte) {
    if (transport->input_length >= INPUT_CAPACITY) {
        fprintf(stderr, "test input overflow\n");
        return;
    }
    transport->input[transport->input_length++] = byte;
}

static void append_packet(
    FakeTransport *transport,
    uint8_t block_number,
    const uint8_t *data,
    uint16_t size,
    bool corrupt_crc
) {
    uint16_t index;
    uint16_t crc = 0;

    append_byte(transport, size == 128u ? SOH : STX);
    append_byte(transport, block_number);
    append_byte(transport, (uint8_t)~block_number);
    for (index = 0; index < size; ++index) {
        append_byte(transport, data[index]);
        crc = crc16_xmodem_byte(crc, data[index]);
    }
    append_byte(transport, (uint8_t)((crc >> 8) ^ (corrupt_crc ? 1u : 0u)));
    append_byte(transport, (uint8_t)crc);
}

static bool fake_read_byte(void *context, uint8_t *byte) {
    FakeTransport *const transport = context;

    if (transport->input_index == transport->input_length) {
        return false;
    }
    *byte = transport->input[transport->input_index++];
    return true;
}

static void fake_write_byte(void *context, uint8_t byte) {
    FakeTransport *const transport = context;

    if (transport->output_length < OUTPUT_CAPACITY) {
        transport->output[transport->output_length++] = byte;
    }
}

static void fake_store_byte(void *context, uint32_t address, uint8_t byte) {
    FakeTransport *const transport = context;
    const uint32_t offset = address - XMODEM_LOAD_MIN;

    if (offset < MEMORY_CAPACITY) {
        transport->memory[offset] = byte;
        ++transport->store_count;
    }
}

static XmodemIo make_io(FakeTransport *transport) {
    const XmodemIo io = {
        .read_byte = fake_read_byte,
        .write_byte = fake_write_byte,
        .store_byte = fake_store_byte,
        .context = transport,
    };

    return io;
}

static void make_first_packet(
    uint8_t *packet,
    uint16_t packet_size,
    const uint8_t *payload,
    uint16_t payload_size
) {
    uint16_t index;

    memset(packet, PAD, packet_size);
    packet[0] = 0x00;
    packet[1] = 0x01;
    packet[2] = 0x00;
    packet[3] = 0x00;
    packet[4] = 0x00;
    packet[5] = 0x00;
    packet[6] = (uint8_t)(payload_size >> 8);
    packet[7] = (uint8_t)payload_size;
    for (index = 0; index < payload_size && index + 8u < packet_size; ++index) {
        packet[index + 8u] = payload[index];
    }
}

static bool test_1k_transfer(void) {
    FakeTransport transport = {0};
    uint8_t packet[XMODEM_PACKET_BUFFER_SIZE];
    uint8_t receiver_buffer[XMODEM_PACKET_BUFFER_SIZE];
    const uint8_t payload[] = {0xde, 0xad, 0xbe, 0xef};
    const uint8_t expected_output[] = {CRC_REQUEST, ACK, NAK, ACK};
    const XmodemIo io = make_io(&transport);

    make_first_packet(packet, sizeof(packet), payload, sizeof(payload));
    append_packet(&transport, 1u, packet, sizeof(packet), false);
    append_byte(&transport, EOT);
    append_byte(&transport, EOT);

    CHECK(xmodem_receive(&io, receiver_buffer));
    CHECK(transport.store_count == sizeof(payload));
    CHECK(memcmp(transport.memory, payload, sizeof(payload)) == 0);
    CHECK(transport.output_length == sizeof(expected_output));
    CHECK(memcmp(transport.output, expected_output, sizeof(expected_output)) == 0);
    return true;
}

static bool test_crc_retry(void) {
    FakeTransport transport = {0};
    uint8_t packet[128];
    uint8_t receiver_buffer[XMODEM_PACKET_BUFFER_SIZE];
    const uint8_t payload[] = {0x11, 0x22, 0x33, 0x44};
    const uint8_t expected_output[] = {CRC_REQUEST, NAK, ACK, NAK, ACK};
    const XmodemIo io = make_io(&transport);

    make_first_packet(packet, sizeof(packet), payload, sizeof(payload));
    append_packet(&transport, 1u, packet, sizeof(packet), true);
    append_packet(&transport, 1u, packet, sizeof(packet), false);
    append_byte(&transport, EOT);
    append_byte(&transport, EOT);

    CHECK(xmodem_receive(&io, receiver_buffer));
    CHECK(transport.store_count == sizeof(payload));
    CHECK(memcmp(transport.memory, payload, sizeof(payload)) == 0);
    CHECK(transport.output_length == sizeof(expected_output));
    CHECK(memcmp(transport.output, expected_output, sizeof(expected_output)) == 0);
    return true;
}

static bool test_duplicate_packet_is_not_copied_twice(void) {
    FakeTransport transport = {0};
    uint8_t first_packet[128];
    uint8_t second_packet[128];
    uint8_t receiver_buffer[XMODEM_PACKET_BUFFER_SIZE];
    uint8_t payload[122];
    const uint8_t expected_output[] = {
        CRC_REQUEST, ACK, ACK, ACK, NAK, ACK,
    };
    const XmodemIo io = make_io(&transport);
    uint16_t index;

    for (index = 0; index < sizeof(payload); ++index) {
        payload[index] = (uint8_t)index;
    }
    make_first_packet(first_packet, sizeof(first_packet), payload, sizeof(payload));
    memset(second_packet, PAD, sizeof(second_packet));
    second_packet[0] = payload[120];
    second_packet[1] = payload[121];

    append_packet(&transport, 1u, first_packet, sizeof(first_packet), false);
    append_packet(&transport, 1u, first_packet, sizeof(first_packet), false);
    append_packet(&transport, 2u, second_packet, sizeof(second_packet), false);
    append_byte(&transport, EOT);
    append_byte(&transport, EOT);

    CHECK(xmodem_receive(&io, receiver_buffer));
    CHECK(transport.store_count == sizeof(payload));
    CHECK(memcmp(transport.memory, payload, sizeof(payload)) == 0);
    CHECK(transport.output_length == sizeof(expected_output));
    CHECK(memcmp(transport.output, expected_output, sizeof(expected_output)) == 0);
    return true;
}

static bool test_invalid_image_is_cancelled(void) {
    FakeTransport transport = {0};
    uint8_t packet[128];
    uint8_t receiver_buffer[XMODEM_PACKET_BUFFER_SIZE];
    const uint8_t expected_output[] = {CRC_REQUEST, CAN, CAN};
    const XmodemIo io = make_io(&transport);

    memset(packet, PAD, sizeof(packet));
    packet[3] = 1u; /* Odd load address: rejected before any payload write. */
    packet[7] = 1u;
    append_packet(&transport, 1u, packet, sizeof(packet), false);

    CHECK(!xmodem_receive(&io, receiver_buffer));
    CHECK(transport.store_count == 0u);
    CHECK(transport.output_length == sizeof(expected_output));
    CHECK(memcmp(transport.output, expected_output, sizeof(expected_output)) == 0);
    return true;
}

int main(void) {
    return test_1k_transfer() && test_crc_retry() &&
                   test_duplicate_packet_is_not_copied_twice() &&
                   test_invalid_image_is_cancelled()
               ? 0
               : 1;
}
