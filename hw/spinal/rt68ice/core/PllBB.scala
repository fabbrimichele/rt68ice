package rt68ice.core

import spinal.core._

class PllBB extends BlackBox {
  val io = new Bundle {
    val clkin   = in Bool()   // 25 MHz, 0 deg
    val clkout0 = out Bool()  // 20 MHz, 0 deg
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin)

  setDefinitionName("pll_i25_o20") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll_i25_o20.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
