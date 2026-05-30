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
    val colorIndex = out Bits(8 bits)

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

    val isActiveX = hCounter >= timings.h.colorStart
    val pixelX = isActiveX ? (hCounter - timings.h.colorStart) | U(0)
    val pixelY = (vCounter > timings.v.colorStart) ? (vCounter - timings.v.colorStart) | U(0)
    val virtualY = io.resolution ? pixelY | (pixelY >> 1)

    val planeStride  = io.resolution ? U(2, 3 bits) | U(4, 3 bits)

    // Calculate vertical line baseline offset based on line width
    val lineBaseAddress = io.resolution ?
      ((virtualY << 6) + (virtualY << 4)) | // Y * 80
      ((virtualY << 7) + (virtualY << 5))   // Y * 160

    // --- ROBUST FETCH ENGINE COUNTERS ---
    // Instead of computing look-ahead purely combinatorially from pixelX,
    // we manage a clear 16-step cycle counter that runs continuously.
    val cycleCounter = pixelX(3 downto 0)

    // Robust Look-Ahead: We are fetching Group 0 during the 16 cycles BEFORE active video,
    // and advancing to group N+1 when the shift registers dump at cycle 15.
    val fetchGroupIdx = Reg(UInt(6 bits)) init 0

    when(!isActiveX) {
      fetchGroupIdx := 0 // Hold at group 0 during blanking/back porch to pre-fetch safely
    } elsewhen(cycleCounter === 15) {
      fetchGroupIdx := fetchGroupIdx + 1
    }

    // Address generation is now steady and won't glitch at the edge of the screen
    val currentPlaneAddress = lineBaseAddress + (fetchGroupIdx * planeStride) + cycleCounter(1 downto 0)
    io.memAddress := currentPlaneAddress.resized

    // The Fetch Team: Updates sequentially, one cycle at a time
    val fetchRegs = Vec(Reg(Bits(16 bits)) init 0, 4)
    when(cycleCounter === 1) { fetchRegs(0) := io.memData }
    when(cycleCounter === 2) { fetchRegs(1) := io.memData }
    when(cycleCounter === 3) { fetchRegs(2) := io.memData }
    when(cycleCounter === 4) { fetchRegs(3) := io.memData }

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
    (B"6'b000000" ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit) |
    (B"4'b0000" ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit)

  io.hSync   := Delay(vgaCounter.io.hSync, 16)
  io.vSync   := Delay(vgaCounter.io.vSync, 16)
  io.colorEn := Delay(vgaCounter.io.colorEn, 16)
}