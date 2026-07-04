package rt68ice.core

import spinal.core._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class PllBB extends BlackBox {
  val io = new Bundle {
    val clkin_25   = in Bool()
    val clk_hdmi = out Bool()
    val clk_vga = out Bool()
    val clk_cpu = out Bool()
    val clk_sdram = out Bool()
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin_25)

  setDefinitionName("pll") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
