package rt68ice.io

import spinal.core._

import scala.language.postfixOps

class SpiMasterBB extends BlackBox {
  val io = new Bundle {
    // CPU Interface Signals
    val clk      = in Bool()
    val reset    = in Bool()
    val cs       = in Bool()
    val rw       = in Bool()
    val addr     = in Bits(2 bits)
    val data_in  = in Bits(8 bits)
    val data_out = out Bits(8 bits)
    val irq      = out Bool()

    // SPI Interface Signals
    val spi_miso = in Bool()
    val spi_mosi = out Bool()
    val spi_clk  = out Bool()
    val spi_cs_n = out Bits(8 bits)
  }

  mapClockDomain(clock = io.clk, reset = io.reset, resetActiveLevel = HIGH)

  setDefinitionName("spi_master") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/vhdl/spi_master.vhd") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
