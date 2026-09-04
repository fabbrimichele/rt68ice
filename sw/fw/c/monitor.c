#include <stdbool.h>
#include <stdint.h>

#include "xmodem.h"

/* Memory-mapped devices.  UART registers must always use byte transfers. */
#define MMIO8(address) (*(volatile uint8_t *)(uintptr_t)(address))
#define MMIO16(address) (*(volatile uint16_t *)(uintptr_t)(address))
#define MMIO32(address) (*(volatile uint32_t *)(uintptr_t)(address))

#define UART_RBR 0x00F04000u
#define UART_IER 0x00F04002u
#define UART_LCR 0x00F04006u
#define UART_LSR 0x00F0400Au

#define VIDEO_IRQ_STATUS 0x00F0C002u
#define VIDEO_IRQ_ENABLE 0x00F0C004u
#define VIDEO_PALETTE 0x00F08000u

#define COUNTER 0x00F10000u

#define USB_IRQ_ENABLE 0x00F18002u
#define USB1_STATUS 0x00F18010u
#define USB2_STATUS 0x00F18030u
#define USB3_STATUS 0x00F18050u
#define USB4_STATUS 0x00F18070u

#define TIMER_CONTROL 0x00F1C000u
#define TIMER_STATUS 0x00F1C002u

#define VECTOR_BUS_ERROR 2u
#define VECTOR_ADDRESS_ERROR 3u
#define VECTOR_TRAP_14 46u

#define UART_LSR_DATA_READY 0x01u
#define UART_LSR_THR_EMPTY 0x20u
#define XMODEM_TIMEOUT_TICKS 763u
#define FRAMEBUFFER_START 0x00E00000u
#define FRAMEBUFFER_WORDS (76800u / 2u)
#define COMMAND_BUFFER_SIZE 80u

extern void monitor_bus_error_entry(void);
extern void monitor_trap14_entry(void);
extern void monitor_jump(uint32_t address) __attribute__((noreturn));

static char command_buffer[COMMAND_BUFFER_SIZE];

static const uint32_t default_palette[] = {
    0x00000000u, 0x000000AAu, 0x0000AA00u, 0x0000AAAAu,
    0x00AA0000u, 0x00AA00AAu, 0x00AA5500u, 0x00AAAAAAu,
    0x00555555u, 0x005555FFu, 0x0055FF55u, 0x0055FFFFu,
    0x00FF5555u, 0x00FF55FFu, 0x00FFFF55u, 0x00FFFFFFu,
};

static void uart_putc(uint8_t character) {
    while ((MMIO8(UART_LSR) & UART_LSR_THR_EMPTY) == 0u) {
    }
    MMIO8(UART_RBR) = character;
}

static void uart_puts(const char *string) {
    while (*string != '\0') {
        uart_putc((uint8_t)*string++);
    }
}

static uint8_t uart_getc(void) {
    while ((MMIO8(UART_LSR) & UART_LSR_DATA_READY) == 0u) {
    }
    return MMIO8(UART_RBR);
}

static void uart_init(void) {
    /* 25 MHz / (16 * 27) = 57,870 baud: 0.47% from 57,600. */
    MMIO8(UART_LCR) = 0x80u;
    MMIO8(UART_IER) = 0x00u;
    MMIO8(UART_RBR) = 27u;
    MMIO8(UART_LCR) = 0x03u;
    MMIO8(UART_IER) = 0x00u;
}

/*
 * The monitor owns the UART while it is active.  This prevents a previous
 * program's RX ISR from consuming an XMODEM byte before the polling receiver
 * can read it, and it makes handlers in overwritten SDRAM unreachable.
 */
static void quiesce_interrupt_sources(void) {
    uint16_t ignored;
    uint8_t drain_count;

    MMIO8(UART_IER) = 0u;
    for (drain_count = 0; drain_count < 16u; ++drain_count) {
        if ((MMIO8(UART_LSR) & UART_LSR_DATA_READY) == 0u) {
            break;
        }
        (void)MMIO8(UART_RBR);
    }

    MMIO16(VIDEO_IRQ_ENABLE) = 0u;
    ignored = MMIO16(VIDEO_IRQ_STATUS);
    (void)ignored;

    MMIO16(TIMER_CONTROL) = 0u;
    ignored = MMIO16(TIMER_STATUS);
    (void)ignored;

    MMIO16(USB_IRQ_ENABLE) = 0u;
    ignored = MMIO16(USB1_STATUS);
    ignored = MMIO16(USB2_STATUS);
    ignored = MMIO16(USB3_STATUS);
    ignored = MMIO16(USB4_STATUS);
    (void)ignored;
}

static void install_monitor_vectors(void) {
    volatile uint32_t *const vectors = (volatile uint32_t *)(uintptr_t)0u;

    vectors[VECTOR_BUS_ERROR] = (uint32_t)(uintptr_t)monitor_bus_error_entry;
    vectors[VECTOR_ADDRESS_ERROR] = (uint32_t)(uintptr_t)monitor_bus_error_entry;
    vectors[VECTOR_TRAP_14] = (uint32_t)(uintptr_t)monitor_trap14_entry;
}

static void init_palette(void) {
    uint8_t color;

    for (color = 0; color < 16u; ++color) {
        MMIO32(VIDEO_PALETTE + ((uint32_t)color * 4u)) = default_palette[color];
    }
}

static bool xmodem_read_byte(void *context, uint8_t *byte) {
    const uint16_t start = MMIO16(COUNTER);
    uint16_t elapsed;

    (void)context;
    while ((MMIO8(UART_LSR) & UART_LSR_DATA_READY) == 0u) {
        elapsed = (uint16_t)(MMIO16(COUNTER) - start);
        if (elapsed >= XMODEM_TIMEOUT_TICKS) {
            return false;
        }
    }
    *byte = MMIO8(UART_RBR);
    return true;
}

static void xmodem_write_byte(void *context, uint8_t byte) {
    (void)context;
    uart_putc(byte);
}

static void xmodem_store_byte(void *context, uint32_t address, uint8_t byte) {
    (void)context;
    MMIO8(address) = byte;
}

static bool matches_command(const char **cursor, const char *command) {
    const char *input = *cursor;

    while (*command != '\0') {
        if (*input != *command) {
            return false;
        }
        ++input;
        ++command;
    }
    *cursor = input;
    return true;
}

static bool consume_separator(const char **cursor) {
    const char *input = *cursor;

    if (*input != ' ') {
        return false;
    }
    do {
        ++input;
    } while (*input == ' ');
    *cursor = input;
    return true;
}

static bool has_only_trailing_spaces(const char *input) {
    while (*input == ' ') {
        ++input;
    }
    return *input == '\0';
}

static bool parse_hex(const char **cursor, uint32_t *value) {
    const char *input = *cursor;
    uint32_t result = 0;
    uint8_t digits = 0;
    uint8_t nibble;

    while (*input != '\0' && *input != ' ') {
        if (digits == 8u) {
            return false;
        }
        if (*input >= '0' && *input <= '9') {
            nibble = (uint8_t)(*input - '0');
        } else if (*input >= 'A' && *input <= 'F') {
            nibble = (uint8_t)(*input - 'A' + 10);
        } else if (*input >= 'a' && *input <= 'f') {
            nibble = (uint8_t)(*input - 'a' + 10);
        } else {
            return false;
        }
        result = (result << 4) | nibble;
        ++digits;
        ++input;
    }

    if (digits == 0u) {
        return false;
    }
    *cursor = input;
    *value = result;
    return true;
}

static void print_hex(uint32_t value, uint8_t digits) {
    static const char hex[] = "0123456789ABCDEF";
    uint8_t shift;

    while (digits != 0u) {
        shift = (uint8_t)((digits - 1u) * 4u);
        uart_putc((uint8_t)hex[(value >> shift) & 0x0fu]);
        --digits;
    }
}

static void dump_memory(uint32_t address) {
    uint8_t line;
    uint8_t cell;

    for (line = 0; line < 8u; ++line) {
        print_hex(address, 8u);
        uart_putc(':');
        for (cell = 0; cell < 8u; ++cell) {
            uart_putc(' ');
            print_hex(*(volatile uint16_t *)(uintptr_t)address, 4u);
            address += 2u;
        }
        uart_puts("\r\n");
    }
}

static void clear_framebuffer(void) {
    volatile uint16_t *framebuffer =
        (volatile uint16_t *)(uintptr_t)FRAMEBUFFER_START;
    uint32_t index;

    for (index = 0; index < FRAMEBUFFER_WORDS; ++index) {
        framebuffer[index] = 0u;
    }
}

static void handle_command(const char *line) {
    const char *cursor;
    uint32_t address;
    uint32_t value;

    cursor = line;
    if (matches_command(&cursor, "dump")) {
        if (consume_separator(&cursor) && parse_hex(&cursor, &address) &&
            has_only_trailing_spaces(cursor)) {
            dump_memory(address);
            return;
        }
    } else {
        cursor = line;
        if (matches_command(&cursor, "write")) {
            if (consume_separator(&cursor) && parse_hex(&cursor, &address) &&
                consume_separator(&cursor) && parse_hex(&cursor, &value) &&
                has_only_trailing_spaces(cursor)) {
                *(volatile uint16_t *)(uintptr_t)address = (uint16_t)value;
                return;
            }
        } else {
            cursor = line;
            if (matches_command(&cursor, "help") && has_only_trailing_spaces(cursor)) {
                uart_puts(
                    "dump  <ADDR>       - Dump from ADDR (HEX)\r\n"
                    "write <ADDR> <VAL> - Write to ADDR (HEX) the VALUE (HEX)\r\n"
                    "load               - Load an XMODEM-CRC image\r\n"
                    "run   <ADDR>       - Run program at ADDR (HEX)\r\n"
                    "fbclr              - Clear framebuffer\r\n"
                    "help               - Print this list of commands\r\n"
                );
                return;
            }

            cursor = line;
            if (matches_command(&cursor, "load") && has_only_trailing_spaces(cursor)) {
                const XmodemIo io = {
                    .read_byte = xmodem_read_byte,
                    .write_byte = xmodem_write_byte,
                    .store_byte = xmodem_store_byte,
                    .context = 0,
                };

                uart_puts("Loading...\r\n");
                /* Packet staging remains in SDRAM below the app load area. */
                if (xmodem_receive(&io, (uint8_t *)(uintptr_t)0x00008000u)) {
                    uart_puts("Done.\r\n");
                } else {
                    uart_puts("Load failed.\r\n");
                }
                return;
            }

            cursor = line;
            if (matches_command(&cursor, "run") && consume_separator(&cursor) &&
                parse_hex(&cursor, &address) && has_only_trailing_spaces(cursor)) {
                monitor_jump(address);
            }

            cursor = line;
            if (matches_command(&cursor, "fbclr") && has_only_trailing_spaces(cursor)) {
                clear_framebuffer();
                return;
            }
        }
    }

    uart_puts("Error: Unknown command or syntax\r\n");
}

static void command_loop(void) __attribute__((noreturn));

static void command_loop(void) {
    uint8_t character;
    uint8_t length;
    bool full;

    for (;;) {
        uart_puts("\r\n>");
        length = 0;
        full = false;

        for (;;) {
            character = uart_getc();
            if (character == '\r') {
                command_buffer[length] = '\0';
                uart_puts("\r\n");
                break;
            }
            if (character == 0x08u || character == 0x7fu) {
                if (length != 0u) {
                    --length;
                    uart_puts("\b \b");
                }
                full = false;
                continue;
            }
            if (length == COMMAND_BUFFER_SIZE - 1u) {
                if (!full) {
                    uart_putc(0x07u);
                    full = true;
                }
                continue;
            }
            uart_putc(character);
            command_buffer[length++] = (char)character;
        }

        handle_command(command_buffer);
    }
}

static void monitor_initialize(void) {
    quiesce_interrupt_sources();
    install_monitor_vectors();
    init_palette();
    uart_init();
}

void monitor_main(void) __attribute__((noreturn));

void monitor_main(void) {
    monitor_initialize();
    uart_puts("RT68F Monitor v0.2\r\n");
    command_loop();
}

void monitor_bus_error(void) __attribute__((noreturn));

void monitor_bus_error(void) {
    monitor_initialize();
    uart_puts("Bus Error!\r\n");
    command_loop();
}
