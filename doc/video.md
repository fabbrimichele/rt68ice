# Video controller

The framebuffer starts at `0x00E00000`, the palette at `0x00F08000`, and the
video control registers at `0x00F0C000`. Control registers use 16-bit accesses.

| Offset | Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x00` | CONTROL | R/W | Bits 2-0 select the screen mode; see the table below |
| `0x02` | IRQ_STATUS | R/C | Bit 0 vertical-blank interrupt pending; reading acknowledges the interrupt |
| `0x04` | IRQ_ENABLE | R/W | Bit 0 vertical-blank interrupt enable |

| Mode | Resolution | Bitplanes | Words per line | Framebuffer size |
| :---: | :---: | :---: | ---: | ---: |
| `0` | 320x240 | 4 | 80 | 37.5 KiB |
| `1` | 640x240 | 2 | 80 | 37.5 KiB |
| `2` | 640x480 | 1 | 40 | 37.5 KiB |
| `3` | 320x240 | 8 | 160 | 75 KiB |
| `4` | 640x240 | 4 | 160 | 75 KiB |
| `5` | 640x480 | 2 | 80 | 75 KiB |
| `6`-`7` | 640x480 | 1 | 40 | 37.5 KiB |

The framebuffer is word-interleaved by bitplane: each 16-pixel group stores
one big-endian 16-bit word per plane before the next group begins. Within each
word, bit 15 is the leftmost pixel. The one-bitplane modes select palette
entries 0 and 1 for clear and set pixels respectively.

Vertical blank is latched once per physical VGA frame as the raster leaves the
last visible pixel. Pending state is recorded even while the interrupt is
disabled, matching the timer and USB devices. If a new vertical blank occurs
on the same system-clock edge that reads `IRQ_STATUS`, the new event remains
pending.

The video interrupt uses 68000 autovector level 4. Enabling it therefore
requires a handler at vector 28 (`0x70`). The UART uses autovector level 3.
