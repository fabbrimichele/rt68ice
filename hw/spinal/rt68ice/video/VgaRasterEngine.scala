package rt68ice.video

import rt68ice.video.VgaRasterEngine._
import spinal.core._
import spinal.lib._
import spinal.lib.graphic.RgbConfig

import scala.language.postfixOps

//noinspection ScalaWeakerAccess
object VgaRasterEngine {
  val rgbConfig = RgbConfig(8, 8, 8)
  val MODE_320X240_4BPP = 0
  val MODE_640X240_2BPP = 1
  val MODE_640X480_1BPP = 2
  val MODE_320X240_8BPP = 3
  val MODE_640X240_4BPP = 4
  val MODE_640X480_2BPP = 5
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class VgaRasterEngine() extends Component {
  val io = new Bundle {
    val resolution  = in Bits(3 bits)

    // Interface to the top-level memory blocks
    val memAddress = out UInt(16 bits)
    val memData     = in Bits(16 bits)

    // Pixel color index routed out to the Palette
    val colorIndex = out Bits(8 bits)

    // VGA signals
    val vSync   = out Bool()
    val hSync   = out Bool()
    val colorEn = out Bool()
    val vBlankStart = out Bool()
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
      MODE_320X240_4BPP -> (pixelY >> 1).resized,
      MODE_640X240_2BPP -> (pixelY >> 1).resized,
      MODE_320X240_8BPP -> (pixelY >> 1).resized,
      MODE_640X240_4BPP -> (pixelY >> 1).resized,
      default -> pixelY
    )

    // 320-pixel modes duplicate each virtual pixel horizontally.
    val virtualX = io.resolution.mux(
      MODE_320X240_4BPP -> (pixelX >> 1).resized,
      MODE_320X240_8BPP -> (pixelX >> 1).resized,
      default -> pixelX
    )

    // THE FIX: Stretch the internal pipeline counter to match the physical clock scaling
    // In low-res, the pipeline takes 2 physical clock cycles to advance 1 step.
    val cycleCounter = io.resolution.mux(
      MODE_320X240_4BPP -> pixelX(4 downto 1), // 16 steps spanning 32 physical cycles
      MODE_320X240_8BPP -> pixelX(4 downto 1),
      default -> pixelX(3 downto 0)  // 16 steps spanning 16 physical cycles
    )

    // The block latch trigger needs to hit at the very end of the physical block width
    val isEndOfBlock = io.resolution.mux(
      MODE_320X240_4BPP -> (pixelX(4 downto 0) === 31),
      MODE_320X240_8BPP -> (pixelX(4 downto 0) === 31),
      default -> (pixelX(3 downto 0) === 15)
    )

    val planeStride = io.resolution.mux(
      MODE_320X240_4BPP -> U(4, 4 bits),
      MODE_640X240_2BPP -> U(2, 4 bits),
      MODE_320X240_8BPP -> U(8, 4 bits),
      MODE_640X240_4BPP -> U(4, 4 bits),
      MODE_640X480_2BPP -> U(2, 4 bits),
      default -> U(1, 4 bits)
    )

    // Address offset pointer steps evenly across words
    val planeFetchOffset = io.resolution.mux(
      MODE_320X240_4BPP -> cycleCounter(1 downto 0).resized,
      MODE_640X240_2BPP -> (B"2'0" ## cycleCounter(0)).asUInt,
      MODE_320X240_8BPP -> cycleCounter(2 downto 0),
      MODE_640X240_4BPP -> cycleCounter(1 downto 0).resized,
      MODE_640X480_2BPP -> (B"2'0" ## cycleCounter(0)).asUInt,
      default -> U(0, 3 bits)
    )

    // Calculate vertical line baseline offset based on the line width
    val lineBaseAddress = io.resolution.mux(
      MODE_320X240_4BPP -> ((virtualY << 6) + (virtualY << 4)).resized, // Y * 80
      MODE_640X240_2BPP -> ((virtualY << 6) + (virtualY << 4)).resized, // Y * 80
      MODE_320X240_8BPP -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160
      MODE_640X240_4BPP -> ((virtualY << 7) + (virtualY << 5)),         // Y * 160
      MODE_640X480_2BPP -> ((virtualY << 6) + (virtualY << 4)).resized, // Y * 80
      default -> ((virtualY << 5) + (virtualY << 3)).resized            // Y * 40
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
        MODE_320X240_4BPP -> (if (i < 4) pixelX(4 downto 0) === (i * 2 + 1) else False),
        MODE_640X240_2BPP -> (if (i < 2) pixelX(3 downto 0) === (i + 1) else False),
        MODE_320X240_8BPP -> (pixelX(4 downto 0) === (i * 2 + 1)),
        MODE_640X240_4BPP -> (if (i < 4) pixelX(3 downto 0) === (i + 1) else False),
        MODE_640X480_2BPP -> (if (i < 2) pixelX(3 downto 0) === (i + 1) else False),
        default -> (if (i == 0) pixelX(3 downto 0) === 1 else False)
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
      MODE_320X240_4BPP -> shiftIndexLow,
      MODE_320X240_8BPP -> shiftIndexLow,
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
    MODE_320X240_4BPP -> (B"4'0" ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
    MODE_640X240_2BPP -> (B"6'0" ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
    MODE_320X240_8BPP -> (videoPipeline.plane7Bit ## videoPipeline.plane6Bit ## videoPipeline.plane5Bit ## videoPipeline.plane4Bit
      ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
    MODE_640X240_4BPP -> (B"4'0" ## videoPipeline.plane3Bit ## videoPipeline.plane2Bit ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
    MODE_640X480_2BPP -> (B"6'0" ## videoPipeline.plane1Bit ## videoPipeline.plane0Bit),
    default -> (B"7'0" ## videoPipeline.plane0Bit)
  )

  // Pulse once per frame as the raster leaves the final visible pixel. Delay
  // the event by the same amount as the video outputs so software observes
  // vertical blanking at the connector-visible raster boundary.
  val rawVBlankStart =
    (vgaCounter.io.vCounter === vgaCounter.io.timings.v.colorEnd) &&
      (vgaCounter.io.hCounter === vgaCounter.io.timings.h.colorEnd)

  io.vBlankStart := io.resolution.mux(
    MODE_320X240_4BPP -> Delay(rawVBlankStart, videoPipeline.lowResDelay),
    MODE_320X240_8BPP -> Delay(rawVBlankStart, videoPipeline.lowResDelay),
    default -> Delay(rawVBlankStart, videoPipeline.medHighResDelay)
  )

  when((io.resolution === MODE_320X240_4BPP) || (io.resolution === MODE_320X240_8BPP)) {
    io.hSync   := Delay(vgaCounter.io.hSync, videoPipeline.lowResDelay)
    io.vSync   := Delay(vgaCounter.io.vSync, videoPipeline.lowResDelay)
    io.colorEn := Delay(vgaCounter.io.colorEn, videoPipeline.lowResDelay)
  } otherwise {
    io.hSync   := Delay(vgaCounter.io.hSync, videoPipeline.medHighResDelay)
    io.vSync   := Delay(vgaCounter.io.vSync, videoPipeline.medHighResDelay)
    io.colorEn := Delay(vgaCounter.io.colorEn, videoPipeline.medHighResDelay)
  }
}
