# TODOs

## Completed features

- **Keyboard-triggered reset (2026-11-24).** Implemented and merged into
  emutos-ice `master`; the user confirmed it works on the board. The reset
  path waits for Ctrl, Alt, right Shift and Delete to be released, preserving
  normal boot options. Temporary welcome overrides were removed: warm reset
  has no welcome delay; cold reset uses the standard three-second countdown.
  No FPGA change is required. See the
  [EmuTOS reset documentation](https://github.com/fabbrimichele/emutos-ice/blob/master/readme-rt68ice.md#reset-button-and-boot-switches).

- **DS3231 integration (2026-11-28).** The generic I²C FPGA interface and
  matching EmuTOS RTC driver are merged into their default branches.
  Standalone date/time read/write, temperature and battery-retention tests
  passed; the user also confirmed EmuTOS boot-time reads, clock setting and
  reset/power-cycle checks. Wiring uses VCC, GND, SCL and SDA. Alarms/SQW,
  32K output, an EmuTOS temperature API and the module's EEPROM remain deferred.
  See the [module reference and validation results](ds3231.md).

## Planned features

Suggested order; keep each feature in a separate branch.

1. **Add MIDI IN/OUT.** Implement the appropriate DIN electrical interfaces
   (including opto-isolated input), UART operation at 31,250 baud and EmuTOS
   MIDI BIOS support. Start with standalone loopback and sustained-traffic
   tests. Add an RX FIFO only if testing demonstrates a need; direct Atari
   MIDI ACIA compatibility is separate follow-up work.
2. **Evaluate 68020 mode.** Verify TG68K's supported instructions and exception
   behavior, compare synthesis resources with 68000 mode, then check routed
   timing and run compatibility/performance tests. Keep the validated 68000
   configuration available; the external data bus remains 16 bits.
3. **Test FreeMiNT.** Start with a minimal 68000-compatible setup using the
   existing EmuTOS services, without Atari-specific hardware drivers. Verify
   machine detection, timer scheduling, keyboard, storage and memory handling.
   This can be attempted before 68020 mode; MMU memory protection is unavailable.
4. **Add chunky video and port Doom.** Add a 320x240, 8-bit indexed-colour
   mode using the existing FPGA framebuffer. Start with a silent doomgeneric
   EmuTOS port, WAD loading, keyboard input, palette updates and a 320x200 game
   image. Measure a repeatable timedemo before choosing optimizations; neither
   a particular frame rate nor 68020 speedup is established yet.
5. **Optionally move VRAM to SDRAM.** Add SDRAM arbitration, scanout buffering,
   clock-domain handling and a policy ensuring video sees completed CPU writes.
   Validate one fixed mode before adding programmable framebuffer addresses
   and switching buffers at vertical blank. Preserve the FPGA framebuffer as
   a bring-up fallback where resources permit. Double buffering needs software
   support; it is not automatically enabled for the desktop.

## Engineering work

- Investigate the consistently longer FPGA builds after the 32 MiB expansion.
  Compare synthesis, placement and routing times, resource usage and routing
  iterations against the previous design with identical tool settings/seeds.
- Complete timing and clock-domain-crossing checks, particularly the related
  CPU/SDRAM clocks and external SDRAM interface. Passing per-clock frequency
  reports alone do not establish complete timing/CDC sign-off.

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