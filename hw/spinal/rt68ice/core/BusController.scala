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
    val romBus      = master(M68KDeviceBus())
    val ramBus      = master(M68KDeviceBus())
    val ledBus      = master(M68KDeviceBus())
    val uartBus     = master(M68KDeviceBus())
    val videoBus    = master(M68KVideoBus())
    val sdRamBus    = master(M68KDeviceBus())
    val counterBus  = master(M68KDeviceBus())
    val ledsBus     = master(M68KDeviceBus())
    val usbBus      = master(M68KDeviceBus())
    val timerBus    = master(M68KDeviceBus())
    val spiBus      = master(M68KDeviceBus())
    val psgBus      = master(M68KDeviceBus())
    val ds1302Bus   = master(M68KDeviceBus())

    // Interrupts
    val uartInt     = in Bool()
    val usbInt      = in Bool()
    val timerInt    = in Bool()
    val videoInt    = in Bool()
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
  val busActive = memoryCycle && !io.sdRamBus.sel

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
  } elsewhen io.timerInt {
    io.ipl := B"010"        // bitwise not 5
  } elsewhen io.videoInt {
    io.ipl := B"011"        // bitwise not 4
  } elsewhen io.uartInt {
    io.ipl := B"100"        // bitwise not 3
  } otherwise {
    io.ipl := B"111"        // bitwise not 0
  }


  // ------------------------
  //    Address Decoding
  // ------------------------
  // Preserve RTC data-cycle qualification formerly wired in the top level.
  val ds1302AddressSelected = Bool()
  io.ds1302Bus.sel := ds1302AddressSelected &&
    ((io.busState === B"10") || (io.busState === B"11"))

  // Default assignments
  io.ramBus.sel := False
  io.romBus.sel := False
  io.ledBus.sel := False
  io.uartBus.sel := False
  io.videoBus.palSel := False
  io.videoBus.ctrlSel := False
  io.counterBus.sel := False
  io.ledsBus.sel := False
  io.usbBus.sel := False
  io.timerBus.sel := False
  io.spiBus.sel := False
  io.psgBus.sel := False
  ds1302AddressSelected := False
  io.videoBus.fbSel := False
  io.sdRamBus.sel := False
  io.busErr     := False

  // Address Bitmask Definitions
  // TG68K emits a CPU-space interrupt-acknowledge read at $FFFFFFF2-$FFFFFFFE.
  // Autovector mode generates the vector internally, so this cycle only needs
  // to complete without raising BERR; the default bus input value of zero is
  // ignored by the CPU.
  val interruptAckMapping = MaskMapping(0xFFFFFFF0L, 0xFFFFFFF0L)
  // Boot vectors look at the absolute first 8 bytes via a 3-bit wildcard mask
  val bootMapping     = MaskMapping(0x00000000L, 0xFFFFFFF8L)
  val ramMapping      = SizeMapping(0x00000000L, 1 KiB)   // $000000 - $0003FF, overlays SDRAM
  val sdRamMapping1   = SizeMapping(0x00000000L, 8 MiB)   // $000000 - $7FFFFF
  val sdRamMapping2   = SizeMapping(0x00800000L, 4 MiB)   // $800000 - $BFFFFF
  val sdRamMapping3   = SizeMapping(0x00C00000L, 2 MiB)   // $C00000 - $DFFFFF
  val vidFbMapping    = SizeMapping(0x00E00000L, 128 KiB) // $E00000 - $E1FFFF; first 75 KiB used
  val ledMapping      = SizeMapping(0x00F00000L, 16 KiB)  // $F00000 - $F03FFF
  val uartMapping     = SizeMapping(0x00F04000L, 16 KiB)  // $F04000 - $F07FFF
  val vidPalMapping   = SizeMapping(0x00F08000L, 16 KiB)  // $F08000 - $F0BFFF
  val vidCtrlMapping  = SizeMapping(0x00F0C000L, 16 KiB)  // $F0C000 - $F0FFFF
  val counterMapping  = SizeMapping(0x00F10000L, 16 KiB)  // $F10000 - $F13FFF
  val ledsMapping     = SizeMapping(0x00F14000L, 16 KiB)  // $F14000 - $F17FFF
  val usbMapping      = SizeMapping(0x00F18000L, 16 KiB)  // $F18000 - $F1BFFF
  val timerMapping    = SizeMapping(0x00F1C000L, 16 KiB)  // $F1C000 - $F1FFFF
  val spiMapping      = SizeMapping(0x00F20000L, 16 KiB)  // $F20000 - $F23FFF
  val psgMapping      = SizeMapping(0x00F24000L, 16 KiB)  // $F24000 - $F27FFF
  val ds1302Mapping   = SizeMapping(0x00F28000L, 16 KiB)  // $F28000 - $F2BFFF
  val romMapping      = SizeMapping(0x00FC0000L, 16 KiB)  // $FC0000 - $FC3FFF

  saveMemoryLayout(
    "doc/memory_layout.md",
    "BOOT VECTORS" -> bootMapping,
    "FAST RAM" -> ramMapping,
    "SDRAM 8MB" -> sdRamMapping1,
    "SDRAM 4MB" -> sdRamMapping2,
    "SDRAM 2MB" -> sdRamMapping3,
    "VIDEO FB" -> vidFbMapping,
    "LED PERIPH" -> ledMapping,
    "UART PERIPH" -> uartMapping,
    "VIDEO PALETTE" -> vidPalMapping,
    "VIDEO CONTROL" -> vidCtrlMapping,
    "COUNTER" -> counterMapping,
    "LED_ARRAY" -> ledsMapping,
    "USB HID HOST" -> usbMapping,
    "TIMER" -> timerMapping,
    "SPI" -> spiMapping,
    "PSG" -> psgMapping,
    "MAIN ROM" -> romMapping,
  )

  // Decoder Execution Logic
  val address = io.cpuBus.address.asUInt
  when(interruptAckMapping.hit(address)) {
    io.busErr := False
  } elsewhen bootMapping.hit(address) {
    io.romBus.sel := True
  } elsewhen ramMapping.hit(address) {
    io.ramBus.sel := True
  } elsewhen (sdRamMapping1.hit(address) || sdRamMapping2.hit(address) || sdRamMapping3.hit(address)) {
    io.sdRamBus.sel := True
  } elsewhen romMapping.hit(address) {
    io.romBus.sel := True
  } elsewhen ledMapping.hit(address) {
    io.ledBus.sel := True
  } elsewhen uartMapping.hit(address) {
    io.uartBus.sel := True
  } elsewhen vidPalMapping .hit(address) {
    io.videoBus.palSel := True
  } elsewhen vidCtrlMapping .hit(address) {
    io.videoBus.ctrlSel := True
  } elsewhen vidFbMapping.hit(address) {
    io.videoBus.fbSel := True
  } elsewhen counterMapping.hit(address) {
    io.counterBus.sel := True
  } elsewhen ledsMapping.hit(address) {
    io.ledsBus.sel := True
  } elsewhen usbMapping.hit(address) {
    io.usbBus.sel := True
  } elsewhen timerMapping.hit(address) {
    io.timerBus.sel := True
  } elsewhen spiMapping.hit(address) {
    io.spiBus.sel := True
  } elsewhen psgMapping.hit(address) {
    io.psgBus.sel := True
  } elsewhen ds1302Mapping.hit(address) {
    ds1302AddressSelected := True
  } otherwise {
    io.busErr := cpuTransferActive // Out-of-bounds active transfer
  }

  // ----------------------
  //    Buses mapping
  // ----------------------
  // Separate standard peripherals from the smart SDRAM controller
  val buses = List(
    io.romBus, io.ramBus, io.ledBus, io.uartBus,
    io.videoBus, io.counterBus, io.ledsBus, io.usbBus,
    io.timerBus, io.spiBus
  )

  for (bus <- buses) {
    bus.address := io.cpuBus.address
    bus.dataOut := io.cpuBus.dataOut
    bus.lds := io.cpuBus.lds
    bus.uds := io.cpuBus.uds
    bus.wr := gatedWrite
  }

  // SDRAM, PSG and DS1302 manage their own extended waits and need the original
  // write direction throughout the access, rather than a one-cycle pulse.
  for (bus <- List(io.sdRamBus, io.psgBus, io.ds1302Bus)) {
    bus.address := io.cpuBus.address
    bus.dataOut := io.cpuBus.dataOut
    bus.lds := io.cpuBus.lds
    bus.uds := io.cpuBus.uds
    bus.wr := io.cpuBus.wr
  }

  io.cpuBus.dataIn := 0
  when(io.romBus.sel) {
    io.cpuBus.dataIn := io.romBus.dataIn
  } elsewhen io.ramBus.sel {
    io.cpuBus.dataIn := io.ramBus.dataIn
  } elsewhen io.ledBus.sel {
    io.cpuBus.dataIn := io.ledBus.dataIn
  } elsewhen io.uartBus.sel {
    io.cpuBus.dataIn := io.uartBus.dataIn
  } elsewhen (io.videoBus.palSel || io.videoBus.ctrlSel || io.videoBus.fbSel) {
    io.cpuBus.dataIn := io.videoBus.dataIn
  } elsewhen io.sdRamBus.sel {
    io.cpuBus.dataIn := io.sdRamBus.dataIn
  } elsewhen io.counterBus.sel {
    io.cpuBus.dataIn := io.counterBus.dataIn
  } elsewhen io.ledsBus.sel {
    io.cpuBus.dataIn := io.ledsBus.dataIn
  } elsewhen io.usbBus.sel {
    io.cpuBus.dataIn := io.usbBus.dataIn
  } elsewhen io.timerBus.sel {
    io.cpuBus.dataIn := io.timerBus.dataIn
  } elsewhen io.spiBus.sel {
    io.cpuBus.dataIn := io.spiBus.dataIn
  } elsewhen io.psgBus.sel {
    io.cpuBus.dataIn := io.psgBus.dataIn
  } elsewhen ds1302AddressSelected {
    io.cpuBus.dataIn := io.ds1302Bus.dataIn
  }
}
