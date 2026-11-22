UART_RBR    equ     $02104000   ; Receive Buffer Register(RBR) / Transmitter Holding Register(THR) / Divisor Latch (LSB)
UART_IER    equ     $02104002   ; Interrupt enable register / Divisor Latch (MSB)
UART_IIR    equ     $02104004   ; Interrupt Identification Register
UART_LCR    equ     $02104006   ; Line control register
UART_MCR    equ     $02104008   ; MODEM control register
UART_LSR    equ     $0210400A   ; Line status register
UART_MSR    equ     $0210400C   ; MODEM status register
