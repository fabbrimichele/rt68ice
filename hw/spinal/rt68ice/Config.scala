package rt68ice

import spinal.core._
import spinal.core.sim._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
object Config {
  def spinal = SpinalConfig(
    targetDirectory = "hw/gen",
    defaultConfigForClockDomains = ClockDomainConfig(resetKind = BOOT),
    defaultClockDomainFrequency = FixedFrequency(25 MHz),
    onlyStdLogicVectorAtTopLevelIo = false
  )

  def sim = SimConfig.withConfig(spinal).withFstWave
}