package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class LedArrayDevice() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val leds = out Bits (16 bits)
  }

  val ledsReg = Reg(Bits(16 bits)) init 0
  io.leds := ~ledsReg

  io.bus.dataIn := 0

  when(io.sel) {
    when(io.bus.wr) {
      // Write
      when (io.bus.lds) { ledsReg(7 downto 0) := io.bus.dataOut(7 downto 0) }
      when (io.bus.uds) { ledsReg(15 downto 8) := io.bus.dataOut(15 downto 8) }
    } otherwise {
      // Read
      io.bus.dataIn := ledsReg
    }
  }
}
