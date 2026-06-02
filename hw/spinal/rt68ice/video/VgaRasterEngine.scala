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

    // virtualX slows down by half ONLY for low-res pixel tracking
    val virtualX = io.resolution.mux(
      RES_LOW -> (pixelX >> 1).resized,
      RES_MED -> pixelX,
      default -> pixelX
    )

    // THE FIX: Stretch the internal pipeline counter to match the physical clock scaling
    // In low-res, the pipeline takes 2 physical clock cycles to advance 1 step.
    val cycleCounter = io.resolution.mux(
      RES_LOW -> pixelX(4 downto 1), // 16 steps spanning 32 physical cycles
      default -> pixelX(3 downto 0)  // 16 steps spanning 16 physical cycles
    )

    // The block latch trigger needs to hit at the very end of the physical block width
    val isEndOfBlock = io.resolution.mux(
      RES_LOW -> (pixelX(4 downto 0) === 31),
      default -> (pixelX(3 downto 0) === 15)
    )

    val planeStride = io.resolution.mux(
      RES_LOW -> U(8, 4 bits).resized,  // 8 words per block line segment
      RES_MED -> U(4, 4 bits).resized,  // 4 words per block line segment
      default -> U(2, 4 bits).resized   // 2 words per block line segment
    )

    // Address offset pointer steps evenly across words
    val planeFetchOffset = io.resolution.mux(
      RES_LOW -> cycleCounter(2 downto 0),                  // Steps 0-7 once per block
      RES_MED -> cycleCounter(1 downto 0).resized,          // Steps 0-3
      default -> (B"2'0" ## cycleCounter(0)).asUInt.resized // Steps 0-1
    )

    // Calculate vertical line baseline offset based on the line width
    val lineBaseAddress = io.resolution.mux(
      RES_LOW -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160
      RES_MED -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160
      default -> ((virtualY << 6) + (virtualY << 4)).resized  // Y * 80
    )

    // Safely increment only when the physical block is completely finished rendering
    val fetchGroupIdx = Reg(UInt(6 bits)) init 0
    when(!isActiveX) {
      fetchGroupIdx := 0
    } elsewhen(isActiveX && isEndOfBlock) {
      fetchGroupIdx := fetchGroupIdx + 1
    }

    // Address generation is gated safely to prevent look-ahead address leaking out of bounds
    val currentPlaneAddress = lineBaseAddress + (fetchGroupIdx * planeStride) + planeFetchOffset
    io.memAddress := currentPlaneAddress.resized

    // The Fetch Team: Updates sequentially, one cycle at a time
    val fetchEnable = Vec(Bool(), 8)
    for(i <- 0 until 8) {
      fetchEnable(i) := io.resolution.mux(
        RES_LOW -> (pixelX(4 downto 0) === (i * 2 + 1)), // Pulses on 1, 3, 5, 7, 9, 11, 13, 15
        default -> (pixelX(3 downto 0) === (i + 1))      // Pulses on 1, 2, 3, 4, 5, 6, 7, 8
      )
    }

    val fetchRegs = Vec(Reg(Bits(16 bits)) init 0, 8)
    when(fetchEnable(0)) { fetchRegs(0) := io.memData }
    when(fetchEnable(1)) { fetchRegs(1) := io.memData }
    when(fetchEnable(2)) { fetchRegs(2) := io.memData }
    when(fetchEnable(3)) { fetchRegs(3) := io.memData }
    when(fetchEnable(4)) { fetchRegs(4) := io.memData }
    when(fetchEnable(5)) { fetchRegs(5) := io.memData }
    when(fetchEnable(6)) { fetchRegs(6) := io.memData }
    when(fetchEnable(7)) { fetchRegs(7) := io.memData }

    // THE DOUBLE-BUFFER SHIFT LATCH
    val shiftRegs = Vec(Reg(Bits(16 bits)) init 0, 8)
    when(isEndOfBlock) {
      shiftRegs(0) := fetchRegs(0)
      shiftRegs(1) := fetchRegs(1)
      shiftRegs(2) := fetchRegs(2)
      shiftRegs(3) := fetchRegs(3)
      shiftRegs(4) := fetchRegs(4)
      shiftRegs(5) := fetchRegs(5)
      shiftRegs(6) := fetchRegs(6)
      shiftRegs(7) := fetchRegs(7)
    }

    // We must delay the shift index so the multiplexer keeps streaming
    // data during the delayed monitor output window.
    val lowResDelay = 32
    val medHighResDelay = 16

    val shiftIndexLow = Delay(virtualX(3 downto 0), lowResDelay)
    val shiftIndexMed = Delay(virtualX(3 downto 0), medHighResDelay)

    val outVirtualX = io.resolution.mux(
      RES_LOW -> shiftIndexLow,
      default -> shiftIndexMed
    )

    // PIXEL STREAM OUT
    val pixelBitIdx = ~outVirtualX
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

  when(io.resolution === RES_LOW) {
    io.hSync   := Delay(vgaCounter.io.hSync, videoPipeline.lowResDelay)
    io.vSync   := Delay(vgaCounter.io.vSync, videoPipeline.lowResDelay)
    io.colorEn := Delay(vgaCounter.io.colorEn, videoPipeline.lowResDelay)
  } otherwise {
    io.hSync   := Delay(vgaCounter.io.hSync, videoPipeline.medHighResDelay)
    io.vSync   := Delay(vgaCounter.io.vSync, videoPipeline.medHighResDelay)
    io.colorEn := Delay(vgaCounter.io.colorEn, videoPipeline.medHighResDelay)
  }
}
