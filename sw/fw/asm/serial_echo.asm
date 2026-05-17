    ORG     $4000            ; Start of ROM

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
    bsr     uart_init
    lea     MSG_HELLO,A0
    bsr     put_str
.LOOP:
    bsr     get_chr
    cmp.b   #CR,D0          ; Check if the user pressed ENTER (Carriage Return)
    beq     .NEWLINE
    cmp.b   #BS,D0          ; Check if the user pressed BACKSPACE
    beq     .BACKSPACE
    cmp.b   #DEL,D0         ; Check if the user pressed DEL
    beq     .BACKSPACE
    bsr     put_chr
    bra     .LOOP
.NEWLINE:
    move.b  #CR,D0
    bsr     put_chr
    move.b  #LF,D0
    bsr     put_chr
    bra     .LOOP
.BACKSPACE:
    move.b  #BS,D0
    bsr     put_chr
    move.b  #SPACE,D0
    bsr     put_chr
    move.b  #BS,D0
    bsr     put_chr
    bra     .LOOP

; ===========================
; Value Constants
; ===========================
RAM_END     EQU     $00004000   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    INCLUDE '../../lib/asm/console_io_uart.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
MSG_HELLO:
    DC.B    "Type something:",CR,LF,NUL
