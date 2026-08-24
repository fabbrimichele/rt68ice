# Programmable timer

The timer is mapped at `0x00F1C000` and uses 16-bit registers. It is a 32-bit
countdown timer whose input clock is divided by `DIVIDER + 1`. For a non-zero
reload value, an interval is therefore `(DIVIDER + 1) * RELOAD` system-clock
cycles. A reload value of zero expires on the first divided tick.

| Offset | Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x00` | CONTROL | R/W | Bit 0 enable, bit 1 auto-reload, bit 2 interrupt enable, bit 3 reload command (write only) |
| `0x02` | STATUS | R/W1C | Bit 0 interrupt pending, bit 1 running; write bit 0 to acknowledge the interrupt |
| `0x04` | DIVIDER_HI | R/W | Divider bits 31-16 |
| `0x06` | DIVIDER_LO | R/W | Divider bits 15-0 |
| `0x08` | RELOAD_HI | R/W | Reload value bits 31-16 |
| `0x0A` | RELOAD_LO | R/W | Reload value bits 15-0 |
| `0x0C` | VALUE_HI | R | Current value bits 31-16; also latches the low word |
| `0x0E` | VALUE_LO | R | Low word latched by the preceding `VALUE_HI` read |

Setting enable on a stopped timer loads `RELOAD` and resets the divider phase.
Writing CONTROL bit 3 reloads explicitly. On expiry, STATUS bit 0 remains set
until acknowledged even if interrupts are disabled. In one-shot mode the timer
stops; in auto-reload mode it starts the next interval immediately.

At the default 25 MHz system clock, a 200 Hz EmuTOS tick can use `DIVIDER = 124`
and `RELOAD = 1000` (or any other pair whose product is 125,000). The timer uses
68000 autovector interrupt level 5.
