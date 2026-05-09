# 68k-FPGA Homebrew Computer

## Project Goals
Design and implement a high-performance Motorola 680x0-compatible System-on-a-Chip (SoC) using SpinalHDL, targeting the iCESugar Pro (Lattice ECP5-25F).
* **EmuTOS:** Boot a fully functional GUI for classic Atari-style productivity.
* **uClinux:** Run a multitasking POSIX environment (No-MMU variant).
* **DOOM:** Execute the 1993 classic via uClinux or bare-metal.

## Architectural Specification
* **Processor: [TG68K](https://github.com/TobiFlex/TG68K.C/tree/master)**
    * **68020 Configuration:** Enables 32-bit internal longword operations, improved bitfield instructions, and advanced addressing modes.
    * **Data Bus:** 16-bit external; direct 1:1 mapping to the iCESugar Pro’s 16-bit wide SDRAM chip.
* **RAM:**
    * **Vector Table:** 0x0 - 0x400 mapped to FPGA BRAM for high-speed exception and interrupt handling (bypassing SDRAM latency).
    * **Main Memory:** 32MB SDRAM with a dedicated controller and L1 cache to mitigate row-access latencies and burst-mode penalties.
* **ROM:**
    * System firmware stored in iCESugar SPI Flash; shadowed to SDRAM at boot for high-speed execution or executed via eXecute-In-Place (XIP).
* **Graphic Modes:**
    * **Planar:** Hardware-accelerated bitplane support for EmuTOS compatibility.
    * **Chunky:** Linear 8-bit framebuffer for optimized DOOM rendering and uClinux console.
* **No MMU:**
    * Memory Management Unit omitted due to the current lack of a verified 68851 or 68030-compatible MMU softcore.
