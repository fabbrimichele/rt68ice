    section .text, code

; ===========================
; Program code
; ===========================
start:
    lea     msg_title,a0
    bsr     put_str
menu:
    lea     msg_menu,a0
    bsr     put_str
    bsr     get_chr
    cmp.b   #'1',d0
    beq     data_test
    cmp.b   #'2',d0
    beq     addr_test
    cmp.b   #'3',d0
    beq     time_test
    cmp.b   #'e',d0,
    beq     .end
    bra     menu
.end:
    trap    #14

data_test:
    lea     msg_tst_data,a0
    bsr     put_str
    bsr     run_data_test
    lea     msg_pass,a0
    bsr     put_str
    bra     menu

addr_test:
    lea     msg_tst_addr,a0
    bsr     put_str
    bsr     run_addr_test
    lea     msg_pass,a0
    bsr     put_str
    bra     menu

time_test:
    lea     msg_tst_time,a0
    bsr     put_str
    bsr     run_time_test
    lea     msg_pass,a0
    bsr     put_str
    bra     menu

; ======================================================
; Test subroutines
; ======================================================


; ------------------------------------------------------
; Walking Bits Test (Data Bus Integrity)
; Output: d0.b -> 0 OK, 1 Error
;         d1.w -> Failed bit
;         a1   -> last address verified
; ------------------------------------------------------
run_data_test:
    move.b  #1,LED

    move.w  #1,d1
.bit_loop:
    ; Write loop
    lea     RAM_START,a1
    move.l  #RAM_SIZE,d0
.wr_loop:
    move.w  d1,(a1)+
    subq.l  #1,d0
    bne.s   .wr_loop

    move.b  #2,LED


    ; Read loop
    lea     RAM_START,a1
    move.l  #RAM_SIZE,d0
.rd_loop:
    move.w  (a1)+,d2
    cmp.w   d1,d2
    bne.s   .error
    subq.l  #1,d0           ; Needs long, can't use dbra
    bne.s   .rd_loop

    ; Move to next bit
    lsl.w   #1,d1           ; Shift bit pattern (0001 -> 0002 -> 0004...)
    tst.w   d1              ; Did we shift all the way through?
    bne.s     .bit_loop

    move.b  #0,d0           ; Success
    rts

.error:
    move.b  #1,d0           ; Error
    rts


run_addr_test:
    rts

run_time_test:
    rts

; ===========================
; Value Constants
; ===========================
RAM_START   EQU 800000      ; SDRAM start address
RAM_SIZE    EQU 4194304     ; In words

; ===========================
; Include files
; ===========================
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/mem_map_led.asm'

; ===========================
; Data Constants
; Must be after code to avoid alignment issues
; ===========================
msg_title:
    dc.b    CR,LF
    dc.b    "** SDRAM Test **",CR,LF,NUL

msg_menu:
    dc.b    CR,LF
    dc.b    "Select test:",CR,LF
    dc.b    "1. Data Bus Integrity",CR,LF
    dc.b    "2. Row/Bank Conflict",CR,LF
    dc.b    "3. Timing Stress",CR,LF
    dc.b    "e. Exit",CR,LF,NUL

msg_tst_data:
    dc.b    CR,LF,"Data Bus Integrity...",CR,LF,NUL

msg_tst_addr:
    dc.b    CR,LF,"Row/Bank Conflict...",CR,LF,NUL

msg_tst_time:
    dc.b    CR,LF,"Timing Stress...",CR,LF,NUL

msg_pass:
    dc.b    "Passed!",CR,LF,NUL

msg_err:
    dc.b    "Error!",CR,LF,NUL

; ===========================
; RAM Data Section
; ===========================
    section .bss
buffer  ds.w 1
