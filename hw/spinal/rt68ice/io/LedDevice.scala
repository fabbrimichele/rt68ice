package rt68ice.io

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class LedDevice() extends Component {
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val led = out Bits (3 bits)
  }

  // This device does not generate interrupt requests.
  io.bus.irq := False

  val ledReg = Reg(Bits(3 bits)) init 0
  io.led := ledReg

  io.bus.dataIn := 0

  when(io.bus.sel) {
    when(io.bus.wr) {
      // Write
      ledReg := io.bus.dataOut(10 downto 8)
    } otherwise {
      // Read
      io.bus.dataIn(10 downto 8) := ledReg
    }
  }
}
