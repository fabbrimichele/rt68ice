package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

/*
  TODO: the USB gamepad for some reason it's not always recognized,
        it's not the FPGA, it's the gamepad itself, I also tried
        to move it to the 2nd port and I get the same issues.
        The USB mouse on the other hand works fine.
        I tried the mechanical keyboard and it doesn't work at all.
 */
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
  val usb1Typ = BufferCC(usb1Typ12MHz, init = B"00")
  val usb2Typ = BufferCC(usb2Typ12MHz, init = B"00")

  // TODO: I'm unsure this intermediate register is really necessary
  // USB1 Control register
  // USB1[10] device type. 0: no device, 1: keyboard, 2: mouse, 3: gamepad
  val usb1TypeReg = Reg(Bits(2 bits)) init 0
  usb1TypeReg := usb1Typ

  val usb2TypeReg = Reg(Bits(2 bits)) init 0
  usb2TypeReg := usb2Typ

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
