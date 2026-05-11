# How to generate a PLL

## Using `ecppll`
The easiest way to generate the Verilog code for a PLL is to use the `ecppll` utility, which is part of the 
Project Trellis suite (installed alongside nextpnr).
Run this command in your terminal to generate a module that converts the 25 MHz input to a desired output (e.g., 50 MHz):
```Bash
ecppll -i 25 -o 50 --file pll.v
```
* -i: Input frequency (25 MHz for iCESugar Pro).
* -o: Desired output frequency (up to 3 clocks)
* --file: The filename for the generated Verilog wrapper.

To generate 2 output clocks
```Bash
ecppll -i 25 -o 16 -o 32 --file pll.v
```

## Details
When generating multiple clocks from a single PLL primitive, they are mathematically "locked" together because they share the same internal VCO (Voltage Controlled Oscillator).
* **The Constraint:** Both frequencies must be achievable by dividing down the same VCO frequency.
* **The Benefit:** The clocks will be phase-aligned, which is useful for high-speed interfaces like DDR memory or video signals.
