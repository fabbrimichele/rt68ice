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
    val sdRamBus  = master(M68KBus())

    // Slave select signals (to peripherals)
    val romSel      = out Bool()
    val ledSel      = out Bool()
    val ramSel      = out Bool()
    val uartSel     = out Bool()
    val vidPalSel   = out Bool()
    val vidCtrlSel  = out Bool()
    val vidFbSel    = out Bool()
    val sdRamSel    = out Bool()
  }

  // ---------------------------
  //     Bus Synchronization
  // ---------------------------
  // Handle pipelining (3 phases total)
  val waitState = RegInit(U"00")

  // Handle memory sync access
  // Disable the CPU for one clock cycle when accessing memory
  // Detect if the CPU is trying to use the bus
  // We pause for Fetch (00), Data Read (10), and Data Write (11)
  val busActive = io.busState === B"00" ||
                  io.busState === B"10" ||
                  io.busState === B"11"

  val clkEnReg = RegInit(True)
  io.clockEn := clkEnReg

  when(busActive && waitState === 0) {
    clkEnReg  := False
    waitState := 1
  } elsewhen(waitState === 1) {
    clkEnReg  := False
    waitState := 2
  } otherwise {
    clkEnReg  := True
    waitState := 0
  }

  /// Gate the write so it asserts cleanly while the address is stable in the pipeline
  val gatedWrite = io.cpuBus.wr && (waitState =/= 0)

  // ------------------------
  //    Address Decoding
  // ------------------------
  val address = io.cpuBus.address.asUInt

  // Address Bitmask Definitions
  // Boot vectors look at the absolute first 8 bytes via a 3-bit wildcard mask
  val bootMapping     = MaskMapping(0x00000000L, 0xFFFFFFF8L)
  val ramMapping      = SizeMapping(0x00000000L, 16 KiB)   // $000000 - $003FFF
  val romMapping      = SizeMapping(0x00004000L, 16 KiB)   // $004000 - $007FFF
  val ledMapping      = SizeMapping(0x00008000L, 16 KiB)   // $008000 - $00BFFF
  val uartMapping     = SizeMapping(0x0000C000L, 16 KiB)   // $00C000 - $00FFFF
  val vidPalMapping   = SizeMapping(0x00010000L, 16 KiB)   // $010000 - $013FFF
  val vidCtrlMapping  = SizeMapping(0x00014000L, 16 KiB)   // $014000 - $017FFF
  val vidFbMapping    = SizeMapping(0x00020000L, 128 KiB)  // $020000 - $03FFFF - only the first 75KB are available
  val sdRamMapping    = SizeMapping(0x00040000L, 128 KiB)  // $040000 - $05FFFF TODO: extend it to 32 MB

  // PIPELINE STAGE 1: Register the Hit Logic and Selects
  val romSelReg    = RegNext(bootMapping.hit(address) || romMapping.hit(address), False)
  val ramSelReg    = RegNext(ramMapping.hit(address), False)
  val ledSelReg    = RegNext(ledMapping.hit(address), False)
  val uartSelReg   = RegNext(uartMapping.hit(address), False)
  val vidPalSelReg = RegNext(vidPalMapping.hit(address), False)
  val vidCtrlSelReg= RegNext(vidCtrlMapping.hit(address), False)
  val vidFbSelReg  = RegNext(vidFbMapping.hit(address), False)
  val sdRamSelReg  = RegNext(sdRamMapping.hit(address), False)

  io.romSel      := romSelReg
  io.ramSel      := ramSelReg
  io.ledSel      := ledSelReg
  io.uartSel     := uartSelReg
  io.vidPalSel   := vidPalSelReg
  io.vidCtrlSel  := vidCtrlSelReg
  io.vidFbSel    := vidFbSelReg
  io.sdRamSel    := sdRamSelReg

  saveMemoryLayout(
    "doc/memory_layout.md",
    "BOOT VECTORS" -> bootMapping,
    "MAIN RAM" -> ramMapping,
    "MAIN ROM" -> romMapping,
    "LED PERIPH" -> ledMapping,
    "UART PERIPH" -> uartMapping,
    "VIDEO PALETTE" -> vidPalMapping,
    "VIDEO CONTROL" -> vidCtrlMapping,
    "VIDEO FB" -> vidFbMapping,
  )

  // Register the Bus Error check
  io.busErr := RegNext(~(bootMapping.hit(address) || ramMapping.hit(address) ||
    romMapping.hit(address) || ledMapping.hit(address) ||
    uartMapping.hit(address) || vidPalMapping.hit(address) ||
    vidCtrlMapping.hit(address) || vidFbMapping.hit(address) ||
    sdRamMapping.hit(address)), False)

  // ----------------------
  //    Buses mapping
  // ----------------------
  // PIPELINE STAGE 1 (cont): Register the outgoing bus signals to break fanout routing delay
  val buses = List(io.romBus, io.ramBus, io.ledBus, io.uartBus, io.videoBus, io.sdRamBus)
  for (bus <- buses) {
    bus.address := RegNext(io.cpuBus.address)
    bus.dataOut := RegNext(io.cpuBus.dataOut)
    bus.lds     := RegNext(io.cpuBus.lds)
    bus.uds     := RegNext(io.cpuBus.uds)
    bus.wr      := RegNext(gatedWrite, False)
  }

  // PIPELINE STAGE 2: Register the incoming Data Mux
  val readDataReg = Reg(Bits(16 bits))
  readDataReg := 0

  when(romSelReg) {
    readDataReg := io.romBus.dataIn
  } elsewhen ramSelReg {
    readDataReg := io.ramBus.dataIn
  } elsewhen ledSelReg {
    readDataReg := io.ledBus.dataIn
  } elsewhen uartSelReg {
    readDataReg := io.uartBus.dataIn
  } elsewhen (vidPalSelReg || vidCtrlSelReg || vidFbSelReg) {
    readDataReg := io.videoBus.dataIn
  } elsewhen (sdRamSelReg) {
    readDataReg := io.sdRamBus.dataIn
  }

  // Data is now securely pipelined and stable for the CPU to latch
  io.cpuBus.dataIn := readDataReg
}
