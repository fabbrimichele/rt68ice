# Cache performance counters (register ABI 1)

The counter peripheral observes the registered four-word SDRAM cache.
The observer does not change the cache policy or clock rates. The current
cache uses a single-entry posted-write buffer; see the event definitions below
for the legacy write RAM-wait counter's limitation.
Counters are disabled and zero after FPGA reset. They occupy flip-flops,
not block RAM. Resource use and routed timing must be checked for the build.

Use the accompanying `CACHSTAT.TOS` utility in
[`emutos-apps/rt68ice/cachestat`](https://github.com/fabbrimichele/emutos-apps/tree/main/rt68ice/cachestat).
Run it from EmuTOS, for example:

```text
CACHSTAT START
COREMARK.TOS
CACHSTAT STOP
```

`START` atomically clears/enables. `STOP` stops, snapshots all counters at
one clock edge, and prints that frozen bank. `SHOW` also stops/snapshots/prints;
no arguments means SHOW. `CLEAR` zeros/disables and invalidates
the previous snapshot. `RESUME` enables without clearing. Output and ratios
are computed outside supervisor mode; MMIO uses a supervisor callback.
The 68000 utility guards its hardware identity probe with a temporary BERR
handler, restoring the vector and status register afterward.

This window includes application loading/exit, shell/desktop and interrupts
between commands. START prints its message before enabling and STOP snapshots
before printing, but startup/shutdown and the supervisor call still contribute.
STOP and SHOW freeze before printing; use RESUME to continue the same totals.
For a precise
region, future benchmark code can write the same control register directly.
No automatic benchmark launching or modification of CoreMark is included.

## Register map

Base **`$00F30000`**, 16 KiB decoded window (`$F30000`–`$F33FFF`).
All defined registers use 16-bit accesses at even byte offsets. Unused offsets
read zero, writes to read-only offsets are ignored, and partial-byte writes
to CONTROL are ignored. The peripheral is always ready and generates no IRQ.

| Byte offset | Access | Meaning |
| --- | --- | --- |
| `$00` | R | ID `$4343` ("CC") |
| `$02` | R | Register ABI version `1` |
| `$04` | RW | CONTROL: bit 0 RUN, bit 1 CLEAR pulse, bit 2 SNAPSHOT pulse; reads return RUN only |
| `$06` | R | STATUS: bit 0 snapshot valid, bit 1 RUN value immediately before the snapshot |
| `$0C`, `$0E` | R | System clock Hz, 32 bits, high word first (currently 25,000,000) |
| `$20`–`$27` | R | Snapshot: elapsed enabled system cycles |
| `$28`–`$2F` | R | Snapshot: read hits |
| `$30`–`$37` | R | Snapshot: read misses |
| `$38`–`$3F` | R | Snapshot: accepted write requests |
| `$40`–`$47` | R | Snapshot: CPU read stall cycles |
| `$48`–`$4F` | R | Snapshot: CPU write stall cycles |
| `$50`–`$57` | R | Snapshot: read RAM-wait cycles |
| `$58`–`$5F` | R | Snapshot: write RAM-wait cycles |

All eight counters are **64 bits**. Each snapshot value is four successive
16-bit words, most significant first. The counter bank is frozen in place,
without a duplicate 512-bit snapshot bank. Data reads return zero while
running or before a valid snapshot. The frozen bank is stable until RESUME,
START, CLEAR or reset. This avoids
torn reads even though the 68000 needs multiple bus cycles to read 64 bits.
Counters wrap modulo 2^64. No saturating or overflow flag is provided.

CONTROL word values: START=`$0003`, STOP/snapshot=`$0004`, SHOW=`$0004`,
CLEAR/stop=`$0002`, RESUME=`$0001`. Writing zero stops without marking a new
valid snapshot. CLEAR takes priority over SNAPSHOT, which takes priority over
RUN. Even `$0005` freezes; it cannot snapshot while running. Control-write
clock edges are excluded from all increments. A snapshot contains the live
values immediately before its command edge; STATUS bit 1 records the old RUN
state, while CONTROL reflects the new stopped state. RESUME invalidates the
frozen view; counter data reads return zero until the next snapshot.

## What the counters measure

- **Read hits/misses:** one event per resolved 16-bit read request. A hit is
  counted in the lookup state; a miss waiting for a pending write is counted
  only when it can launch its SDRAM read, not on each waiting cycle. A four-word
  fill counts as one miss, not four reads. A longword can
  count as a miss followed by a hit. Instruction and data reads are combined.
- **Writes:** one event when Idle accepts a write; a held/stalled request is
  not repeatedly counted. A 32-bit CPU store consists of two word requests.
- **CPU read/write stalls:** active selected SDRAM-bus operation with cache
  READY low, counted each system cycle. Includes lookup and request launch as
  well as controller and registered-response waiting. Hit lookup therefore
  contributes to read stalls. Other peripherals' and CPU-internal cycles do
  not contribute to these two counts.
- **Read RAM waits:** cycles in the read SDRAM-wait state with captured
  completion still false. Includes response-register waiting; excludes lookup,
  time spent draining a pending write before read launch, and the ready cycle.
- **Write RAM waits (legacy limitation):** the current event is asserted only
  in the one-cycle write lookup/acknowledgement state when captured completion
  is false. It does not follow the background SDRAM write after CPU release.
  Consequently this value and its per-write average must not be interpreted as
  SDRAM write duration or CPU write-stall time. The register ABI/layout is
  unchanged. Earlier blocking-write builds counted their write-wait state.
  All wait counts use system clocks, not SDRAM clocks or isolated chip latency.
- **Elapsed:** all enabled system-clock cycles, including idle/ROM/peripheral
  work and interrupts. Counts continue while the CPU is stalled.

Hit rate = hits / (hits + misses). Average read/write stalls divide total
stall cycles by the corresponding read/write request count. RAM-wait averages
divide by misses or writes. Stall shares divide by elapsed cycles. Ratios with
zero denominators print `n/a`; displayed fractions are truncated. Read RAM
waits are a subset of CPU read stalls. With posted writes, write RAM waits are
not a CPU write-stall subset; use CPU write stalls to assess CPU blocking.
Do not add RAM-wait and CPU-stall totals together.
These figures do not split instruction from data traffic or identify why a
particular miss happened.

## Verification

```sh
sbt 'runMain rt68ice.io.CacheStatsDeviceSim' \
    'runMain rt68ice.memory.DirectMappedCacheSim' \
    'runMain rt68ice.core.BusControllerSim' \
    'runMain rt68ice.core.MemoryMapSim' \
    'runMain rt68ice.Rt68IceTopLevelVerilog'
make -C ../emutos-apps/rt68ice/cachestat build check
```

The CSR simulation checks reset/disabled behaviour, event counts, clear/start,
stop/snapshot, resume, freeze from running, ignored partial/unselected
writes, 64-bit carry, and big-endian read ordering. The cache regression also
checks hit/miss event classification, one-time write acceptance, early CPU
acknowledgement, full-buffer stalls, overlapping cache hits and ordered misses.
Memory-map regressions cover selection/readback and the window boundary.
The utility's host mock checks commands, unavailable/unsupported hardware,
64-bit formatting, arithmetic and zero denominators. A headless Hatari/EmuTOS
run exercised the actual 68000 probe against an unmapped address and printed
the expected missing-hardware message. An on-board START/CoreMark/STOP
measurement was subsequently supplied, as recorded below.

## Built artifacts (2026-11-16)

`make rt68ice.bit` completed successfully after changing the snapshot design
to freeze counters in place. The first copy-bank version was stopped because
routing was congested at about 95% logic use. The final version uses
**21,365/24,288 TRELLIS_COMB (88%)**, **6,866 TRELLIS_FF**, and **54/56 DP16KD**.
It removes the duplicate 512-bit snapshot bank and does not consume extra BRAM.

Final routed per-clock Fmax: system **30.91 MHz** against 25 MHz; SDRAM
**95.08 MHz** against 50 MHz; VGA **47.13 MHz** against 25 MHz; HDMI
**272.03 MHz** against 125 MHz; USB **74.24 MHz** against 12 MHz. The earlier
placement-stage system estimate failed, but the final routed result passes.
These are per-clock checks, not complete external SDRAM or related-clock
setup/hold sign-off. The build log is local `/tmp/rt68ice-cache-stats-build.log`.

The generated hardware artifact is `rt68ice.bit`. The built program is
`../emutos-apps/rt68ice/cachestat/CACHSTAT.TOS` (about 17 KiB). The board has
not been programmed by the agent; the user supplied the hardware result below.
Repeated instrumented SDRAM/memory tests remain useful to check instrumentation
impact beyond this one validated CoreMark run.

## First on-board counter measurement (2026-11-16)

The user ran START, `COREMARK.TOS`, then STOP. CoreMark completed 200 iterations
in 3,161 ticks (15.805000 seconds), scoring **12.654223 iterations/s** with
successful validation and CRCs `e9f5/e714/1fd7/8e3a/382f` (seed/list/matrix/
state/final). This is one instrumented run, not proof that instrumentation
improved performance. The utility was initially run under its DOS alias
`CACHES~1.TOS`; its build output/help was then corrected to the eight-character
basename `CACHSTAT`, without changing the register ABI.

| Counter | Reported value |
| --- | ---: |
| Clock | 25,000,000 Hz |
| Elapsed cycles | 1,497,167,333 |
| Read hits | 478,498,478 |
| Read misses | 25,669,385 |
| Write requests | 51,433,247 |
| Read stall cycles | 683,978,829 |
| Write stall cycles | 206,021,945 |
| Read RAM-wait cycles | 154,141,581 |
| Write RAM-wait cycles | 154,588,698 |

The frozen bank reports valid/stopped. Its 59.886-second window includes about
44 seconds beyond CoreMark's timed region: shell/desktop activity, typing,
loading, initialization, printing and exit. The 94.90% hit rate and 45.68%/
13.76% read/write stall shares describe that whole window, not CoreMark alone.
This measurement does not identify instruction versus data accesses, write
hits, or the misses attributable to write invalidation.

The counts satisfy the implementation's identities exactly:
`read stalls = hits + 2 * misses + read RAM waits` and
`write stalls = write requests + write RAM waits`. A hit has the expected
one-cycle synchronous RAM lookup; a miss averages about 8.005 CPU stall cycles
and a write about 4.006. These are stalls, not complete instruction execution
times. The lookup cycle is not an established removable bottleneck.

An automated external wrapper could remove manual delays, but would still
include program loading/setup/output/exit. Exact timed-region statistics would
require an instrumented benchmark build. Neither wrapper mode nor modified
CoreMark is implemented in this utility.
