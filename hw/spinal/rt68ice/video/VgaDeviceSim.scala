package rt68ice.video

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

object VgaDeviceSim extends App {
  // 1. Configure the simulation with support for multi-clock structures
  val simConfig = SimConfig
    .withWave // Generates a VCD wave file to trace video signals
    .allOptimisation

  simConfig.compile(VgaDevice(
    vgaCd  = ClockDomain.external("vgaCd", frequency = FixedFrequency(25 MHz)),
  )).doSim { dut =>

    // ------------------------------------------------------------
    // 2. Initialize and Fork All Clock Domains
    // ------------------------------------------------------------

    // Default system/CPU bus clock (e.g., 10 ns period -> 100 MHz)
    dut.clockDomain.forkStimulus(period = 10)

    // VGA pixel clock (e.g., ~40 ns period -> 25 MHz for standard 640x480)
    dut.vgaCd.forkStimulus(period = 40)

    // ------------------------------------------------------------
    // 3. Initialize Inputs to Safe Default States
    // ------------------------------------------------------------
    dut.io.sel #= false
    dut.io.bus.wr #= false
    dut.io.bus.address #= 0
    dut.io.bus.dataOut #= 0
    dut.io.bus.uds #= false
    dut.io.bus.lds #= false

    // Wait out the reset sequences safely across domains
    dut.clockDomain.waitRisingEdge(5)

    println("[SIM] Clocks initialized. Starting simulation loops...")

    // ------------------------------------------------------------
    // 4. Test Step A: Populate Framebuffer over the M68K Bus
    // ------------------------------------------------------------
    // We write a few distinct 16-bit color patterns into memory locations
    println("[SIM] Writing color patterns to framebuffer via 68k Bus...")

    def writeBus(wordAddress: Int, data16Bit: Int): Unit = {
      dut.io.bus.address #= (wordAddress << 1) // Match the 15 downto 1 slicing
      dut.io.bus.dataOut #= data16Bit
      dut.io.bus.wr #= true
      dut.io.bus.uds #= true
      dut.io.bus.lds #= true
      dut.io.sel #= true

      dut.clockDomain.waitRisingEdge()

      // Deassert control wires
      dut.io.sel #= false
      dut.io.bus.wr #= false
    }

    // Write a magenta pixel (RGB 565: R=31, G=0, B=31 -> 0xF81F) at word 0
    writeBus(0, 0xF81F)
    // Write a green pixel (RGB 565: R=0, G=63, B=0 -> 0x07E0) at word 1
    writeBus(1, 0x07E0)
    // Write a white pixel (RGB 565: 0xFFFF) at word 2
    writeBus(2, 0xFFFF)

    dut.clockDomain.waitRisingEdge(2)

    // ------------------------------------------------------------
    // 5. Test Step B: Observe Video Output Generation
    // ------------------------------------------------------------
    // We fork an independent monitor thread specifically synchronized
    // to the VGA pixel clock to watch memory addressing and data output flow.
    var activePixelsObserved = 0

    val videoMonitor = fork {
      println("[SIM] Video output monitor active...")

      // Loop for a duration long enough to track active lines ticking out
      for (_ <- 0 until 500) {
        dut.vgaCd.waitRisingEdge()

        // Read signals generated inside the vgaCd clocking area
        // Note: Accessing internal variables via standard dot notation is supported in simulation
        val currentAddr = dut.vgaCd { dut.vgaArea.addressCounter.toInt }
        val fifoPushValid = dut.vgaCd { dut.vgaArea.memReadStream.valid.toBoolean }
        val fifoPushData = dut.vgaCd { dut.vgaArea.memReadStream.payload.toInt }

        // Track valid data pushes into the pixel processing pipeline
        if (fifoPushValid) {
          activePixelsObserved += 1
          if (activePixelsObserved <= 5) {
            println(s"[VGA MATCH] Pixel #$activePixelsObserved - Read Addr: 0x${currentAddr.toHexString.toUpperCase}, Data: 0x${fifoPushData.toHexString.toUpperCase}")
          }
        }
      }
    }

    // Let the simulation advance until our background monitor concludes its verification
    videoMonitor.join()
    println(s"[SIM] Completed successfully. Monitored $activePixelsObserved valid pixel transfers.")
  }
}