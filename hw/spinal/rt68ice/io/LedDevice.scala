package rt68ice.io

import rt68ice.core.M68KBus
import rt68ice.core.M68KBus.DATA_WIDTH
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class LedDevice(width: Int = 3) extends Component {
  val io = new Bundle {
    val bus = slave(M68KBus())
    val sel = in Bool()
    val led = out Bits (width bits)
  }

  val ledReg = Reg(Bits(DATA_WIDTH bits)) init 0
  io.led := ledReg(width - 1 downto 0)

  io.bus.dataIn := 0

  when(io.sel) {
    when(io.bus.wr) {
      // Write
      ledReg := io.bus.dataOut
    } otherwise {
      // Read
      io.bus.dataIn := ledReg
    }
  }
}
