## Serial
The serial port is based on the 16450 UART.

### How To
To test the serial 
* open a terminal, e.g.:
  ```bash
  picocom -b 19200 /dev/ttyACM0
  ```
* program the FPGA with the program `serial_test`.

