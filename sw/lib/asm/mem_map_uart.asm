UART_RBR    equ     $00F04000   ; Receive Buffer Register(RBR) / Transmitter Holding Register(THR) / Divisor Latch (LSB)
UART_IER    equ     $00F04002   ; Interrupt enable register / Divisor Latch (MSB)
UART_IIR    equ     $00F04004   ; Interrupt Identification Register
UART_LCR    equ     $00F04006   ; Line control register
UART_MCR    equ     $00F04008   ; MODEM control register
UART_LSR    equ     $00F0400A   ; Line status register
UART_MSR    equ     $00F0400C   ; MODEM status register
