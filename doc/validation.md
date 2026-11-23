# RT68ICE validation results

Recorded: 2026-11-05. Updated with posted-write hardware results: 2026-11-18.

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
| SDRAM | 32 MiB physical and CPU window, 16-bit data bus |
| Low-memory overlay | 1 KiB FPGA BRAM; first eight bytes selected from ROM |
| Framebuffer | Dedicated FPGA BRAM, up to 75 KiB |
| Operating system | RT68ICE EmuTOS port |
| EmuTOS image address | `$01F80000` (boot, six YAART passes and one CoreMark run confirmed) |

Repository state inspected when recording this report:

- RT68ICE: `e6a7e0dc7315d1efcbe1ea538a2c52eb0702691e`.
- EmuTOS: `a10c167a6ab4deb2555fbe6c1218e57db09e1ef0`.
- No tracked local changes in either repository before creating this report.
  RT68ICE contained an untracked `tools/__pycache__/` directory.

These are source-checkout references, not verified identities of the binaries
loaded on the board. Bitstream/image hashes and exact build provenance were not
captured. The display mode used during testing was not recorded.

## Summary

See the [complete cache performance progression](#cache-performance-progression)
and [current posted-write results](#single-entry-posted-write-buffer-validation-2026-11-18).

The original rows below describe the uncached baseline. Later cache results
are recorded separately in [cached-system validation](#cached-system-validation-2026-11-14)
and [registered-cache validation](#registered-cache-hardware-validation-2026-11-14).

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

## Cached-system validation (2026-11-14)

The user supplied the following on-board results after integrating a 2 KiB
unified direct-mapped SDRAM cache on branch `direct-mapped-cache`. CPU/system
clock remains **25 MHz**, SDRAM **50 MHz**. Entries hold one 16-bit word;
writes reach SDRAM and invalidate the selected cache slot, without allocating
an entry. These results precede any proposed same-address direction-change
hardening of the SDRAM wrapper. Loaded binary hashes were not supplied.

### Performance comparison

| Workload | Uncached | Cached | Improvement |
| --- | ---: | ---: | --- |
| SDRAM 256-word copy, counter cycles | 7,191 | 3,733 | 1.93x workload speed; 48.1% less elapsed time |
| Atari CoreMark 1.0, iterations/s | 7.013069 | 12.861736 | 1.83x score; 83.4% higher iterations/s |

The SDRAM copy measurements are from one supplied run per configuration;
see [SDRAM benchmark details](sdram.md#cached-result-and-comparison-2026-11-14).
CoreMark compares the five previously recorded uncached runs with five supplied
cached runs. Compiler version, flags, seed CRC and workload size match the
reported baseline. Executable identity was not verified by hash, and timer
accuracy has not been checked against an external clock. This supports a
workload-level improvement, not a raw SDRAM-bandwidth claim.

### CoreMark: five repeatable validated runs

Command: `COREMARK.TOS`, without arguments, from `C:\APPS\COREMARK`.
All five supplied runs reported exactly:

```text
CoreMark Size    : 666
Total ticks      : 3110
Total time (secs): 15.550000
Iterations/Sec   : 12.861736
Iterations       : 200
Compiler version : GCC8.2.1 20181017
Compiler flags   : -O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1
Memory location  : Code and data in RAM
seedcrc          : 0xe9f5
[0]crclist       : 0xe714
[0]crcmatrix     : 0x1fd7
[0]crcstate      : 0x8e3a
[0]crcfinal      : 0x382f
Correct operation validated. See README.md for run and reporting rules.
```

The arithmetic is consistent: `3110 / 200 = 15.550` seconds and
`200 / 15.550 = 12.861736` iterations/s. The benchmark selected 200 iterations
instead of the uncached run's 110. Its final CRC is iteration-dependent;
both configurations reported successful validation. Identical reported scores
show repeatability at the 5 ms timer resolution, not necessarily identical
sub-tick execution times.

The fifth matching run was supplied after a reboot, alongside successful
verification of the retained SD file described below.

The published Atari port [reference results](https://github.com/czietz/coremark/wiki/Results)
list a stock ST / MegaST / MegaSTE at 8 MHz at **1.92 iterations/s**. Relative
to that reference, RT68ICE scores **3.65x uncached** and **6.70x cached**.
These ratios compare CoreMark workloads; they do not establish the same
speedup for graphics, games or all applications, nor identical binary hashes.

For a longer run with the same default performance seeds, the released Atari
port accepts the iteration count as its fourth argument:

```text
COREMARK.TOS 0 0 0 1000
```

This requests 1,000 internal iterations, not 1,000 independent benchmark runs.
It is documented for future use; no result for this command has been supplied.
See the [released port's argument handling](https://github.com/czietz/coremark/blob/atari_port/core_main.c).

### YAARTTT: three completed passes, zero reported errors

Command: `YAARTTT.TOS` from `C:\APPS\YAART`, selecting `S` for ST-RAM.
Version 0.2.3 reported the range **`$00026B18` to `$00D80000`**, approximately
**13.35 MiB**. The supplied log shows:

| Elapsed time | Progress | Reported errors |
| --- | --- | ---: |
| 00:00:00 | Pass 1 begins | 0 |
| 00:04:15 | Pass 2 begins; pass 1 complete | 0 |
| 00:08:31 | Pass 3 begins; two passes complete | 0 |
| 00:12:46 | Pass 4 begins; three passes complete | 0 |
| 00:12:54 | Pass 4, ones/zeros test, pattern `0000` | 0 |

The completed passes exercised own-address patterns, moving inversions with
zero/one and walking-bit patterns, and random patterns `67b5`, `6a81`, and
`76e6`. Do not count pass 4 as completed. These are tests of the cached system;
they do not independently prove every physical SDRAM access or cover reserved
OS memory, low-memory BRAM, or the framebuffer. The earlier uncached YAARTTT
log establishes correctness evidence, but its capture gap and different start
address make it unsuitable as a controlled timing comparison.

### SD integrity and persistence with the cache enabled

The user ran `filetest run C:\CACHE01.DAT 1 3` on the cached bitstream. All
three write/close/reopen/verify passes succeeded, checking the header, every
payload byte and exact file length. Each pass used a 1 MiB payload, for 3 MiB
written and immediately verified in total.

| Pass | Seed | Write time | Verify time | Result |
| --- | --- | ---: | ---: | --- |
| 1 | `0x12345678` | 14.410 s | 12.880 s | Passed |
| 2 | `0x12345679` | 13.825 s | 13.050 s | Passed |
| 3 | `0x1234567A` | 14.075 s | 13.115 s | Passed |

After a reboot, `filetest verify C:\CACHE01.DAT` successfully checked the
retained pass-3 file: **1,048,576 payload bytes**, seed **`0x1234567A`**, plus
header and length. Verification took **13.345 s** (76.7 KiB/s). The user
explicitly confirmed that this verification and the fifth matching CoreMark
run were after reboot. This establishes post-reboot persistence for the
reported file and configuration, not interrupted-write recovery.

The integrity-mode rates include pattern generation and byte-by-byte checking:
reported writes were 71.0-74.0 KiB/s and immediate verification 78.0-79.5 KiB/s.
These are not comparable to the earlier sequential `speed` workload's
245.0/293.0 KiB/s write/read figures; no new cached `speed` result was supplied.

### Physical timing report review

The user generated a detailed nextpnr report, inspected on 2026-11-14.
Place-and-route finished normally. Final system-domain Fmax is **29.12 MHz**
against a **25 MHz** constraint; SDRAM-domain Fmax is **77.27 MHz** against
**50 MHz**. Block RAM usage is **51/56 DP16KD blocks**.

Cross-domain maximum delays are reported separately: **25.53 ns** from system
to SDRAM and **22.15 ns** in the reverse direction. Both exceed the shortest
20 ns positive-edge separation for the nominal aligned 25/50 MHz clocks.
The JSON paths do not report an explicit required time or setup/hold slack for
these crossings. Therefore, passing per-clock Fmax checks and a positive
summary histogram do **not** establish cross-domain timing closure. Determine
which paths are sensitizable and whether any multicycle allowances are valid,
or revise the interface. Hold timing is not established by these maxima.

The report's routed configuration is byte-identical to the repository's
current `rt68ice.config`; the FPGA's loaded bitstream identity remains
unverified. See the [timing review](cache-timing-2026-11-14.md) for the command,
critical endpoints, resource usage, and artifact hashes. The Desk reset must
not be attributed to these paths without further evidence.

### Remaining integration observations

EmuTOS booted, but the user initially reported a reset when opening the Desk
menu. After reprogramming the FPGA, the user could not reproduce it. The cause
is unresolved; no exception output was supplied, and it must not be marked
fixed solely because later benchmarks pass.

Top-level elaboration passes after declaring the system and SDRAM clock
domains synchronous; their clocks originate from the same PLL. This declaration
does not establish physical setup/hold timing. The report review above leaves
cross-domain timing closure unresolved. Repeated desktop-menu and power-cycle
checks also remain outstanding; the supplied reboot/FILETEST/CoreMark checks
passed but do not by themselves fully validate the integration. A same-address direction change
without an idle gap is a conditional limitation of the SDRAM wrapper; that
sequence has not been established for the current TG68K workload and is not a
confirmed cause of the reset.

## Registered-cache hardware validation (2026-11-14)

After building and programming the request/response-registered cache version,
the user supplied the results below. The implementation is commit `b57059f`
on `cache-registered-interface`, subsequently merged through
`direct-mapped-cache` into `main`. These are user-reported source/build
associations; hashes of the loaded bitstream and test executables were not
supplied. CPU/system and SDRAM clocks remain 25 MHz and 50 MHz. The cache
still has 1,024 one-word entries (2 KiB of data), with write-through,
no write allocation and slot invalidation on writes.

The request and response registers each add one system clock to SDRAM
transactions: two cycles total (80 ns) for misses and writes relative to the
original cache. Cache-hit latency is unchanged.

### SDRAM diagnostics and performance

All four correctness tests reported `Passed!`: data bus integrity, row/bank
conflict, timing stress and upper SDRAM stress (`$D82000`). The copy benchmark
reported **4,433 counter cycles**, or **177.32 microseconds** at 25 MHz.

| Configuration | Copy counter cycles | CoreMark iterations/s |
| --- | ---: | ---: |
| Uncached | 7,191 | 7.013069 |
| Original 2 KiB cache | 3,733 | 12.861736 |
| Registered 2 KiB cache | 4,433 | 11.010184–11.013216 |

Compared with the original cache, copy time increases **18.8%** and CoreMark
throughput falls about **14.4%**. Compared with uncached, the registered cache
still gives **1.62x copy speed** (38.4% less elapsed time) and approximately
**57.0% higher CoreMark throughput**. Copy results are single supplied runs per
configuration, and CoreMark's uncached run uses 110 iterations versus 200 in
both cached versions. These are workload comparisons, not raw memory bandwidth.

### CoreMark: three validated runs

Command: `COREMARK.TOS`, without arguments, from `C:\APPS\COREMARK`.

| Run | Iterations | Total ticks | Seconds | Iterations/s | Validation |
| --- | ---: | ---: | ---: | ---: | --- |
| 1 | 200 | 3,632 | 18.159999 | 11.013216 | Passed |
| 2 | 200 | 3,632 | 18.159999 | 11.013216 | Passed |
| 3 | 200 | 3,633 | 18.165000 | 11.010184 | Passed |

All runs retain size 666, GCC8.2.1 20181017 and flags
`-O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1`, with code and data
in RAM. All report seed CRC `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`
and final `382f`, matching the original cached runs, and successful validation.
The third run differs by one 5 ms timer tick, about 0.028%. Timer wall-clock
accuracy and executable identity were not independently established.

### YAARTTT: three completed passes, zero reported errors

`YAARTTT.TOS` v0.2.3, selecting `S` for ST-RAM, tested the same reported range
`$00026B18`–`$00D80000` as the original cached run (approximately 13.35 MiB).

| Elapsed time | Progress | Reported errors |
| --- | --- | ---: |
| 00:00:00 | Pass 1 begins | 0 |
| 00:04:50 | Pass 2 begins; one complete | 0 |
| 00:09:40 | Pass 3 begins; two complete | 0 |
| 00:14:30 | Pass 4 begins; three complete | 0 |
| 00:14:39 | Pass 4, ones/zeros pattern `0000` | 0 |
| 00:15:04 | Pass 4, ones/zeros pattern `ffff` begins | Not printed in final excerpt |

The three completed passes include own-address, zero/one, walking-bit and
random patterns (`2d7f`, `7c7a`, `2654`). Pass 4 is incomplete in the log.
Each complete pass takes about 4 min 50 s, versus about 4 min 15 s in the
original cache log (roughly 14% longer). The same coverage limitations apply:
this is free-memory testing of the cached system, not the full physical SDRAM.

### Timing and remaining observations

The detailed registered-interface route reports **11.370 ns** system to SDRAM
and **7.841 ns** SDRAM to system, versus 25.528 ns and 22.153 ns for the
original cache. All final per-clock checks pass; block RAM use remains
**51/56**. These maxima are below the shortest nominal 20 ns edge interval,
but explicit related-clock setup/hold sign-off remains outstanding. The
user rebuilt the bitstream for hardware testing; its routing identity relative
to the archived timing report was not verified. See
[registered-interface details](cache-registered-interface.md).

The registered interface releases RAM selection between transactions even
when CPU selection stays asserted; its standalone simulation checks a read
miss followed by a same-address write. The earlier conditional wrapper
limitation is therefore not carried forward unchanged at the cache boundary.
The initial unexplained Desk-menu reset remains unresolved. Repeated desktop,
reset and power-cycle checks and FILETEST were not supplied for this registered
version; the earlier FILETEST and post-reboot success applies to the original
cache version only. Larger lines and burst fills are future work, not part of
this tested implementation.

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

## Four-word burst-cache validation (2026-11-15)

Branch `expand_cache_line` extends the registered cache to **1,024 lines of
four 16-bit words**, or **8 KiB of data**, with 11-bit tags and separate valid
bits. Read misses fetch an aligned eight-byte line through the 64-bit SDRAM
response. Writes remain single-word write-through, with byte enables, no
allocation and whole-line invalidation. CPU/system and SDRAM clocks remain
25/50 MHz. Results were supplied by the user after rebuilding/programming;
loaded binary hashes and external timer accuracy were not recorded.

### Capture timing correction and startup

The first burst build stalled at startup and produced garbled serial output
following reset. The controller retained a delay for a fast input-register
stage, although the SpinalHDL capture logic reads DQ directly. Setting
`settingUseFastInputRegister` to `false` removes the unmatched cycle; the user
then reported the monitor starting successfully.

A temporary pin-waveform diagnostic using the generated controller and the
chip's CAS-2 timing captured `2222,3333,4444,invalid` before the correction,
instead of the supplied `1111,2222,3333,4444` burst. Advancing capture one
cycle returned all four words correctly; the diagnostic was repeated against
the corrected generated controller and passed. This diagnostic is not a full
SDRAM model or a permanent repository test. See the manufacturer
[datasheet, page 26](https://media.digikey.com/pdf/Data%20Sheets/ISSI%20PDFs/IS42S83200B,IS42S16160B.pdf)
for CAS timing. The explanation that late single-word reads previously worked
because of residual bus data remains unverified.

### SDRAM diagnostics and performance

Data bus integrity, row/bank conflict, timing stress and upper-SDRAM stress
all reported `Passed!`. The copy benchmark reported **4,137 cycles**,
**165.48 microseconds** at 25 MHz.

| Configuration | Copy cycles | Copy speed vs uncached | CoreMark iterations/s |
| --- | ---: | ---: | ---: |
| Uncached | 7,191 | 1.00x | 7.013069 |
| Original unregistered 2 KiB cache | 3,733 | 1.93x | 12.861736 |
| Registered 2 KiB, one-word lines | 4,433 | 1.62x | 11.010184–11.013216 |
| Registered 8 KiB, four-word lines | 4,137 | 1.74x | 12.578616–12.582572 |

Against the registered 2 KiB baseline, copy time falls **6.7%** (7.2% higher
workload speed), while CoreMark throughput rises about **14.2%**. CoreMark is
about 2.2% below the original unregistered cache and 79.4% above uncached.
These changes combine capacity, burst fills and corrected capture timing;
no isolated measurement attributes the gain to one change. Copy measurements
are one supplied run per configuration and do not measure raw SDRAM bandwidth.

### CoreMark: four validated runs

Command: `COREMARK.TOS`, without arguments. All use 200 iterations, size 666,
GCC8.2.1 20181017 and flags
`-O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1`, code/data in RAM.

| Run | Ticks | Reported seconds | Iterations/s | Validation |
| --- | ---: | ---: | ---: | --- |
| 1 | 3,179 | 15.895000 | 12.582572 | Passed |
| 2 | 3,180 | 15.899999 | 12.578616 | Passed |
| 3 | 3,179 | 15.895000 | 12.582572 | Passed |
| 4 | 3,179 | 15.895000 | 12.582572 | Passed |

All report seed CRC `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`, final
`382f` and successful operation validation. Variation is one 5 ms timer tick.

### YAARTTT: three completed passes without reported errors

`YAARTTT.TOS` 0.2.3, ST-RAM (`S`), reported `$00026B18`–`$00D80000`,
approximately 13.35 MiB of free memory.

| Elapsed | Progress | Reported errors |
| --- | --- | ---: |
| 00:00:00 | Pass 1 begins | 0 |
| 00:04:51 | Pass 2 begins; one complete | 0 |
| 00:09:43 | Pass 3 begins; two complete | 0 |
| 00:14:35 | Pass 4 begins; three complete | 0 |

Completed passes exercised address, zero/one, walking-bit and random patterns
`1ed5`, `0e23`, `23da`. Pass 4 is not established complete. The registered
one-word version took 14:30 for three passes; this workload is effectively
unchanged (five seconds longer). These tests do not cover the full physical
32 MiB, reserved OS regions, low BRAM or framebuffer.

### Simulation, resources and remaining checks

The updated `DirectMappedCacheSim` and top-level generation pass. Simulation
supplies 64-bit lines, checks all word offsets on misses and hits, aligned
reads, same-line and cross-line consecutive reads, byte reads, whole-line byte
write invalidation, single-word writes, reset and registered responses.
Conflict/replacement now uses `$1234` and `$3234`, `$2000` apart. The simulation
uses a backing-store model rather than the SDRAM controller/pins.

A detailed route of the burst design before the capture-delay correction used
**54/56 DP16KD blocks** and passed final per-clock checks, with maxima
12.123 ns system-to-SDRAM and 7.477 ns reverse. Artifacts are local
`/tmp/rt68ice-burst-debug.{json,config,log}`. Those figures are not timing
sign-off of the corrected implementation. Final related-clock setup/hold
verification remains outstanding. FILETEST and verification after reboot have
not been supplied for this version; earlier storage results apply to earlier
cache configurations.


## SDRAM input-register validation (2026-11-17)

Configuration: 25 MHz CPU/cache, 50 MHz SDRAM, 8 KiB four-word cache,
write-through writes with invalidation only on a tag hit. The controller now
samples DQ through 16 dedicated IOLOGIC input registers before assembling each
burst, with one additional SDRAM read-pipeline cycle. The original Makefile
synthesis recipe and default placement seed were used.

The tested sources are the input-register change following `0fee3af` on
`cache-write-hit-update`. The local programming file matches the preserved
build artifact. Its SHA-256 is:

```text
d9c1ff96b2acd0848c94ee6b95bb521e3bb141c7f65205e65c7121270cda34ef
```

### Hardware results reported by the user

- YAARTTT 0.2.3, ST-RAM: seven complete passes with zero errors; pass eight
  remained clean through the `4040` pattern at 37:32. The previously failing
  `0404` pattern passed eight times. This is not eight completed passes.
- CoreMark: three validated runs, each 200 iterations, 3180 ticks,
  15.899999 reported seconds, and 12.578616 iterations/s.
- CoreMark CRCs: seed `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`,
  final `382f`.

Compared with the earlier counter-enabled main baseline of 12.654223
iterations/s and 3161 ticks, this is about 0.6% lower throughput and 95 ms
longer per run. Conditional invalidation and input capture changed between
those builds, so the comparison does not isolate the register's cost.

Before the input stage, the original conditional-invalidation route failed
repeatedly with `0404` read as `0004`, while an identical synthesis netlist
rerouted with seed 2 completed five clean passes and remained clean in pass
six's final test. That comparison and the new clean run support a timing-sensitive
failure; they do not establish complete external SDRAM timing closure.

### Implementation checks

- `DirectMappedCacheSim` passed, including write-hit invalidation and
  preservation of a conflicting cached line on a write miss.
- `SdRamCtrlInputSim` passed burst lengths 1, 4 and 8 with CAS latencies 2 and
  3. Each of the six configurations checked 128 read bursts and 96 full-word
  and byte writes using a simplified SDRAM pin model.
- A temporary negative control that removed the added read-delay cycle failed
  the burst-alignment check, as expected.
- Post-route inspection confirmed all 16 DQ input FFs packed into IOLOGIC,
  with no `dqInput` flip-flops remaining in general fabric.
- Internal per-clock Fmax: system 27.305 MHz against 25 MHz; SDRAM 96.600 MHz
  against 50 MHz. These checks do not include complete board-level external
  setup/hold signoff.

The SDRAM diagnostic copy benchmark and file-I/O tests have not been rerun for
this input-register build.


## Write-hit update validation (2026-11-18)

Configuration: 25 MHz CPU/cache, 50 MHz SDRAM, 8 KiB direct-mapped cache with
1,024 four-word lines, burst read fills and dedicated SDRAM input registers.
Write hits now update the cached word or enabled bytes rather than invalidate
the line. Write misses preserve cached lines without allocating. The policy
remains write-through and CPU completion still waits for the SDRAM write.

Two implementations were tested on the board by the user:

- Commit `4074442`, with explicit word-selection and concatenation muxes.
- The working-tree refactor based on that commit, using `Vec` word selection,
  copying the original line and word, and independent byte overrides. This
  refactor is the implementation accompanying this report.

The user identified which version was programmed. The final local generated
Verilog contains `updateWriteHit`, matching the refactor. The local bitstream
associated with that run has SHA-256:

```text
251c1b7d94fb3190ab8d0b5d2bda8f639fe1f6794f89238d9bccebdbf3b8539b
```

Programming-file identity on the board was not independently read back.

### YAARTTT 0.2.3, ST-RAM

Both runs tested `$00026B18` through `$00D80000`, selecting S, with zero
reported errors through four complete passes:

| Implementation | Pass 5 began | Last supplied status |
| --- | --- | --- |
| `4074442` | 17:15 | Pass 5, zero/one test at 17:23; zero errors |
| Vec/byte-override refactor | 17:16 | Pass 5, zero/one test at 17:23; zero errors |

The completed passes include own-address, zero/one, all eight walking-bit
patterns and random-pattern tests. Pass five is not established complete.
The preceding input-register build with write-hit invalidation reached pass
five at 19:26. Four passes therefore took about 11.2% less time with the first
write-hit update implementation, and 11.1% less with the refactor. This is
YAART workload throughput, not a general application performance claim.

### CoreMark

All eight write-hit-update runs used 200 iterations and validated. Compiler:
GCC 8.2.1 20181017, flags `-O2 -mcpu=68000 -fomit-frame-pointer
-DPERFORMANCE_RUN=1`; code and data were in RAM. All reported CRCs matched:
seed `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`, final `382f`.

| Implementation | Runs | Ticks | Reported seconds | Iterations/s |
| --- | ---: | --- | --- | --- |
| Input-register baseline, invalidate on write hit | 3 | 3180 each | 15.899999 each | 12.578616 each |
| `4074442`, update on write hit | 4 | 2792, 2793, 2792, 2793 | 13.960000–13.965000 | 14.321517–14.326647 |
| Vec/byte-override refactor | 4 | 2794 each | 13.970000 each | 14.316391 each |

The final version has about 13.8% higher throughput and 12.1% less execution
time than the input-register invalidating baseline. Its throughput is about
104.1% above the original uncached 7.013069 result (2.04x). These compare the
supplied benchmark results; performance improvements are workload-dependent.

Relative to `4074442`, the refactor takes 1–2 more 5 ms timer ticks, a measured
throughput reduction of approximately 0.04–0.07%. The cause has not been
established. YAART's four-pass elapsed time differs by one second.

### Simulation and synthesis comparison

`DirectMappedCacheSim` passes with the new policy. It checks 48 write-hit
cases spanning all four word offsets, word/upper-byte/lower-byte writes and
RAM delays of 0, 1, 3 and 8 waiting clocks. It verifies all words remain hits
with the correct data, neighbours and other slots remain unchanged, writes
reach RAM with the original address/data/lanes, and live CPU inputs do not
replace the captured request. Arbitrary RAM response data is supplied during
writes to catch accidental use of read-response data for cache updates.

Another 24 cases cover conflicting-tag write misses across all offsets and
byte lanes with immediate and delayed responses: the resident line must
remain a correct hit, the RAM write must occur, and the written address must
still miss until a later read fills it. Existing reset, read-fill, byte-read,
replacement and consecutive-request tests remain in the regression. Tests
also check consecutive write hits and immediate read-after-write hits.

Cache-only Yosys synthesis, using the same settings and normalized top-module
name, produced the following counts. These are not full-system placement or
timing results:

| Cell type | `4074442` | Refactor |
| --- | ---: | ---: |
| DP16KD | 5 | 5 |
| TRELLIS_FF | 1145 | 1145 |
| LUT4 | 2957 | 2955 |
| PFUMX | 240 | 242 |
| L6MUX21 | 47 | 47 |
| CCU2C | 6 | 6 |

The storage counts are unchanged, with a small combinational mapping change.
This is not a formal equivalence proof or proof of equal physical timing.

### Remaining coverage

The diagnostic SDRAM copy benchmark, file-I/O verification and profiling
counter measurements have not been rerun for this version. The cache-only
simulation does not model SDRAM pins. External SDRAM and related-clock
setup/hold timing signoff remain outstanding; clean hardware runs do not
replace that analysis.


## Cache performance progression

All rows use 25 MHz CPU/system and 50 MHz SDRAM. Scores below are arithmetic
means of the supplied validated CoreMark runs, rounded to six decimal places;
percentages use the unrounded means. The uncached executable ran 110
iterations; later runs used 200. All report GCC 8.2.1 20181017,
`-O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1`, code and data in RAM.
Executable hashes and all historical bitstream hashes were not captured.
These are workload measurements, not raw SDRAM bandwidth or isolated effects
of individual RTL changes. The detailed dated sections preserve the original
ranges, clocks, coverage and provenance.

| Step | Validated runs | Mean iterations/s | Change vs previous step | Change vs uncached |
| --- | ---: | ---: | ---: | ---: |
| No cache | 5 | 7.013069 | — | — |
| Original 2 KiB cache, one-word lines, invalidating writes | 5 | 12.861736 | +83.40% | +83.40% |
| Registered request/response interface, 2 KiB cache | 3 | 11.012205 | -14.38% | +57.02% |
| 8 KiB cache, four-word burst fills | 4 | 12.581583 | +14.25% | +79.40% |
| Dedicated SDRAM input stage, invalidate only on write hit | 3 | 12.578616 | -0.02% | +79.36% |
| Write-hit updates, explicit mux implementation (`4074442`) | 4 | 14.324082 | +13.88% | +104.25% |
| Write-hit updates, Vec/byte-override refactor | 4 | 14.316391 | -0.05% | +104.14% |
| Single-entry posted-write buffer | 3 | 15.170670 | +5.97% | +116.32% |

The initial cache removed the largest bottleneck. Registration traded some
throughput for registered interfaces; larger burst lines recovered it. Keeping
lines cached on write hits and posting one write then raised throughput about
20.6% beyond the dedicated-input-stage baseline. The final score is **2.16x**
uncached. Differences of one or two timer ticks around the input-stage and
Vec refactor steps should not be treated as isolated optimization effects.


## Single-entry posted-write buffer validation (2026-11-18)

Configuration remains 25 MHz CPU/cache, 50 MHz SDRAM, 8 KiB direct-mapped cache,
1,024 four-word lines and dedicated SDRAM input registers. The tested working
tree is on `cache-posted-write-buffer`, based on `00aca70` and merged write-hit
implementation `3e83b17`. The user built/programmed that working version;
loaded bitstream and executable hashes were not supplied for these runs.

The one entry includes the in-flight write. CPU acknowledgement follows the
synchronous lookup and write-hit cache update, rather than waiting for SDRAM.
Pending address/data/byte enables remain stable until registered completion.
A second write stalls while the entry is occupied. Read hits can proceed; a
miss waits with its captured request until the write drains, with a deselected
system-clock interval before the next SDRAM request. Write misses do not
allocate. SDRAM writes are still single-word, without combining or bursts.

### CoreMark: three validated runs

Each run used 200 iterations, size 666, the compiler/flags listed above and
RAM for code and data. All reported correct-operation validation and matching
CRCs: seed `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`, final `382f`.

| Run | Ticks | Reported seconds | Iterations/s |
| --- | ---: | ---: | ---: |
| 1 | 2637 | 13.185000 | 15.168752 |
| 2 | 2636 | 13.180000 | 15.174507 |
| 3 | 2637 | 13.185000 | 15.168752 |

Mean throughput is **15.170670 iterations/s** and mean elapsed time
**13.183333 seconds**. Against the preceding four identical unbuffered Vec
runs (14.316391 iterations/s, 13.970000 seconds), this is **5.97% higher
throughput** and **5.63% less execution time**. Variation within the new runs
is one 5 ms timer tick. Against uncached, the score is **116.32% higher**.

### YAARTTT: six clean completed passes on retry; earlier reboot unresolved

The user ran YAARTTT 0.2.3, ST-RAM (`S`), over `$00026B18`–`$00D80000`.
An initial run reached pass five's random-pattern test at 19:27 with zero
reported errors, then printed the RT68F boot monitor banner. The reboot cause
has not been identified. The user reported long reset-button wires on a
breadboard, making an input glitch a plausible hypothesis, not proof. The
reset logic in that tested write-buffer version asserted immediately on the
raw button; its existing 20 ms hold after release did not require a sustained
press. The separate [reset-filter improvement](reset.md) was implemented later
and has not yet been validated on the board.

The retry used the same bitstream according to the user and recorded:

| Elapsed time | Progress | Reported errors |
| --- | --- | ---: |
| 00:00:00 | Pass 1 begins | 0 |
| 00:03:58 | Pass 2 begins | 0 |
| 00:07:57 | Pass 3 begins | 0 |
| 00:11:55 | Pass 4 begins | 0 |
| 00:15:54 | Pass 5 begins; four complete | 0 |
| 00:19:52 | Pass 6 begins; five complete | 0 |
| 00:23:51 | Pass 7 begins; six complete | 0 |
| 00:24:18 | Pass 7, zero/one pattern `ffff` | 0 |

No reboot appears in the supplied retry. The first four complete passes took
954 seconds versus 1,036 seconds without the buffer: **7.92% less time** or
**8.60% higher workload throughput**. This clean retry does not explain or
invalidate the earlier reboot. Pass seven is not established complete.

### Simulation coverage and remaining checks

`DirectMappedCacheSim` passed against the final controller after removing the
duplicate unconditional ready assignment. It separates CPU acknowledgement
from downstream completion and verifies:

- CPU acknowledgement while SDRAM ready is still low.
- Word/upper-byte/lower-byte write hits followed by reads of updated and
  neighbouring cached words while the write is still pending in SDRAM.
- Consecutive longword halves with CPU selection held, second-write stalls,
  no duplicate acceptance events, and stable pending address/data/byte enables.
- Same-address and conflicting-address read misses waiting for posted word or
  byte writes, launching only after drain and a deselected cycle.
- Held-high RAM readiness across write completion/read launch cannot let stale
  write completion or poisoned write-response data satisfy the new read.
- SDRAM completion coinciding with CPU write acknowledgement.
- The existing 48 write-hit and 24 conflicting-write-miss cases, all word
  offsets and byte lanes, plus read fill/replacement/reset regressions.

The legacy write RAM-wait event remains tied to the one-cycle write lookup
state; it does not measure background write occupancy. CPU write-stall counts
remain the metric for CPU blocking. No new on-board profiling snapshot was
supplied. See [counter definitions](cache-stats.md#what-the-counters-measure).

A longer stability run is recommended before merging because the earlier
reboot is unresolved. Diagnostic SDRAM copy and file-I/O verification have not
been rerun for the posted-write build. Cache simulation is not a pin-level
SDRAM or physical timing test; related-clock and external SDRAM setup/hold
signoff remain outstanding. Peripherals currently do not access SDRAM; future
DMA/video clients will need explicit ordering/coherency review.


Pre-PR verification also passed `CacheStatsDeviceSim`, `BusControllerSim`,
`MemoryMapSim` and integrated `Rt68IceTopLevelVerilog` generation, alongside
the cache regression. No additional full bitstream build was run for these
documentation/comment changes; the on-board results above refer to the user's
previously built and programmed version.

## 32 MiB expansion and top-of-RAM EmuTOS (2026-11-23)

The user confirmed that the standalone 32 MiB RAM test passed options 1, 2,
3, 5 and 6. These include the walking-one sweep through `$01FFFFFF`, address
alias checks across all four 8 MiB regions, longwords crossing 16 MiB and the
final longword at `$01FFFFFC`. The copy benchmark measured 3,329 counter ticks.
Final routed system timing passed at 28.49 MHz against a 25 MHz requirement;
all reported clocks passed. This is not complete external-interface/CDC sign-off.

The monitor was updated to accept images through the 32 MiB SDRAM limit, and
EmuTOS was linked at `$01F80000`, reserving the last 512 KiB. Both builds and
local monitor range-check simulations passed. After programming the FPGA and
serial-uploading EmuTOS, a power cycle was needed to discard the old warm-boot
13,824 KiB limit. The user then confirmed approximately 32 MiB on the welcome
screen (31.5 MiB configured ST-RAM).

YAARTTT reported the test range `$00026AE8–$01F80000` with ST-RAM selected.
The user subsequently reported six complete passes with zero errors by
55:55, confirmed by the start of pass 7; pass 7 also continued cleanly through
its ones/zeros test at 56:12. This exercises approximately 31.35 MiB of free
ST-RAM, including the added upper 16 MiB, without overwriting the OS image.

One CoreMark 1.0 run validated with 200 iterations, 2,635 ticks, 13.175 seconds
and **15.180265 iterations/sec**. Compiler: GCC 8.2.1 20181017, flags
`-O2 -mcpu=68000 -fomit-frame-pointer -DPERFORMANCE_RUN=1`, code and data in RAM.
CRCs: seed `e9f5`, list `e714`, matrix `1fd7`, state `8e3a`, final `382f`.
This is consistent with the previous approximately 15.17 iterations/sec;
no meaningful performance change is established by a single run. More CoreMark
repetitions, flash-image boot and broader peripheral/reset/file-I/O checks
remain useful. The known keyboard-reset boot-modifier issue is separate from
the RAM expansion.
