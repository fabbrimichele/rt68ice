package rt68ice.core

import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class BusController() extends Component {
  val io = new Bundle {
    // Master Interface (from CPU)
    val cpuBus    = slave(M68KBus())
    val busState	= in Bits(2 bits)  // 00-> fetch code 10->read data 11->write data 01->no memaccess
    val clockEn   = out Bool()

    // Slave buses
    val romBus    = master(M68KBus())
    val ramBus    = master(M68KBus())
    val ledBus    = master(M68KBus())

    // Slave select signals (to peripherals)
    val romSel    = out Bool()
    val ledSel    = out Bool()
    val ramSel    = out Bool()
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
  val sectionAddress = io.cpuBus.address(31 downto 11).asUInt // 2KB each memory section

  io.romSel := False
  io.ledSel := False
  io.ramSel := False
  when (sectionAddress === 0) {       //    0 - 2048
    io.romSel := True
  } elsewhen(sectionAddress === 1) {  // 2048 - 4096
    io.ledSel := True
  } elsewhen(sectionAddress === 2) {  // 4096 - 6144
    io.ramSel := True
  } otherwise {
    // TODO: busErr?
  }

  // ----------------------
  //    Buses mapping
  // ----------------------
  val buses = List(io.romBus, io.ramBus, io.ledBus)
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
  }
}
