package rt68ice.core

import spinal.core._

import scala.annotation.unused
import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class TG68KdotCBB extends BlackBox {
  // TODO: Add  generics
  // addGeneric("MUL_Hardware", hardwareMul)

  val io = new Bundle {
    val clk						  = in Bool()
    val nReset				  = in Bool()
    val clkena_in			  = in Bool()
    val data_in					= in Bits(16 bits)
    val IPL						  = in Bits(3 bits)
    val IPL_autovector  = in Bool()
    val berr					  = in Bool()         // only 68000 Stackpointer dummy
    val CPU						  = in Bits(2 bits)   // 00->68000  01->68010  11->68020(only some parts - yet)
    val addr_out				= out Bits(32 bits)
    val data_write			= out Bits(16 bits)
    val nWr						  = out Bool()
    val nUDS						= out Bool()
    val nLDS						= out Bool()
    val busstate				= out Bits(2 bits)  // 00-> fetch code 10->read data 11->write data 01->no memaccess
    val longword				= out Bool()
    val nResetOut				= out Bool()
    val FC							= out Bits(3 bits)
    val clr_berr				= out Bool()
    // for debug
    val skipFetch				= out Bool()
    val regin_out				= out Bits(32 bits)
    val CACR_out				= out Bits(4 bits)
    val VBR_out					= out Bits(32 bits)
  }

  // Map the clock domain
  // Mapped in the wrapper
  mapClockDomain(clock = io.clk, reset = io.nReset, resetActiveLevel = LOW/*, enable = io.clkena_in*/)

  setDefinitionName("TG68KdotC_Kernel") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/vhdl/TG68K_ALU.vhd")   // Merge the file to the generated 'mergeRTL.vhdl' file
  addRTLPath("hw/vhdl/TG68K_Pack.vhd")  // Merge the file to the generated 'mergeRTL.vhdl' file
  addRTLPath("hw/vhdl/TG68KdotC_Kernel.vhd")  // Merge the file to the generated 'mergeRTL.vhdl' file
  noIoPrefix() // Remove io_ prefix
}
