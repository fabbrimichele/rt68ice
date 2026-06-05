package rt68ice.core

import spinal.core._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class PllBB extends BlackBox {
  val io = new Bundle {
    val clkin25   = in Bool()     // 25 MHz, input
    val clk_125_hdmi = out Bool() // 125 MHz, 0 deg
    val clk_25_vga = out Bool()   // 25 MHz, 0 deg
    val clk_20_cpu = out Bool()   // 20 MHz, 0 deg
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin25)

  setDefinitionName("pll") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
