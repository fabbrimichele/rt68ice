# Video controller

The framebuffer starts at `0x00E00000`, the palette at `0x00F08000`, and the
video control registers at `0x00F0C000`. Control registers use 16-bit accesses.

| Offset | Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x00` | CONTROL | R/W | Bits 1-0 select 320x240 8bpp (`0`), 640x240 4bpp (`1`), 640x480 2bpp (`2`), or 640x480 1bpp (`3`) |
| `0x02` | IRQ_STATUS | R/C | Bit 0 vertical-blank interrupt pending; reading acknowledges the interrupt |
| `0x04` | IRQ_ENABLE | R/W | Bit 0 vertical-blank interrupt enable |

The 640x480 1bpp mode stores 40 big-endian 16-bit words per scanline, for a
total of 19,200 words (37.5 KiB). Within each word, bit 15 is the leftmost
pixel. Clear and set pixels select palette entries 0 and 1 respectively.

Vertical blank is latched once per physical VGA frame as the raster leaves the
last visible pixel. Pending state is recorded even while the interrupt is
disabled, matching the timer and USB devices. If a new vertical blank occurs
on the same system-clock edge that reads `IRQ_STATUS`, the new event remains
pending.

The video interrupt uses 68000 autovector level 4. Enabling it therefore
requires a handler at vector 28 (`0x70`). The UART uses autovector level 3.
