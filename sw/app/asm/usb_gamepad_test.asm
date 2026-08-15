    section .text, code

; ===========================
; Program code
; ===========================
start:
    move.l  #usb_isr,VT_INT_6   ; Set USB interrupt handler
    and.w   #$F8FF,SR           ; Enable all interrupts on 68000 (Clear mask bits)
    trap    #14

usb_isr:
    move.l  d0,-(sp)
    move.w  USB1_STATUS,D0      ; Read status register -> clear interrupts
    move.w  USB1_GAMEPAD,D0     ; Read gamepad status
    move.w  D0,LEDS             ; Write gamepad status to LEDs
    move.w  #1,LEDS             ; Write gamepad status to LEDs
    move.l  (sp)+,d0
    rte

; ===========================
; Value Constants
; ===========================
DLY_VAL     equ     312500   ;

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_leds.asm'
    include '../../lib/asm/mem_map_usb.asm'
    include '../../lib/asm/isr_vector.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
; Add here data costants, e.g. `msg_hello dc.b    "Type something:",CR,LF,NUL`

; ===========================
; RAM Data Section
; ===========================
    section .bss
; Add here variables and buffers, e.g. `buffer ds.b 80`

