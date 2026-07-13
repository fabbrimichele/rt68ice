package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class UsbDevice(usbCd: ClockDomain) extends Component {
  val io = new Bundle {
    val bus       = slave(M68KBus())
    val sel       = in Bool()
    // TODO: if possible define an interrupt
    val usb1     = master(Usb())
  }

  // ------ USB interface ------
  val usb1Typ12MHz = Bits(2 bits)

  usbCd {
    val usbHost1 = new UsbHidHostBB
    usbHost1.io.usb_dp := io.usb1.dp
    usbHost1.io.usb_dm := io.usb1.dm

    usb1Typ12MHz := usbHost1.io.typ

    // TODO: map remain registers
  }


  // --- 68000 bus interface ---
  val usb1Typ28MHz = BufferCC(usb1Typ12MHz, init = B"00")

  // USB1 Control register
  // USB1[10] device type. 0: no device, 1: keyboard, 2: mouse, 3: gamepad
  val usb1TypeReg = Reg(Bits(2 bits)) init 0
  usb1TypeReg := usb1Typ28MHz

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := usb1TypeReg.resized
    }
  }
}
