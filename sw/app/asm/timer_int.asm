    section .text, code

; Increment a 16-bit counter every half second and display it on the LED array.
; The timer runs from the 25 MHz system clock and raises autovector interrupt 5.

start:
    or.w    #$0700,sr           ; Mask interrupts while configuring the timer
    move.l  #timer_isr,VT_INT_5 ; Install the level-5 interrupt handler

    clr.w   counter
    clr.w   LEDS

    clr.w   TIMER_CONTROL       ; Stop the timer before changing its period
    move.w  TIMER_STATUS,d0     ; Clear a pending interrupt

    ; (DIVIDER + 1) * RELOAD / 25 MHz = 0.5 seconds
    ; (24,999 + 1) * 500 / 25,000,000 = 0.5
    clr.w   TIMER_DIVIDER_HI
    move.w  #24999,TIMER_DIVIDER_LO
    clr.w   TIMER_RELOAD_HI
    move.w  #500,TIMER_RELOAD_LO

    move.w  #(TIMER_ENABLE+TIMER_AUTO_RELOAD+TIMER_IRQ_ENABLE),TIMER_CONTROL
    and.w   #$f8ff,sr           ; Enable interrupts on the 68000

.loop:
    bra     .loop               ; All work is performed by the ISR

timer_isr:
    movem.l d0,-(sp)
    move.w  TIMER_STATUS,d0     ; Read the status to acknowledge the interrupt
    addq.w  #1,counter
    move.w  counter,LEDS
    movem.l (sp)+,d0
    rte

; ===========================
; Include files
; ===========================
    include '../../lib/asm/isr_vector.asm'
    include '../../lib/asm/mem_map_timer.asm'
    include '../../lib/asm/mem_map_leds.asm'

; ===========================
; RAM Data Section
; ===========================
    section .bss
counter     ds.w    1
