    section .text, code

; ===========================
; Program code
; ===========================
start:
    lea     LEDS,a0             ;
    lea     USB2_MOUSE_BTN,a1   ;

.loop:
    move.w  (a1),d0         ; Read mouse button
    move.w  d0,(a0)         ; Show button on LEDs
    btst    #2,d0           ; Test middle button
    bne     end             ; Exit program if button pressed
    jsr     delay           ; Else continue
    jmp     .loop           ; Infinite loop

end:
    trap    #14

delay:
    move.l  #DLY_VAL,d0     ;
.dly_loop:
    subq.l  #1,d0           ; 4 cycles
    bne     .dly_loop       ; 10 cycles when taken
    rts

; ===========================
; Value Constants
; ===========================
DLY_VAL     equ     312500   ;

; ===========================
; Include files
; ===========================
    include '../../lib/asm/mem_map_leds.asm'
    include '../../lib/asm/mem_map_usb.asm'

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

