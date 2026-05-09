# rt68ice
RT68 IceSugar Pro version

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