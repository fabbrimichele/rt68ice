package rt68ice.io

import rt68ice.core.M68KDeviceBus
import rt68ice.io.I2cDevice._
import spinal.core._
import spinal.lib._

import scala.language.postfixOps

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
object I2cDevice {
  val ControlAddr = 0
  val AddressAddr = 1
  val DataAddr = 2
}

//noinspection TypeAnnotation
//noinspection ScalaWeakerAccess
case class I2cDevice() extends Component {
  val io = new Bundle {
    val bus = slave(M68KDeviceBus())
    val i2c = master(I2c())
  }

  // Fixed bus wait states are handled by BusController.
  io.bus.ready := True
  io.bus.irq := False

  val i2cController = new I2cControllerBB()
  i2cController.io.scl := io.i2c.scl
  i2cController.io.sda := io.i2c.sda

  val controlReg = Reg(Bits(16 bits)) init 0
  val addressReg = Reg(Bits(7 bits)) init 0
  val dataReg = Reg(Bits(8 bits)) init 0
  val writeHandled = Reg(Bool()) init False

  i2cController.io.write_data := dataReg
  i2cController.io.address := addressReg
  i2cController.io.read_write := controlReg(0)
  i2cController.io.last_byte := controlReg(1)

  val wordAddress = io.bus.address(2 downto 1).asUInt
  val registerAccess = io.bus.sel && io.bus.uds && io.bus.lds
  val registerRead = registerAccess && !io.bus.wr
  val registerWrite = registerAccess && io.bus.wr

  // Handled pulses
  i2cController.io.trigger := False
  i2cController.io.restart := False
  when(registerWrite && !writeHandled && wordAddress === ControlAddr) {
    i2cController.io.trigger := io.bus.dataOut(2)
    i2cController.io.restart := io.bus.dataOut(3)
    writeHandled := True
  } elsewhen !registerWrite  {
    writeHandled := False
  }

  io.bus.dataIn := 0
  when(registerRead) {
    io.bus.dataIn := wordAddress.mux(
      ControlAddr -> (i2cController.io.ack_error ## i2cController.io.busy).resized,
      AddressAddr -> addressReg.resized,
      DataAddr -> i2cController.io.read_data.resized,
      default -> B(0, 16 bits),
    )
  } elsewhen registerWrite {
    switch(wordAddress) {
      is(ControlAddr) {
        controlReg := io.bus.dataOut
      }
      is(AddressAddr) {
        addressReg := io.bus.dataOut(6 downto 0)
      }
      is(DataAddr) {
        dataReg := io.bus.dataOut(7 downto 0)
      }
    }
  }
}
