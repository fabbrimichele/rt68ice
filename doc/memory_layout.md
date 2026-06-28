## System Memory Layout
| Device | Start Address | End Address | Size |
| :--- | :---: | :---: | ---: |
| BOOT VECTORS    | 0x00000000 | 0x00000007 | 8 Bytes |
| MAIN RAM        | 0x00000000 | 0x00003FFF |   16 KB |
| MAIN ROM        | 0x00004000 | 0x00007FFF |   16 KB |
| LED PERIPH      | 0x00008000 | 0x0000BFFF |   16 KB |
| UART PERIPH     | 0x0000C000 | 0x0000FFFF |   16 KB |
| VIDEO PALETTE   | 0x00010000 | 0x00013FFF |   16 KB |
| VIDEO CONTROL   | 0x00014000 | 0x00017FFF |   16 KB |
| COUNTER         | 0x00018000 | 0x0001BFFF |   16 KB |
| LED_ARRAY       | 0x00020000 | 0x00023FFF |   16 KB |
| VIDEO FB        | 0x00100000 | 0x0011FFFF |  128 KB |
| SDRAM           | 0x00800000 | 0x00FFFFFF | 8192 KB |
