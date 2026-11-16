package rt68ice.memory

import spinal.core._

/** System-clock events, observed without feeding back into the cache datapath. */
case class CacheEvents() extends Bundle {
  val readHit = Bool()
  val readMiss = Bool()
  val writeRequest = Bool()
  val readStall = Bool()
  val writeStall = Bool()
  val readRamWait = Bool()
  val writeRamWait = Bool()
}
