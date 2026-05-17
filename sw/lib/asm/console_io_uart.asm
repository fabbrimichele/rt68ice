; =============================================================================
; Routine:    PUT_STR
; Purpose:    Prints a null-terminated string to the serial interface.
; Input:      A0 = Pointer to the start of the string (ASCII, 0-terminated).
; Output:     None.
; Clobbers:   D0 = Last read character (will be $00 upon termination).
;             A0 = Points to the byte immediately following the null terminator.
; =============================================================================
PUT_STR:
    movem.l D0/A0,-(SP)
.LOOP:
    move.b  (A0)+,D0        ; Read character from address A0 into D0,
                            ; then auto-increment A0 to point to the next char
    beq     .STR_DONE       ; If the character is 0 (null terminator), we are done
    bsr     PUT_CHR         ; Print the character using your existing routine
    bra     .LOOP           ; Repeat for next character
.STR_DONE:
    movem.l (SP)+,D0/A0
    rts                     ; Return to caller

; =============================================================================
; Routine:    PUT_CHR
; Purpose:    Transmits a single byte/character over the UART.
; Input:      D0.B = The 8-bit ASCII character to transmit.
; Output:     None.
; Clobbers:   D1 = Lower byte used for checking line status (LSR).
; Note:       Blocks execution (polls) until Transmitter Holding Register
;             is completely empty (THRE, bit 5).
; =============================================================================
PUT_CHR:
    movem.l D1,-(SP)         ; Save D2
.WAIT:
    move.b  UART_LSR,D1
	btst    #5,D1   		; write buffer empty?
	beq     .WAIT    		; eq 0, not ready, check again
	move.b  D0,UART_RBR		; write D0 to serial
    movem.l (SP)+,D1
	rts						; return

; =============================================================================
; Routine:    GET_CHR
; Purpose:    Reads a single byte/character from the UART receiver buffer.
; Input:      None.
; Output:     D0.B = The received 8-bit ASCII character.
; Clobbers:   D1 = Lower byte used for checking line status (LSR).
; Note:       Blocks execution (polls) until Data Ready flag (DR, bit 0)
;             is set, indicating a byte has arrived in the FIFO.
; =============================================================================
GET_CHR:
    movem.l D1,-(SP)         ; Save D2
.WAIT:
    move.b  UART_LSR,D1     ; Read status register
    btst    #0,D1           ; read full?
    beq     .WAIT           ; Wait until RX ready
    move.b  UART_RBR,D0     ; Read character to D0
    movem.l (SP)+,D1
    rts

; =============================================================================
; Routine:    UART_INIT
; Purpose:    Initializes the T16450/16550 UART hardware layout.
;             Configures 19200 baud rate (at 20MHz), 8N1 frame, and disables
;             peripheral interrupts.
; Input:      None.
; Output:     None.
; Clobbers:   None (safe to call at early initialization boot phases).
; Note:       Temporarily manipulates the DLAB bit (Line Control Register, bit 7)
;             to write baud generator latches sharing physical register spaces.
; =============================================================================
UART_INIT:
	move.b  #$80,UART_LCR	; select DLAB = 1, to access the Divisor Latches of the Baud Generator
	move.b  #$00,UART_IER	; set divisor MSB to 0
	move.b  #65,UART_RBR     ; set divisor LSB to 65: 20MHz/16/65 = 19231 (should be 19200)
	move.b  #$00,UART_LCR	; select DLAB = 0
	move.b  #$03,UART_LCR	; set options to 8N1
	move.b  #$00,UART_IER	; disable interrupt
	rts

; ===========================
; Constants
; ===========================
CR          EQU     $0D
LF          EQU     $0A
NUL         EQU     $00
ESC         EQU     $1B
BS          EQU     $08
DEL         EQU     $7F
SPACE       EQU     $20
BEL         EQU     $07

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/mem_map_uart.asm'
