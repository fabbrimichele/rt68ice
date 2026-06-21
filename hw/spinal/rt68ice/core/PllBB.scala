package rt68ice.core

import spinal.core._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class PllBB extends BlackBox {
  val io = new Bundle {
    val clkin25   = in Bool()
    val clk_125 = out Bool()    // HDMI
    val clk_25 = out Bool()     // VGA
    val clk_31 = out Bool()     // System
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin25)

  setDefinitionName("pll") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
