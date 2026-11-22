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
  in address bit 24; `$02010000` differs from the low address only in bit 25.
  A separate word is checked near the top of 32 MiB at `$01FFFFF0`.
- Upper- and lower-byte writes at low/high addresses, preserving the other byte.
- Distinct longwords at low/high/top addresses, with both 16-bit halves checked.
- Longwords beginning at `$00FFFFFE` and `$01FFFFFE`, crossing into
  `$01000000` and `$02000000` respectively between halves.
- A word at `$01FFFFFE`, the last aligned word below the 32 MiB boundary.
- Jumps to code above 16 MiB and then above 32 MiB, with actual instruction
  fetches required in both regions before the CPU reports success.

The ROM model supplies the same code image at low and high execution addresses;
that deliberate ROM mapping is separate from the independently stored data
regions. A bus observer requires separate instruction fetches with bit 24
and bit 25 set.

The normal test runs twice: with CPU clock enable always high, then with three
paused clocks between enabled clocks. This exercises the core's clock-enable
input; it is not a simulation of the full bus controller or SDRAM handshake.

Two additional runs are negative controls: the memory model deliberately masks
addresses to 24 bits, then to 25 bits. The first aliases `$01010000` to the low
address; the second preserves that pair but aliases `$02010000` to the low
address. In each run the CPU program must detect the resulting alias and report
failure. The runner only accepts that specific failure and reports the negative
control as passed; a timeout or unrelated failure does not count as success.

## Recorded result (2026-11-19)

Both normal runs pass, and both negative controls detect aliasing as expected.
For the inspected core and wrapper, **68000 mode preserves address bits 24 and
25 for these data accesses and instruction fetches**. Switching the core to 68020 mode
is therefore not required merely to emit these tested addresses.

This is a softcore-specific result, not a claim that a physical Motorola 68000
has more than a 24-bit external address space. Bits 26 through 31 are not the
subject of this test. Results and compiler/translation logs are generated in
`simWorkspace/M68KUpperAddress/`; `negative-control-bit24.log` and
`negative-control-bit25.log` contain the expected failures from deliberately
aliased memory runs. The earlier bit-24-only test is extended, not replaced.

## Next integration checks

Production RTL and the memory map are unchanged by this regression. The test
bypasses the bus decoder, cache, SDRAM wrapper and physical SDRAM. It does not
establish EmuTOS compatibility or make the board's full 32 MiB accessible.

The planned layout uses contiguous 32 MiB SDRAM and relocates ROM/peripherals
above `$02000000`. This regression verifies the CPU can emit addresses in that
higher region; it does not itself implement the relocation. The separate
[device-relocation step](memory-map-relocation.md) now updates the decoder and
rt68ice software references, while retaining the existing 14 MiB SDRAM window.
The EmuTOS port, wider cache tags/read address and SDRAM wrapper word address
remain follow-up work. Cache/controller aliasing tests and EmuTOS memory
configuration/allocation checks are still needed before on-board validation.
