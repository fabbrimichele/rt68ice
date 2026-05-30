## Serial
The serial port is based on the 16450 UART.
There is also a 16550 UART core based on WishBone available [here](https://github.com/freecores/uart16550/tree/master)

### How To
To test the serial 
* open a terminal, e.g.:
  ```bash
  picocom -b 19200 /dev/ttyACM0
  ```
* program the FPGA with the program `serial_test`.

