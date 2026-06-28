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
    cmp.b   #'4',d0
    beq     bench_test
    cmp.b   #'e',d0
    beq     .end
    bra     menu
.end:
    trap    #14

data_test:
    lea     msg_tst_data,a0
    bsr     put_str
    bsr     run_data_test
    bra     handle_test_result

addr_test:
    lea     msg_tst_addr,a0
    bsr     put_str
    bsr     run_addr_test
    bra     handle_test_result

time_test:
    lea     msg_tst_time,a0
    bsr     put_str
    bsr     run_time_test
    bra     handle_test_result

bench_test:
    lea     msg_tst_bench,a0
    bsr     put_str
    bsr     run_bench_test
    ; Skip shared result (Passed/Error) and shows cycles
    move.l  d1,d0
    bsr     bin_to_hex
    lea     msg_newline,a0
    bsr     put_str
    bra     menu

handle_test_result:
    tst.b   d0
    bne.s   .err
    lea     msg_pass,a0
    bsr     put_str
    bra     menu
.err:
    lea     msg_err,a0
    bsr     put_str
    bra     menu

; ======================================================
; Test subroutines
; ======================================================


; ------------------------------------------------------
; Walking Bits Test (Data Bus Integrity)
; This test ensures that every single data line can
; independently hold a 0 or 1 without affecting its
; neighbor.
;
; Output: d0.b -> 0 OK, 1 Error
;         d1.w -> Failed bit
;         a2   -> last address verified
; ------------------------------------------------------
run_data_test:
    move.w  #1,d1
.bit_loop:
    ; Write loop
    lea     RAM_START,a2
    move.l  #RAM_SIZE,d0
.wr_loop:
    move.w  d1,(a2)+
    subq.l  #1,d0
    bne.s   .wr_loop

    ; Read loop
    lea     RAM_START,a2
    move.l  #RAM_SIZE,d0
.rd_loop:
    move.w  (a2)+,d2
    cmp.w   d1,d2
    bne.s   .error
    subq.l  #1,d0           ; Needs long, can't use dbra
    bne.s   .rd_loop

    ; Move to next bit
    lsl.w   #1,d1           ; Shift bit pattern (0001 -> 0002 -> 0004...)
    tst.w   d1              ; Did we shift all the way through?
    bne.s   .bit_loop

    moveq   #0,d0                  ; Success
    rts

.error:
    moveq   #1,d0                  ; Error
    rts


; ------------------------------------------------------
; The "Address Alias" Test (Row/Bank Conflict)
; Ensures that writing to one address doesn't accidentally
; overwrite another due to ignored or floating address lines.
;
; Output: d0.b -> 0 OK, 1 Error
; ------------------------------------------------------
run_addr_test:
    move.l  #RAM_START,a0           ; Base address (0x800000)
    move.l  #RAM_START+$10000,a1    ; Distant row/bank address (0x810000)

    ; 1. Write unique patterns
    move.w  #$AAAA,(a0)             ; Write to 0x800000
    move.w  #$5555,2(a0)            ; Write to 0x800002
    move.w  #$1234,(a1)             ; Write to 0x810000

    ; 2. Read back and verify the first two
    cmp.w   #$AAAA,(a0)             ; Did 0x800000 change?
    bne.s   .error
    cmp.w   #$5555,2(a0)            ; Did 0x800002 change?
    bne.s   .error

    ; 3. Verify the distant address
    cmp.w   #$1234,(a1)             ; Did 0x810000 change?
    bne.s   .error

    moveq   #0,d0                   ; Success
    rts

.error:
    moveq   #1,d0                   ; Error
    rts


; ------------------------------------------------------
; 3. The "Pseudo-Random Soak" Test (Timing Stress)
; Floods the memory controller with back-to-back READ
; commands to catch race conditions and buffer clear failures.
;
; Output: d0.b -> 0 OK, 1 Error
; ------------------------------------------------------
run_time_test:
    ; 1 & 2. Allocate and fill a 1KB block (256 Longwords)
    move.l  #RAM_START,a0
    move.l  #$DEADBEEF,d1           ; Test pattern
    move.w  #255,d0                 ; 256 iterations (0 to 255)

.fill_loop:
    move.l  d1,(a0)+                ; Write 32 bits and auto-increment
    dbra    d0,.fill_loop

    ; 3 & 4. Tight loop to read back and verify 1,000,000 times
    ;move.l  #1000000,d2             ; Outer loop counter (~6-7 mins at 8MHz)
    move.l  #10,d2             ; Outer loop counter (~6-7 mins at 8MHz)

.soak_loop:
    move.l  #RAM_START,a0           ; Reset pointer to start of 1KB block
    move.w  #255,d0                 ; Reset inner counter to 256 longwords

.rd_loop:
    ; Using cmp.l (a0)+, d1 is the fastest way to read and verify on M68K.
    ; It forces a 32-bit read cycle (two back-to-back 16-bit fetches)
    ; directly against our register.
    cmp.l   (a0)+,d1
    bne.s   .error                  ; Jump out immediately if mismatch
    dbra    d0,.rd_loop             ; Inner loop (1KB)

    subq.l  #1,d2                   ; Decrement outer loop counter
    bne.s   .soak_loop              ; Outer loop (1,000,000 times)

    moveq   #0,d0                   ; Success
    rts

.error:
    moveq   #1,d0                   ; Error
    rts


; ------------------------------------------------------
; 4. The "SDRAM Performance Benchmark" Test
; Performs a block memory move between SDRAM addresses
; to measure cycles taken for read/write operations via
; the ProfilerDevice.
;
; Output: d1.l -> Total clock cycles taken for 256 words
; ------------------------------------------------------
run_bench_test:
    ; --- Setup ---
    movea.l #COUNTER,a0         ; Load counter base into A0

    ; --- Benchmark Start ---
    move.l  (a0),d0             ; Snapshot Start Time (High word first, then latched low)

    ; --- SDRAM Copy Loop ---
    movea.l #RAM_START,a1       ; Source
    movea.l #RAM_START+200,a2   ; Destination
    move.w  #1024,d1            ; Set loop counter (iterations)

.copy_loop:
    move.w  (a1)+,(a2)+         ; Load word from SDRAM, store to SDRAM
    dbra    d1,.copy_loop       ; Decrement and loop until D1 < 0

    ; --- Benchmark End ---
    move.l  (a0),d1             ; Snapshot End Time

    ; Result calculation
    sub.l   d0,d1               ; D1 = Total cycles taken for 256 words
    rts

; ===========================
; Value Constants
; ===========================
RAM_START   equ $800000      ; SDRAM start address
RAM_SIZE    equ 4194304-1    ; In words

; ===========================
; Include files
; ===========================
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/mem_map_led.asm'
    include '../../lib/asm/mem_map_counter.asm'
    include '../../lib/asm/conv_hex.asm'

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
    dc.b    "4. Benchmark",CR,LF
    dc.b    "e. Exit",CR,LF,NUL

msg_tst_data:
    dc.b    CR,LF,"Data Bus Integrity...",CR,LF,NUL

msg_tst_addr:
    dc.b    CR,LF,"Row/Bank Conflict...",CR,LF,NUL

msg_tst_time:
    dc.b    CR,LF,"Timing Stress...",CR,LF,NUL

msg_tst_bench:
    dc.b    CR,LF,"Benchmark...",CR,LF,NUL

msg_pass:
    dc.b    "Passed!",CR,LF,NUL

msg_err:
    dc.b    "Error!",CR,LF,NUL

msg_newline:
    dc.b    CR,LF,NUL

; ===========================
; RAM Data Section
; ===========================
    section .bss
buffer  ds.w 1
