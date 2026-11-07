# RT68ICE validation results

Recorded: 2026-11-05. Updated with SPI CPU word-access results: 2026-11-07.

These results summarize tests run on the board by the user and reported during
the EmuTOS porting session. They provide an initial CPU, memory and storage
stability baseline, rather than exhaustive hardware certification. Times below
are those reported by the test programs; they were not independently measured.

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
| GEMDOS filetest on SD | Three 1 MiB passes and four 4 MiB passes verified; longer run incomplete | Promising initial file-I/O integrity; persistence check pending |
| SD sequential file I/O | Three verified 4 MiB passes per version; latest CPU word-access driver achieves 245.0 KiB/s write and 293.0 KiB/s read | 18.7% write and 29.9% read improvement over paired-byte register accesses |
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

## SD-card file integrity: partial results

Program: generic `filetest.ttp` in the `emutos-apps/filetest` directory.
It uses GEMDOS file calls, without direct Atari peripheral access. The tested
executable was the initial uncommitted development build; its hash was not
captured. These runs preceded the CRLF alignment fix and the addition of
per-phase timing, so no elapsed-time or throughput results are available.

Each complete pass creates or replaces its own test file, writes a deterministic
payload, closes the file, reopens it, verifies the header and every payload
byte, checks the file length at EOF, then closes it again. The seed changes
between passes. Transfer sizes vary across sector boundaries and the reader
uses different boundaries from the writer.

| Command | Requested workload | Completed and verified in supplied log | Last visible progress |
| --- | --- | --- | --- |
| `filetest run C:\SDTEST.DAT 1 3` | Three passes, 1 MiB payload each | All three passes; `SUCCESS` reported | Complete |
| `filetest run C:\SDTEST2.DAT 4 25` | Twenty-five passes, 4 MiB payload each | Passes 1 through 4 | Pass 5 writing; 2,108,998 payload bytes reported written |

The shorter run used seeds `0x12345678` through `0x1234567A`. The four completed
passes of the longer run used `0x12345678` through `0x1234567B`; pass 5 began
with `0x1234567C`.

Selected output from the longer run:

```text
Pass 4 WRITE: 4194304 payload bytes, seed 0x1234567B
  Written 4194304 bytes
Pass 4 VERIFY: 4194304 payload bytes, seed 0x1234567B
  Verified 4194304 bytes
PASS: header, every payload byte and file length verified.
Pass 5 WRITE: 4194304 payload bytes, seed 0x1234567C
  Written 1051683 bytes
  Written 2108998 bytes
```

This records **19 MiB of successfully written and read-back-verified payload**
across seven completed passes (3 MiB in the first run plus 16 MiB in the
second). Header bytes and the incomplete pass-5 write are excluded. No data
mismatches or file-I/O failures were reported in the supplied execution logs.
Pass 5 has no completed write or verification result, and completion of the
requested 25-pass run has not been established.

These are promising initial results for the EmuTOS filesystem, SD driver,
FPGA SPI interface and card working together. They complement the YAARTTT
memory results, but do not test every card sector or isolate individual
components. Immediate readback can involve filesystem caches and does not
establish reboot persistence or recovery from interrupted writes. A normal
reboot followed by `filetest verify C:\SDTEST2.DAT` remains pending and requires
the last write to have finished and closed successfully first.

## SD-card sequential throughput

Command: `filetest speed C:\SPEED.DAT 4 3`.
Reported on 2026-11-06 using the development build with speed mode; the
executable hash and card model were not captured.

Each pass writes and reads a 4 MiB payload using 16 KiB blocks. Payload
preparation and a separate full-content verification occur outside the timed
transfers. Timings include payload file I/O and close; reads also check EOF.
These are sequential GEMDOS file-I/O measurements, not raw SPI bus rates.

| Pass | Seed | Write time (s) | Write (KiB/s) | Read time (s) | Read (KiB/s) | Untimed verification |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `0x12345678` | 47.305 | 86.5 | 52.510 | 78.0 | Passed |
| 2 | `0x12345679` | 47.210 | 86.7 | 52.530 | 77.9 | Passed |
| 3 | `0x1234567A` | 47.225 | 86.7 | 52.530 | 77.9 | Passed |

The program reported `SUCCESS: 3 speed passes; untimed verification passed.`
All three passes verified the header, every payload byte and file length:
**12 MiB of payload written, timed-read, and separately verified**. The final
file is retained as `C:\SPEED.DAT`.

Aggregate throughput (total payload divided by total transfer time) is
**86.7 KiB/s write** and **78.0 KiB/s read**. Write durations span just 0.095 s
and read durations 0.020 s across the three passes. This establishes a
consistent baseline for subsequent optimizations, but does not identify
whether filesystem, driver, CPU/register overhead or card behavior dominates.
Reboot persistence has not been tested by this run.

### 16-bit SPI payload transfers

Reported on 2026-11-07 after loading the EmuTOS build from branch
`sd-spi-16bit`. Command: `filetest speed C:\SPEED16.DAT 4 3`.
The loaded image and benchmark hashes were not captured.

This driver-only change uses the existing FPGA's 16-bit SPI mode through its
two byte data registers. Each 512-byte payload requires 256 START/completion
sequences instead of 512. Payload loops avoid per-byte function calls and
redundant readiness checks. Reads also preload dummy `0xffff` once per block:
this SPI core retains its TX buffer separately from its RX/TX shift register.
Commands, tokens, response polling and CRC bytes remain in 8-bit mode.

| Pass | Seed | Write time (s) | Write (KiB/s), printed | Read time (s) | Read (KiB/s), printed | Untimed verification |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `0x12345678` | 19.855 | 206.2 | 18.170 | 225.4 | Passed |
| 2 | `0x12345679` | 19.845 | 206.3 | 18.165 | 225.4 | Passed |
| 3 | `0x1234567A` | 19.850 | 206.3 | 18.165 | 225.4 | Passed |

The program reported `SUCCESS: 3 speed passes; untimed verification passed.`
All three passes verified the header, every payload byte and file length,
covering another **12 MiB of payload**. Write durations span 0.010 s and read
durations span 0.005 s. The final file is retained as `C:\SPEED16.DAT`.

| Operation | Original byte-mode baseline (KiB/s) | 16-bit payload mode (KiB/s) | Speedup |
| --- | --- | --- | --- |
| Write | 86.7 | 206.3 | 2.38x |
| Read | 78.0 | 225.5 | 2.89x |

Aggregate rates are calculated from total payload divided by summed durations;
speedup uses those unrounded rates. The aggregate read rate rounds to 225.5,
whereas FILETEST prints 225.4 for each individual pass.

The improvement strongly supports CPU/driver overhead as a major bottleneck.
It exceeds 2x because the change removes additional per-byte work as well as
halving the number of START/completion sequences. Individual contributions
have not been profiled, and these remain filesystem-level measurements.

Next, run the mixed-size integrity workload on this build and verify the final
speed file after a normal reboot with `filetest verify C:\SPEED16.DAT`.
Reboot persistence is still pending. Profile the remaining sector-transfer and
filesystem costs before deciding on further buffering or native four-bit SD.

### CPU word-access SPI interface

Reported on 2026-11-07 using FPGA branch `spi-16bit-databus` and EmuTOS
branch `sd-spi-word-access`. Loaded bitstream, image and benchmark hashes
were not captured. The new interface exposes word data at `$F20000`, received
low byte at `$F20001`, byte command/status at `$F20003` and byte configuration
at `$F20005`. SPI remains at 12.5 MHz for normal SD operation.

The controller and wrapper now accept complete CPU word data accesses.
EmuTOS uses these for payload pairs, with direct word RAM accesses for aligned
buffers and byte packing/unpacking for unaligned buffers. Commands and tokens
remain 8-bit SPI transfers. Byte status reads retain direct BUSY polling.

Hardware/monitor checks reported before the EmuTOS file tests:

- `loadflash` completed image loading and CRC validation at `$00D80000`.
- The read-only flash example reported JEDEC ID `EF4019`, status `00`, and
  matching results from two reads, with no writes performed.
- The SD smoke test initialized the card and read LBA 0 with signature `$55AA`.

Mixed-size integrity command: `filetest run C:\CHECKW.DAT 1 3`.
All three write/close/reopen/verify passes succeeded, covering **3 MiB** of
payload with seeds `0x12345678` through `0x1234567A`.

| Pass | Write time (s) | Write (KiB/s), printed | Verify time (s) | Verify (KiB/s), printed |
| --- | --- | --- | --- | --- |
| 1 | 20.300 | 50.4 | 20.015 | 51.1 |
| 2 | 19.695 | 51.9 | 20.200 | 50.6 |
| 3 | 19.910 | 51.4 | 20.225 | 50.6 |

These end-to-end times include pattern generation or comparison and progress
output; they are separate from the sequential file-I/O benchmark below.

Sequential command: `filetest speed C:\SPEEDW.DAT 4 3`.

| Pass | Seed | Write time (s) | Write (KiB/s), printed | Read time (s) | Read (KiB/s), printed | Untimed verification |
| --- | --- | --- | --- | --- | --- | --- |
| 1 | `0x12345678` | 16.475 | 248.6 | 13.970 | 293.1 | Passed |
| 2 | `0x12345679` | 16.930 | 241.9 | 13.980 | 292.9 | Passed |
| 3 | `0x1234567A` | 16.760 | 244.3 | 13.990 | 292.7 | Passed |

The program reported `SUCCESS: 3 speed passes; untimed verification passed.`
All headers, payload bytes and file lengths passed: **12 MiB** of sequential
payload plus the 3 MiB mixed-size run above. The final speed file is retained
as `C:\SPEEDW.DAT`. Read durations span 0.020 s; write durations span 0.455 s.

| Driver/interface | Aggregate write (KiB/s) | Aggregate read (KiB/s) |
| --- | --- | --- |
| Original byte SPI transfers | 86.7 | 78.0 |
| 16-bit SPI through separate data-byte registers | 206.3 | 225.5 |
| 16-bit SPI through CPU word data accesses | 245.0 | 293.0 |

Rates use total payload divided by summed durations. Relative to the previous
16-bit SPI driver, the latest run improves writes by **18.7%** and reads by
**29.9%**. Relative to the original driver, it is **2.83x** faster for writes and
**3.76x** for reads. This is a filesystem-level comparison; it does not isolate
individual CPU instructions, filesystem costs or card behavior.

The tested scope is a useful stopping point before adding automatic block
transfers or FPGA buffering. Reboot persistence for this version remains
pending: after a normal reboot run `filetest verify C:\SPEEDW.DAT`.

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
- Complete the longer SD filetest run (or start a new run with a new filename),
  then reboot normally after a successful close and verify the retained file.
  Initial repeated write/close/reopen/readback checks have passed as recorded
  above; do not count the incomplete pass as verified.
- Record loaded bitstream and EmuTOS image hashes for future runs.

Full original logs are not stored in this report; the CoreMark output and
selected YAART, SD filetest and GEMBench evidence above were transcribed from
the supplied logs.
