# CPU upper-address verification

Run the real TG68K core in **68000 mode** through the production SpinalHDL
`M68K` wrapper, with a bare-metal test program and a small memory model:

```sh
sbt 'runMain rt68ice.core.M68KUpperAddressSim'
```

Dependencies: the normal sbt/SpinalHDL environment, Yosys with its GHDL plugin,
Icarus Verilog (`iverilog` and `vvp`), and `m68k-atari-mint-as` /
`m68k-atari-mint-ld`. No EmuTOS image or FPGA is needed.

## What runs

The runner generates the production wrapper with `M68KType.M68000` (`CPU=00`)
and merges its actual VHDL dependencies. The GHDL/Yosys frontend used by the
FPGA build translates that core to Verilog for Icarus. This includes the real
CPU implementation, not a black-box stub or a model of the instruction set.

`hw/sim/M68KUpperAddressProgram.S` is assembled with `-m68000`, linked at zero
as a raw binary, and loaded into the test ROM. It supplies reset vectors and
executes checks on the CPU, branching to a failure marker if a value differs.
The Verilog testbench models memory by the full byte address and independently
checks the final contents, bus activity and pass marker. A cycle limit rejects
hangs and missing completion.

The checked operations are:

- Distinct word writes/readbacks at `$00010000` and `$01010000`, differing only
  in address bit 24; a separate word near the top of 32 MiB at `$01FFFFF0`.
- Upper- and lower-byte writes at low/high addresses, preserving the other byte.
- Distinct longwords at low/high/top addresses, with both 16-bit halves checked.
- A longword beginning at `$00FFFFFE`, crossing into `$01000000` between halves.
- A word at `$01FFFFFE`, the last aligned word below the 32 MiB boundary.
- A jump to code above 16 MiB and actual high-address instruction fetches
  before the CPU reports success.

The ROM model supplies the same code image at low and high execution addresses;
that deliberate ROM mapping is separate from the independently stored data
regions. A bus observer requires instruction fetches with bit 24 set.

The normal test runs twice: with CPU clock enable always high, then with three
paused clocks between enabled clocks. This exercises the core's clock-enable
input; it is not a simulation of the full bus controller or SDRAM handshake.

A third run is a negative control: the memory model deliberately masks addresses
to 24 bits. The CPU program must detect the resulting low/high alias and report
failure. The runner only accepts that specific failure and reports the negative
control as passed; a timeout or unrelated failure does not count as success.

## Recorded result (2026-11-19)

Both normal runs pass, and the negative control detects aliasing as expected.
For the inspected core and wrapper, **68000 mode preserves address bit 24 for
these data accesses and instruction fetches**. Switching the core to 68020 mode
is therefore not required merely to emit these tested addresses.

This is a softcore-specific result, not a claim that a physical Motorola 68000
has more than a 24-bit external address space. Bits 25 through 31 are not the
subject of this test. Results and compiler/translation logs are generated in
`simWorkspace/M68KUpperAddress/`; `negative-control.log` contains the expected
failure from the deliberately aliased memory run.

## Next integration checks

Production RTL and the memory map are unchanged by this regression. The test
bypasses the bus decoder, cache, SDRAM wrapper and physical SDRAM. It does not
establish EmuTOS compatibility or make the board's full 32 MiB accessible.

The next feature must define the larger CPU-to-physical-memory mapping while
preserving ROM/peripheral windows, retain the required upper bits through the
cache tags and read address path, and widen the SDRAM wrapper's word address.
Decoder/cache/controller aliasing tests and EmuTOS memory-discovery/allocation
checks are still needed before on-board validation.
