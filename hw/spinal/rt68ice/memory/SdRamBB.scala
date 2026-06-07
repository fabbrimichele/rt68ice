package rt68ice.memory

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class SdRamBB extends BlackBox {
  // Define IO
  val io = new Bundle {
    // Interface to the SDRAM chip
    val sd_clk  = out Bool()
    val sd_cke  = out Bool()
    val sd_data = inout(Analog(Bits(16 bits)))  // 16 bit bidirectional data bus
    val sd_addr = out Bits(13 bits)             // 13 bit multiplexed address bus
    val sd_dqm  = out Bits(2 bits)              // two byte masks
    val sd_ba   = out Bits(2 bits)              // two banks
    val sd_cs   = out Bool()                    // a single chip select
    val sd_we   = out Bool()                    // write enable
    val sd_ras  = out Bool()                    // row address select
    val sd_cas  = out Bool()                    // columns address select

    // CPU/Chipset interface
    val clk     = in Bool()         // SDRAM is accessed at 32 MHz
    val reset_n = in Bool()         // init signal after FPGA config to initialize RAM
    val ready   = out Bool()        // ram is ready and has been initialized
    val refresh = in Bool()         // chipset requests a refresh cycle
    val din     = in Bits(16 bits)  // data input from chipset/cpu
    val dout    = out Bits(16 bits) // data output to chipset/cpu
    val addr    = in Bits(22 bits)  // 22 bit word address
    val ds      = in Bits(2 bits)   // upper/lower data strobe
    val cs      = in Bool()         // cpu/chipset requests read/write
    val we      = in Bool()         // cpu/chipset requests write
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.clk, reset = io.reset_n, resetActiveLevel = LOW)

  setDefinitionName("sdram") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/sdram.v") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
