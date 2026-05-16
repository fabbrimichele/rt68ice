UART_RBR    EQU     $00001800   ; Receive Buffer Register(RBR) / Transmitter Holding Register(THR) / Divisor Latch (LSB)
UART_IER    EQU     $00001802   ; Interrupt enable register / Divisor Latch (MSB)
UART_IIR    EQU     $00001804   ; Interrupt Identification Register
UART_LCR    EQU     $00001806   ; Line control register
UART_MCR    EQU     $00001808   ; MODEM control register
UART_LSR    EQU     $0000180A   ; Line status register
UART_MSR    EQU     $0000180C   ; MODEM status register
