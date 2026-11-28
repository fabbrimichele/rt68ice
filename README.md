# rt68ice
RT68 IceSugar Pro version

## SDRAM cache

The design includes an 8 KiB unified direct-mapped cache with 1,024 four-word
lines and SDRAM burst read fills. Writes are write-through with no write
allocation: a write hit updates the selected cached word or byte(s), while a
write miss preserves existing lines. A single-entry posted-write buffer
releases the CPU after the cache lookup/update while SDRAM finishes the write.
Cache read hits can proceed; another write or a read miss waits for the entry
to drain. Requests and responses remain registered at 25/50 MHz system/SDRAM
clocks, and SDRAM inputs use dedicated IOLOGIC registers.

The posted-write implementation completed six clean YAARTTT passes in 23:51
on a retry and three validated CoreMark runs averaging **15.170670 iterations/s**.
That is **6.0%** higher CoreMark throughput than the preceding unbuffered
write-through cache and **116.3%** above uncached (**2.16x**). YAART's first
four passes are **8.6% faster**. One earlier run rebooted during pass five;
the cause remains unresolved. See the
[complete performance progression](doc/validation.md#cache-performance-progression)
and [posted-write validation](doc/validation.md#single-entry-posted-write-buffer-validation-2026-11-18).
Hardware tests do not establish complete external timing closure.

[Cache profiling registers and CACHSTAT.TOS](doc/cache-stats.md) provide atomic
snapshots of hits, misses, writes, elapsed time and read/write stall cycles.
The write RAM-wait counter has a documented limitation with posted writes;
it does not measure background write duration.

## RTC

The [DS1302 RTC](doc/ds1302.md) interface has been replaced with generic I²C
for the DS3231. The matching RTC driver is merged into emutos-ice `master`
([PR #20](https://github.com/fabbrimichele/emutos-ice/pull/20)); boot-time reads,
clock setting and reset/power-cycle retention were confirmed on hardware on
2026-11-28. See the [module reference, wiring, assembly diagnostics and
vendor images](doc/ds3231.md). The standalone diagnostic and setter use the
monitor UART. Older DS1302 EmuTOS images are incompatible with this interface.

## Reset button

The active-low reset button must remain pressed for 10 ms after synchronization
before resetting the system. Reset remains asserted while held and for 20 ms
after release. Power-on reset and PLL-lock reset requests remain independent
of button qualification. See [reset timing and tests](doc/reset.md).

## Memory Layout
See the generated [System Memory Layout](./doc/memory_layout.md).

The device block has moved above 32 MiB: framebuffer `$02000000`, I/O
`$02100000`, and boot ROM `$021C0000`, preserving relative register offsets.
SDRAM now covers `$00000000–$01FFFFFF` (32 MiB), with the boot-vector and
1 KiB fast-RAM overlays preserved. The standalone full-range RAM, alias and
boundary tests passed on the board; see [SDRAM validation](doc/sdram.md#standalone-32-mib-ram-test).
The companion emutos-ice image now loads at `$01F80000`, reserving the top
512 KiB and configuring 31.5 MiB contiguous ST-RAM. The monitor accepts images
through the 32 MiB SDRAM limit; this requires the matching rebuilt bitstream.
Board testing confirmed the new monitor/image boots with 31.5 MiB ST-RAM;
YAARTTT completed six full expanded-range passes with zero errors, and one
CoreMark run validated at 15.180265 iterations/sec. Images and external utilities using the
old device addresses are incompatible with this map.
See [device relocation and migration](doc/memory-map-relocation.md).

The [CPU upper-address regression](doc/cpu-upper-address.md) verifies accesses
with bits 24 and 25 set in the real core's 68000 mode, including instruction
fetches above 32 MiB. It is groundwork for a larger memory map; the production
SDRAM window remains unchanged.

See [onboard SPI flash](./doc/spi_flash.md) for the SD/flash device ports,
flash pin assignments, and SPI register selection.

## How to Install
1. **Install OSS CAD Suite:** follow the official [installation instructions](https://github.com/yosyshq/oss-cad-suite-build#installation).
2. **Configure openFPGALoader:** (Refer to the [install guide](https://trabucayre.github.io/openFPGALoader/guide/install.html#udev-rules) for more details)
    1. Download [99-openfpgaloader.rules](https://github.com/trabucayre/openFPGALoader/blob/master/99-openfpgaloader.rules) and save it to `/etc/udev/rules.d`
    2. Append the following lines to `/etc/udev/rules.d/99-openfpgaloader.rules` (see [issue #398](https://github.com/trabucayre/openFPGALoader/issues/398#issue-1962212397))
       ```sh
       # IceSugar-Pro
       ATTRS{idVendor}=="1d50", ATTRS{idProduct}=="602b", MODE="664", GROUP="plugdev", TAG+="uaccess"```
       ```
    3. Unplug and plug the board.
3. **Test the Board:** Run the following command to verify the connection:
   ```sh
    $ openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b --detect
    ```

## How to Build
Depending on your Linux configuration you might need to run `enable-oss-cad.sh` to set
the OSS CAD environment variables.

To run the testbench:
```
sbt "runMain rt68ice.memory.DirectMappedCacheSim"
```
To generate the Verilog:
```sh
sbt "runMain rt68ice.Rt68IceTopLevelVerilog"
```
To generate the bit stream:
```sh
make
```
To load to the FPGA the bit stream:
```sh
make prog
```
To load to the FLASH (permanent) the bit stream:
```sh
make prog-flash
```
To view the simulation wave form:
1. Run the simulation
2. ```
   make view-wave
   ```
