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
  val usbDomain = new ClockingArea(usbCd) {
    val usbHost1 = new UsbHidHostBB
    usbHost1.io.usb_dp := io.usb1.dp
    usbHost1.io.usb_dm := io.usb1.dm

    val usbHost2 = new UsbHidHostBB
    usbHost2.io.usb_dp := io.usb2.dp
    usbHost2.io.usb_dm := io.usb2.dm
  }

  // --- 68000 bus interface ---
  // TODO: map remain registers
  val usb1Typ       = BufferCC(usbDomain.usbHost1.io.typ, init = B"00")
  val usb1ConErr    = BufferCC(usbDomain.usbHost1.io.conerr, init = False)
  val usb1MouseBtn  = BufferCC(usbDomain.usbHost1.io.mouse_btn, init = B"00000000")
  val usb1MouseDx  = BufferCC(usbDomain.usbHost1.io.mouse_dx, init = B"00000000")
  val usb1MouseDy  = BufferCC(usbDomain.usbHost1.io.mouse_dy, init = B"00000000")

  val usb2Typ       = BufferCC(usbDomain.usbHost2.io.typ, init = B"00")
  val usb2ConErr    = BufferCC(usbDomain.usbHost2.io.conerr, init = False)
  val usb2MouseBtn  = BufferCC(usbDomain.usbHost2.io.mouse_btn, init = B"00000000")
  val usb2MouseDx  = BufferCC(usbDomain.usbHost2.io.mouse_dx, init = B"00000000")
  val usb2MouseDy  = BufferCC(usbDomain.usbHost2.io.mouse_dy, init = B"00000000")

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := io.bus.address(4 downto 1).mux(
        0  -> usb1Typ.resize(16),
        1  -> usb1ConErr.asBits.resize(16),
        2  -> usb1MouseBtn.resize(16),
        3  -> usb1MouseDx.resize(16),
        4  -> usb1MouseDy.resize(16),
        5  -> B"x0000",
        6  -> B"x0000",
        7  -> B"x0000",
        8  -> usb2Typ.resize(16),
        9  -> usb2ConErr.asBits.resize(16),
        10 -> usb2MouseBtn.resize(16),
        11 -> usb2MouseDx.resize(16),
        12 -> usb2MouseDy.resize(16),
        13 -> B"x0000",
        14 -> B"x0000",
        15 -> B"x0000",
      )
    }
  }
}
