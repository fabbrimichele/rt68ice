# rt68ice
RT68 IceSugar Pro version

## SDRAM cache

The design includes an 8 KiB unified direct-mapped cache with 1,024 four-word
lines and SDRAM burst read fills. Writes are write-through with no write
allocation: a write hit updates the selected cached word or byte(s), while a
write miss preserves existing lines. The CPU still waits for the SDRAM write
to complete. Requests and responses remain registered at 25/50 MHz
system/SDRAM clocks, and SDRAM inputs use dedicated IOLOGIC registers.

The current implementation completed four clean YAARTTT passes and four
validated CoreMark runs at **14.316391 iterations/s**, versus 12.578616 with
write-hit invalidation and 7.013069 uncached. These are about **13.8%** and
**104.1%** higher CoreMark throughput respectively. See
[write-hit update validation](doc/validation.md#write-hit-update-validation-2026-11-18)
for source provenance, the earlier implementation's results and remaining
coverage. Hardware tests do not establish complete external timing closure.

[Cache profiling registers and CACHSTAT.TOS](doc/cache-stats.md) provide atomic
snapshots of hits, misses, writes, elapsed time and read/write stall cycles.

## Memory Layout
See the generated [System Memory Layout](./doc/memory_layout.md).

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
sbt "runMain playground.BlinkSim"
```
To generate the Verilog:
```sh
make hw/gen/Blink.v
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
