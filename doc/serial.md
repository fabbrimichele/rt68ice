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

### How calculate the baud divisor
```
divisor = UART Clock / 16 / baud
```
e.g. 
```
divisor = 31.250 MHz / 16 / 19200 = 101
31.250 MHz / 16 / 101 = 19337 
```


## Upload progress

The host uploader shows the percentage sent, bytes sent/total and percentage
remaining automatically:

```sh
python3 tools/serial_load.py --port /dev/ttyACM0 --baud 57600 target/app/blink.bin
```

A terminal gets one updating line; redirected output gets updates roughly every
10 percent. Counts include the monitor image header. Progress advances after
each 1 KiB chunk has drained from the host serial driver; 100 percent describes
serial transmission, not confirmation of the monitor's CRC check. The transfer
format, automatic `run` command and `--no-run` option are unchanged.
