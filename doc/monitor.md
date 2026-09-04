# ROM monitor

The ROM monitor is a freestanding C program.  Its tiny assembly companion is
limited to reset and exception entry plus the non-returning jump used by the
`run` command.

| Source | Responsibility |
| --- | --- |
| `sw/fw/c/monitor.c` | Commands, UART/MMIO access, monitor startup and interrupt cleanup |
| `sw/fw/c/xmodem.c` | Platform-independent XMODEM-CRC receiver |
| `sw/fw/asm/monitor_start.asm` | Reset vector, `trap #14` entry, bus-error entry and `run` jump |

`sw/fw/asm/monitor.asm` is retained as the prior assembly implementation for
reference only; it is no longer part of the ROM build.

## Building and testing

The monitor build needs an ELF bare-metal 68000 GCC cross compiler.  The
default is `m68k-elf-gcc`; override it when your toolchain has another prefix:

```sh
make monitor
make monitor-size
make M68K_CC=m68k-unknown-elf-gcc monitor
```

The protocol test does not need the cross compiler, a board, or the FPGA tool
chain:

```sh
make test-xmodem
```

It tests 1 KiB and 128-byte packets, a corrupt-CRC retransmission, a duplicated
packet after a lost ACK, and rejection of an invalid image header.

## XMODEM contract

The receiver is intentionally compatible with `tools/serial_load.py` and the
previous monitor:

- It initiates CRC mode by sending `C` and accepts `SOH` (128-byte) and `STX`
  (1024-byte) packets.
- CRC is CRC-16/XMODEM: polynomial `0x1021`, initial value `0`, transmitted
  most-significant byte first.
- A valid duplicate of the preceding packet is ACKed without being copied
  twice.  Invalid packets are NAKed up to ten times.
- Completion is the classic `EOT`, `NAK`, `EOT`, `ACK` exchange.  A failure
  sends `CAN`, `CAN`.
- The first eight validated data bytes are a big-endian load address and a
  big-endian payload length.  Payload is limited to the half-open range
  `0x00010000..0x00800000` and is copied only after a packet passes its CRC.

The target-specific two-second UART timeout is the `xmodem_read_byte` callback
in `monitor.c`; the protocol code itself has no knowledge of registers or
timers.  Keeping this boundary means protocol changes can be covered by the
host test first.

## Interrupt safety

Reset and `trap #14` entry mask all CPU interrupts before entering C.  Monitor
initialisation disables and acknowledges UART, video, timer, and USB sources.
This prevents application RX handlers from consuming loader bytes and prevents
an interrupt from executing old application code while a new image overwrites
SDRAM.
