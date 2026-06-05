; =============================================================================
; Routine:    put_str
; Purpose:    Prints a null-terminated string to the serial interface.
; Input:      a0 = Pointer to the start of the string (ASCII, 0-terminated).
; Output:     None.
; Clobbers:   d0 = Last read character (will be $00 upon termination).
;             a0 = Points to the byte immediately following the null terminator.
; =============================================================================
put_str:
    movem.l d0/a0,-(sp)
.loop:
    move.b  (a0)+,d0        ; Read character from address a0 into d0,
                            ; then auto-increment a0 to point to the next char
    beq     .str_done       ; If the character is 0 (null terminator), we are done
    bsr     put_chr         ; Print the character using your existing routine
    bra     .loop           ; Repeat for next character
.str_done:
    movem.l (sp)+,d0/a0
    rts                     ; Return to caller

; =============================================================================
; Routine:    put_chr
; Purpose:    Transmits a single byte/character over the UART.
; Input:      d0.B = The 8-bit ASCII character to transmit.
; Output:     None.
; Clobbers:   d1 = Lower byte used for checking line status (LSR).
; Note:       Blocks execution (polls) until Transmitter Holding Register
;             is completely empty (THRE, bit 5).
; =============================================================================
put_chr:
    movem.l d1,-(sp)
.wait:
    move.b  UART_LSR,d1
	btst    #5,d1   		; write buffer empty?
	beq     .wait    		; eq 0, not ready, check again
	move.b  d0,UART_RBR		; write d0 to serial
    movem.l (sp)+,d1
	rts						; return

; =============================================================================
; Routine:    get_chr
; Purpose:    Reads a single byte/character from the UART receiver buffer.
; Input:      None.
; Output:     d0.B = The received 8-bit ASCII character.
; Clobbers:   d1 = Lower byte used for checking line status (LSR).
; Note:       Blocks execution (polls) until Data Ready flag (DR, bit 0)
;             is set, indicating a byte has arrived in the FIFO.
; =============================================================================
get_chr:
    movem.l d1,-(sp)
.wait:
    move.b  UART_LSR,d1     ; Read status register
    btst    #0,d1           ; read full?
    beq     .wait           ; Wait until RX ready
    move.b  UART_RBR,d0     ; Read character to d0
    movem.l (sp)+,d1
    rts

; =============================================================================
; Routine:    uart_init
; Purpose:    Initializes the T16450/16550 UART hardware layout.
;             Configures 19200 baud rate (at 20MHz), 8N1 frame, and disables
;             peripheral interrupts.
; Input:      None.
; Output:     None.
; Clobbers:   None (safe to call at early initialization boot phases).
; Note:       Temporarily manipulates the DLAB bit (Line Control Register, bit 7)
;             to write baud generator latches sharing physical register spaces.
; =============================================================================
uart_init:
	move.b  #$80,UART_LCR	; select DLAB = 1, to access the Divisor Latches of the Baud Generator
	move.b  #$00,UART_IER	; set divisor MSB to 0
	;move.b  #65,UART_RBR    ; set divisor LSB to 65: 20MHz/16/65 = 19231 (should be 19200)
	move.b  #27,UART_RBR    ; set divisor LSB to 26 (DLL): 8.333MHz/16/26 = 19290 (should be 19200, still in specs)
	;move.b  #9,UART_RBR     ; set divisor LSB to 9 (DLL): 8.333MHz/16/9 = 57870 (should be 57600, still in specs)
	move.b  #$03,UART_LCR	; set options to 8N1
	move.b  #$00,UART_IER	; disable interrupt
	rts

; ===========================
; Constants
; ===========================
CR          equ     $0D
LF          equ     $0A
NUL         equ     $00
ESC         equ     $1B
BS          equ     $08
DEL         equ     $7F
SPACE       equ     $20
BEL         equ     $07

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_uart.asm'
