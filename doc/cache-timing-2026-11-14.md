# Cache integration timing review, 2026-11-14

The user ran the following command from the RT68ICE repository using the
existing synthesized netlist. It reroutes the design without programming the
board or replacing the normal build outputs.

```sh
nextpnr-ecp5 --25k --package CABGA256 --speed 6 \
  --json rt68ice.json \
  --lpf hw/constraints/Rt68IceTopLevel.lpf \
  --freq 30 \
  --textcfg /tmp/rt68ice-timing.config \
  --report /tmp/rt68ice-timing.json \
  --detailed-timing-report \
  > /tmp/rt68ice-timing.log 2>&1
```

The installed tool identifies itself as `nextpnr-0.8-34-g311a1a71`. Its log
ends with `Program finished normally.` The final report derives the actual
25/50 MHz system/SDRAM frequencies from the PLL, despite the command's
30 MHz fallback target.

## Final per-clock results

| Domain | Constraint | Reported Fmax | Result |
| --- | ---: | ---: | --- |
| System | 25 MHz | 29.12 MHz | PASS |
| SDRAM | 50 MHz | 77.27 MHz | PASS |
| VGA | 25 MHz | 45.77 MHz | PASS |
| HDMI | 125 MHz | 243.01 MHz | PASS |
| USB | 12 MHz | 70.74 MHz | PASS |

An earlier placement-stage system estimate fails at 25 MHz; the final routed
per-clock result above passes. Do not confuse the intermediate estimate with
the final result. These Fmax numbers are not recommendations to raise clocks.

Reported resources: **51/56 DP16KD** block RAMs, **19,888/24,288 TRELLIS_COMB**
cells and **6,151/24,288 TRELLIS_FF** cells. Relative to the earlier inspected
49-block uncached netlist, the cache uses two additional block RAMs.

## Cross-domain paths: not signed off

| Launch domain | Capture domain | Reported maximum path delay |
| --- | --- | ---: |
| System, positive edge | SDRAM, positive edge | 25.528 ns |
| SDRAM, positive edge | System, positive edge | 22.153 ns |

The system-to-SDRAM path starts at
`m68K_1.tg68kernel.rf_dest_addr_LUT4_D_Z_TRELLIS_FF_DI.Q` and ends at
`sdRamDevice_1.sdRamCtrl_1.address_TRELLIS_FF_Q_9.CE`.
The reverse path starts at
`sdRamDevice_1.io_bus_address_regNext_TRELLIS_FF_Q_26.Q` and ends at
`m68K_1.tg68kernel.alu.micro_state_TRELLIS_FF_Q_2.LSR`.
These are whole logic paths, not merely the cache/RAM data wires.

The nominal PLL clocks are related, positive-edge clocks with aligned edges
and periods 40 ns and 20 ns. Their shortest strictly subsequent edge spacing
is 20 ns. Both listed maxima exceed that interval. The report lists these as
cross-clock maximum delays, without explicit required times or setup/hold
slack for those paths. Passing per-clock checks is therefore insufficient to
claim related-clock timing closure. Any proposed multicycle or false-path
exception must be justified by the actual handshake and path sensitization;
none is established by this review. Maximum-delay figures do not verify hold
requirements. Do not mask the issue by disabling crossing checks or declaring
all paths false.

Next steps are to establish correct timing constraints and analyze setup/hold
for the real handshake, or revise/register the boundary so the transfer has
provable timing. Successful monitor, YAARTTT, CoreMark and SD tests are useful
hardware evidence but do not replace that analysis. The earlier unrepeatable
Desk reset has not been linked to these paths.

## Artifact provenance

The generated `/tmp/rt68ice-timing.config` is byte-identical to the current
repository `rt68ice.config`. This ties the inspected report to the current
routed configuration, but does not prove what is physically loaded in the FPGA.
Raw artifacts remain at the local paths above; the multi-megabyte report is
not copied into the repository. SHA-256 hashes:

| Artifact | SHA-256 |
| --- | --- |
| `rt68ice.json` | `519483891f8a5b108b60a79e85846ba356f880a6efed8083472f0c1109b5dff1` |
| `rt68ice-timing.log` | `ebf047968fdab9310cec1d722847190fda7faec9f3897f053d14af593fc9ca68` |
| `rt68ice-timing.json` | `abfe98a97f1156802dc33989f4133d940feeee90603146270e67933c044a5375` |
| `rt68ice-timing.config` | `2740d824cc236fd9f2c3438568728366e24579e4e251f5b5e66386603b86305c` |
