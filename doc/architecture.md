# 68k-FPGA Homebrew Computer

## Internal bus bundles

`M68KBusSignals` defines the common address, data, direction, and byte-strobe
signals. `M68KBus` is the CPU-facing interface. `M68KDeviceBusSignals` adds the common
interrupt response for peripheral bundles. Decoded peripherals use
`M68KDeviceBus`, which adds `sel`, an active-high device-to-controller `irq`, and `ready`,
so data, selection, interrupt requests, and wait handling connect together
with `<>`. Video uses `M68KVideoBus`, containing the same common signals plus
`fbSel`, `palSel`, and `ctrlSel` for its three address regions, plus `irq` and `ready`.
Devices without interrupts explicitly drive `irq` low. Interrupt requests are
independent of selection; masking, acknowledgement, and clock-domain crossing
remain within the existing device logic. Interrupt priority remains centralized
in `BusController` (USB 6, timer 5, video 4, UART 3).

The address map and decoding priority remain centralized in `BusController`.
It also preserves the separate gated-write and held-direction wiring groups.
RTC selection includes CPU data-cycle qualification before it reaches the
device. Bundling these signals does not add registers or change the device
handshakes.

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
    * **Low Memory:** The 14MB CPU window is contiguous from `0x000000` to `0xDFFFFF`.
    * **Vector Table:** The first 16KB is overlaid by FPGA BRAM for high-speed exception, interrupt, stack and firmware-data access. The first eight bytes are read from ROM for the reset stack pointer and program counter.
    * **Main Memory:** The board's 32MB SDRAM is accessed through the 14MB low-memory CPU window. A larger window and cache can be added later.
* **ROM:**
    * The 16KB boot ROM is mapped at the Atari-compatible `0xFC0000` address, with its first eight bytes also visible at reset-vector addresses `0x000000-0x000007`.
* **Custom I/O:**
    * The dedicated framebuffer is at `0xE00000`; custom peripherals occupy 16KB windows beginning at `0xF00000`.
* **Graphic Modes:**
    * **Planar:** Hardware-accelerated bitplane support for EmuTOS compatibility.
    * **Chunky:** Linear 8-bit framebuffer for optimized DOOM rendering and uClinux console.
* **No MMU:**
    * Memory Management Unit omitted due to the current lack of a verified 68851 or 68030-compatible MMU softcore.

Device `ready` signals are high while idle or when a transfer can complete.
Devices with no extended waits drive `ready` high; SDRAM, PSG, and RTC use
their existing completion logic. `BusController` ANDs every device's `ready`
with its own fixed-wait condition to produce the final CPU `clockEn`. The top
level connects that result directly to the CPU. Peripheral state machines keep
running while CPU advancement is held.
