package rt68ice.memory

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class SdRamBB extends BlackBox {
  // Define IO
  val io = new Bundle {
    // CPU/Chipset interface
    val clk           = in Bool()
    val reset         = in Bool()         // Used to trigger start of FSM
    val init_complete = out Bool()        // SDRAM is done initializing

    // Port 0
    val p0_addr       = in Bits(25 bits)
    val p0_data       = in Bits(16 bits)
    val p0_byte_en    = in Bits(2 bits)   // Byte enable for writes
    val p0_q          = out Bits(16 bits)
    val p0_wr_req     = in Bool()
    val p0_rd_req     = in Bool()
    val p0_available  = out Bool()        // The port is ready to be used
    val p0_ready      = out Bool()        // The port has finished its task. Will rise for a single cycle

    // Interface to the SDRAM chip
    val SDRAM_DQ    = inout(Analog(Bits(16 bits)))  // Bidirectional data bus
    val SDRAM_A     = out Bits(13 bits)             // Address bus
    val SDRAM_DQM   = out Bits(2 bits)              // High/low byte mask
    val SDRAM_BA    = out Bits(2 bits)              // Bank select (single bits)
    val SDRAM_nCS   = out Bool()                    // Chip select, neg triggered
    val SDRAM_nWE   = out Bool()                    // Write enable, neg triggered
    val SDRAM_nRAS  = out Bool()                    // Select row address, neg triggered
    val SDRAM_nCAS  = out Bool()                    // Select column address, neg triggered
    val SDRAM_CKE   = out Bool()                    // Clock enable
    val SDRAM_CLK   = out Bool()                    // Chip clock
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.clk, reset = io.reset, resetActiveLevel = HIGH)

  setDefinitionName("sdram") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/sdram.v") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
