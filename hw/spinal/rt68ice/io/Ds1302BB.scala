package rt68ice.io

import spinal.core._

import scala.language.postfixOps

class Ds1302BB extends BlackBox {
  val io = new Bundle {
    val reset = in Bool()
    val clk   = in Bool()
    val nCE   = in Bool()
    val nRD   = in Bool()
    val nWR   = in Bool()
    val DI    = in Bits(8 bits)
    val DO    = out Bits(8 bits)
    val A     = in Bits(6 bits)
    val BUSY  = out Bool()
    val CE    = out Bool()
    val SCLK  = out Bool()
    val IO    = inout(Analog(Bool()))
  }

  mapClockDomain(clock = io.clk, reset = io.reset, resetActiveLevel = HIGH)

  setDefinitionName("DS1302") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/vhdl/microbasic_package.vhd")
  addRTLPath("hw/vhdl/DS1302.vhd")
  noIoPrefix() // Remove io_ prefix
}
