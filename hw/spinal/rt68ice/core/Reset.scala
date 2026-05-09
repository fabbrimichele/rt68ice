package rt68ice.core

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Reset(resetCycles: Int) extends Component {
  val io = new Bundle {
    val resetOut = out Bool()
  }

  private val counter = Reg(UInt(log2Up(resetCycles + 1) bits)) init 0
  private val resetActive = Reg(Bool()) init True

  when(resetActive) {
    counter := counter + 1

    when(counter === resetCycles - 1) {
      resetActive := False
    }
  }

  io.resetOut := resetActive
}
