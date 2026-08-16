# How to generate a PLL
See also:
- [blog.dave - ecp5-pll](https://blog.dave.tf/post/ecp5-pll/)

## Using `ecppll`
The easiest way to generate the Verilog code for a PLL is to use the `ecppll` utility, which is part of the 
Project Trellis suite (installed alongside nextpnr).
Run this command in your terminal to generate a module that converts the 25 MHz input to a desired output (e.g., 50 MHz):
```Bash
ecppll -i 25 -o 50 --file pll1.v
```
* -i: Input frequency (25 MHz for iCESugar Pro).
* -o: Desired output frequency (up to 3 clocks)
* --file: The filename for the generated Verilog wrapper.

To generate multiple output clocks with names (up to `--clkout3`):
```Bash
ecppll -i 25 \
  --clkin_name clkin_25 \
  --clkout0 125 --clkout0_name clk_hdmi \
  --clkout1 25 --clkout1_name clk_vga \
  --clkout2 28 --clkout2_name clk_cpu \
  --clkout3 56 --clkout3_name clk_sdram --phase3 180 \
  -f hw/verilog/pll1.v
```
**Note:** Order matters. Put the highest frequency on `clkout0` to force the internal math solver to scale up the VCO.

To generate with phase rotation:
```Bash
ecppll --clkin 25 --clkin_name clkin25 \
       --clkout0 8 --clkout0_name clk_8_cpu \
       --clkout1 96 --clkout1_name clk_96_sdram --phase2 90 \
       --file pll1.v
```
For more details:
```Bash
ecppll -h
```

### Details
When generating multiple clocks from a single PLL primitive, they are mathematically "locked" together because they share 
the same internal VCO (Voltage Controlled Oscillator).
* **The Constraint:** Both frequencies must be achievable by dividing down the same VCO frequency.
* **The Benefit:** The clocks will be phase-aligned, which is useful for high-speed interfaces like DDR memory or video signals.


## Commands for project
The followings are the command used to generate the PLL for the project

### Video PLL
It provides the HDMI and VGA clocks:
```Bash
ecppll -i 25 --clkin_name clkin_25 \
  --clkout0 125 --clkout0_name clk_hdmi \
  --clkout1 25 --clkout1_name clk_vga \
  -f hw/verilog/pll1.v
```

### CPU, SDRAM and USB PLL
It provides phase-related 25 MHz CPU and 50 MHz SDRAM clocks, plus the precise
12 MHz USB clock:
```Bash
ecppll -i 25 --clkin_name clkin_25 \
  --clkout0 25 --clkout0_name clk_cpu \
  --clkout1 50 --clkout1_name clk_sdram --phase3 180 \
  --clkout2 12 --clkout2_name clk_usb \
  -f hw/verilog/pll2.v
```

The two generated PLL instances are combined in `hw/verilog/pll.v`.
