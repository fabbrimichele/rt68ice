package rt68ice.video

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

/** Checks the compact word layout and pixel unpacking of the 640x480 1bpp mode. */
object VgaMonoRasterEngineSim extends App {
  val simConfig = SimConfig.withConfig(
    SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH))
  )

  simConfig.compile {
    val rasterEngine = VgaRasterEngine()
    rasterEngine.videoPipeline.pixelX.simPublic()
    rasterEngine.videoPipeline.virtualY.simPublic()
    rasterEngine
  }.doSim { dut =>
    dut.io.resolution #= VgaRasterEngine.MODE_640X480_1BPP
    dut.io.memData #= 0
    dut.clockDomain.forkStimulus(period = 40)

    dut.clockDomain.assertReset()
    dut.clockDomain.waitRisingEdge(5)
    dut.clockDomain.deassertReset()

    // Model the one-cycle latency of the framebuffer port. The first two
    // words on virtual row 1 exercise both ends of the 16-pixel word.
    val framebuffer = Map[Int, Long](
      40 -> 0x8001L,
      41 -> 0x4002L
    )
    fork {
      while (true) {
        dut.clockDomain.waitRisingEdge()
        dut.io.memData #= framebuffer.getOrElse(dut.io.memAddress.toInt, 0L)
      }
    }

    def waitForPosition(y: Int, x: Int): Unit = {
      var clocks = 0
      while ((dut.videoPipeline.virtualY.toInt != y || dut.videoPipeline.pixelX.toInt != x) && clocks < 500000) {
        dut.clockDomain.waitRisingEdge()
        clocks += 1
      }
      assert(clocks < 500000, s"Timed out waiting for virtual pixel ($x,$y)")
    }

    // Address = y * 40 + x / 16, including the final word of a row and the
    // transition to the following row.
    // At x=0 the line-start register update and the sampled testbench value
    // share an edge. Check x=1, where the reset group index is settled.
    waitForPosition(y = 1, x = 1)
    assert(dut.io.memAddress.toInt == 40, s"Row 1 base was ${dut.io.memAddress.toInt}, expected 40")

    waitForPosition(y = 1, x = 16)
    assert(dut.io.memAddress.toInt == 41, s"Second word was ${dut.io.memAddress.toInt}, expected 41")
    assert(dut.io.colorIndex.toInt == 1, "Bit 15 of word 40 did not select palette entry 1")

    waitForPosition(y = 1, x = 17)
    assert(dut.io.colorIndex.toInt == 0, "Bit 14 of word 40 did not select palette entry 0")

    waitForPosition(y = 1, x = 31)
    assert(dut.io.colorIndex.toInt == 1, "Bit 0 of word 40 did not select palette entry 1")

    waitForPosition(y = 1, x = 33)
    assert(dut.io.colorIndex.toInt == 1, "Bit 14 of word 41 did not select palette entry 1")

    waitForPosition(y = 1, x = 624)
    assert(dut.io.memAddress.toInt == 79, s"Last word was ${dut.io.memAddress.toInt}, expected 79")

    waitForPosition(y = 2, x = 1)
    assert(dut.io.memAddress.toInt == 80, s"Row 2 base was ${dut.io.memAddress.toInt}, expected 80")

    println("[SIM] 640x480 1bpp addressing and pixel unpacking passed")
  }
}
