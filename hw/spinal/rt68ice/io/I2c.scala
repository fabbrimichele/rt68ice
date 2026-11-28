package rt68ice.io

import spinal.core.{Analog, Bool, Bundle, in, inout, out}
import spinal.lib.IMasterSlave

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class I2c() extends Bundle with IMasterSlave {
  val sda = Analog(Bool())
  val scl = Analog(Bool())

  override def asMaster(): Unit = {
    inout(scl)
    inout(sda)
  }
}
