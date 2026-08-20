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
    * **Low Memory:** The 8MB CPU window is contiguous from `0x000000` to `0x7FFFFF`, as expected by EmuTOS.
    * **Vector Table:** The first 16KB is overlaid by FPGA BRAM for high-speed exception, interrupt, stack and firmware-data access. The first eight bytes are read from ROM for the reset stack pointer and program counter.
    * **Main Memory:** The board's 32MB SDRAM is accessed through the 8MB low-memory CPU window. A larger window and cache can be added later.
* **ROM:**
    * The 16KB boot ROM is mapped at the Atari-compatible `0xFC0000` address, with its first eight bytes also visible at reset-vector addresses `0x000000-0x000007`.
* **Custom I/O:**
    * The dedicated framebuffer is at `0xE00000`; custom peripherals occupy 16KB windows beginning at `0xF00000`.
* **Graphic Modes:**
    * **Planar:** Hardware-accelerated bitplane support for EmuTOS compatibility.
    * **Chunky:** Linear 8-bit framebuffer for optimized DOOM rendering and uClinux console.
* **No MMU:**
    * Memory Management Unit omitted due to the current lack of a verified 68851 or 68030-compatible MMU softcore.
