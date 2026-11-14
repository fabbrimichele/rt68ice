# Registered cache SDRAM interface

Branch: `cache-registered-interface`, based on `direct-mapped-cache` at
`f69cec1`. The baseline timing report is
[cache-timing-2026-11-14.md](cache-timing-2026-11-14.md).

## Interface change

`DirectMappedCache` captures the CPU address, write data, direction and byte
strobes in system-clock registers when accepting a request in Idle. Downstream
selection is also registered: writes select RAM after capture; read misses
select RAM after the synchronous cache lookup establishes a miss. Read misses
still fetch both bytes, and writes still invalidate the captured cache slot.

The captured request remains stable until completion. RAM selection is cleared
at the completion edge, providing a system-clock idle interval before the next
RAM request even when CPU selection stays asserted. No change to `SdRamDevice`
or to clock frequencies is included.

The return path now captures RAM readiness and read data together in
system-clock registers. Completion is qualified by registered RAM selection,
so an idle RAM's ready signal cannot complete a future request. Cache fills,
CPU read data and CPU readiness use the captured response. RAM selection stays
asserted until the CPU accepts completion; the downstream request remains stable.

Cache hits retain their previous latency. RAM writes and read misses each gain
one system clock (40 ns at 25 MHz) in request launch relative to the baseline.
The response register adds another system clock to RAM completion, making the
total added latency two system clocks (80 ns) for a read miss or write compared
with the original cache. Cache hit latency is unchanged. These cycle counts do
not predict the overall hardware benchmark result.

## Functional checks

The cache simulation and integrated top-level generation pass:

```sh
sbt 'runMain rt68ice.memory.DirectMappedCacheSim' \
    'runMain rt68ice.Rt68IceTopLevelVerilog'
```

The simulation retains cold miss, hit, conflict/replacement, reset, consecutive
word, byte read, write-through, byte write, invalidation and no-allocation
checks. It now checks registered request launch, address/data changes only
after capture, RAM deselection after completed writes, and a read miss followed
by a same-address write with CPU selection held high. An immediately ready RAM
response also waits for registered request selection and response capture.
The CPU remains stalled until completion is captured; changing raw RAM data
and readiness after capture does not change the CPU's registered response.
Existing consecutive request checks also cover clearing stale completion before
the next request. This is the standalone
RAM test model, not a simulation of the actual SDRAM controller or physical
clock timing. Hardware tests supplied after the user rebuilt/programmed this version are recorded below.

## Request-only routed timing comparison, 2026-11-14

Synthesis (`make rt68ice.json`) completed successfully. Routing used the same
nextpnr version, device, speed grade, LPF and fallback frequency as the baseline:

```sh
nextpnr-ecp5 --25k --package CABGA256 --speed 6 \
  --json rt68ice.json --lpf hw/constraints/Rt68IceTopLevel.lpf --freq 30 \
  --textcfg /tmp/rt68ice-registered-interface.config \
  --report /tmp/rt68ice-registered-interface.json --detailed-timing-report \
  > /tmp/rt68ice-registered-interface-timing.log 2>&1
```

| Maximum reported cross-domain delay | Baseline | Registered request |
| --- | ---: | ---: |
| System to SDRAM | 25.528 ns | 9.410 ns |
| SDRAM to system | 22.153 ns | 27.325 ns |

The system-to-SDRAM maximum now starts at
`directMappedCache_1.requestAddress_TRELLIS_FF_Q_28.Q` and ends at
`sdRamDevice_1.sdRamCtrl_1.delayState_TRELLIS_FF_Q.DI`.
The reverse maximum starts at
`sdRamDevice_1.io_bus_address_regNext_TRELLIS_FF_Q_18.Q` and ends at
`m68K_1.tg68kernel.alu.micro_state_TRELLIS_FF_Q.LSR`.
The maxima can have different endpoints after a change; this compares each
entire direction rather than tracking an identical net.

The request stage substantially shortens the outgoing maximum. The incoming
maximum worsened in this route and still exceeds the shortest nominal 20 ns
interval between related clock edges. These reports do not establish setup/hold
closure for either direction. The response path needs further work; registering
the outgoing request alone does not resolve overall cross-domain timing.
Placement and routing changed globally, so improvements in other domains are
not evidence of a direct architectural benefit there.

| Domain | Constraint | Baseline Fmax | Registered request Fmax |
| --- | ---: | ---: | ---: |
| System | 25 MHz | 29.12 MHz | 28.76 MHz |
| SDRAM | 50 MHz | 77.27 MHz | 105.92 MHz |
| VGA | 25 MHz | 45.77 MHz | 47.85 MHz |
| HDMI | 125 MHz | 243.01 MHz | 315.56 MHz |
| USB | 12 MHz | 70.74 MHz | 68.52 MHz |

All final per-clock checks pass and routing ends normally. Resources are
51/56 DP16KD (unchanged), 19,337 TRELLIS_COMB (previously 19,888), and 6,190
TRELLIS_FF (previously 6,151).

At this stage, `rt68ice.json` contained the request-only experiment. Routing outputs are at
the `/tmp` paths above; the normal `rt68ice.config` and `rt68ice.bit` were not
rebuilt by this timing experiment. Baseline copies were preserved as
`/tmp/rt68ice-cache-baseline.{json,config,bit}`. No FPGA programming was performed.

| Request-only artifact | SHA-256 |
| --- | --- |
| Synthesized netlist | `cfac7e2832604b0a45a4f41afe575acf070a63e5dcb03f695603b187ad337cfe` |
| Timing report | `931c8a4aa2cf09c32e9d8e838c5090bc4026b42a9627c84097922c8c37909b9d` |
| Routed configuration | `76e522ff0dac3e80325f7bf10d056ef9b479fe30ef8130d8323f38af4895b218` |

## Request and response registers: routed results, 2026-11-14

Both directions are now registered. The simulation, integrated top-level
elaboration and `make rt68ice.json` synthesis pass. Routing completed normally
using the same nextpnr options as above, with outputs named
`/tmp/rt68ice-registered-response.{config,json}` and log
`/tmp/rt68ice-registered-response-timing.log`.

| Maximum reported cross-domain delay | Original cache | Request only | Request and response |
| --- | ---: | ---: | ---: |
| System to SDRAM | 25.528 ns | 9.410 ns | 11.370 ns |
| SDRAM to system | 22.153 ns | 27.325 ns | 7.841 ns |

The final outgoing maximum starts at
`directMappedCache_1.requestAddress_TRELLIS_FF_Q_31.Q` and ends at
`sdRamDevice_1.sdRamCtrl_1.address_TRELLIS_FF_Q_10.CE`.
The incoming maximum starts at
`sdRamDevice_1.io_bus_address_regNext_TRELLIS_FF_Q_30.Q` and ends at
`directMappedCache_1.responseData_TRELLIS_FF_Q_6.CE`.
The response register removes the previous long incoming path to CPU control.
Routing changed, so the outgoing maximum also changed despite retaining the
same request-stage logic.

Both reported cross-domain maxima are now below the shortest nominal 20 ns
interval. This is a substantial improvement, but the cross-domain report still
does not provide explicit related-clock setup/hold slack. These maximum delays
alone do not establish full timing closure. No timing exceptions were added.

| Domain | Constraint | Final Fmax |
| --- | ---: | ---: |
| System | 25 MHz | 30.17 MHz |
| SDRAM | 50 MHz | 89.51 MHz |
| VGA | 25 MHz | 45.12 MHz |
| HDMI | 125 MHz | 289.27 MHz |
| USB | 12 MHz | 75.16 MHz |

All final per-clock checks pass. Resources are 51/56 DP16KD, 19,936
TRELLIS_COMB and 6,207 TRELLIS_FF. No additional block RAM is consumed.

At the end of this timing experiment, `rt68ice.json` contained both stages,
but the normal configuration and bitstream had not been rebuilt: routing
outputs were written to `/tmp`. The user subsequently rebuilt and programmed
the FPGA, then supplied the hardware results below. The loaded bitstream's
identity relative to this archived route was not verified by hash.

| Request and response artifact | SHA-256 |
| --- | --- |
| Synthesized netlist | `3060827eb8366739949ac7aae291c934ee76b113e4de10326d780ecc27983cb5` |
| Timing report | `1ffb35ea13471221dc7042ceed28dc2d4216e7aa8ae136926580c4c59bf4cb6f` |
| Routed configuration | `55f0f90452abf4d58defc087556550785e5c975707483f9fc7f460768d5122e4` |

## Hardware validation after rebuild, 2026-11-14

The user reports all four SDRAM correctness diagnostics passing and a copy
benchmark of **4,433 cycles** (original cache 3,733; uncached 7,191). YAARTTT
completed three passes with zero reported errors over `$00026B18`–`$00D80000`;
pass 4 was in progress. Three CoreMark runs validated: the first two report
3,632 ticks and 11.013216 iterations/s; the third reports 3,633 ticks and
11.010184 iterations/s, all for 200 iterations with matching CRCs.

Copy time is 18.8% longer and CoreMark throughput about 14.4% lower than the
original cache. The registered cache remains 1.62x faster for the copy workload
and about 57.0% faster in CoreMark than uncached. These results establish the
baseline for future multiword cache lines; that feature is not implemented.

See [registered-cache hardware validation](validation.md#registered-cache-hardware-validation-2026-11-14)
for the complete run table, provenance and limitations. FILETEST and repeated
desktop/power-cycle checks have not been supplied for this version. The timing
qualification above remains applicable despite successful hardware tests.
