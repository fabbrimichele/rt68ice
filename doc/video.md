# Video controller

The framebuffer starts at `0x00E00000`, the palette at `0x00F08000`, and the
video control registers at `0x00F0C000`. Control registers use 16-bit accesses.

| Offset | Name | Access | Description |
| :---: | :--- | :---: | :--- |
| `0x00` | CONTROL | R/W | Bits 3-0 select the screen mode; see the table below |
| `0x02` | IRQ_STATUS | R/C | Bit 0 vertical-blank interrupt pending; reading acknowledges the interrupt |
| `0x04` | IRQ_ENABLE | R/W | Bit 0 vertical-blank interrupt enable |

| Mode | Resolution | Bitplanes | Words per line | Framebuffer size |
| :---: | :---: | :---: | ---: | ---: |
| `0` | 320x200 | 4 | 80 | 31.25 KiB |
| `1` | 640x200 | 2 | 80 | 31.25 KiB |
| `2` | 640x400 | 1 | 40 | 31.25 KiB |
| `3` | 320x240 | 4 | 80 | 37.5 KiB |
| `4` | 640x240 | 2 | 80 | 37.5 KiB |
| `5` | 640x480 | 1 | 40 | 37.5 KiB |
| `6` | 320x240 | 8 | 160 | 75 KiB |
| `7` | 640x240 | 4 | 160 | 75 KiB |
| `8` | 640x480 | 2 | 80 | 75 KiB |
| `9`-`15` | 640x480 | 1 | 40 | 37.5 KiB |

The framebuffer is word-interleaved by bitplane: each 16-pixel group stores
one big-endian 16-bit word per plane before the next group begins. Within each
word, bit 15 is the leftmost pixel. The one-bitplane modes select palette
entries 0 and 1 for clear and set pixels respectively.

All modes use the existing 640x480 output timing. Modes 0–2 center a 640x400
image in that raster, with 40 physical black lines above and below it. Mode 0
doubles each source pixel horizontally and vertically; mode 1 doubles lines;
mode 2 uses one output pixel per source pixel. Borders are forced to RGB black
after the palette lookup, independent of palette entry zero, and remain part of
the physical active-video window. No border pixels are stored in the framebuffer.
Each new mode uses exactly 32,000 bytes. Monochrome means one bit per pixel,
selecting palette entries 0 and 1.

The control field is four bits wide. Modes 0–2 are the Atari ST dimensions;
modes 3–8 are the extended dimensions. Reset selects mode 0 (320x200, 16 colors).
The IDs have changed: software using older numeric IDs must be rebuilt or
updated to use this table.
Values 9–15 fall back to 640x480 monochrome. The palette and framebuffer base
addresses are unchanged. Mode changes should be made during vertical blank.

For example, select the new modes from the ROM monitor with one of:

```text
write F0C000 0000
write F0C000 0001
write F0C000 0002
```

Use the `VIDEO_MODE_*` constants in `sw/lib/asm/mem_map_video.asm` from assembly.
Software must use the matching source dimensions and line stride. EmuTOS needs
its own driver update to expose these modes; that is outside this repository.

Vertical blank is latched once per physical VGA frame as the raster leaves the
last visible pixel. Pending state is recorded even while the interrupt is
disabled, matching the timer and USB devices. If a new vertical blank occurs
on the same system-clock edge that reads `IRQ_STATUS`, the new event remains
pending.

The video interrupt uses 68000 autovector level 4. Enabling it therefore
requires a handler at vector 28 (`0x70`). The UART uses autovector level 3.
