package rt68ice.io

import rt68ice.core.M68KBus
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

/*
  TODO:
   - use 12 MHz clock (DONE)
   - check if gamepad is recognized with 12 MHz clock
   - find a keyboard that works
   - interrupt
 */
//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class UsbDevice(usbCd: ClockDomain) extends Component {
  val io = new Bundle {
    val bus   = slave(M68KBus())
    val sel   = in Bool()
    val int   = out Bool()
    val usb1  = master(Usb())
    val usb2  = master(Usb())
  }

  class UsbHostSync(usbHost: UsbHidHostBB, clearInterrupt: Bool) extends Area {
    // Capture a complete report in the USB domain and keep it stable while it
    // crosses into the system domain. Status changes also update the mailbox,
    // but only actual HID reports raise an interrupt.
    val usbSnapshot = new ClockingArea(usbCd) {
      val currentStatus = usbHost.io.conerr ## B"00000" ## usbHost.io.typ

      val status = Reg(Bits(8 bits)) init 0
      val mouseBtn = Reg(Bits(8 bits)) init 0
      val mouseDx = Reg(Bits(8 bits)) init 0
      val mouseDy = Reg(Bits(8 bits)) init 0
      val gamepad = Reg(Bits(10 bits)) init 0
      val hasReport = RegInit(False)
      val toggle = RegInit(False)

      val previousStatus = Reg(Bits(8 bits)) init 0
      val statusChanged = currentStatus =/= previousStatus

      when(usbHost.io.report || statusChanged) {
        previousStatus := currentStatus
        status := currentStatus
        mouseBtn := usbHost.io.mouse_btn
        mouseDx := usbHost.io.mouse_dx
        mouseDy := usbHost.io.mouse_dy
        gamepad :=
          usbHost.io.game_l ## usbHost.io.game_r ##
          usbHost.io.game_u ## usbHost.io.game_d ##
          usbHost.io.game_a ## usbHost.io.game_b ##
          usbHost.io.game_x ## usbHost.io.game_y ##
          usbHost.io.game_sel ## usbHost.io.game_sta
        hasReport := usbHost.io.report
        toggle := !toggle
      }
    }

    // Synchronize the stable mailbox and its single-bit event toggle. The
    // payload is consumed two extra system clocks after the toggle arrives so
    // all independently synchronized bits have settled to the same snapshot.
    val sysStatus = BufferCC(usbSnapshot.status, init = B"00000000")
    val sysMouseBtn = BufferCC(usbSnapshot.mouseBtn, init = B"00000000")
    val sysMouseDx = BufferCC(usbSnapshot.mouseDx, init = B"00000000")
    val sysMouseDy = BufferCC(usbSnapshot.mouseDy, init = B"00000000")
    val sysGamepad = BufferCC(usbSnapshot.gamepad, init = B"0000000000")
    val sysHasReport = BufferCC(usbSnapshot.hasReport, init = False)
    val sysToggle = BufferCC(usbSnapshot.toggle, init = False)
    val sysEvent = sysToggle =/= RegNext(sysToggle, False)
    val captureEvent = RegNext(RegNext(sysEvent, False), False)

    // All software-visible state lives in the system domain.
    // Status: bit 7 conErr, bits 1-0 device type, bits 6-2 reserved.
    val status = Reg(Bits(8 bits)) init 0
    val mouseBtn = Reg(Bits(8 bits)) init 0
    val mouseDxLast = Reg(Bits(8 bits)) init 0
    val mouseDyLast = Reg(Bits(8 bits)) init 0
    val mouseDxAcc = Reg(SInt(8 bits)) init 0
    val mouseDyAcc = Reg(SInt(8 bits)) init 0
    val gamepad = Reg(Bits(10 bits)) init 0

    val int = RegInit(False)
    when(clearInterrupt) {
      int := False
    }

    when(captureEvent) {
      status := sysStatus
      mouseBtn := sysMouseBtn
      gamepad := sysGamepad

      when(sysHasReport) {
        when(sysStatus(1 downto 0) === 2) {
          mouseDxLast := sysMouseDx
          mouseDyLast := sysMouseDy
          mouseDxAcc := mouseDxAcc + sysMouseDx.asSInt
          mouseDyAcc := mouseDyAcc + sysMouseDy.asSInt
        }
        int := True
      }
    }

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
  val registerRead = io.sel &&
    !io.bus.wr &&
    (io.bus.uds || io.bus.lds)

  // Reading a host's status register acknowledges only that host. Keep a new
  // report pending if it arrives during the acknowledgement read.
  val host1StatusRead = registerRead && (io.bus.address(4 downto 1) === 0)
  val host2StatusRead = registerRead && (io.bus.address(4 downto 1) === 8)

  val host1 = new UsbHostSync(usbDomain.usbHost1, host1StatusRead)
  val host2 = new UsbHostSync(usbDomain.usbHost2, host2StatusRead)

  // Both hosts share the same interrupt level. USB_IRQ_STATUS lets software
  // identify all pending sources without acknowledging either one.
  io.int := host1.int || host2.int
  val interruptStatus = (host2.int ## host1.int).resize(16)

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := io.bus.address(4 downto 1).mux(
        0  -> host1.status.resize(16),
        1  -> host1.mouseBtn.resize(16),
        2  -> host1.mouseDxAcc.asBits.resize(16),
        3  -> host1.mouseDyAcc.asBits.resize(16),
        4  -> host1.gamepad.resize(16),
        5  -> interruptStatus,
        6  -> host1.mouseDxLast.resize(16),
        7  -> host1.mouseDyLast.resize(16),
        8  -> host2.status.resize(16),
        9  -> host2.mouseBtn.resize(16),
        10 -> host2.mouseDxAcc.asBits.resize(16),
        11 -> host2.mouseDyAcc.asBits.resize(16),
        12 -> host2.gamepad.resize(16),
        13 -> host2.mouseDxLast.resize(16),
        14 -> host2.mouseDyLast.resize(16),
        15 -> B"x0000",
      )
    }
  }
}
