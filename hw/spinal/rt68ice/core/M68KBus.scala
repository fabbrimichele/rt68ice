package rt68ice.core

import rt68ice.core.M68KBus._
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

object M68KBus {
  private val ADDRESS_WIDTH: Int = 32
  val DATA_WIDTH: Int = 16
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class M68KBus() extends Bundle with IMasterSlave {
  val address = Bits(ADDRESS_WIDTH bits)
  val dataOut = Bits(DATA_WIDTH bits)
  val dataIn = Bits(DATA_WIDTH bits)
  val wr = Bool()
  val uds = Bool()
  val lds = Bool()

  override def asMaster(): Unit = {
    out(address, dataOut, uds, lds, wr)
    in(dataIn)
  }
}