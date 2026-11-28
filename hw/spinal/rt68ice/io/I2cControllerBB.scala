package rt68ice.io

import spinal.core.{Analog, BlackBox, Bool, Bundle, HIGH, IntToBuilder, in, inout, out}

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class I2cControllerBB  extends BlackBox {
  val io = new Bundle {
    val clock = in Bool()
    val reset = in Bool()
    val trigger = in Bool()
    val restart = in Bool()
    val last_byte = in Bool()
    val address = in Bits(7 bits)
    val read_write = in Bool()
    val write_data = in Bits(8 bits)
    val read_data = out Bits(8 bits)
    val ack_error = out Bool()
    val busy = out Bool()
    val scl = inout(Analog(Bool()))
    val sda = inout(Analog(Bool()))
  }

  mapClockDomain(clock = io.clock, reset = io.reset, resetActiveLevel = HIGH)

  setDefinitionName("i2c_controller") // Match the VHDL entity name.
  addRTLPath("hw/vhdl/i2c.vhd") // Include the VHDL implementation.
  noIoPrefix() // Remove io_ prefix
}

