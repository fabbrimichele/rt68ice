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

  class UsbHostSync(usbHost: UsbHidHostBB) extends Area {
    // -- Port status ---
    // Bit 7: conErr
    // Bits 1-0: typ
    // Bits 6-2: Reserved (0)
    val status = BufferCC(
      usbHost.io.conerr ## B"00000" ## usbHost.io.typ,
      init = B"00000000"
    )

    // --- Mouse ---
    val mouseBtn = BufferCC(usbHost.io.mouse_btn, init = B"00000000")

    // Accumulate dx/dy inside the USB clock domain
    val accDx = Reg(SInt(8 bits)) init 0
    val accDy = Reg(SInt(8 bits)) init 0

    when(usbHost.io.report && usbHost.io.typ === 2) {
      accDx := accDx + usbHost.io.mouse_dx.asSInt
      accDy := accDy + usbHost.io.mouse_dy.asSInt
    }

    // Safely transfer the accumulating position counters across CDC
    val mouseDxAcc = BufferCC(accDx.asBits, init = B"00000000")
    val mouseDyAcc = BufferCC(accDy.asBits, init = B"00000000")

    // --- Gamepad ---
    val gamepad = BufferCC(
      usbHost.io.game_l ## usbHost.io.game_r ##
      usbHost.io.game_u ## usbHost.io.game_d ##
      usbHost.io.game_a ## usbHost.io.game_b ##
      usbHost.io.game_x ## usbHost.io.game_y ##
      usbHost.io.game_sel ## usbHost.io.game_sta,
      init = B"0000000000"
    )

    // --- Keyboard ---
    // TODO
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
  val host1 = new UsbHostSync(usbDomain.usbHost1)
  val host2 = new UsbHostSync(usbDomain.usbHost2)

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := io.bus.address(4 downto 1).mux(
        0  -> host1.status.resize(16),
        1  -> host1.mouseBtn.resize(16),
        2  -> host1.mouseDxAcc.resize(16),
        3  -> host1.mouseDyAcc.resize(16),
        4  -> host1.gamepad.resize(16),
        5  -> B"x0000",
        6  -> B"x0000",
        7  -> B"x0000",
        8  -> host2.status.resize(16),
        9  -> host2.mouseBtn.resize(16),
        10 -> host2.mouseDxAcc.resize(16),
        11 -> host2.mouseDyAcc.resize(16),
        12 -> host2.gamepad.resize(16),
        13 -> B"x0000",
        14 -> B"x0000",
        15 -> B"x0000",
      )
    }
  }
}
