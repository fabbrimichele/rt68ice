package rt68ice.video

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

/** Verifies geometry, plane count, and fallback behavior for every mode value. */
object VgaScreenModesSim extends App {
  case class Mode(mode: Int, width: Int, height: Int, planes: Int, wordsPerLine: Int)

  val modes = Seq(
    Mode(0, 320, 200, 4, 80),
    Mode(1, 640, 200, 2, 80),
    Mode(2, 640, 400, 1, 40),
    Mode(3, 320, 240, 4, 80),
    Mode(4, 640, 240, 2, 80),
    Mode(5, 640, 480, 1, 40),
    Mode(6, 320, 240, 8, 160),
    Mode(7, 640, 240, 4, 160),
    Mode(8, 640, 480, 2, 80)
  ) ++ (9 to 15).map(id => Mode(id, 640, 480, 1, 40))


  val simConfig = SimConfig.withConfig(
    SpinalConfig(defaultConfigForClockDomains = ClockDomainConfig(resetKind = SYNC, resetActiveLevel = HIGH))
  )

  simConfig.compile {
    val rasterEngine = VgaRasterEngine()
    rasterEngine.videoPipeline.pixelX.simPublic()
    rasterEngine.videoPipeline.pixelY.simPublic()
    rasterEngine.videoPipeline.virtualY.simPublic()
    rasterEngine.videoPipeline.lineBaseAddress.simPublic()
    rasterEngine
  }.doSim { dut =>
    dut.io.resolution #= 0
    dut.io.memData #= 0
    dut.clockDomain.forkStimulus(period = 40)

    var framebuffer = Map.empty[Int, Long]
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

    def waitForPhysicalPosition(y: Int, x: Int): Unit = {
      var clocks = 0
      while ((dut.videoPipeline.pixelY.toInt != y || dut.videoPipeline.pixelX.toInt != x) && clocks < 500000) {
        dut.clockDomain.waitRisingEdge()
        clocks += 1
      }
      assert(clocks < 500000, s"Timed out waiting for physical pixel ($x,$y)")
    }

    for (mode <- modes) {
      dut.io.resolution #= mode.mode

      val firstLineBase = mode.wordsPerLine
      framebuffer = (0 until mode.planes).map { plane =>
        (firstLineBase + plane) -> 0x8001L
      }.toMap

      dut.clockDomain.assertReset()
      dut.clockDomain.waitRisingEdge(5)
      dut.clockDomain.deassertReset()

      val physicalBlockWidth = if (mode.width == 320) 32 else 16
      val physicalPixelStep = if (mode.width == 320) 2 else 1
      val groupCount = mode.width / 16
      val expectedColor = (1 << mode.planes) - 1

      val letterboxed = mode.mode >= 0 && mode.mode <= 2
      if (letterboxed) {
        waitForPhysicalPosition(y = 39, x = physicalBlockWidth)
        assert(!dut.io.imageEn.toBoolean, s"Mode ${mode.mode}: top band enabled image")
        assert(dut.io.colorEn.toBoolean, "Black band must remain physical active video")
        assert(dut.io.memAddress.toInt == 0, "Border must clamp framebuffer address")
        waitForPhysicalPosition(y = 40, x = physicalBlockWidth)
        assert(dut.videoPipeline.lineBaseAddress.toInt == 0, "First image line must start at framebuffer zero")
        assert(dut.io.imageEn.toBoolean, "First image line must enable image")
      }

      // The first group's words all have bit 15 set, so its first output
      // pixel must combine every active plane into the maximum color index.
      waitForPosition(y = 1, x = physicalBlockWidth)
      assert(dut.videoPipeline.lineBaseAddress.toInt == firstLineBase,
        s"Mode ${mode.mode}: line base was ${dut.videoPipeline.lineBaseAddress.toInt}, expected $firstLineBase")
      assert(dut.io.memAddress.toInt == firstLineBase + mode.planes,
        s"Mode ${mode.mode}: second group started at ${dut.io.memAddress.toInt}, expected ${firstLineBase + mode.planes}")
      assert(dut.io.colorIndex.toInt == expectedColor,
        s"Mode ${mode.mode}: color was ${dut.io.colorIndex.toInt}, expected $expectedColor")

      // Bit 14 is clear in every seeded plane. In 320-wide modes each source
      // pixel occupies two physical clocks.
      waitForPosition(y = 1, x = physicalBlockWidth + physicalPixelStep)
      assert(dut.io.colorIndex.toInt == 0,
        s"Mode ${mode.mode}: pixel duplication/bit order produced ${dut.io.colorIndex.toInt}, expected 0")

      val lastGroupX = (groupCount - 1) * physicalBlockWidth
      val lastGroupAddress = firstLineBase + (groupCount - 1) * mode.planes
      waitForPosition(y = 1, x = lastGroupX)
      assert(dut.io.memAddress.toInt == lastGroupAddress,
        s"Mode ${mode.mode}: last group started at ${dut.io.memAddress.toInt}, expected $lastGroupAddress")

      waitForPosition(y = 2, x = 1)
      assert(dut.videoPipeline.lineBaseAddress.toInt == 2 * mode.wordsPerLine,
        s"Mode ${mode.mode}: next line base was ${dut.videoPipeline.lineBaseAddress.toInt}, expected ${2 * mode.wordsPerLine}")

      if (letterboxed) {
        waitForPhysicalPosition(y = 439, x = lastGroupX)
        val lastLineBase = (mode.height - 1) * mode.wordsPerLine
        assert(dut.videoPipeline.lineBaseAddress.toInt == lastLineBase, "Wrong last source line")
        assert(dut.io.memAddress.toInt == lastLineBase + (groupCount - 1) * mode.planes,
          "Wrong last pixel-group address")
        assert(dut.io.imageEn.toBoolean, "Last image line must remain enabled")
        waitForPhysicalPosition(y = 440, x = physicalBlockWidth)
        assert(!dut.io.imageEn.toBoolean, "Bottom band must disable image")
        assert(dut.io.colorEn.toBoolean, "Bottom band must remain physical active video")
        assert(dut.io.memAddress.toInt == 0, "Bottom band must clamp framebuffer address")
      }

      println(s"[SIM] Mode ${mode.mode}: ${mode.width}x${mode.height} ${mode.planes}bpp passed")
    }
  }
}
