package rt68ice.video

import spinal.core.sim.{SimBitVectorPimper, SimBoolPimper, SimClockDomainHandlePimper, SimConfig, SimDataPimper, SimEquivBitVectorBigIntPimper, fork}
import spinal.core.{ClockDomainConfig, HIGH, SYNC, SpinalConfig}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
object VgaRasterEngineSim extends App {
  val simConfig = SimConfig
    .withFstWave
    .withConfig(SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH)))

  simConfig.compile {
    val vgaDevice = VgaRasterEngine()

    vgaDevice.videoPipeline.hCounter.simPublic()
    vgaDevice.videoPipeline.vCounter.simPublic()
    vgaDevice.videoPipeline.pixelX.simPublic()
    vgaDevice.videoPipeline.pixelY.simPublic()
    vgaDevice.videoPipeline.virtualY.simPublic()
    vgaDevice.videoPipeline.cycleCounter.simPublic()

    vgaDevice
  }.doSim { dut =>
    // ------------------------------------------------------------
    // 2. Setup the Master Pixel Clock (25 MHz -> 40 ns Period)
    // ------------------------------------------------------------
    dut.clockDomain.forkStimulus(period = 40)

    // ------------------------------------------------------------
    // 3. Initialize Inputs and Mock Video RAM
    // ------------------------------------------------------------
    dut.io.resolution #= VgaRasterEngine.RES_HIGH

    // Initialize our mocked VRAM buffer array
    // Word Group 0 (Line 0, Column 0) -> words 0, 1, 2, 3
    // Word Group 1 (Line 0, Column 1) -> words 4, 5, 6, 7
    val mockVram = new Array[Int](40 * 4) // Enough for the first active row

    // Let's seed Word Group 1 (Pixels 16 to 31) with visible flags
    // Pixel 16 (Bit 15 of the words) will be set to Palette Index 9 (%1001 -> Planes 0 & 3 active)
    mockVram(4) = 0x8000 // Word Group 1, Plane 0: Bit 15 active
    mockVram(5) = 0x0000 // Word Group 1, Plane 1: Inactive
    mockVram(6) = 0x0000 // Word Group 1, Plane 2: Inactive
    mockVram(7) = 0x8000 // Word Group 1, Plane 3: Bit 15 active

    // Let's seed Word Group 2 (Pixels 32 to 47)
    // Pixel 32 (Bit 15) set to Palette Index 6 (%0110 -> Planes 1 & 2 active)
    mockVram(8)  = 0x0000
    mockVram(9)  = 0x8000 // Word Group 2, Plane 1: Bit 15 active
    mockVram(10) = 0x8000 // Word Group 2, Plane 2: Bit 15 active
    mockVram(11) = 0x0000

    dut.io.memData #= 0

    // Apply Reset
    dut.clockDomain.assertReset()
    dut.clockDomain.waitRisingEdge(5)
    dut.clockDomain.deassertReset()
    dut.clockDomain.waitRisingEdge(2)

    // ------------------------------------------------------------
    // 4. Fork Thread: Asynchronous Memory Controller Model
    // ------------------------------------------------------------
    // This background process continually samples the output `memAddress`
    // and loops back the stored data into `memData` for the next clock edge.
    fork {
      while(true) {
        dut.clockDomain.waitRisingEdge()
        val requestedAddr = dut.io.memAddress.toInt
        if (requestedAddr < mockVram.length) {
          dut.io.memData #= mockVram(requestedAddr)
        } else {
          dut.io.memData #= 0 // Return blank for unpopulated bounds
        }
      }
    }

    // ------------------------------------------------------------
    // 5. Fork Thread: Pixel Output Assertion Pipeline
    // ------------------------------------------------------------
    var targetPixelsVerified = 0

    val pixelMonitor = fork {
      // Advance past the front horizontal porch space until active pixels are flowing

      dut.clockDomain.waitRisingEdge()

      while(true) {
        val hCounter = dut.videoPipeline.hCounter.toInt
        val vCounter = dut.videoPipeline.vCounter.toInt
        val pixelX = dut.videoPipeline.pixelX.toInt
        val pixelY = dut.videoPipeline.pixelY.toInt
        val virtualY = dut.videoPipeline.virtualY.toInt
        val cyclesCounter = dut.videoPipeline.cycleCounter.toInt

        println(s"hCounter=$hCounter | vCounter=$vCounter | pixelX=$pixelX | pixelY=$pixelY | virtualY=$virtualY | cyclesCounter=$cyclesCounter ")
        if (vCounter == 524) {
          println("**********************************************************************************************************************************")
        }
        dut.clockDomain.waitRisingEdge()
      }

      while(!dut.io.colorEn.toBoolean) {
        dut.clockDomain.waitRisingEdge()
      }

      println("[SIM] Active display window reached. Analyzing pipeline outputs...")

      // We wait exactly 16 cycles inside the active window.
      // Because your fetch engine looks *one group ahead*, the data fetched from
      // Word Group 1 (addresses 4-7) will latch and display during Pixels 16-31.
      dut.clockDomain.waitRisingEdge(16)

      // --- VERIFY PIXEL 16 (First pixel of Word Group 1) ---
      val colorIdx16 = dut.io.colorIndex.toInt
      assert(colorIdx16 == 9, s"Pixel 16 Mismatch! Got Index $colorIdx16, Expected 9")
      println(s"[MATCH] Pixel 16 parsed correctly! Interleaved Index: $colorIdx16 (%1001)")
      targetPixelsVerified += 1

      // Advance by 16 more clock ticks to cross into Word Group 2 (Pixels 32-47)
      //dut.clockDomain.waitRisingEdge(16)

      // --- VERIFY PIXEL 32 (First pixel of Word Group 2) ---
      val colorIdx32 = dut.io.colorIndex.toInt
      assert(colorIdx32 == 6, s"Pixel 32 Mismatch! Got Index $colorIdx32, Expected 6")
      println(s"[MATCH] Pixel 32 parsed correctly! Interleaved Index: $colorIdx32 (%0110)")
      targetPixelsVerified += 1
    }

    // Join the pixel output thread
    pixelMonitor.join()
    println(s"[SIM] Test completed successfully. Verified $targetPixelsVerified target pixel transitions.")
  }
}