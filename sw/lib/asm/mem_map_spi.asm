; Assembly convention: word data writes, byte data reads and control accesses.
; Register access width is independent of the 4/8/12/16-bit SPI length.
SPI_BASE      EQU $00F20000
SPI_DATA      EQU SPI_BASE+$0     ; move.w: 16-bit TX / RX; move.b SPI_DATA+1: low RX byte
SPI_STATUS    EQU SPI_BASE+$3     ; move.b / btst: command / BUSY bit 0
SPI_CONFIG    EQU SPI_BASE+$5     ; move.b: clock divisor and transfer length
