package rt68ice.io

import spinal.core.{Analog, Bool, Bundle, inout}
import spinal.lib.IMasterSlave

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Usb() extends Bundle with IMasterSlave {
  val dp = Analog(Bool()) // USB D+
  val dm = Analog(Bool()) // USB D-

  override def asMaster(): Unit = {
    inout(dp)
    inout(dm)
  }
}