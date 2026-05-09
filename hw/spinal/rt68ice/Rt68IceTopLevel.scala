package rt68ice

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Rt68IceTopLevel() extends Component {
  val io = new Bundle {
    val led = out Bits(3 bits)
  }

  val clockCounter = Reg(UInt(24 bits)).init(0)
  val ledCounter = Reg(UInt(3 bits)) init 0

  when(clockCounter === 12_500_000) {
    clockCounter := 0
    ledCounter := ledCounter + 1
  } otherwise {
    clockCounter := clockCounter + 1
  }

  io.led := ledCounter.asBits
}

object Rt68IceTopLevelVerilog extends App {
  Config.spinal.generateVerilog(Rt68IceTopLevel())
}

