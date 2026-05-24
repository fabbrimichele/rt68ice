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

    // Slave buses
    val romBus    = master(M68KBus())
    val ramBus    = master(M68KBus())
    val ledBus    = master(M68KBus())
    val uartBus   = master(M68KBus())
    val videoBus  = master(M68KBus())

    // Slave select signals (to peripherals)
    val romSel    = out Bool()
    val ledSel    = out Bool()
    val ramSel    = out Bool()
    val uartSel   = out Bool()
    val videoSel  = out Bool()
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
  val busActive = io.busState === B"00" ||
    io.busState === B"10" ||
    io.busState === B"11"

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
  //    Address Decoding
  // ------------------------
  // Default assignments
  io.ramSel := False
  io.romSel := False
  io.ledSel := False
  io.uartSel := False
  io.videoSel := False
  io.busErr := False

  // Address Bitmask Definitions
  // Boot vectors look at the absolute first 8 bytes via a 3-bit wildcard mask
  val bootMapping  = MaskMapping(0x00000000L, 0xFFFFFFF8L)
  val ramMapping   = SizeMapping(0x00000000L, 16 KiB) // $000000 - $003FFF
  val romMapping   = SizeMapping(0x00004000L, 16 KiB) // $004000 - $007FFF
  val ledMapping   = SizeMapping(0x00008000L, 16 KiB) // $008000 - $00BFFF
  val uartMapping  = SizeMapping(0x0000C000L, 16 KiB) // $00C000 - $00FFFF
  val videoMapping = SizeMapping(0x00010000L, 64 KiB) // $010000 - $01FFFF

  saveMemoryLayout(
    "doc/memory_layout.md",
    "BOOT VECTORS" -> bootMapping,
    "MAIN RAM" -> ramMapping,
    "MAIN ROM" -> romMapping,
    "LED PERIPH" -> ledMapping,
    "UART PERIPH" -> uartMapping,
    "FRAMEBUFFER" -> videoMapping,
  )

  // Decoder Execution Logic
  val address = io.cpuBus.address.asUInt
  when(bootMapping.hit(address)) {
    io.romSel := True
  } elsewhen ramMapping.hit(address) {
    io.ramSel := True
  } elsewhen romMapping.hit(address) {
    io.romSel := True
  } elsewhen ledMapping.hit(address) {
    io.ledSel := True
  } elsewhen uartMapping.hit(address) {
    io.uartSel := True
  } elsewhen videoMapping.hit(address) {
    io.videoSel := True
  } otherwise {
    io.busErr := True // Out of bounds access! Trigger BERR
  }

  // ----------------------
  //    Buses mapping
  // ----------------------
  val buses = List(io.romBus, io.ramBus, io.ledBus, io.uartBus, io.videoBus)
  for (bus <- buses) {
    bus.address := io.cpuBus.address
    bus.dataOut := io.cpuBus.dataOut
    bus.lds := io.cpuBus.lds
    bus.uds := io.cpuBus.uds
    bus.wr := gatedWrite
  }

  io.cpuBus.dataIn := 0
  when(io.romSel) {
    io.cpuBus.dataIn := io.romBus.dataIn
  } elsewhen io.ramSel {
    io.cpuBus.dataIn := io.ramBus.dataIn
  } elsewhen io.ledSel {
    io.cpuBus.dataIn := io.ledBus.dataIn
  } elsewhen io.uartSel {
    io.cpuBus.dataIn := io.uartBus.dataIn
  } elsewhen io.videoSel {
    io.cpuBus.dataIn := io.videoBus.dataIn
  }
}
