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
| 50 SDRAM (2 KiB cache, 2026-11-14) | 25 | 3733 |

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
