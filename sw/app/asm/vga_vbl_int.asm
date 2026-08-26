    section .text, code

; Count vertical-blank interrupts and increment the LED array every 60 frames.
; VGA raises 68000 autovector interrupt level 4.

start:
    or.w    #$0700,sr               ; Mask interrupts during setup
    move.l  #vbl_isr,VT_INT_4       ; Install the level-4 VBL handler

    clr.w   frame_counter
    clr.w   seconds
    clr.w   LEDS

    clr.w   VIDEO_IRQ_ENABLE        ; Disable VGA interrupts during setup
    move.w  VIDEO_IRQ_STATUS,d0     ; Clear an existing pending VBL
    move.w  #VIDEO_IRQ_VBL,VIDEO_IRQ_ENABLE

    and.w   #$f8ff,sr               ; Enable interrupts on the 68000

    ; Return to the monitor while leaving the interrupt handler installed.
    trap    #14

vbl_isr:
    movem.l d0,-(sp)

    move.w  VIDEO_IRQ_STATUS,d0     ; Read status to acknowledge the VBL
    addq.w  #1,frame_counter
    cmpi.w  #60,frame_counter
    bne.s   .done

    clr.w   frame_counter
    addq.w  #1,seconds
    move.w  seconds,LEDS

.done:
    movem.l (sp)+,d0
    rte

; ===========================
; Include files
; ===========================
    include '../../lib/asm/isr_vector.asm'
    include '../../lib/asm/mem_map_video.asm'
    include '../../lib/asm/mem_map_leds.asm'

; ===========================
; RAM Data Section
; ===========================
    section .bss
frame_counter   ds.w    1
seconds         ds.w    1
