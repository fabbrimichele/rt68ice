package rt68ice.video

import rt68ice.video.VgaRasterEngine._
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.RgbConfig

import scala.language.postfixOps

//noinspection ScalaWeakerAccess
object VgaRasterEngine {
  val rgbConfig = RgbConfig(8, 8, 8)
  val RES_LOW   = 0
  val RES_MED   = 1
  val RES_HIGH  = 2
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaRasterEngine() extends Component {
  val io = new Bundle {
    val resolution  = in Bits(2 bits)

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

    val virtualY = io.resolution.mux(
      RES_LOW -> (pixelY >> 1).resized,
      RES_MED -> (pixelY >> 1).resized,
      default -> pixelY
    )

    val planeStride  = io.resolution.mux(
      RES_LOW -> U(4, 3 bits), // TODO
      RES_MED -> U(4, 3 bits),
      default -> U(2, 3 bits).resized
    )

    // Calculate vertical line baseline offset based on the line width
    val lineBaseAddress = io.resolution.mux(
      RES_LOW -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160 TODO
      RES_MED -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160
      default -> ((virtualY << 6) + (virtualY << 4)).resized  // Y * 80
    )

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
    val fetchRegs = Vec(Reg(Bits(16 bits)) init 0, 8)
    when(cycleCounter === 1) { fetchRegs(0) := io.memData }
    when(cycleCounter === 2) { fetchRegs(1) := io.memData }
    when(cycleCounter === 3) { fetchRegs(2) := io.memData }
    when(cycleCounter === 4) { fetchRegs(3) := io.memData }
    when(cycleCounter === 5) { fetchRegs(4) := io.memData }
    when(cycleCounter === 6) { fetchRegs(5) := io.memData }
    when(cycleCounter === 7) { fetchRegs(6) := io.memData }
    when(cycleCounter === 8) { fetchRegs(7) := io.memData }

    // THE DOUBLE-BUFFER SHIFT LATCH
    val shiftRegs = Vec(Reg(Bits(16 bits)) init 0, 8)
    when(cycleCounter === 15) {
      shiftRegs(0) := fetchRegs(0)
      shiftRegs(1) := fetchRegs(1)
      shiftRegs(2) := fetchRegs(2)
      shiftRegs(3) := fetchRegs(3)
      shiftRegs(4) := fetchRegs(4)
      shiftRegs(5) := fetchRegs(5)
      shiftRegs(6) := fetchRegs(6)
      shiftRegs(7) := fetchRegs(7)
    }

    // PIXEL STREAM OUT
    val pixelBitIdx = ~pixelX(3 downto 0)
    val plane0Bit = shiftRegs(0)(pixelBitIdx)
    val plane1Bit = shiftRegs(1)(pixelBitIdx)
    val plane2Bit = shiftRegs(2)(pixelBitIdx)
    val plane3Bit = shiftRegs(3)(pixelBitIdx)
    val plane4Bit = shiftRegs(4)(pixelBitIdx)
    val plane5Bit = shiftRegs(5)(pixelBitIdx)
    val plane6Bit = shiftRegs(6)(pixelBitIdx)
    val plane7Bit = shiftRegs(7)(pixelBitIdx)
  }

  // Combine planes into color index
  io.colorIndex := io.resolution.mux(
      RES_LOW -> (videoPipeline.plane7Bit ## videoPipeline.plane6Bit ## videoPipeline.plane5Bit ## videoPipeline.plane4Bit
        ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
      RES_MED -> (B"4'0" ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
      default -> (B"6'0" ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit)
    )

  io.hSync   := Delay(vgaCounter.io.hSync, 16)
  io.vSync   := Delay(vgaCounter.io.vSync, 16)
  io.colorEn := Delay(vgaCounter.io.colorEn, 16)
}