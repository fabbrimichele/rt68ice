package rt68ice.video

import spinal.core.{B, Bits, IntToBuilder}

import scala.language.postfixOps

class TestPatterns(sizeInWords: Int, width: Int, height: Int) {
  def box(): Seq[Bits] = {
    val totalPixels = width * height

    // 1. Define rectangle geometry parameters
    val boxLeft   = 0
    val boxRight  = width - 1
    val boxTop    = 0
    val boxBottom = height - 1

    // Border Color: Bright Green in RGB565 (R=0, G=63, B=0) -> 0x07E0
    val borderColor = (0 << 11) | (63 << 5) | 0

    val frameData = (0 until totalPixels).map { index =>
      val x = index % width
      val y = index / width

      // Check if the coordinate falls exactly on the border edges
      val isLeftEdge   = (x == boxLeft)   && (y >= boxTop  && y <= boxBottom)
      val isRightEdge  = (x == boxRight)  && (y >= boxTop  && y <= boxBottom)
      val isTopEdge    = (y == boxTop)    && (x >= boxLeft && x <= boxRight)
      val isBottomEdge = (y == boxBottom) && (x >= boxLeft && x <= boxRight)

      if (isLeftEdge || isRightEdge || isTopEdge || isBottomEdge) {
        B(borderColor, 16 bits)
      } else {
        B(0, 16 bits) // Empty space (Black)
      }
    }

    frameData ++ Seq.fill(sizeInWords - frameData.size)(B(0, 16 bits))
  }

  def verticalGradient(): Seq[Bits] = {
    val totalPixels = width * height

    // 1. Generate the 16-bit RGB565 horizontal gradient sequence using Scala
    val gradientData = (0 until totalPixels).map { index =>
      val x = index % width

      // Normalize X coordinate to fit into the respective channel bit-widths:
      // Red (5 bits: 0 to 31)
      val r = ((x * 31) / width) & 0x1F
      // Green (6 bits: 0 to 63)
      val g = ((x * 63) / width) & 0x3F
      // Blue (5 bits: 0 to 31)
      val b = ((x * 31) / width) & 0x1F

      // Pack channels into a single 16-bit integer: RRRRR_GGGGGG_BBBBB
      val rgb565Value = (r << 11) | (g << 5) | b

      // Convert the integer into a SpinalHDL Bits literal
      B(rgb565Value, 16 bits)
    }

    gradientData ++ Seq.fill(sizeInWords - gradientData.size)(B(0, 16 bits))
  }

}
