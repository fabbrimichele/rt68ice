# Reset button filtering

The external `btn_rst` input is active-low. `ClockCtrl` runs `Reset` in the
free-running 25 MHz board-clock domain, outside the reset domains it drives.
A two-stage `BufferCC` synchronizes the button before it reaches the timers.

## Button timing

A button press must remain asserted continuously for **10 ms after
synchronization** before it can assert reset. At 25 MHz this is 250,000 board
clock cycles, plus the input synchronizer latency. Any sampled release before
qualification clears the press timer; separate short presses do not accumulate.
There is no direct asynchronous path from the raw button to reset assertion.

A qualified press holds reset for as long as the button remains pressed.
After its synchronized release, reset remains asserted for **20 ms** (500,000
board clock cycles). A short bounce during that hold does not restart it;
a new fully qualified press does. A deliberate reset therefore needs a press
slightly longer than 10 ms, while a normal manual press should be unaffected.

The hold timer starts unexpired at FPGA configuration, so power-on reset still
lasts 20 ms without requiring a button press. If the button is held at startup,
its 10 ms qualification completes within the 20 ms power-on hold and keeps
reset asserted until release plus the normal hold interval.

`ClockCtrl` configures both intervals explicitly:

```scala
Reset(holdTime = 20 ms, pressTime = 10 ms)
```

## PLL resets and scope

Each generated clock domain still requests reset from the filtered/power-on
reset **or** loss of PLL lock. PLL-lock loss bypasses button qualification;
the existing `ResetCtrl.asyncAssertSyncDeassert` wiring is unchanged. The
filter's counters keep running from the board clock during system reset.
No PLL, clock-frequency, pin assignment, pull-up or cache policy changes are
included.

This rejects short sampled button glitches. It cannot reject an unwanted
input held low for 10 ms or more, replace reliable button wiring, or establish
the cause of a reboot. The earlier write-buffer YAART run rebooted without
reported memory errors; a breadboard button with long wires was identified
as a possible source, not a confirmed diagnosis.

## Verification

```sh
sbt 'runMain rt68ice.core.ResetSim' \
    'runMain rt68ice.Rt68IceTopLevelVerilog'
make rt68ice.bit
```

`ResetSim` uses the production 10/20 ms intervals with a 1 MHz BOOT clock
domain to reduce simulation cost. It checks power-on release, a raw pulse
entirely between sample edges, 0.1/1/5/9.999 ms presses, repeated short presses,
full press qualification, a long hold, the full release hold, short release
bounce, a qualified re-press during hold, idle timer wrap, and a button held
at power-on. Both simulation scenarios and integrated Verilog generation pass.
Generated 25 MHz top-level RTL retains the independent PLL-lock reset requests.
The PLL itself and analog input noise are not modeled by this simulation.

`make rt68ice.bit` completed successfully, including synthesis, placement,
routing and bitstream packing. The tool reported passing per-clock frequency
checks, including 25 MHz board/system and 50 MHz SDRAM clocks. This is not a
complete external SDRAM or related-clock timing signoff. The resulting local
bitstream SHA-256 is:

```text
d3e34b25185801a9190835c8e7ceed05bd55eaa267d6ff760c2617e9c7c9c602
```

On-board validation of this change remains pending. After programming, check
normal startup, a deliberate reset and a button held during startup, then run
a longer YAART test. The earlier six-pass clean retry and CoreMark scores were
measured before this filter and are not validation of the new bitstream.
