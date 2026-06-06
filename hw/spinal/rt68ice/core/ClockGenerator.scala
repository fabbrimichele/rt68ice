package rt68ice.core

import spinal.core._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
class ClockGenerator extends Component {
  val io = new Bundle {
    val mhz8_en = out Bool() // Strobe for your internal 32MHz logic
  }

  // 2-bit counter gives exactly 4 states (0 to 3)
  val counter = Reg(UInt(2 bits)) init(0)

  // Increment on every 32MHz rising edge
  counter := counter + 1

  // Active for exactly 1 master clock cycle out of 4
  io.mhz8_en := (counter === 3)
}