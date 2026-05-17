    org     $4000            ; Start of ROM

; ===========================
; 68000 Vector Table, only initial PC and SP
; Each vector is 32 bits (long)
; ===========================
    dc.l   RAM_END      ; 0: Initial Stack Pointer (SP)
    dc.l   start        ; 1: Reset vector (PC start address)

; ===========================
; Program code
; ===========================
start:
    bsr     uart_init
    lea     msg_hello,a0
    bsr     put_str
.loop:
    bra     .loop

; ===========================
; Value Constants
; ===========================
RAM_END     equ     $00004000   ; End of RAM address (+1)

; ===========================
; Include files
; ===========================
    include '../../lib/asm/console_io_uart.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
msg_hello:
    dc.b    "Hello World!",CR,LF,NUL
