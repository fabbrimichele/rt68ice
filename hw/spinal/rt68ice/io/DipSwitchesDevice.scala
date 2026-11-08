package rt68ice.io

import rt68ice.core.M68KDeviceBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class DipSwitchesDevice() extends Component {
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val switches = in Bits(3 bits)
  }

  // Fixed bus wait states are handled by BusController.
  io.bus.ready := True

  // This device does not generate interrupt requests.
  io.bus.irq := False

  val switchesSync = BufferCC(io.switches, init = B"000")

  io.bus.dataIn := 0
  when(io.bus.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := (~switchesSync).resized
    }
  }
}
