#ifndef RT68ICE_XMODEM_H
#define RT68ICE_XMODEM_H

#include <stdbool.h>
#include <stdint.h>

/*
 * Small, platform-independent XMODEM-CRC receiver.
 *
 * The monitor provides the callbacks below: read_byte must return false after
 * its own receive timeout, and store_byte writes a validated payload byte to
 * the requested address.  Keeping timing and MMIO outside this module makes
 * the protocol easy to exercise on the host.
 */

#define XMODEM_PACKET_BUFFER_SIZE 1024u
#define XMODEM_LOAD_MIN 0x00010000u
#define XMODEM_LOAD_END 0x00800000u /* exclusive */

typedef struct {
    bool (*read_byte)(void *context, uint8_t *byte);
    void (*write_byte)(void *context, uint8_t byte);
    void (*store_byte)(void *context, uint32_t address, uint8_t byte);
    void *context;
} XmodemIo;

/*
 * Receive one image in the monitor's existing wire format:
 * four big-endian address bytes, four big-endian payload-length bytes, then
 * the payload.  Both 128-byte and 1 KiB XMODEM-CRC packets are accepted.
 */
bool xmodem_receive(const XmodemIo *io, uint8_t *packet_buffer);

#endif
