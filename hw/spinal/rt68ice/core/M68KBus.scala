package rt68ice.core

import rt68ice.core.M68KBus._
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

object M68KBus {
  val ADDRESS_WIDTH: Int = 32
  val DATA_WIDTH: Int = 16
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
abstract class M68KBusSignals extends Bundle with IMasterSlave {
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

/** CPU-facing bus: selection is supplied by the address decoder. */
case class M68KBus() extends M68KBusSignals

/** Common device responses; active-high IRQ is independent of selection. */
abstract class M68KDeviceBusSignals extends M68KBusSignals {
  val irq = Bool()
  // High when idle or when the device transfer can complete.
  val ready = Bool()

  override def asMaster(): Unit = {
    super.asMaster()
    in(irq, ready)
  }
}

/** Decoded device port. SEL retains the device's existing qualification rules. */
case class M68KDeviceBus() extends M68KDeviceBusSignals {
  val sel = Bool()

  override def asMaster(): Unit = {
    super.asMaster()
    out(sel)
  }
}

/** Video shares data/address signals across three separately decoded regions. */
case class M68KVideoBus() extends M68KDeviceBusSignals {
  val fbSel = Bool()
  val palSel = Bool()
  val ctrlSel = Bool()

  override def asMaster(): Unit = {
    super.asMaster()
    out(fbSel, palSel, ctrlSel)
  }
}
