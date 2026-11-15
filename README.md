# rt68ice
RT68 IceSugar Pro version

## SDRAM cache

This branch includes an 8 KiB unified direct-mapped cache with 1,024 four-word
lines, SDRAM burst read fills, and single-word write-through with whole-line
invalidation. Requests and responses remain registered at 25/50 MHz system/SDRAM
clocks. SDRAM diagnostics, three YAARTTT passes and four CoreMark runs passed.
CoreMark reports about 12.58 iterations/s versus 11.01 with the registered
2 KiB cache and 7.01 uncached. See [hardware validation](doc/validation.md#four-word-burst-cache-validation-2026-11-15)
for the capture-timing correction, comparisons and remaining checks. The earlier
[registered-interface report](doc/cache-registered-interface.md) describes the
one-word baseline; its timing figures do not sign off this wider cache.

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
