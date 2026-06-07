package rt68ice.memory

import spinal.core._
import spinal.lib.IMasterSlave

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class SdRam() extends Bundle with IMasterSlave {
  val dq    = Analog(Bits(16 bits))  // 16 bit bidirectional data bus
  val a     = Bits(13 bits)          // 13 bit multiplexed address bus
  val dm    = Bits(2 bits)           // two byte masks
  val ba    = Bits(2 bits)           // two banks
  val cs_n  = Bool()                 // a single chip select
  val we_n  = Bool()                 // write enable
  val ras_n = Bool()                 // row address select
  val cas_n = Bool()                 // columns address select
  val cke   = Bool()
  val clock = Bool()

  override def asMaster(): Unit = {
    inout(dq)
    out(a)
    out(dm)
    out(ba)
    out(cs_n)
    out(we_n)
    out(ras_n)
    out(cas_n)
    out(cke)
    out(clock)
  }
}

