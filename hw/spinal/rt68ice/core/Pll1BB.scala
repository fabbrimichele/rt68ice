package rt68ice.core

import spinal.core._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class Pll1BB extends BlackBox {
  val io = new Bundle {
    val clkin25       = in Bool()   // 25 MHz input oscillator
    val clk_96_sdram  = out Bool()  // 96 MHz, 90 deg phase shift for SDRAM
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin25)

  setDefinitionName("pll1")          // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll1.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix()                      // Remove io_ prefix
}
