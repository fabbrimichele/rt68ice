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
    lea     MSG_HELLO,A0
    bsr     PUT_STR
.LOOP:
    bsr     GET_CHR
    cmp.b   #CR,D0          ; Check if the user pressed ENTER (Carriage Return)
    beq     .NEWLINE        ; If yes, branch to the newline handler
    move.b  D0,LED
    bsr     PUT_CHR
    bra     .LOOP
.NEWLINE:
    move.b  #CR,D0
    bsr     PUT_CHR
    move.b  #LF,D0
    bsr     PUT_CHR
    bra     .LOOP

; =================================================================
; PUT_STR - Prints a null-terminated string
; Input: A0 = Pointer to the start of the string
; Modifies: D0 (clobbered by character data), A0 (moves to end of string)
; =================================================================
PUT_STR:
    move.b  (A0)+,D0        ; Read character from address A0 into D0,
                            ; then auto-increment A0 to point to the next char
    beq     .STR_DONE       ; If the character is 0 (null terminator), we are done
    bsr     PUT_CHR         ; Print the character using your existing routine
    bra     PUT_STR         ; Repeat for next character
.STR_DONE:
    rts                     ; Return to caller

PUT_CHR:
    move.w  UART_LSR,D1
	btst    #5,D1   		; write buffer empty?
	beq     PUT_CHR 		; eq 0, not ready, check again
	move.b  D0,UART_RBR		; write D0 to serial
	rts						; return

; =================================================================
; GETCHAR - Gets a single character from the UART data register and stores it in D0
; =================================================================
GET_CHR:
    move.w  UART_LSR,D1     ; Read status register
    btst    #0,D1           ; read full?
    beq     GET_CHR         ; Wait until RX ready
    move.w  UART_RBR,D0     ; Read character to D0
    rts

; =================================================================
; Serial initialization 19200 baud, 8N1
; =================================================================
UART_INIT:
	move.b #$80,UART_LCR	; select DLAB = 1, to access the Divisor Latches of the Baud Generator
	move.b #$00,UART_IER	; set divisor MSB to 0
	move.b #65,UART_RBR     ; set divisor LSB to 65: 20MHz/16/65 = 19231 (should be 19200)
	move.b #$00,UART_LCR	; select DLAB = 0
	move.b #$03,UART_LCR	; set options to 8N1
	move.b #$00,UART_IER	; disable interrupt
	RTS

MSG_HELLO:
    DC.B    "Type something:",CR,LF,NUL

    ; ===========================
    ; Constants
    ; ===========================

CR          EQU     $0D
LF          EQU     $0A
NUL         EQU     $00
ESC         EQU     $1B

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