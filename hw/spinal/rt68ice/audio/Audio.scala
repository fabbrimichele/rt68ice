package rt68ice.audio

import spinal.core._

//noinspection TypeAnnotation
case class Audio() extends Bundle {
  val right = out Bool()
  val left = out Bool()
}
