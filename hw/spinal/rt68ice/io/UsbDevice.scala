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
  // TODO: this variable are probably unnecessary,
  //       you can access directly the usbHost1 inside the domain clock and assign it to the BufferCC
  val usb1Typ12MHz      = Bits(2 bits)
  val usb1ConErr12MHz   = Bits(1 bits)
  val usb1MouseBtn12MHz = Bits(8 bits)
  val usb1MouseDx12MHz  = Bits(8 bits)
  val usb1MouseDy12MHz  = Bits(8 bits)

  val usb2Typ12MHz      = Bits(2 bits)
  val usb2ConErr12MHz   = Bits(1 bits)
  val usb2MouseBtn12MHz = Bits(8 bits)
  val usb2MouseDx12MHz  = Bits(8 bits)
  val usb2MouseDy12MHz  = Bits(8 bits)

  usbCd {
    val usbHost1 = new UsbHidHostBB
    usbHost1.io.usb_dp := io.usb1.dp
    usbHost1.io.usb_dm := io.usb1.dm
    usb1Typ12MHz       := usbHost1.io.typ
    usb1ConErr12MHz(0) := usbHost1.io.conerr
    usb1MouseBtn12MHz  := usbHost1.io.mouse_btn
    usb1MouseDx12MHz   := usbHost1.io.mouse_dx
    usb1MouseDy12MHz   := usbHost1.io.mouse_dy

    val usbHost2 = new UsbHidHostBB
    usbHost2.io.usb_dp := io.usb2.dp
    usbHost2.io.usb_dm := io.usb2.dm
    usb2Typ12MHz       := usbHost2.io.typ
    usb2ConErr12MHz(0) := usbHost2.io.conerr
    usb2MouseBtn12MHz  := usbHost2.io.mouse_btn
    usb2MouseDx12MHz   := usbHost2.io.mouse_dx
    usb2MouseDy12MHz   := usbHost2.io.mouse_dy

    // TODO: map remain registers
  }


  // --- 68000 bus interface ---
  val usb1Typ       = BufferCC(usb1Typ12MHz, init = B"00")
  val usb1ConErr    = BufferCC(usb1ConErr12MHz, init = B"0")
  val usb1MouseBtn  = BufferCC(usb1MouseBtn12MHz, init = B"00000000")

  val usb2Typ       = BufferCC(usb2Typ12MHz, init = B"00")
  val usb2ConErr    = BufferCC(usb2ConErr12MHz, init = B"0")
  val usb2MouseBtn  = BufferCC(usb2MouseBtn12MHz, init = B"00000000")

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := io.bus.address(3 downto 1).mux(
        0 -> usb1Typ.resize(16),
        1 -> usb1ConErr.resize(16),
        2 -> usb1MouseBtn.resize(16),
        3 -> usb1MouseBtn.resize(16),
        4 -> usb2Typ.resize(16),
        5 -> usb2ConErr.resize(16),
        6 -> usb2MouseBtn.resize(16),
        7 -> usb2MouseBtn.resize(16),
      )
    }
  }
}
