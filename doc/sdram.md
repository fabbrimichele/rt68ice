# SDRAM and cache

The current cache is 8 KiB with four-word burst fills, write-hit updates and
a [single-entry posted-write buffer](#single-entry-posted-write-buffer-2026-11-18).
For measured changes at every step, see the
[performance progression](validation.md#cache-performance-progression).
The dated results below describe successive historical versions.

## Standalone 32 MiB RAM test

`sw/app/asm/sdram_test.asm` now targets the expanded SDRAM window ending at
`$01FFFFFF`. Use the matching 32 MiB FPGA build. This is a destructive monitor
application: run it before loading EmuTOS. The full sweep starts at `$00020000`,
leaving the monitor's low memory and the application's reserved 64 KiB intact;
it overwrites the EmuTOS image area.

```sh
make target/app/sdram_test.bin
python3 tools/serial_load.py --port /dev/ttyACM0 --baud 57600 target/app/sdram_test.bin
```

Run option **2** and then **6** first, followed by **1**, **3** and **5**.
Option **4** remains the existing copy benchmark.

- **1 — Data Bus Integrity:** walking-one word patterns over
  `$00020000–$01FFFFFF`, including the upper 16 MiB.
- **2 — Address alias:** distinct longword patterns at `$00020400`,
  `$00820400`, `$01020400` and `$01820400`. These share a cache slot and
  cover all four 8 MiB regions. Writes to all locations precede verification,
  so dropped address bits cannot be hidden by immediate write/read pairs.
- **6 — Boundary longwords:** test `$00FFFFF8`, `$00FFFFFE`, `$01000002`,
  `$01000008`, `$01FFFFF8` and `$01FFFFFC`. The access at `$00FFFFFE`
  crosses 16 MiB; the final access ends exactly at `$01FFFFFF` without touching
  the framebuffer at `$02000000`.

Options 2 and 6 repeat ten passes with address-derived patterns and their
complements. The assembled routines passed local TG68K CPU simulation with a
32 MiB memory model; deliberately discarding address bit 24 made option 2 fail.
The application image header/CRC and full-sweep bounds were checked locally.
Board validation on 2026-11-23 passed all five integrity/stress tests
(options 1, 2, 3, 5 and 6), including the full sweep through `$01FFFFFF`,
address alias checks and boundary/top-of-RAM accesses. Option 4 measured
**3,329 counter ticks**. Final routed timing passed all reported clocks:
system 28.49 MHz at a 25 MHz requirement, SDRAM controller 96.39 MHz at 50 MHz,
HDMI 212.99 MHz at 125 MHz. These reports do not independently establish
external SDRAM timing or complete clock-domain-crossing sign-off.

The FPGA now maps 32 MiB. The monitor image-load limit and companion EmuTOS
RAM configuration still require follow-up; the standalone test runs below
that load limit and accesses the expanded range directly.

### Resources
- [AtariST_MiSTer/rtl/sdram.v](https://github.com/MiSTer-devel/AtariST_MiSTer/blob/master/rtl/sdram.v)
- [Higher level SDRAM controller - hides refresh](https://github.com/agg23/sdram-controller/blob/master/sdram.sv)
- [icesugar-pro - sdram](https://github.com/wuxx/icesugar-pro#sdram)

### Performance
Tested with `sdram_test.asm`, test name `Benchmark`:

| RAM (MHz)        | CPU (MHz) | Clock Cycles |
|------------------|-----------|-------------:|
| 28.4091 SDRAM    | 28.4091   |         5910 |
| 56.8182 SDRAM    | 28.4091   |         4739 |
| 28.4091 FPGA RAM | 28.4091   |         3076 |
| 50 SDRAM (uncached, 2026-11-14) | 25 | 7191 |
| 50 SDRAM (original 2 KiB cache, 2026-11-14) | 25 | 3733 |
| 50 SDRAM (registered 2 KiB cache, 2026-11-14) | 25 | 4433 |
| 50 SDRAM (registered 8 KiB, four-word lines, 2026-11-15) | 25 | 4137 |

I tried the following configuration but the SDRAM doesn't work:
- SDRAM clock: 78.1250 
- CPU clock: 26.0417

### Uncached comparison baseline (2026-11-14)

On 2026-11-14, the user reran `sdram_test.asm`, menu option **4. Benchmark**,
with the cache not integrated. The supplied output reports **7,191 clock
cycles**. The current design configures the CPU/system clock at **25 MHz**
and SDRAM at **50 MHz**. This is one supplied measurement; bitstream and
benchmark binary hashes were not recorded.

The current benchmark copies 256 words (512 bytes) from `$00020000` to
`$00021000`, measuring elapsed free-running system-counter ticks. At 25 MHz,
7,191 ticks correspond to approximately **287.64 microseconds**, including
benchmark measurement overhead.

Use **7,191 cycles** as the uncached baseline when comparing the same benchmark
binary and clock settings with the cache enabled. The older rows above are
historical results at different clocks and may use an earlier benchmark
version; they are not a controlled cache comparison.

### Cached result and comparison (2026-11-14)

With the 2 KiB unified, direct-mapped, one-word-entry cache integrated, the user
reported **3,733 cycles** for the same menu option at CPU/system **25 MHz** and
SDRAM **50 MHz**. This cache uses write-through without write allocation and
invalidates the selected cache slot on CPU writes. The data-bus integrity,
row/bank conflict, timing stress and upper-SDRAM stress tests all reported
`Passed!` in the supplied run.

| Configuration | Counter cycles | Time at 25 MHz | Relative speed |
| --- | ---: | ---: | ---: |
| Uncached | 7,191 | 287.64 microseconds | 1.00x |
| 2 KiB cache | 3,733 | 149.32 microseconds | 1.93x |

Elapsed time falls by **48.1%**; the fixed-workload rate rises by **92.6%**.
These are one supplied uncached and one supplied cached measurement. They
measure the complete copy loop, including instruction fetches and writes,
not raw SDRAM bandwidth. The loop's repeated instructions can benefit from
cache hits even though source words are read sequentially and destination
writes still reach SDRAM. Binary hashes were not supplied, so identical
benchmark provenance has not been independently established.

The cached design also completed three YAARTTT passes without reported errors
and five identical validated CoreMark runs at **12.861736 iterations/s**.
See [cached-system validation](validation.md#cached-system-validation-2026-11-14)
for comparison with the uncached CoreMark baseline, successful cached SD
integrity/post-reboot verification, and remaining limitations. The cached
CoreMark score is **6.70x** the published 8 MHz Atari ST reference of
1.92 iterations/s; this is a CoreMark comparison, not an application-wide claim.

The [physical timing report review](cache-timing-2026-11-14.md) records passing
per-clock checks but unresolved system/SDRAM cross-domain timing. Hardware
benchmark success does not substitute for timing closure.

### Registered cache hardware result (2026-11-14)

With both request and response stages registered (implementation `b57059f`),
the user rebuilt/programmed the FPGA and reported **4,433 cycles** for the
same copy benchmark at 25/50 MHz. Data bus integrity, row/bank conflict,
timing stress and upper-SDRAM stress all passed.

| Configuration | Counter cycles | Time at 25 MHz | Speed versus uncached |
| --- | ---: | ---: | ---: |
| Uncached | 7,191 | 287.64 microseconds | 1.00x |
| Original 2 KiB cache | 3,733 | 149.32 microseconds | 1.93x |
| Registered 2 KiB cache | 4,433 | 177.32 microseconds | 1.62x |

Registration increases copy time by 18.8% versus the original cache, while
remaining 38.4% quicker than uncached. Hits retain their latency; misses and
writes each gain two system cycles total. Three YAARTTT passes completed
without reported errors, and three CoreMark runs validated at
11.010184–11.013216 iterations/s. See
[registered-cache validation](validation.md#registered-cache-hardware-validation-2026-11-14)
for results, comparisons and coverage limitations. Registered-interface routed
maxima are 11.370/7.841 ns in the two directions; explicit related-clock
setup/hold sign-off remains outstanding.

### Four-word burst cache (2026-11-15)

The `expand_cache_line` version has 1,024 four-word lines (8 KiB of data),
registered requests/responses and single-word write-through. Its unmatched
fast-input-register delay was disabled to capture SDRAM bursts at the proper
cycle. All four SDRAM diagnostics passed; the copy benchmark reports
**4,137 cycles**, or **165.48 microseconds** at 25 MHz.

This is 6.7% less copy time than the registered one-word cache's 4,433 cycles,
and 1.74x copy speed versus uncached. Four CoreMark runs validated at
12.578616–12.582572 iterations/s, about 14.2% higher throughput than the
registered 2 KiB baseline. Three YAARTTT passes completed without errors
in 14:35, essentially unchanged from 14:30. Capacity, burst fills and capture
timing changed together; the individual contributions were not measured.
See [burst-cache validation](validation.md#four-word-burst-cache-validation-2026-11-15)
for full results, resource measurements and remaining checks.

## Dedicated SDRAM read input stage

The 16 SDRAM DQ pins now feed a common input-register stage in the 50 MHz
SDRAM controller domain. The burst buffer consumes those registered samples
instead of sampling each pin directly into four separate fabric registers.
The `syn_useioff` attribute requests dedicated ECP5 IOLOGIC input flip-flops;
the installed Yosys `synth_ecp5` flow already transfers that attribute to the
mapped FFs, so the Makefile needs no additional synthesis pass.

`settingUseFastInputRegister` is now true because the input stage actually
exists. Its read-delay adjustment compensates for the added pipeline cycle:
one 20 ns SDRAM cycle per read burst at 50 MHz. The write path is unchanged.
The prior direct-sampling implementation correctly kept that setting false.

Run the controller-level regression with:

```sh
sbt 'runMain rt68ice.memory.SdRamCtrlInputSim'
```

The simulation generates the SpinalHDL controller and uses Icarus with a small
SDRAM pin model. It checks burst lengths 1, 4 and 8 at CAS latencies 2 and 3,
including distinct consecutive words, repeated reads, full-word writes, and
both byte-write lanes. Each configuration checks 128 bursts and 96 writes.
It verifies pipeline alignment and data handling; the simplified pin model
does not establish board-level setup/hold timing closure.

Hardware validation on 2026-11-17 recorded seven complete clean YAARTTT passes
and three validated CoreMark runs at 12.578616 iterations/s. See the
[input-register validation results](validation.md#sdram-input-register-validation-2026-11-17)
for provenance, comparisons, and remaining coverage.


## Write-hit updates (2026-11-18)

Before the posted-write buffer, the cache updated the selected word or enabled
byte(s) on a completed SDRAM write hit and retained the line's tag, valid bit
and other data. A write
miss left the cache unchanged and did not allocate. In that version, writes
completed in SDRAM before the CPU was released. The posted-write version below
supersedes that CPU completion policy.

The line-update logic views the 64-bit data as four 16-bit words using `Vec`.
Default copies preserve the old line and word, and independent UDS/LDS
overrides replace the enabled bytes using the captured request. This replaces
manual word-selection and whole-line concatenation cases without adding a
register stage.

The final refactor completed four YAARTTT passes without errors in 17:16,
with pass five underway. Four CoreMark runs all validated at 2794 ticks,
13.970000 seconds and 14.316391 iterations/s. Compared with the input-register
baseline that invalidated on a write hit (3180 ticks, 12.578616 iterations/s),
this is about 12.1% less execution time and 13.8% higher throughput.

The preceding implementation also completed four clean YAARTTT passes in
17:15 and four validated CoreMark runs at 14.321517–14.326647 iterations/s.
The refactor is 5–10 ms slower in these supplied CoreMark runs, approximately
0.04–0.07%; the cause of that small difference has not been established.
See [write-hit update validation](validation.md#write-hit-update-validation-2026-11-18)
for the detailed results and simulation coverage. The diagnostic copy benchmark
and file-I/O tests have not been rerun for this version.


## Single-entry posted-write buffer (2026-11-18)

Writes remain write-through with no write allocation. The cache captures a
write's address, data and byte enables in a separate pending entry and starts
the SDRAM transaction. After the synchronous cache lookup, a hit updates the
selected cached bytes and the CPU is acknowledged without waiting for SDRAM.
The pending entry stays occupied until registered SDRAM completion releases it.
There is one entry total, including the in-flight write, not an extra queued
entry behind it. SDRAM writes remain single-word; no write bursts or combining
were added.

While a write is pending, cache read hits can complete using updated cached
data. A second write stalls without overwriting the entry. A read miss holds
its captured request in the lookup state until the write finishes, then launches
a burst read after a full deselected system-clock interval. Write responses
cannot supply read data or cache fills. Peripherals do not currently access
SDRAM; peripheral operations are not gated on buffer drain. Future SDRAM DMA
or video clients will require an ordering/coherency review.

Three CoreMark runs validated at 15.168752, 15.174507 and 15.168752 iterations/s,
with a mean of **15.170670**: **6.0% higher throughput** and **5.6% less execution
time** than the preceding Vec implementation. Relative to uncached, throughput
is **116.3% higher (2.16x)**. YAART completed six clean passes in 23:51 on a
retry; its first four passes took 15:54 versus 17:16 without the buffer, an
**8.6% throughput increase** (7.9% less time).

An earlier run rebooted during pass five's random-pattern test after the 19:27
status, with no reported memory errors beforehand. The cause is unresolved.
Long breadboard reset-button wiring is a possible source of glitches, not a
confirmed diagnosis; reset filtering has not changed in this version.

See [posted-write validation](validation.md#single-entry-posted-write-buffer-validation-2026-11-18)
for simulation coverage, exact runs, remaining checks and the
[full performance history](validation.md#cache-performance-progression).
The legacy write RAM-wait event does not track background write duration;
see [counter definitions](cache-stats.md#what-the-counters-measure).
