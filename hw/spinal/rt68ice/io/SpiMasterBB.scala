package rt68ice.io

import spinal.core._

import scala.language.postfixOps

class SpiMasterBB extends BlackBox {
  val io = new Bundle {
    // Generic 16-bit register interface; CPU bus decoding belongs in the wrapper.
    // CS enables register access, RW is high for reads and low for writes.
    // ADDR: 0 = data, 1 = command/status, 2 = configuration, 3 = reserved.
    val clk      = in Bool()
    val reset    = in Bool()
    val cs       = in Bool()
    val rw       = in Bool()
    val addr     = in Bits(2 bits)
    val data_in  = in Bits(16 bits)
    val data_out = out Bits(16 bits)
    val irq      = out Bool()

    // Serial interface; spi_cs_n contains the active-low device chip selects.
    val spi_miso = in Bool()
    val spi_mosi = out Bool()
    val spi_clk  = out Bool()
    val spi_cs_n = out Bits(8 bits)
  }

  mapClockDomain(clock = io.clk, reset = io.reset, resetActiveLevel = HIGH)

  setDefinitionName("spi_master") // Match the VHDL entity name.
  addRTLPath("hw/vhdl/spi_master.vhd") // Include the VHDL implementation.
  noIoPrefix() // Remove io_ prefix
}
