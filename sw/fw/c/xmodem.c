#include "xmodem.h"

enum {
    XMODEM_SOH = 0x01,
    XMODEM_STX = 0x02,
    XMODEM_EOT = 0x04,
    XMODEM_ACK = 0x06,
    XMODEM_NAK = 0x15,
    XMODEM_CAN = 0x18,
    XMODEM_CRC_REQUEST = 'C',
    XMODEM_MAX_RETRIES = 10,
    XMODEM_HEADER_SIZE = 8,
};

typedef struct {
    uint8_t expected_block;
    uint8_t retries;
    bool started;
    uint32_t destination;
    uint32_t remaining;
} XmodemState;

typedef enum {
    BLOCK_REJECTED,
    BLOCK_ACCEPTED,
    BLOCK_FATAL,
} BlockResult;

static uint16_t crc16_xmodem_byte(uint16_t crc, uint8_t byte) {
    uint8_t bit;

    crc ^= (uint16_t)byte << 8;
    for (bit = 0; bit < 8; ++bit) {
        if ((crc & 0x8000u) != 0u) {
            crc = (uint16_t)((crc << 1) ^ 0x1021u);
        } else {
            crc = (uint16_t)(crc << 1);
        }
    }
    return crc;
}

static uint32_t read_be32(const uint8_t *bytes) {
    return ((uint32_t)bytes[0] << 24) |
           ((uint32_t)bytes[1] << 16) |
           ((uint32_t)bytes[2] << 8) |
           (uint32_t)bytes[3];
}

/* Consume an entire packet before deciding whether it is valid. */
static bool receive_packet(
    const XmodemIo *io,
    uint8_t *packet_buffer,
    uint16_t packet_size,
    uint8_t *block_number
) {
    uint8_t complement;
    uint8_t byte;
    uint8_t crc_high;
    uint8_t crc_low;
    uint16_t received_crc;
    uint16_t calculated_crc = 0;
    uint16_t index;

    if (!io->read_byte(io->context, block_number) ||
        !io->read_byte(io->context, &complement)) {
        return false;
    }

    for (index = 0; index < packet_size; ++index) {
        if (!io->read_byte(io->context, &byte)) {
            return false;
        }
        packet_buffer[index] = byte;
        calculated_crc = crc16_xmodem_byte(calculated_crc, byte);
    }

    if (!io->read_byte(io->context, &crc_high) ||
        !io->read_byte(io->context, &crc_low)) {
        return false;
    }

    received_crc = ((uint16_t)crc_high << 8) | crc_low;
    return complement == (uint8_t)~(*block_number) &&
           calculated_crc == received_crc;
}

static BlockResult process_packet(
    const XmodemIo *io,
    XmodemState *state,
    const uint8_t *packet_buffer,
    uint16_t packet_size,
    uint8_t received_block
) {
    uint16_t payload_offset = 0;
    uint16_t available;
    uint32_t copy_length;
    uint32_t index;

    if (received_block != state->expected_block) {
        if (state->started &&
            received_block == (uint8_t)(state->expected_block - 1u)) {
            /* The sender missed our ACK.  Do not write this block twice. */
            return BLOCK_ACCEPTED;
        }
        return BLOCK_REJECTED;
    }

    if (!state->started) {
        const uint32_t load_address = read_be32(packet_buffer);
        const uint32_t payload_length = read_be32(packet_buffer + 4);

        /* Check subtraction-style to avoid overflow in address + length. */
        if (load_address < XMODEM_LOAD_MIN ||
            (load_address & 1u) != 0u ||
            payload_length == 0u ||
            load_address >= XMODEM_LOAD_END ||
            payload_length > XMODEM_LOAD_END - load_address) {
            return BLOCK_FATAL;
        }

        state->destination = load_address;
        state->remaining = payload_length;
        state->started = true;
        payload_offset = XMODEM_HEADER_SIZE;
    }

    if (state->remaining == 0u) {
        /* A new data packet after the advertised image is malformed. */
        return BLOCK_FATAL;
    }

    available = (uint16_t)(packet_size - payload_offset);
    copy_length = state->remaining;
    if (copy_length > available) {
        copy_length = available;
    }

    for (index = 0; index < copy_length; ++index) {
        io->store_byte(
            io->context,
            state->destination + index,
            packet_buffer[payload_offset + index]
        );
    }

    state->destination += copy_length;
    state->remaining -= copy_length;
    ++state->expected_block;
    return BLOCK_ACCEPTED;
}

static void cancel_transfer(const XmodemIo *io) {
    io->write_byte(io->context, XMODEM_CAN);
    io->write_byte(io->context, XMODEM_CAN);
}

bool xmodem_receive(const XmodemIo *io, uint8_t *packet_buffer) {
    XmodemState state = {
        .expected_block = 1,
        .retries = XMODEM_MAX_RETRIES,
        .started = false,
        .destination = 0,
        .remaining = 0,
    };
    uint8_t control;
    uint8_t received_block;
    uint16_t packet_size;

    if (io == 0 || packet_buffer == 0 || io->read_byte == 0 ||
        io->write_byte == 0 || io->store_byte == 0) {
        return false;
    }

    io->write_byte(io->context, XMODEM_CRC_REQUEST);

    for (;;) {
        if (!io->read_byte(io->context, &control)) {
            if (--state.retries == 0u) {
                break;
            }
            io->write_byte(
                io->context,
                state.started ? XMODEM_NAK : XMODEM_CRC_REQUEST
            );
            continue;
        }

        if (control == XMODEM_SOH || control == XMODEM_STX) {
            packet_size = control == XMODEM_SOH ? 128u : XMODEM_PACKET_BUFFER_SIZE;
            if (receive_packet(io, packet_buffer, packet_size, &received_block)) {
                const BlockResult result = process_packet(
                    io, &state, packet_buffer, packet_size, received_block
                );

                if (result == BLOCK_FATAL) {
                    break;
                }
                if (result == BLOCK_ACCEPTED) {
                    io->write_byte(io->context, XMODEM_ACK);
                    state.retries = XMODEM_MAX_RETRIES;
                    continue;
                }
            }

            if (--state.retries == 0u) {
                break;
            }
            io->write_byte(io->context, XMODEM_NAK);
            continue;
        }

        if (control == XMODEM_EOT) {
            if (!state.started || state.remaining != 0u) {
                break;
            }

            /* Classic XMODEM terminates with EOT / NAK / EOT / ACK. */
            io->write_byte(io->context, XMODEM_NAK);
            state.retries = XMODEM_MAX_RETRIES;
            for (;;) {
                if (!io->read_byte(io->context, &control)) {
                    if (--state.retries == 0u) {
                        cancel_transfer(io);
                        return false;
                    }
                    io->write_byte(io->context, XMODEM_NAK);
                    continue;
                }
                if (control == XMODEM_EOT) {
                    io->write_byte(io->context, XMODEM_ACK);
                    return true;
                }
                if (control == XMODEM_CAN) {
                    cancel_transfer(io);
                    return false;
                }
                /* Ignore noise while waiting for the final EOT. */
            }
        }

        if (control == XMODEM_CAN) {
            break;
        }

        if (--state.retries == 0u) {
            break;
        }
        io->write_byte(
            io->context,
            state.started ? XMODEM_NAK : XMODEM_CRC_REQUEST
        );
    }

    cancel_transfer(io);
    return false;
}
