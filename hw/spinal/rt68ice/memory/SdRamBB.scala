package rt68ice.memory

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class SdRamBB extends BlackBox {
  // Define IO
  val io = new Bundle {
    // Interface to the SDRAM chip
    val sd_data = inout(Analog(Bits(16 bits)))  // 16 bit bidirectional data bus
    val sd_addr = out Bits(13 bits)             // 13 bit multiplexed address bus
    val sd_dqm  = out Bits(2 bits)              // two byte masks
    val sd_ba   = out Bits(2 bits)              // two banks
    val sd_cs   = out Bool()                    // a single chip select
    val sd_we   = out Bool()                    // write enable
    val sd_ras  = out Bool()                    // row address select
    val sd_cas  = out Bool()                    // columns address select
    val sd_clk  = out Bool()

    // CPU/Chipset interface
    val init      = in Bool()   // init signal after FPGA config to initialize RAM
    val clk_96    = in Bool()   // SDRAM is accessed at 96 MHz
    val clk_8_en  = in Bool()   // 8MHz chipset clock to which SDRAM state machine is synchronized

    val din     = in Bits(16 bits)    // data input from chipset/cpu
    val dout64  = out Bits(64 bits)   // data output to chipset/cpu
    val dout    = out Bits(16 bits)   // data output to chipset/cpu
    val addr    = in Bits(24 bits)    // 24 bit word address
    val ds      = in Bits(2 bits)     // upper/lower data strobe
    val req     = in Bool()           // cpu/chipset requests read/write
    val we      = in Bool()           // cpu/chipset requests write

    val rom_oe    = in Bool()
    val rom_addr  = in Bits(24 bits)
    val rom_dout  = out Bits(16 bits)
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.clk_96, reset = io.init, resetActiveLevel = HIGH)

  setDefinitionName("sdram") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/sdram.v") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
