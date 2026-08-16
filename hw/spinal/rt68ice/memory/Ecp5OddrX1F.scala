package rt68ice.memory

import spinal.core._

// ECP5 DDR output register used to forward clocks through a dedicated I/O cell.
//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class Ecp5OddrX1F extends BlackBox {
  val io = new Bundle {
    val D0   = in Bool()
    val D1   = in Bool()
    val SCLK = in Bool()
    val RST  = in Bool()
    val Q    = out Bool()
  }

  setDefinitionName("ODDRX1F")
  noIoPrefix()
}
