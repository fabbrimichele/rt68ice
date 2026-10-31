package rt68ice.io

import spinal.core.{Analog, Bool, Bundle, inout, out}
import spinal.lib.IMasterSlave

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Ds1302() extends Bundle with IMasterSlave {
  val ce = Bool()
  val sclk = Bool()
  val io = Analog(Bool())

  override def asMaster(): Unit = {
    out(ce, sclk)
    inout(io)
  }
}
