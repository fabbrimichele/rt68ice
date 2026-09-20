package rt68ice.io

import spinal.core.{Bool, Bundle, in, out}
import spinal.lib.IMasterSlave

case class Spi() extends Bundle with IMasterSlave {
  val clk = Bool()
  val miso = Bool()
  val mosi = Bool()
  val cs = Bool()

  override def asMaster(): Unit = {
    out(clk)
    in(miso)
    out(mosi)
    out(cs)
  }
}
