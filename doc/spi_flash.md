# Onboard SPI flash

The SPI controller at CPU address `$F20000` has two device ports:

| Port | Device | Chip-select | MOSI | MISO | Clock |
| --- | --- | --- | --- | --- | --- |
| 0 | PMOD SD card | C8 | D8 | D7 | C7 |
| 1 | Onboard W25Q256JV flash (32 MiB) | N8 | T8 | T7 | Dedicated MCLK through `USRMCLK` |

Flash pin assignments follow the [iCESugar Pro platform definition](https://github.com/litex-hub/litex-boards/blob/master/litex_boards/platforms/muselab_icesugar_pro.py).
The configuration SPI ports are disabled in user mode so MOSI, MISO and CS
are available to the application. `USRMCLK` drives the dedicated flash clock
while port 1 is selected and releases it while flash CS is high. There is no
ordinary `flash_clk` top-level port or clock pin constraint.

Both physical chip-select outputs reset high. The SPI device index resets to
zero, with selection disabled. The controller shares its clock generator and
transfer settings between devices; firmware must finish and deselect one
device before selecting another and set the appropriate transfer configuration.

The CPU accesses the controller's registers using low-byte accesses:

| Address | Register |
| --- | --- |
| `$F20001` | Data low byte |
| `$F20003` | Data high byte |
| `$F20005` | Command/status |
| `$F20007` | Transfer length and clock divider |

Command bits 6:4 select the port, bit 1 asserts chip select, and bit 0 starts a
transfer. With interrupts disabled in the SPI controller, use:

| Operation | SD (port 0) | Flash (port 1) |
| --- | --- | --- |
| Assert CS | `$02` | `$12` |
| Transfer with CS held low | `$03` | `$13` |
| Deassert CS | `$00` | `$10` |

Read status bit 0 to wait until a transfer finishes. Keep CS asserted across
the flash command, address and data bytes. The existing EmuTOS SD driver selects
port 0; flash firmware must explicitly select port 1 for every transfer.

Application images occupy a separate flash region from the FPGA configuration
bitstream. The monitor uses a fixed image offset of `$00100000` (1 MiB).
Reserve the region below this offset for configuration; any FPGA bitstream
written there must fit without overlapping the application image.

## Monitor image loading

The monitor provides two commands, neither taking an address argument:

```text
loadflash
boot
```

`loadflash` reads and verifies the image at flash offset `$00100000`, copies
its payload to the SDRAM address in its header, and prints that address. It
does not run the image. For EmuTOS, subsequently use `run 00D80000`.
`boot` performs the same load and verification, then jumps to the header's
load address only if successful. There is no automatic boot countdown.

Store the complete **`emutos-rt68ice.img`**, including its 16-byte `RT68`
header, at this offset, not raw `emutos.img`. The serial `load` command and
both flash commands share header parsing, CRC-32/ISO-HDLC checking and range
validation. Images require a nonzero payload length and an even load address;
the complete payload must fit in application SDRAM (`$00010000` through
`$00DFFFFF`). Flash lengths are also checked against the 32 MiB capacity.
SPI timeouts, busy flash, invalid headers/ranges and CRC failures return to the
monitor without executing the image. A failed CRC can leave partial/untrusted
data in SDRAM; do not manually run that data.

Flash loading uses read-only SPI commands at about 195 kHz and releases chip
select and the dedicated clock before returning or jumping. At this speed,
loading a roughly 230 KiB EmuTOS image takes at least about ten seconds.
The monitor does not program flash. `make prog` only loads the FPGA into SRAM,
so it does not install EmuTOS at the application offset. Installing that image
requires a separate, offset-aware flash programming operation that preserves
the configuration region. From the repository root, close the serial terminal
and run:

```sh
make prog-emutos
make prog
make serial-open
```

`prog-emutos` writes `../emutos-ice/emutos-rt68ice.img` at 1 MiB and verifies
the write. It overwrites the application image and affected erase sectors, not
the bitstream region below 1 MiB. It does not rebuild EmuTOS. To select another
headered image file, use `make prog-emutos EMUTOS_IMAGE=/path/to/image.img`.
`make prog` then restores the updated FPGA design into SRAM. At the monitor
prompt, use `loadflash` or `boot`.
Do not use the ordinary bitstream `prog-flash` target on the EmuTOS image.

### Board checks

After building and loading the updated FPGA bitstream:

- With no image at 1 MiB, `loadflash` and `boot` must report an invalid magic
  (or another read error) and remain at the prompt.
- With the headered image installed, `loadflash` must report `Done.` and
  `Loaded at 00D80000`; `boot` must start EmuTOS.
- Repeat serial loading of an existing monitor application to check that the
  UART path remains functional.
- For a negative CRC test, use a copy of the application image with one payload
  byte changed but the original header retained. Install only in the application
  region: both commands must report a CRC mismatch and must not execute it.

## Verification

`make rt68ice.bit` elaborates, synthesizes, routes and packs the design,
including the dedicated `USRMCLK` primitive and the three flash pin constraints.
Run the reset/selection regression from the repository root:

```sh
spi_test_dir=$(mktemp -d)
ghdl -a --std=08 -fsynopsys --workdir="$spi_test_dir" \
  hw/vhdl/spi_master.vhd hw/vhdl/tests/spi_master_reset_tb.vhd
ghdl -e --std=08 -fsynopsys --workdir="$spi_test_dir" spi_master_reset_tb
ghdl -r --std=08 -fsynopsys --workdir="$spi_test_dir" \
  spi_master_reset_tb --assert-level=error --stop-time=1us
```

### Read-only board test

`sw/app/asm/flash_smoke.asm` reads the JEDEC ID (`9F`), status register 1
(`05`), and the first 32 flash bytes twice using the dedicated four-byte read
command (`13`). It prints the bytes, checks the expected ID (`EF4019` or
`EF7019`), and compares the two reads. Transactions have bounded timeouts;
both devices are deselected before returning to the monitor.

It never enables writes, programs, erases, resets the flash, or changes its
address mode. Command details and both ID variants are specified in the
[Winbond W25Q256JV datasheet](https://e2e.ti.com/cfs-file/__key/communityserver-discussions-components-files/908/W25Q256JV-SPI-RevQ-02072025-Plus.pdf).
Run it from the ROM monitor, not from EmuTOS: it temporarily masks CPU
interrupts and configures the shared SPI controller for 8-bit transfers at
about 195 kHz. The controller retains that configuration afterward.

```sh
make target/app/flash_smoke.bin
make prog
make serial-load BIN=flash_smoke.bin RUN=0
make serial-open
```

Close any existing serial terminal before loading. `make prog` loads the
previously built two-port FPGA bitstream into SRAM; it does **not** write flash.
At the monitor prompt, enter:

```text
run 00010000
```

Expected output includes a JEDEC ID, status, byte dump and `PASS`. A matching
read is a basic communication/consistency check, not verification of the
whole stored bitstream. Erased bytes (`FF`) can be legitimate; loading a new
FPGA bitstream into SRAM does not update the bytes stored in flash. A timeout
or ID of all zeroes/ones points to a controller, pin, clock or flash-mode issue.
Actual flash transactions still need board validation; the build and reset
regression do not model the physical flash.
