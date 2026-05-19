; ===========================
; Vector Table
; ===========================
; Bus Error
VT_INT_BE       equ $08
; Spurious Interrupt
VT_INT_SP       equ $60

; Interrupts (Autovectors)
VT_INT_1        equ $64
VT_INT_2        equ $68
VT_INT_3        equ $6C
VT_INT_4        equ $70
VT_INT_5        equ $74
VT_INT_6        equ $78
VT_INT_7        equ $7C

; Traps
VT_TRAP_14      equ $B8

