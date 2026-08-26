package rt68ice.video

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
object VgaDeviceSim extends App {
  // 1. Configure the simulation with support for multi-clock structures
  val simConfig = SimConfig
    .withFstWave
    .withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH)))
    //.allOptimisation

  simConfig.compile {
    val vgaDevice = VgaDevice(
      vgaCd  = ClockDomain.external("vgaCd", frequency = FixedFrequency(25 MHz)),
    )

    vgaDevice
  }.doSim { dut =>

    // ------------------------------------------------------------
    // 2. Initialize and Fork All Clock Domains
    // ------------------------------------------------------------

    // System/CPU bus clock (25 MHz)
    dut.clockDomain.forkStimulus(period = 40)

    // VGA pixel clock (e.g., ~40 ns period -> 25 MHz for standard 640x480)
    dut.vgaCd.forkStimulus(period = 40)

    // ------------------------------------------------------------
    // 3. Initialize Inputs to Safe Default States
    // ------------------------------------------------------------
    dut.io.fbSel #= false
    dut.io.palSel #= false
    dut.io.ctrlSel #= false
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
      dut.io.fbSel #= true

      dut.clockDomain.waitRisingEdge()

      // Deassert control wires
      dut.io.fbSel #= false
      dut.io.bus.wr #= false
    }

    // Write a magenta pixel (RGB 565: R=31, G=0, B=31 -> 0xF81F) at word 0
    writeBus(0, 0xF81F)
    // Write a green pixel (RGB 565: R=0, G=63, B=0 -> 0x07E0) at word 1
    writeBus(1, 0x07E0)
    // Write a white pixel (RGB 565: 0xFFFF) at word 2
    writeBus(2, 0xFFFF)

    // ------------------------------------------------------------
    // 5. Test Step B: Enable, receive and acknowledge the VBL IRQ
    // ------------------------------------------------------------
    def writeControlRegister(byteOffset: Int, data16Bit: Int): Unit = {
      dut.io.bus.address #= byteOffset
      dut.io.bus.dataOut #= data16Bit
      dut.io.bus.wr #= true
      dut.io.bus.uds #= true
      dut.io.bus.lds #= true
      dut.io.ctrlSel #= true

      dut.clockDomain.waitRisingEdge()

      dut.io.ctrlSel #= false
      dut.io.bus.wr #= false
      dut.io.bus.uds #= false
      dut.io.bus.lds #= false
    }

    writeControlRegister(byteOffset = 4, data16Bit = 1)
    assert(!dut.io.int.toBoolean, "VBL interrupt was active before vertical blank")

    var pixelClocks = 0
    while(!dut.io.int.toBoolean && pixelClocks < 430_000) {
      dut.vgaCd.waitRisingEdge()
      pixelClocks += 1
    }
    assert(dut.io.int.toBoolean, "VBL did not raise an interrupt within one frame")

    dut.clockDomain.waitRisingEdge(4)
    assert(dut.io.int.toBoolean, "VBL interrupt was not held pending")

    dut.io.bus.address #= 2
    dut.io.bus.wr #= false
    dut.io.bus.uds #= true
    dut.io.bus.lds #= true
    dut.io.ctrlSel #= true
    sleep(1)
    assert((dut.io.bus.dataIn.toInt & 1) == 1, "IRQ_STATUS did not report VBL pending")

    dut.clockDomain.waitRisingEdge()
    sleep(1)
    dut.io.ctrlSel #= false
    dut.io.bus.uds #= false
    dut.io.bus.lds #= false
    assert(!dut.io.int.toBoolean, "Reading IRQ_STATUS did not clear the VBL interrupt")

    println(s"[SIM] VBL interrupt asserted after $pixelClocks pixel clocks and cleared on status read")


    /*
    // ------------------------------------------------------------
    // 5. Test Step B: Observe Video Output Generation
    // ------------------------------------------------------------
    // We fork an independent monitor thread specifically synchronized
    // to the VGA pixel clock to watch memory addressing and data output flow.
    var activePixelsObserved = 0

    val videoMonitor = fork {
      println("[SIM] Video output monitor active...")

      // Keep checking until we have successfully verified our 3 target pixels
      // or verified up to a safe portion of the active line (e.g., 20 pixels)
      while (activePixelsObserved < 10) {

        // 1. ALWAYS wait for the clock edge first to let hardware settle cleanly
        dut.vgaCd.waitRisingEdge()

        // 2. Capture fresh, safe states immediately following the edge
        val hSync = dut.io.vga.hSync.toBoolean
        val vSync = dut.io.vga.vSync.toBoolean
        val colorEn = dut.io.vga.colorEn.toBoolean

        val r = dut.io.vga.color.r.toInt
        val g = dut.io.vga.color.g.toInt
        val b = dut.io.vga.color.b.toInt

        // 3. Main Verification Logic
        if (colorEn) {
          activePixelsObserved += 1

          activePixelsObserved match {
            case 1 => // First pixel: Magenta (0xF81F)
              assert(r == 248 && g == 0 && b == 248, s"Pixel 1 mismatch! Got R:$r G:$g B:$b")
              println(s"[VGA MATCH] Pixel #1 is correct Magenta (248, 0, 248)")

            case 2 => // Second pixel: Green (0x07E0)
              assert(r == 0 && g == 252 && b == 0, s"Pixel 2 mismatch! Got R:$r G:$g B:$b")
              println(s"[VGA MATCH] Pixel #2 is correct Green (0, 252, 0)")

            case 3 => // Third pixel: White (0xFFFF)
              assert(r == 248 && g == 252 && b == 248, s"Pixel 3 mismatch! Got R:$r G:$g B:$b")
              println(s"[VGA MATCH] Pixel #3 is correct White (248, 252, 248)")

            case _ =>
              if (activePixelsObserved <= 10) {
                println(s"[VGA INFO] Pixel #$activePixelsObserved seen at IO - R:$r G:$g B:$b")
              }
          }
        }
      }
    }

    // Let the simulation advance until our background monitor concludes its verification
    videoMonitor.join()
    println(s"[SIM] Completed successfully. Monitored $activePixelsObserved valid pixel transfers.")

     */
  }
}
