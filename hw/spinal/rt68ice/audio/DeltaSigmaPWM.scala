package rt68ice.audio

import spinal.core._

import scala.language.postfixOps

case class DeltaSigmaPWM(sampleSizeInBits: Int = 16) extends Component {
  val io = new Bundle {
    val sound = in UInt(sampleSizeInBits bits)  // Signed sample
    val sample = in Bool()        // Sample strobe
    val pwm = out Bool()          // 1-bit PWM audio output
  }

  // ---------------------------
  // 17-bit accumulator for delta-sigma PWM
  // ---------------------------
  val pwmAcc = Reg(UInt(sampleSizeInBits + 1 bits)) init 0

  // ---------------------------
  // Update accumulator ONLY when a new sample is available
  // ---------------------------
  when(io.sample) {
    val sndUnsigned = io.sound ^ U(0x2000)
    pwmAcc := pwmAcc(sampleSizeInBits - 1 downto 0).resize(sampleSizeInBits + 1) + sndUnsigned
  }

  // MSB of accumulator drives PWM output
  io.pwm := pwmAcc(sampleSizeInBits)
}
