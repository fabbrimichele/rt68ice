# Video controller

The framebuffer starts at `0x00E00000`, the palette at `0x00F08000`, and the
video control registers at `0x00F0C000`. Control registers use 16-bit accesses.

| Offset | Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x00` | CONTROL | R/W | Bits 1-0 select 320x240 8bpp, 640x240 4bpp, or 640x480 2bpp |
| `0x02` | IRQ_STATUS | R/C | Bit 0 vertical-blank interrupt pending; reading acknowledges the interrupt |
| `0x04` | IRQ_ENABLE | R/W | Bit 0 vertical-blank interrupt enable |

Vertical blank is latched once per physical VGA frame as the raster leaves the
last visible pixel. Pending state is recorded even while the interrupt is
disabled, matching the timer and USB devices. If a new vertical blank occurs
on the same system-clock edge that reads `IRQ_STATUS`, the new event remains
pending.

The video interrupt uses 68000 autovector level 4. Enabling it therefore
requires a handler at vector 28 (`0x70`). The UART uses autovector level 3.
