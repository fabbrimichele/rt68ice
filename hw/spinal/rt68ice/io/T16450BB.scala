package rt68ice.io

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class T16450BB extends BlackBox {
  // Define IO
  val io = new Bundle {
    val MR_n = in Bool() // Master Reset
    val XIn = in Bool()
    val RClk = in Bool()
    val CS_n = in Bool()
    val Rd_n = in Bool()
    val Wr_n = in Bool()
    val A = in Bits(3 bits)
    val D_In = in Bits(8 bits)
    val D_Out = out Bits(8 bits)
    val SIn = in Bool()
    val CTS_n = in Bool()
    val DSR_n = in Bool()
    val RI_n = in Bool()
    val DCD_n = in Bool()
    val SOut = out Bool()
    val RTS_n = out Bool()
    val DTR_n = out Bool()
    val OUT1_n = out Bool()
    val OUT2_n = out Bool()
    val BaudOut = out Bool()
    val Intr = out Bool()
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.XIn, reset = io.MR_n, resetActiveLevel = LOW)

  setDefinitionName("T16450") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/vhdl/T16450.vhd") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
