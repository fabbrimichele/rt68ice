package rt68ice.core

import spinal.core._
import spinal.lib.{BufferCC, Timeout}

import scala.language.postfixOps

/** Runs in the free-running board clock domain, outside the reset it generates.
  * Filter active-low button presses before assertion; retain power-on reset and
  * hold reset after a qualified press is released.
  */
//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class Reset(holdTime: TimeNumber, pressTime: TimeNumber = 10 ms) extends Component {
  val io = new Bundle {
    val resetButton = in Bool()
    val resetOut = out Bool()
  }

  val pressedSync = BufferCC(!io.resetButton, init = False)
  val pressTimer = Timeout(pressTime)
  when(!pressedSync) {
    pressTimer.clear()
  }
  val pressedQualified = pressedSync && pressTimer.state

  // Starts unexpired at power-on. Only qualified presses can restart the hold;
  // short glitches must neither assert reset nor extend its release delay.
  val holdTimer = Timeout(holdTime)
  when(pressedQualified) {
    holdTimer.clear()
  }

  io.resetOut := pressedQualified || !holdTimer.state
}
