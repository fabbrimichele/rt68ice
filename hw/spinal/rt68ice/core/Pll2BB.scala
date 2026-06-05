package rt68ice.core

import spinal.core._

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class Pll2BB extends BlackBox {
  val io = new Bundle {
    val clkin25   = in Bool()       // 25 MHz input oscillator
    val clk_125_hdmi = out Bool()   // 125.00 MHz, 0 deg phase shift for HDMI
    val clk_93_sdram = out Bool()   // 93.75 MHz, 90 deg phase shift for SDRAM
    val clk_25_vga = out Bool()     // 25.00 MHz, 0 deg phase shift for VGA
    val clk_7_cpu = out Bool()      // 7.8125 MHz, 0 deg phase shift for M68K CPU
    val locked  = out Bool()
  }

  mapClockDomain(clock = io.clkin25)

  setDefinitionName("pll") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/verilog/pll2.v")   // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
