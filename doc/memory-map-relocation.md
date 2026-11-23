# Device relocation above 32 MiB

This step prepares a contiguous 32 MiB SDRAM window by moving the existing
`$00E00000`-based device block to `$02000000`. Every device address increases
by `$01200000`; the relative order, register offsets and decoded sizes stay
the same. The generated [memory layout](memory_layout.md) lists all windows.

| Device | Old base | New base |
| --- | ---: | ---: |
| Framebuffer | `$00E00000` | `$02000000` |
| LED | `$00F00000` | `$02100000` |
| UART | `$00F04000` | `$02104000` |
| Palette | `$00F08000` | `$02108000` |
| Video control | `$00F0C000` | `$0210C000` |
| System counter | `$00F10000` | `$02110000` |
| LED array | `$00F14000` | `$02114000` |
| USB | `$00F18000` | `$02118000` |
| Timer | `$00F1C000` | `$0211C000` |
| SPI | `$00F20000` | `$02120000` |
| PSG | `$00F24000` | `$02124000` |
| RTC | `$00F28000` | `$02128000` |
| DIP switches | `$00F2C000` | `$0212C000` |
| Cache statistics | `$00F30000` | `$02130000` |
| Boot ROM | `$00FC0000` | `$021C0000` |

## Scope and software migration

The first eight reset-vector bytes still read from ROM. The 1 KiB fast-RAM
overlay, interrupt-acknowledge space and interrupt levels are unchanged.
The subsequent expansion maps SDRAM through `$01FFFFFF`, including the former
device windows. There are no legacy device aliases: those addresses now access
RAM. Cache tags include CPU bit 24, and SDRAM receives all 24 word-address bits.

The rt68ice monitor, other ROM examples, assembly register definitions and
application/firmware linker scripts use the new addresses. Framebuffer symbols
such as `_fb_start` now resolve to `$02000000`; the monitor reset PC points
into the relocated ROM. The monitor still accepts images only within the
existing SDRAM application range, ending at `$00E00000`; neither that loader
limit nor the EmuTOS image load address `$00D80000` is a device mapping.
The unrelated serial-flash offsets/capacity and RGB colour constants do not
change. Makefile dependencies include the linker maps so address-only edits
cannot silently leave stale firmware/applications.

Rebuild rt68ice firmware/apps and the FPGA bitstream together. The existing
emutos-ice port still has the old VRAM and MMIO constants and must be updated
as the next step before testing EmuTOS on this bitstream. No emutos-ice or
emutos-apps source changes are included here. External binaries that access
MMIO directly, including CACHSTAT.TOS from emutos-apps, also need a matching
address update/rebuild. Prior validation logs retain the addresses used by
the versions they describe.

## Verification

```sh
make rom apps
sbt 'runMain rt68ice.core.MemoryMapSim' \
    'runMain rt68ice.core.BusControllerSim' \
    'runMain rt68ice.audio.Ym2149DeviceSim' \
    'runMain rt68ice.core.M68KUpperAddressSim' \
    'runMain rt68ice.Rt68IceTopLevelVerilog'
make rt68ice.bit
```

The decoder regression checks each new window at its first/last byte and word,
unique selection, read-data routing, full downstream address/data/strobes,
write direction, rejected old addresses, bit-25-sensitive decoding, gaps,
reset-vector overlay, unchanged SDRAM boundaries and RTC fetch qualification.
The audio harness supplies idle responses for the cache-statistics port too.
The real CPU regression separately verifies bits 24/25, high instruction
fetches and both address-truncation negative controls.

Local verification passed:

- All 23 assembly applications and 6 ROM programs rebuilt with `make rom apps`.
- The monitor image is 3,216 bytes, fits the 4 KiB ROM and has reset vectors
  SP `$00003F00`, PC `$021C0008`. Link symbols confirm the relocated framebuffer,
  UART, DIP-switch, SPI and video-control addresses.
- `MemoryMapSim`, `BusControllerSim`, `Ym2149DeviceSim`, the two normal CPU
  upper-address runs and both negative controls passed.
- Integrated Verilog generation and `make rt68ice.bit` completed successfully.
  Final routed per-clock checks pass, including 28.76 MHz achieved for the
  25 MHz system clock. This is not full external SDRAM/related-clock signoff.

Built bitstream SHA-256:

```text
4d93f5ada355c17f63f421c9b99e7159fc90e6eb763af1ce6647e1d8573a9ef9
```

On-board checks reported by the user on 2026-11-22 passed with the relocated
map: serial input/output, the standalone USB keyboard test, all existing SDRAM
diagnostics, the 640x480 monochrome / 320x240 four-bitplane / 640x480 two-bitplane
video examples, and the VBL interrupt LED-counter test. These SDRAM diagnostics
still cover the existing window, not 32 MiB. Exact command logs and loaded
binary hashes were not supplied. The matching EmuTOS port remains the next
integration step; SD/flash, RTC, timer and audio board checks were not separately
confirmed in these reports.
