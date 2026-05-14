    ORG     $0800            ; Start of ROM

    ; ===========================
    ; 68000 Vector Table, only initial PC and SP
    ; Each vector is 32 bits (long)
    ; ===========================
    DC.L   RAM_END      ; 0: Initial Stack Pointer (SP)
    DC.L   START        ; 1: Reset vector (PC start address)

    ; ===========================
    ; Program code
    ; ===========================
START:
    bsr     UART_INIT
    move.b  #'H',D0
    bsr     PUT_CHAR
    move.b  #'e',D0
    bsr     PUT_CHAR
    move.b  #'l',D0
    bsr     PUT_CHAR
    move.b  #'l',D0
    bsr     PUT_CHAR
    move.b  #'o',D0
    bsr     PUT_CHAR
    move.b  #'!',D0
    bsr     PUT_CHAR
    move.b  #$0D,D0    ; Carriage Return
    bsr     PUT_CHAR
    move.b  #$0A,D0    ; Line Feed
    bsr     PUT_CHAR

LOOP:
    bra     LOOP

PUT_CHAR:
    move.w  UART_LSR,D1
	btst    #5,D1   		; write buffer empty?
	beq     PUT_CHAR 		; eq 0, not ready, check again
	move.b  D0,UART_RBR		; write D0 to serial
	rts						; return

; Serial initialization 19200 baud, 8N1
UART_INIT:
	move.b #$80,UART_LCR	; select DLAB = 1, to access the Divisor Latches of the Baud Generator
	move.b #$00,UART_IER	; set divisor MSB to 0
	move.b #65,UART_RBR     ; set divisor LSB to 52: 20MHz/16/65 = 19231 (should be 19200)
	move.b #$00,UART_LCR	; select DLAB = 0
	move.b #$03,UART_LCR	; set options to 8N1
	move.b #$00,UART_IER	; disable interrupt
	RTS

    ; ===========================
    ; Constants
    ; ===========================
RAM_START   EQU     $00000400
RAM_END     EQU     $00000800   ; End of RAM address (+1)
LED         EQU     $00001000   ; LED-mapped register base address
UART_RBR    EQU     $00001800   ; Receive Buffer Register(RBR) / Transmitter Holding Register(THR) / Divisor Latch (LSB)
UART_IER    EQU     $00001802   ; Interrupt enable register / Divisor Latch (MSB)
UART_IIR    EQU     $00001804   ; Interrupt Identification Register
UART_LCR    EQU     $00001806   ; Line control register
UART_MCR    EQU     $00001808   ; MODEM control register
UART_LSR    EQU     $0000180A   ; Line status register
UART_MSR    EQU     $0000180C   ; MODEM status register