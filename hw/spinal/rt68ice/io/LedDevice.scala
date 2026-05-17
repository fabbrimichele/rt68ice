package rt68ice.io

import rt68ice.core.M68KBus
import rt68ice.core.M68KBus.DATA_WIDTH
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class LedDevice() extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val led = out Bits (3 bits)
  }

  val ledReg = Reg(Bits(3 bits)) init 0
  io.led := ledReg

  io.bus.dataIn := 0

  when(io.sel) {
    when(io.bus.wr) {
      // Write
      ledReg := io.bus.dataOut(10 downto 8)
    } otherwise {
      // Read
      io.bus.dataIn(10 downto 8) := ledReg
    }
  }
}
