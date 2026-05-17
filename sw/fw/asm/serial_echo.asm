    ORG     $4000            ; Start of ROM

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    DC.L   RAM_END      ; 0: Initial Stack Pointer (SP)
    DC.L   start        ; 1: Reset vector (PC start address)

; ===========================
; Program code
; ===========================
start:
    bsr     uart_init
    lea     msg_hello,a0
    bsr     put_str
.loop:
    bsr     get_chr
    cmp.b   #CR,d0          ; Check if the user pressed ENTER (Carriage Return)
    beq     .newline
    cmp.b   #BS,d0          ; Check if the user pressed BACKSPACE
    beq     .backspace
    cmp.b   #DEL,d0         ; Check if the user pressed DEL
    beq     .backspace
    bsr     put_chr
    bra     .loop
.newline:
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    bra     .loop
.backspace:
    move.b  #BS,d0
    bsr     put_chr
    move.b  #SPACE,d0
    bsr     put_chr
    move.b  #BS,d0
    bsr     put_chr
    bra     .loop

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
msg_hello:
    dc.b    "Type something:",CR,LF,NUL
