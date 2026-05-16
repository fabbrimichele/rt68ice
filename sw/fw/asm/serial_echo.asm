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
    beq     .NEWLINE
    cmp.b   #BS,D0          ; Check if the user pressed BACKSPACE
    beq     .BACKSPACE
    cmp.b   #DEL,D0         ; Check if the user pressed DEL
    beq     .BACKSPACE
    bsr     PUT_CHR
    bra     .LOOP
.NEWLINE:
    move.b  #CR,D0
    bsr     PUT_CHR
    move.b  #LF,D0
    bsr     PUT_CHR
    bra     .LOOP
.BACKSPACE:
    move.b  #BS,D0
    bsr     PUT_CHR
    move.b  #SPACE,D0
    bsr     PUT_CHR
    move.b  #BS,D0
    bsr     PUT_CHR
    bra     .LOOP

MSG_HELLO:
    DC.B    "Type something:",CR,LF,NUL

; ===========================
; Constants
; ===========================
RAM_START   EQU     $00000400
RAM_END     EQU     $00000800   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/console_io_uart.asm'
