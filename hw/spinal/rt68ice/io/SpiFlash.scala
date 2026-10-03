package rt68ice.io

import spinal.core._
import spinal.lib.IMasterSlave

// The onboard flash clock is driven through USRMCLK, not a normal FPGA I/O.
case class SpiFlash() extends Bundle with IMasterSlave {
  val miso = Bool()
  val mosi = Bool()
  val cs   = Bool() // Active low.

  override def asMaster(): Unit = {
    in(miso)
    out(mosi, cs)
  }
}
