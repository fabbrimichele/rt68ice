## System Memory Layout
| Device | Start Address | End Address | Size |
| :--- | :---: | :---: | ---: |
| BOOT VECTORS    | 0x00000000 | 0x00000007 | 8 Bytes |
| MAIN RAM        | 0x00000000 | 0x00003FFF |   16 KB |
| MAIN ROM        | 0x00004000 | 0x00007FFF |   16 KB |
| LED PERIPH      | 0x00008000 | 0x0000BFFF |   16 KB |
| UART PERIPH     | 0x0000C000 | 0x0000FFFF |   16 KB |
| FRAMEBUFFER     | 0x00020000 | 0x0003FFFF |  128 KB |
