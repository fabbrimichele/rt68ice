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
    val usb3  = master(Usb())
    val usb4  = master(Usb())
  }

  class UsbHostSync(usbHost: UsbHidHostBB, clearInterrupt: Bool) extends Area {
    // Capture a complete report in the USB domain and keep it stable while it
    // crosses into the system domain. Status changes also update the mailbox,
    // but only actual HID reports raise an interrupt.
    val usbSnapshot = new ClockingArea(usbCd) {
      val currentStatus = usbHost.io.conerr ## B"00000" ## usbHost.io.typ

      val status = Reg(Bits(8 bits)) init 0
      val keyModifiers = Reg(Bits(8 bits)) init 0
      val key1 = Reg(Bits(8 bits)) init 0
      val key2 = Reg(Bits(8 bits)) init 0
      val key3 = Reg(Bits(8 bits)) init 0
      val key4 = Reg(Bits(8 bits)) init 0
      val mouseBtn = Reg(Bits(8 bits)) init 0
      val mouseDx = Reg(Bits(12 bits)) init 0
      val mouseDy = Reg(Bits(12 bits)) init 0
      val gamepad = Reg(Bits(10 bits)) init 0
      val hasReport = RegInit(False)
      val toggle = RegInit(False)

      val previousStatus = Reg(Bits(8 bits)) init 0
      val statusChanged = currentStatus =/= previousStatus

      when(usbHost.io.report || statusChanged) {
        previousStatus := currentStatus
        status := currentStatus
        keyModifiers := usbHost.io.key_modifiers
        key1 := usbHost.io.key1
        key2 := usbHost.io.key2
        key3 := usbHost.io.key3
        key4 := usbHost.io.key4
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
    val sysKeyModifiers = BufferCC(usbSnapshot.keyModifiers, init = B"00000000")
    val sysKey1 = BufferCC(usbSnapshot.key1, init = B"00000000")
    val sysKey2 = BufferCC(usbSnapshot.key2, init = B"00000000")
    val sysKey3 = BufferCC(usbSnapshot.key3, init = B"00000000")
    val sysKey4 = BufferCC(usbSnapshot.key4, init = B"00000000")
    val sysMouseBtn = BufferCC(usbSnapshot.mouseBtn, init = B"00000000")
    val sysMouseDx = BufferCC(usbSnapshot.mouseDx, init = B"000000000000")
    val sysMouseDy = BufferCC(usbSnapshot.mouseDy, init = B"000000000000")
    val sysGamepad = BufferCC(usbSnapshot.gamepad, init = B"0000000000")
    val sysHasReport = BufferCC(usbSnapshot.hasReport, init = False)
    val sysToggle = BufferCC(usbSnapshot.toggle, init = False)
    val sysEvent = sysToggle =/= RegNext(sysToggle, False)
    val captureEvent = RegNext(RegNext(sysEvent, False), False)

    // All software-visible state lives in the system domain.
    // Status: bit 7 conErr, bits 1-0 device type, bits 6-2 reserved.
    val status = Reg(Bits(8 bits)) init 0
    val keyModifiers = Reg(Bits(8 bits)) init 0
    val key1 = Reg(Bits(8 bits)) init 0
    val key2 = Reg(Bits(8 bits)) init 0
    val key3 = Reg(Bits(8 bits)) init 0
    val key4 = Reg(Bits(8 bits)) init 0
    val mouseBtn = Reg(Bits(8 bits)) init 0
    val mouseDxAcc = Reg(SInt(16 bits)) init 0
    val mouseDyAcc = Reg(SInt(16 bits)) init 0
    val gamepad = Reg(Bits(10 bits)) init 0

    val int = RegInit(False)
    when(clearInterrupt) {
      int := False
    }

    when(captureEvent) {
      status := sysStatus
      mouseBtn := sysMouseBtn
      gamepad := sysGamepad

      when(sysStatus(1 downto 0) === 1) {
        keyModifiers := sysKeyModifiers
        key1 := sysKey1
        key2 := sysKey2
        key3 := sysKey3
        key4 := sysKey4
      } otherwise {
        keyModifiers := 0
        key1 := 0
        key2 := 0
        key3 := 0
        key4 := 0
      }

      when(sysHasReport) {
        when(sysStatus(1 downto 0) === 2) {
          mouseDxAcc := mouseDxAcc + sysMouseDx.asSInt
          mouseDyAcc := mouseDyAcc + sysMouseDy.asSInt
        }
        int := True
      }
    }

    // Keyboard reports are exposed as a stable boot-protocol snapshot:
    // modifiers plus the four key usage IDs provided by UsbHidHostBB.
  }

  // ------ USB interface ------
  val usbDomain = new ClockingArea(usbCd) {
    val usbHost1 = new UsbHidHostBB
    usbHost1.io.usb_dp := io.usb1.dp
    usbHost1.io.usb_dm := io.usb1.dm

    val usbHost2 = new UsbHidHostBB
    usbHost2.io.usb_dp := io.usb2.dp
    usbHost2.io.usb_dm := io.usb2.dm

    val usbHost3 = new UsbHidHostBB
    usbHost3.io.usb_dp := io.usb3.dp
    usbHost3.io.usb_dm := io.usb3.dm

    val usbHost4 = new UsbHidHostBB
    usbHost4.io.usb_dp := io.usb4.dp
    usbHost4.io.usb_dm := io.usb4.dm
  }

  // --- 68000 bus interface ---
  // The USB map uses 16-bit registers.  Keep the global interrupt registers
  // at word offsets 0-1 and reserve a 16-word block for each host.
  val wordAddress = io.bus.address(7 downto 1)
  val registerRead = io.sel &&
    !io.bus.wr &&
    (io.bus.uds || io.bus.lds)
  val registerWrite = io.sel &&
    io.bus.wr &&
    (io.bus.uds || io.bus.lds)

  // Reading a host's status register acknowledges only that host. Keep a new
  // report pending if it arrives during the acknowledgement read.
  val host1StatusRead = registerRead && (wordAddress === 8)
  val host2StatusRead = registerRead && (wordAddress === 24)
  val host3StatusRead = registerRead && (wordAddress === 40)
  val host4StatusRead = registerRead && (wordAddress === 56)

  val host1 = new UsbHostSync(usbDomain.usbHost1, host1StatusRead)
  val host2 = new UsbHostSync(usbDomain.usbHost2, host2StatusRead)
  val host3 = new UsbHostSync(usbDomain.usbHost3, host3StatusRead)
  val host4 = new UsbHostSync(usbDomain.usbHost4, host4StatusRead)

  // Both hosts share the same interrupt level. Reports remain pending while
  // masked, allowing polling users to inspect them via USB_IRQ_STATUS.
  val irqEnable = Reg(Bits(4 bits)) init 0
  when(registerWrite && (wordAddress === 1)) {
    irqEnable := io.bus.dataOut(3 downto 0)
  }

  io.int :=
    (host1.int && irqEnable(0)) ||
    (host2.int && irqEnable(1)) ||
    (host3.int && irqEnable(2)) ||
    (host4.int && irqEnable(3))
  val interruptStatus = (host4.int ## host3.int ## host2.int ## host1.int).resize(16)

  io.bus.dataIn := 0
  when(io.sel) {
    when(!io.bus.wr) {
      // Read
      io.bus.dataIn := wordAddress.mux(
        0  -> interruptStatus,
        1  -> irqEnable.resize(16),
        8  -> host1.status.resize(16),
        9  -> host1.mouseBtn.resize(16),
        10 -> host1.mouseDxAcc.asBits.resize(16),
        11 -> host1.mouseDyAcc.asBits.resize(16),
        12 -> host1.gamepad.resize(16),
        13 -> host1.keyModifiers.resize(16),
        14 -> host1.key1.resize(16),
        15 -> host1.key2.resize(16),
        16 -> host1.key3.resize(16),
        17 -> host1.key4.resize(16),
        24 -> host2.status.resize(16),
        25 -> host2.mouseBtn.resize(16),
        26 -> host2.mouseDxAcc.asBits.resize(16),
        27 -> host2.mouseDyAcc.asBits.resize(16),
        28 -> host2.gamepad.resize(16),
        29 -> host2.keyModifiers.resize(16),
        30 -> host2.key1.resize(16),
        31 -> host2.key2.resize(16),
        32 -> host2.key3.resize(16),
        33 -> host2.key4.resize(16),
        40 -> host3.status.resize(16),
        41 -> host3.mouseBtn.resize(16),
        42 -> host3.mouseDxAcc.asBits.resize(16),
        43 -> host3.mouseDyAcc.asBits.resize(16),
        44 -> host3.gamepad.resize(16),
        45 -> host3.keyModifiers.resize(16),
        46 -> host3.key1.resize(16),
        47 -> host3.key2.resize(16),
        48 -> host3.key3.resize(16),
        49 -> host3.key4.resize(16),
        56 -> host4.status.resize(16),
        57 -> host4.mouseBtn.resize(16),
        58 -> host4.mouseDxAcc.asBits.resize(16),
        59 -> host4.mouseDyAcc.asBits.resize(16),
        60 -> host4.gamepad.resize(16),
        61 -> host4.keyModifiers.resize(16),
        62 -> host4.key1.resize(16),
        63 -> host4.key2.resize(16),
        64 -> host4.key3.resize(16),
        65 -> host4.key4.resize(16),
        default -> B(0, 16 bits),
      )
    }
  }
}
