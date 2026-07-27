    section .text, code

; ===========================
; Program code
; ===========================
start:
    lea     msg_title,a0
    bsr     put_str

.loop:
    clr.l   d0
    move.w  USB2_MOUSE_DX,d0    ; Read mouse dx accumulator
    bsr     bin_to_hex
    move.b  #' ',d0
    bsr     put_chr

    clr.l   d0
    move.w  USB2_MOUSE_DY,d0    ; Read mouse dy accumulator
    bsr     bin_to_hex
    move.b  #' ',d0
    bsr     put_chr

    move.w  USB2_MOUSE_BTN,d0   ; Read mouse button
    btst    #2,d0               ; Test middle button
    bne     end                 ; Exit program if button pressed
    bsr     bin_to_hex
    lea     msg_newline,a0
    bsr     put_str

    jsr     delay               ; Else continue
    jmp     .loop               ; Infinite loop

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
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conv_hex.asm'
    include '../../lib/asm/conv_dec.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
msg_title:
    dc.b    CR,LF,"DX       DY       BTN",CR,LF,NUL

msg_newline:
    dc.b    ' ',' ',CR,NUL  ; it also cleans the previous string left over

; ===========================
; RAM Data Section
; ===========================
    section .bss
; Add here variables and buffers, e.g. `buffer ds.b 80`

