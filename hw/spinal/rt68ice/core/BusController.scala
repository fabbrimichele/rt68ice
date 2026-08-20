package rt68ice.core

import rt68ice.util.MemoryMapReporter.saveMemoryLayout
import spinal.core._
import spinal.lib._
import spinal.lib.bus.misc.{MaskMapping, SizeMapping}

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class BusController() extends Component {
  val io = new Bundle {
    // Master Interface (from CPU)
    val cpuBus    = slave(M68KBus())
    val busState	= in Bits(2 bits)  // 00-> fetch code 10->read data 11->write data 01->no memaccess
    val clockEn   = out Bool()
    val busErr    = out Bool()
    val ipl       = out Bits(3 bits)

    // Slave buses
    val romBus      = master(M68KBus())
    val ramBus      = master(M68KBus())
    val ledBus      = master(M68KBus())
    val uartBus     = master(M68KBus())
    val videoBus    = master(M68KBus())
    val sdRamBus    = master(M68KBus())
    val counterBus  = master(M68KBus())
    val ledsBus     = master(M68KBus())
    val usbBus      = master(M68KBus())

    // Slave select signals (to peripherals)
    val romSel      = out Bool()
    val ledSel      = out Bool()
    val ramSel      = out Bool()
    val uartSel     = out Bool()
    val vidPalSel   = out Bool()
    val vidCtrlSel  = out Bool()
    val vidFbSel    = out Bool()
    val sdRamSel    = out Bool()
    val counterSel  = out Bool()
    val ledsSel     = out Bool()
    val usbSel      = out Bool()

    // Interrupts
    val uartInt     = in Bool()
    val usbInt      = in Bool()
  }

  // ---------------------------
  //     Bus Synchronization
  // ---------------------------

  // The Wait-State Register
  // This keeps track of whether we are currently in the middle of a pause
  val isWaiting = RegInit(False)

  // IMPORTANT: Gate the External Write signal
  // Only allow 'io.wr' to be seen by the memory when isWaiting is True.
  // This ensures write happens on the SECOND clock cycle, after the
  // CPU has had time to stabilize the data and address.
  val gatedWrite = io.cpuBus.wr  && isWaiting

  // Handle memory sync access
  // Disable the CPU for one clock cycle when accessing memory
  // Detect if the CPU is trying to use the bus
  // We pause for Fetch (00), Data Read (10), and Data Write (11)
  val memoryCycle = io.busState === B"00" ||
    io.busState === B"10" ||
    io.busState === B"11"
  val busActive = memoryCycle && !io.sdRamSel

  // TG68K keeps driving its address output between transfers and may expose
  // a post-incremented address before the next transfer begins. BERR is only
  // meaningful while at least one data strobe qualifies a real bus transfer.
  val cpuTransferActive = memoryCycle && (io.cpuBus.uds || io.cpuBus.lds)

  // The Handshake Logic
  when(busActive && !isWaiting) {
    // A bus cycle just started.
    // Pull the brake (clkEnable = Low) and set the flag.
    io.clockEn := False
    isWaiting := True
  } otherwise {
    // Either the bus is idle (01), or we already finished our 1-cycle wait.
    // Release the brake.
    io.clockEn := True
    isWaiting := False
  }

  // ------------------------
  //    Interrupts
  // ------------------------
  // Only autovectors are used for interrupts
  // IPL is active low
  when(io.usbInt) {
    io.ipl := B"001"        // bitwise not 6
  } elsewhen(io.uartInt) {
    io.ipl := B"011"        // bitwise not 4
  } otherwise {
    io.ipl := B"111"        // bitwise not 0
  }


  // ------------------------
  //    Address Decoding
  // ------------------------
  // Default assignments
  io.ramSel     := False
  io.romSel     := False
  io.ledSel     := False
  io.uartSel    := False
  io.vidPalSel  := False
  io.vidCtrlSel := False
  io.counterSel := False
  io.ledsSel    := False
  io.usbSel     := False
  io.vidFbSel   := False
  io.sdRamSel   := False
  io.busErr     := False

  // Address Bitmask Definitions
  // TG68K emits a CPU-space interrupt-acknowledge read at $FFFFFFF2-$FFFFFFFE.
  // Autovector mode generates the vector internally, so this cycle only needs
  // to complete without raising BERR; the default bus input value of zero is
  // ignored by the CPU.
  val interruptAckMapping = MaskMapping(0xFFFFFFF0L, 0xFFFFFFF0L)
  // Boot vectors look at the absolute first 8 bytes via a 3-bit wildcard mask
  val bootMapping     = MaskMapping(0x00000000L, 0xFFFFFFF8L)
  val ramMapping      = SizeMapping(0x00000000L, 16 KiB)  // $000000 - $003FFF, overlays SDRAM
  val sdRamMapping    = SizeMapping(0x00000000L, 8 MiB)   // $000000 - $7FFFFF
  val vidFbMapping    = SizeMapping(0x00E00000L, 128 KiB) // $E00000 - $E1FFFF; first 75 KiB used
  val ledMapping      = SizeMapping(0x00F00000L, 16 KiB)  // $F00000 - $F03FFF
  val uartMapping     = SizeMapping(0x00F04000L, 16 KiB)  // $F04000 - $F07FFF
  val vidPalMapping   = SizeMapping(0x00F08000L, 16 KiB)  // $F08000 - $F0BFFF
  val vidCtrlMapping  = SizeMapping(0x00F0C000L, 16 KiB)  // $F0C000 - $F0FFFF
  val counterMapping  = SizeMapping(0x00F10000L, 16 KiB)  // $F10000 - $F13FFF
  val ledsMapping     = SizeMapping(0x00F14000L, 16 KiB)  // $F14000 - $F17FFF
  val usbMapping      = SizeMapping(0x00F18000L, 16 KiB)  // $F18000 - $F1BFFF
  val romMapping      = SizeMapping(0x00FC0000L, 16 KiB)  // $FC0000 - $FC3FFF

  saveMemoryLayout(
    "doc/memory_layout.md",
    "BOOT VECTORS" -> bootMapping,
    "FAST RAM" -> ramMapping,
    "SDRAM" -> sdRamMapping,
    "VIDEO FB" -> vidFbMapping,
    "LED PERIPH" -> ledMapping,
    "UART PERIPH" -> uartMapping,
    "VIDEO PALETTE" -> vidPalMapping,
    "VIDEO CONTROL" -> vidCtrlMapping,
    "COUNTER" -> counterMapping,
    "LED_ARRAY" -> ledsMapping,
    "USB HID HOST" -> usbMapping,
    "MAIN ROM" -> romMapping,
  )

  // Decoder Execution Logic
  val address = io.cpuBus.address.asUInt
  when(interruptAckMapping.hit(address)) {
    io.busErr := False
  } elsewhen bootMapping.hit(address) {
    io.romSel := True
  } elsewhen ramMapping.hit(address) {
    io.ramSel := True
  } elsewhen sdRamMapping.hit(address) {
    io.sdRamSel := True
  } elsewhen romMapping.hit(address) {
    io.romSel := True
  } elsewhen ledMapping.hit(address) {
    io.ledSel := True
  } elsewhen uartMapping.hit(address) {
    io.uartSel := True
  } elsewhen vidPalMapping .hit(address) {
    io.vidPalSel := True
  } elsewhen vidCtrlMapping .hit(address) {
    io.vidCtrlSel := True
  } elsewhen vidFbMapping.hit(address) {
    io.vidFbSel := True
  } elsewhen counterMapping.hit(address) {
    io.counterSel := True
  } elsewhen ledsMapping.hit(address) {
    io.ledsSel := True
  } elsewhen usbMapping.hit(address) {
    io.usbSel := True
  } otherwise {
    io.busErr := cpuTransferActive // Out-of-bounds active transfer
  }

  // ----------------------
  //    Buses mapping
  // ----------------------
  // Separate standard peripherals from the smart SDRAM controller
  val buses = List(
    io.romBus, io.ramBus, io.ledBus, io.uartBus,
    io.videoBus, io.counterBus, io.ledsBus, io.usbBus
  )

  for (bus <- buses) {
    bus.address := io.cpuBus.address
    bus.dataOut := io.cpuBus.dataOut
    bus.lds := io.cpuBus.lds
    bus.uds := io.cpuBus.uds
    bus.wr := gatedWrite
  }

  // Give the SDRAM Controller raw, un-gated write signals instantly on Cycle 1
  io.sdRamBus.address := io.cpuBus.address
  io.sdRamBus.dataOut := io.cpuBus.dataOut
  io.sdRamBus.lds     := io.cpuBus.lds
  io.sdRamBus.uds     := io.cpuBus.uds
  io.sdRamBus.wr      := io.cpuBus.wr


  io.cpuBus.dataIn := 0
  when(io.romSel) {
    io.cpuBus.dataIn := io.romBus.dataIn
  } elsewhen io.ramSel {
    io.cpuBus.dataIn := io.ramBus.dataIn
  } elsewhen io.ledSel {
    io.cpuBus.dataIn := io.ledBus.dataIn
  } elsewhen io.uartSel {
    io.cpuBus.dataIn := io.uartBus.dataIn
  } elsewhen (io.vidPalSel || io.vidCtrlSel || io.vidFbSel) {
    io.cpuBus.dataIn := io.videoBus.dataIn
  } elsewhen io.sdRamSel {
    io.cpuBus.dataIn := io.sdRamBus.dataIn
  } elsewhen io.counterSel {
    io.cpuBus.dataIn := io.counterBus.dataIn
  } elsewhen io.ledsSel {
    io.cpuBus.dataIn := io.ledsBus.dataIn
  } elsewhen io.usbSel {
    io.cpuBus.dataIn := io.usbBus.dataIn
  }
}
