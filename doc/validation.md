# RT68ICE validation results

Recorded: 2026-11-05.

These results summarize tests run on the board by the user and reported during
the EmuTOS porting session. They provide an initial CPU and memory stability
baseline, rather than exhaustive hardware certification. Times below are those
reported by the test programs; they were not independently measured.

## Configuration and provenance

| Item | Configuration |
| --- | --- |
| Board | iCESugar Pro, Lattice ECP5 LFE5U-25F |
| CPU | TG68K in 68000 mode |
| CPU/system clock | 25 MHz in the inspected design |
| SDRAM clock | 50 MHz in the inspected design |
| SDRAM | 32 MiB physical, 16-bit data bus; 14 MiB CPU window |
| Low-memory overlay | 1 KiB FPGA BRAM; first eight bytes selected from ROM |
| Framebuffer | Dedicated FPGA BRAM, up to 75 KiB |
| Operating system | RT68ICE EmuTOS port |
| EmuTOS image address | `$00D80000` |

Repository state inspected when recording this report:

- RT68ICE: `e6a7e0dc7315d1efcbe1ea538a2c52eb0702691e`.
- EmuTOS: `a10c167a6ab4deb2555fbe6c1218e57db09e1ef0`.
- No tracked local changes in either repository before creating this report.
  RT68ICE contained an untracked `tools/__pycache__/` directory.

These are source-checkout references, not verified identities of the binaries
loaded on the board. Bitstream/image hashes and exact build provenance were not
captured. The display mode used during testing was not recorded.

## Summary

| Test | Observed result | Interpretation |
| --- | --- | --- |
| Atari CoreMark 1.0 | Five supplied runs at 7.013069 iterations/s; all validated | Repeatable CPU workload and performance baseline |
| YAARTTT 0.2.3, ST-RAM | Ten complete passes, pass 11 at its final test; zero reported errors | Strong initial evidence for free SDRAM integrity |
| Dhrystone 2.0, Alcyon 4.14 | Printed value checks passed; inconsistent timing figures | Correctness smoke test; performance reporting unresolved |
| GEMBench RAM-access test | Bus-error panic | Strong evidence of unsupported Atari hardware access; no RAM score obtained |

## CoreMark

Program: `COREMARK.TOS`, Atari TOS/EmuTOS port by Christian Zietz.
Command: `COREMARK.TOS`, without arguments.

Reported output:

```text
2K performance run parameters for coremark.
CoreMark Size    : 666
Total ticks      : 3137
Total time (secs): 15.685000
Iterations/Sec   : 7.013069
Iterations       : 110
Compiler version : GCC8.2.1 20181017
Compiler flags   : -O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1
Memory location  : Code and data in RAM
seedcrc          : 0xe9f5
[0]crclist       : 0xe714
[0]crcmatrix     : 0x1fd7
[0]crcstate      : 0x8e3a
[0]crcfinal      : 0x0134
Correct operation validated. See README.md for run and reporting rules.
```

The arithmetic is consistent: `3137 / 200 = 15.685` seconds and
`110 / 15.685 = 7.013069` iterations/s. The initial run and four additional
runs supplied later all reported exactly 3137 ticks, 15.685 seconds, 110
iterations and 7.013069 iterations/s. All five runs passed validation with
identical CRC values. No variation was observed at the reported timer
resolution of 5 ms. This establishes repeatability of the reported result;
no external stopwatch measurement has been supplied to establish wall-clock
accuracy.

The port's published stock 8 MHz ST/MegaST/MegaSTE reference score is 1.92,
giving a nominal score ratio of approximately 3.65. The clock ratio is 3.125.
Build equivalence and the board timer's wall-clock accuracy were not verified,
so this does not isolate CPU-core efficiency from memory-system effects.

- [Atari port download](https://github.com/czietz/coremark/releases/tag/atari_port)
- [Reference results](https://github.com/czietz/coremark/wiki/Results)

## YAARTTT memory integrity

Program: `YAARTTT.TOS`, v0.2.3, copyright CHZ 2016-2021.
Command: `YAARTTT.TOS`; select `S` for ST-RAM.

The program reported the test range `$00026F34` to `$00D80000`, approximately
13.35 MiB. The upper boundary corresponds to the EmuTOS image address. This
tests allocated free memory, leaving the test program, OS and other allocated
regions outside the test range.

Tests shown in the supplied log:

1. Address test using each location's own address.
2. Moving inversions with zero and one patterns (`0000`, `ffff`).
3. Moving inversions with eight bit patterns (`0101` through `8080`).
4. Moving inversions with a changing random pattern each pass.

Selected milestones:

| Reported elapsed time | Progress | Reported errors |
| --- | --- | --- |
| 00:00:00 | Pass 1, test 1 | 0 |
| 00:08:27 | Pass 2 begins; pass 1 complete | 0 |
| 00:42:18 | Pass 6 begins; five passes complete | 0 |
| 00:50:46 | Pass 7 begins; six passes complete | 0 |
| 01:32:10 | Pass 11, test 4, random pattern `6fb7` | 0 |

The captured output includes `<iCELink:Overflow>` during pass 7, followed by a
gap before the pass-11 status. This is a serial/debug transport message, not a
YAART memory-error report. The last visible error counter is zero, but the
intervening output was not captured. The final line shows test 4 of pass 11 in
progress; it does not establish completion of eleven passes.

This exercises a much wider range of locations and patterns than the existing
monitor SDRAM smoke tests. It does not validate the full physical 32 MiB,
reserved EmuTOS memory, low-memory BRAM or framebuffer. The log's "8 bit" test
name alone does not establish coverage of byte-write masks; dedicated byte-lane
tests remain useful.

- [Author's description and downloads](https://www.chzsoft.de/site/hardware/diverse-kleinigkeiten-fur-den-atari-st/#ram-test-software-yaart)

## Dhrystone: timing unresolved

Program identifies itself as Dhrystone 2.0, compiled with DRI/Alcyon 4.14 and
the `register` attribute. Its fixed banner describes an 8 MHz MegaST; that is
not the RT68ICE clock configuration.

| Iterations | Reported microseconds/run | Reported iterations/s |
| --- | --- | --- |
| 5,000 | 805.0 | 1299.9 |
| 25,000 | 485.0 | 2133.3 |
| 25,000, repeated results | 565.0 | 1835.7 |

All supplied final-value checks matched, including `Ar_2_Glob[8][7] = 25010`
for 25,000 iterations. The first command-line argument successfully controls
the iteration count: `dhry 25000`.

The timing figures are not reciprocal: 565 microseconds/run implies about
1769.9 iterations/s, rather than 1835.7. Calculation, timer conversion and
formatting remain possible causes; none has been established. The exact
Dhrystone source/executable was not available for inspection. Do not use these
scores as a verified speedup measurement. A longer run alone does not resolve
the reporting inconsistency.

## GEMBench: compatibility failure

Selecting the RAM-access test produced a bus-error panic. Relevant dump fields:

```text
misc=2004 opcode=2004
addr=00032004 sr=2004 pc=00039456
A0=fffffc00
basepage=00023ea0
text=00023fa0 data=000375d2 bss=000375d2
```

The user identified GEMBench GB606 B22; the local package inspected was
GB608B31. Disassembly of its `BIN/ALLTEST.o` found direct keyboard/mouse ACIA
accesses at `$FFFFFC00`, matching A0 in the dump. The B22 changelog records the
addition of mouse-off routines. The module also accesses Atari MFP registers
and hooks fixed Atari interrupt vectors.

RT68ICE uses USB input and has no ACIA at that address. This strongly suggests
a failure in hardware-specific input-control code surrounding the test,
rather than a demonstrated SDRAM failure. An exact faulting-instruction match
requires the binary used on the board.

The current TG68K implementation constructs dummy extra fields for a 68000
bus-error frame using status-register data. EmuTOS prints these as instruction
and address fields; consequently, `addr=00032004` is not reliable evidence of
a fault at that SDRAM address. See `trap4` through `trap6` in
[`TG68KdotC_Kernel.vhd`](../hw/vhdl/TG68KdotC_Kernel.vhd).

Running the same module without GEM does not remove its hardware accesses.
It requires adaptation before it can provide a meaningful RAM-access result.

## Follow-up checks

- Add byte-lane preservation and mixed byte/word/longword checks on memory
  allocated through GEMDOS.
- Repeatedly write, close, reopen and read back test files on SD, checking
  contents or checksums under EmuTOS.
- Record loaded bitstream and EmuTOS image hashes for future runs.

Full original logs are not stored in this report; the CoreMark output and
selected YAART/GEMBench evidence above were transcribed from the supplied logs.
