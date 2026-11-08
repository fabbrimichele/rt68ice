package rt68ice.video

import spinal.core.{IntToBuilder, out}
import spinal.lib.IMasterSlave
import spinal.lib.experimental.chisel.Bundle

import scala.language.postfixOps

//noinspection SpellCheckingInspection
//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Gpdi() extends Bundle with IMasterSlave {
  val dp = out Bits(4 bits)
  val dn = out Bits(4 bits)

  override def asMaster(): Unit = {
    out(dp)
    out(dn)
  }
}
