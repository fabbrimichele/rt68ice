package rt68ice.video

import rt68ice.video.VgaRasterEngine.rgbConfig
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.RgbConfig

import scala.language.postfixOps

object VgaRasterEngine {
  val rgbConfig = RgbConfig(8, 8, 8)
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaRasterEngine() extends Component {
  val io = new Bundle {
    val resolution  = in Bool()

    // Interface to the top-level memory blocks
    val memAddress = out UInt(16 bits)
    val memData     = in Bits(16 bits)

    // Pixel color index routed out to the Palette
    val colorIndex = out Bits(4 bits)

    // VGA signals
    val vSync   = out Bool()
    val hSync   = out Bool()
    val colorEn = out Bool()
  }

  val vgaCounter = VgaCounter(rgbConfig)
  vgaCounter.io.timings.setAs_h640_v480_r60

  val videoPipeline = new Area {
    val timings = vgaCounter.io.timings
    val hCounter = vgaCounter.io.hCounter
    val vCounter = vgaCounter.io.vCounter

    val pixelX = hCounter - timings.h.colorStart
    val pixelY = vCounter - timings.v.colorStart
    val virtualY = io.resolution ? pixelY | (pixelY >> 1)

    // SEQUENTIAL FETCH BUFFERS
    val cycleCounter = pixelX(3 downto 0)
    val planeStride  = io.resolution ? U(2, 3 bits) | U(4, 3 bits)

    // Calculate vertical line baseline offset based on line width
    // High-Res (2bpp) = Y * 80 words per line
    // Low-Res  (4bpp) = Y * 160 words per line
    val lineBaseAddress = io.resolution ?
      ((virtualY << 6) + (virtualY << 4)) | // (Y * 64) + (Y * 16)  = Y * 80
      ((virtualY << 7) + (virtualY << 5))   // (Y * 128) + (Y * 32) = Y * 160

    // Both resolutions span all 40 columns (0 to 39) across the 640-pixel screen
    val currentGroupIdx = (pixelX >> 4)
    val maxGroupIdx     = U(39, 6 bits)
    val nextGroupIdx    = (currentGroupIdx >= maxGroupIdx) ? U(0) | (currentGroupIdx + 1)

    val nextWordGroupOffset = nextGroupIdx * planeStride
    val currentPlaneAddress = lineBaseAddress + nextWordGroupOffset + cycleCounter(1 downto 0)
    io.memAddress := currentPlaneAddress.resized

    // The Fetch Team: Updates sequentially, one cycle at a time
    val fetchRegs = Vec(Reg(Bits(16 bits)) init 0, 4)
    when(Delay(cycleCounter === 0, 1)) { fetchRegs(0) := io.memData }
    when(Delay(cycleCounter === 1, 1)) { fetchRegs(1) := io.memData }
    when(Delay(cycleCounter === 2, 1)) { fetchRegs(2) := io.memData }
    when(Delay(cycleCounter === 3, 1)) { fetchRegs(3) := io.memData }

    // THE DOUBLE-BUFFER SHIFT LATCH
    val shiftRegs = Vec(Reg(Bits(16 bits)) init 0, 4)
    when(cycleCounter === 15) {
      shiftRegs(0) := fetchRegs(0)
      shiftRegs(1) := fetchRegs(1)
      shiftRegs(2) := fetchRegs(2)
      shiftRegs(3) := fetchRegs(3)
    }

    // PIXEL STREAM OUT
    val pixelBitIdx = ~pixelX(3 downto 0)
    val plane0Bit = shiftRegs(0)(pixelBitIdx)
    val plane1Bit = shiftRegs(1)(pixelBitIdx)
    val plane2Bit = shiftRegs(2)(pixelBitIdx)
    val plane3Bit = shiftRegs(3)(pixelBitIdx)
  }

  // Combine planes into color index
  io.colorIndex := io.resolution ?
    (B"2'b00" ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit) |
    (videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit)

  // No delay required, it might be that VgaCounter
  // already take 1 cycle delay in account.
  io.hSync   := vgaCounter.io.hSync
  io.vSync   := vgaCounter.io.vSync
  io.colorEn := vgaCounter.io.colorEn

  // CRITICAL TIMING LATENCY MATCHING
  //io.hSync   := Delay(vgaCounter.io.hSync, 1)
  //io.vSync   := Delay(vgaCounter.io.vSync, 1)
  //io.colorEn := Delay(vgaCounter.io.colorEn, 1)
}

