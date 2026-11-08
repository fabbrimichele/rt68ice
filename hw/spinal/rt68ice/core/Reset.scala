package rt68ice.core

import spinal.core._
import spinal.lib.{BufferCC, Timeout}

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Reset(time: TimeNumber) extends Component {
  val io = new Bundle {
    val resetButton = in Bool()
    val resetOut = out Bool()
  }

  val timer = Timeout(time)
  val pressedRaw = !io.resetButton
  val pressedSync = BufferCC(pressedRaw, init = True)

  when (pressedSync) {
    timer.clear()
  }

  io.resetOut := pressedRaw || pressedSync || !timer.state
}
