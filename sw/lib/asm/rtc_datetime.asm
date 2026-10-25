; Helpers for rtc_set / rtc_get and the current binary-converting VHDL.
; A1 points to a seven-byte snapshot in the same order as RTC registers.
DT_SECONDS      equ     0
DT_MINUTES      equ     1
DT_HOURS        equ     2
DT_DATE         equ     3
DT_MONTH        equ     4
DT_WEEKDAY      equ     5
DT_YEAR         equ     6
DT_SIZE         equ     7

; Read a snapshot, retrying if seconds change across the individual accesses.
; Return D0=0: valid, D0=1: invalid/unsupported values, D0=2: unstable.
; On success D5=1 if halted (seconds normalized), otherwise D5=0.
; Clobbers D0-D5/A0; preserves A1. No RTC writes.
rtc_read_datetime:
    moveq   #7,d4
.retry:
    ; Load a constant without calculating a peripheral effective address.
    movea.l #RTC_SECONDS,a0
    moveq   #0,d1
    moveq   #0,d2
.read:
    ; Match the monitor's word read, then extract the upper-byte RTC value.
    move.w  0(a0,d2.w),d0
    lsr.w   #8,d0
    move.b  d0,0(a1,d1.w)
    addq.w  #2,d2
    addq.w  #1,d1
    cmpi.w  #DT_SIZE,d1
    bne     .read
    move.w  RTC_SECONDS,d0
    lsr.w   #8,d0
    cmp.b   DT_SECONDS(a1),d0
    beq     .stable
    dbra    d4,.retry
    moveq   #2,d0
    rts
.stable:
    moveq   #0,d5
    cmpi.b  #80,DT_SECONDS(a1)
    blo     .validate
    cmpi.b  #99,DT_SECONDS(a1)
    bhi     .invalid
    ; Native $80-$99 becomes binary 80-99, encoding CH plus seconds 0-19.
    subi.b  #80,DT_SECONDS(a1)
    moveq   #1,d5
.validate:
    bra     rtc_validate_datetime
.invalid:
    moveq   #1,d0
    rts

; Validate a binary 24-hour date/time in years 2000-2099, including leap days.
; Weekday is not checked: the setter computes it, and the reader prints dates.
; Return D0=0: valid, D0=1: invalid. Clobbers D0-D3/A0; preserves A1.
rtc_validate_datetime:
    cmpi.b  #59,DT_SECONDS(a1)
    bhi     .invalid
    cmpi.b  #59,DT_MINUTES(a1)
    bhi     .invalid
    cmpi.b  #23,DT_HOURS(a1)
    bhi     .invalid
    moveq   #0,d1
    move.b  DT_YEAR(a1),d1
    cmpi.w  #99,d1
    bhi     .invalid
    moveq   #0,d2
    move.b  DT_MONTH(a1),d2
    beq     .invalid
    cmpi.w  #12,d2
    bhi     .invalid
    moveq   #0,d3
    move.b  DT_DATE(a1),d3
    beq     .invalid
    lea     .month_lengths(pc),a0
    subq.w  #1,d2
    moveq   #0,d0
    move.b  0(a0,d2.w),d0
    cmpi.w  #1,d2                  ; February (zero-based month).
    bne     .check_day
    andi.w  #3,d1                  ; 2000 is a leap year; 2100 is excluded.
    bne     .check_day
    addq.w  #1,d0
.check_day:
    cmp.w   d0,d3
    bhi     .invalid
    moveq   #0,d0
    rts
.invalid:
    moveq   #1,d0
    rts
.month_lengths:
    dc.b    31,28,31,30,31,30,31,31,30,31,30,31

; Calculate weekday for a validated date, Monday=1 through Sunday=7.
; Clobbers D0-D2/A0; preserves A1. Writes DT_WEEKDAY in the snapshot.
rtc_set_weekday:
    moveq   #0,d1
    move.b  DT_YEAR(a1),d1
    move.w  d1,d0
    mulu.w  #365,d0
    addq.w  #3,d1
    lsr.w   #2,d1                  ; Leap days before January 1 of this year.
    add.w   d1,d0
    moveq   #0,d2
    move.b  DT_MONTH(a1),d2
    subq.w  #1,d2
    add.w   d2,d2
    lea     .month_starts(pc),a0
    add.w   0(a0,d2.w),d0
    cmpi.b  #2,DT_MONTH(a1)
    bls     .day
    move.b  DT_YEAR(a1),d1
    andi.w  #3,d1
    bne     .day
    addq.w  #1,d0
.day:
    moveq   #0,d1
    move.b  DT_DATE(a1),d1
    add.w   d1,d0
    addq.w  #4,d0                  ; Jan 1, 2000 was Saturday (Monday=1).
    divu.w  #7,d0
    swap    d0
    addq.w  #1,d0
    move.b  d0,DT_WEEKDAY(a1)
    rts
.month_starts:
    dc.w    0,31,59,90,120,151,181,212,243,273,304,334

; Print a validated snapshot as YYYY-MM-DD HH:MM:SS followed by CR/LF.
; Clobbers D0-D2/A0; preserves A1 and D5 (the halted flag).
rtc_print_datetime:
    moveq   #'2',d0
    bsr     put_chr
    moveq   #'0',d0
    bsr     put_chr
    lea     .format(pc),a0
    moveq   #5,d2
.field:
    moveq   #0,d1
    move.b  (a0)+,d1
    moveq   #0,d0
    move.b  0(a1,d1.w),d0
    bsr     rtc_put_two_digits
    move.b  (a0)+,d0
    bsr     put_chr
    dbra    d2,.field
    moveq   #LF,d0
    bra     put_chr
.format:
    dc.b    DT_YEAR,'-',DT_MONTH,'-',DT_DATE,' '
    dc.b    DT_HOURS,':',DT_MINUTES,':',DT_SECONDS,CR

; Print D0.B (0-99) with a leading zero. Clobbers D0-D1.
rtc_put_two_digits:
    moveq   #0,d1
    move.b  d0,d1
    divu.w  #10,d1
    move.w  d1,d0
    addi.b  #'0',d0
    bsr     put_chr
    swap    d1
    move.w  d1,d0
    addi.b  #'0',d0
    bra     put_chr

    include '../../lib/asm/mem_map_ds1302.asm'
