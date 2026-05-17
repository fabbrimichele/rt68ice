UART_RBR    EQU     $0000C000   ; Receive Buffer Register(RBR) / Transmitter Holding Register(THR) / Divisor Latch (LSB)
UART_IER    EQU     $0000C002   ; Interrupt enable register / Divisor Latch (MSB)
UART_IIR    EQU     $0000C004   ; Interrupt Identification Register
UART_LCR    EQU     $0000C006   ; Line control register
UART_MCR    EQU     $0000C008   ; MODEM control register
UART_LSR    EQU     $0000C00A   ; Line status register
UART_MSR    EQU     $0000C00C   ; MODEM status register
