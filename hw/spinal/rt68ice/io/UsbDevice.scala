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
    val usb2     = master(Usb())
  }

  // ------ USB interface ------
  val usb1Typ12MHz = Bits(2 bits)
  val usb2Typ12MHz = Bits(2 bits)

  usbCd {
    val usbHost1 = new UsbHidHostBB
    usbHost1.io.usb_dp := io.usb1.dp
    usbHost1.io.usb_dm := io.usb1.dm
    usb1Typ12MHz := usbHost1.io.typ

    val usbHost2 = new UsbHidHostBB
    usbHost2.io.usb_dp := io.usb2.dp
    usbHost2.io.usb_dm := io.usb2.dm
    usb2Typ12MHz := usbHost2.io.typ

    // TODO: map remain registers
  }


  // --- 68000 bus interface ---
  val usb1Typ28MHz = BufferCC(usb1Typ12MHz, init = B"00")
  val usb2Typ28MHz = BufferCC(usb2Typ12MHz, init = B"00")

  // TODO: I'm unsure this intermediate register is really necessary
  // USB1 Control register
  // USB1[10] device type. 0: no device, 1: keyboard, 2: mouse, 3: gamepad
  val usb1TypeReg = Reg(Bits(2 bits)) init 0
  usb1TypeReg := usb1Typ28MHz

  val usb2TypeReg = Reg(Bits(2 bits)) init 0
  usb2TypeReg := usb2Typ28MHz

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := io.bus.address(1 downto 1).mux(
        0 -> usb1TypeReg.resize(16),
        1 -> usb2TypeReg.resize(16),
      )
    }
  }
}
