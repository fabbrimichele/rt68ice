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

### Current uncached baseline

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
