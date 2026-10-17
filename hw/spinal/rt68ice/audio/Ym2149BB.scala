package rt68ice.audio

import spinal.core._

import scala.language.postfixOps

/**
 * Generates:
 * - `ch_X_o`: Unsigned 12-bit output for each channel.
 * - `mix_audio_o`: Unsigned 14-bit summation of the channels.
 * - `pcm14s_o`: Signed 14-bit PCM summation of the channels, with each channel
 *   converted to -/+ zero-centered level or -/+ full-range level
 */
class Ym2149BB extends BlackBox {
  val io = new Bundle {
    val clk_i         = in Bool() // system clock
    val en_clk_psg_i  = in Bool() // PSG clock enable
    val sel_n_i       = in Bool() // divide select, 0=clock-enable/2
    val reset_n_i     = in Bool() // active low
    val bc_i          = in Bool() // bus control
    val bdir_i        = in Bool() // bus direction
    val data_i        = in Bits(8 bits)
    val data_r_o      = out Bits(8 bits) // registered output data
    val ch_a_o        = out UInt(12 bits)
    val ch_b_o        = out UInt(12 bits)
    val ch_c_o        = out UInt(12 bits)
    val mix_audio_o   = out UInt(14 bits)
    val pcm14s_o      = out UInt(14 bits)
  }

  mapClockDomain(clock = io.clk_i, reset = io.reset_n_i, resetActiveLevel = LOW)

  setDefinitionName("ym2149_audio") // This tells SpinalHDL which Verilog module to instantiate
  addRTLPath("hw/vhdl/ym2149_audio.vhd") // Merge the file to the generated 'mergeRTL.v' file
  noIoPrefix() // Remove io_ prefix
}
