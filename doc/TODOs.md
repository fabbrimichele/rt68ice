# TODOs

## Build
- Give ecpdap a try, it should be much faster than openFPGALoader (it's not faster)
## Monitor
- In case of bus error, print all registers (DONE)




## SystemVerilog how to

Option 1: Try the Yosys SystemVerilog flag
Keep the file as .sv (or .v, it doesn't matter as long as the flag is present) and tell Yosys to explicitly parse it as SystemVerilog using the -sv flag in your Makefile:

Makefile
rt68ice.json: rt68ice.v hw/verilog/sdram.sv
yosys -p 'read_verilog -sv hw/verilog/sdram.sv; read_verilog rt68ice.v; synth_ecp5 -top TopLevelName -json rt68ice.json'
Note: Depending on your exact Yosys version, the built-in -sv parser can sometimes still struggle with typedef struct when passing them through functions. If it works, great. If it throws a syntax error on the struct, move to Option 2.

Option 2: Use sv2v (The Robust Open-Source Route)
If Yosys fails to parse the file, the standard practice in the open-source FPGA toolchain is to use a pre-processor to convert SystemVerilog down to Verilog-2001 before handing it to Yosys.

You can grab the sv2v tool (usually available via standard package managers or as a pre-compiled binary for Linux) and update your Makefile to perform the translation on the fly:

Makefile
## Convert SV to V, then synthesize
```
rt68ice.json: rt68ice.v hw/verilog/sdram.sv
sv2v hw/verilog/sdram.sv > hw/verilog/sdram_converted.v
yosys -p 'read_verilog hw/verilog/sdram_converted.v rt68ice.v; synth_ecp5 -top TopLevelName -json rt68ice.json'
```
If you go this route, you would leave addRTLPath("hw/verilog/sdram.v") pointing to the converted .v file in your SpinalHDL blackbox definition, or simply let SpinalHDL generate the wrapper and handle the converted file exclusively in the Makefile.

### Converted to SpinalHDL
I converted the sdram module from system verilog to SpinalHDL using codex, and it worked.