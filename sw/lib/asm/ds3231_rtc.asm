; DS3231 helpers for the generic I2cDevice, not the former DS1302 device.
; Errors: D0=0 OK, 1=NACK, 2=busy timeout, 3=invalid/unsupported date.
; A NACK/timeout can leave this controller between bytes: reset FPGA before retry.
; No automatic recovery command exists in the current hardware register map.
    include '../../lib/asm/mem_map_i2c.asm'

; Issue D0.W command. Preserve D2-D7/A0-A6; return D1.W=status.
; Wait for BUSY to assert and then deassert, not merely for an old zero.
i2c_issue:
    movem.l d2-d4,-(sp)
    move.w  d0,d4
    move.w  d4,last_command
    move.l  #I2C_POLL_LIMIT,d2
.idle:
    move.w  I2C_CONTROL,d1
    btst    #I2C_BUSY_BIT,d1
    beq     .launch
    subq.l  #1,d2
    bne     .idle
    bra     .timeout
.launch:
    move.w  d4,I2C_CONTROL
    move.l  #I2C_POLL_LIMIT,d2
.started:
    move.w  I2C_CONTROL,d1
    btst    #I2C_BUSY_BIT,d1
    bne     .wait_setup
    subq.l  #1,d2
    bne     .started
    bra     .timeout
.wait_setup:
    move.l  #I2C_POLL_LIMIT,d2
.wait:
    move.w  I2C_CONTROL,d1
    btst    #I2C_BUSY_BIT,d1
    beq     .complete
    subq.l  #1,d2
    bne     .wait
.timeout:
    moveq   #2,d0
    bra     .return
.complete:
    moveq   #0,d0
    btst    #I2C_NACK_BIT,d1
    beq     .return
    moveq   #1,d0
.return:
    move.w  d1,last_status
    movem.l (sp)+,d2-d4
    rts

; Select register D2.B by addressing DS3231 for write, then sending its pointer.
; Preserve D2-D7/A0-A6; return D0=error, D1=status.
ds3231_pointer:
    move.w  #DS3231_ADDRESS,I2C_ADDRESS
    moveq   #I2C_TRIGGER,d0
    bsr     i2c_issue
    tst.l   d0
    bne     .return
    move.w  d2,I2C_DATA
    moveq   #I2C_TRIGGER,d0
    bsr     i2c_issue
.return:
    rts

; Read D3.W bytes from register D2.B into (A2)+, using repeated START.
; Clobbers D0-D1/D4; preserves D2-D3/D5-D7. A2 advances on success.
ds3231_read:
    bsr     ds3231_pointer
    tst.l   d0
    bne     .return
    moveq   #I2C_TRIGGER+I2C_RESTART+I2C_READ,d0
    bsr     i2c_issue                ; Address phase still requires slave ACK.
    tst.l   d0
    bne     .return
    move.w  d3,d4
.loop:
    moveq   #I2C_TRIGGER+I2C_READ,d0
    cmpi.w  #1,d4
    bne     .receive
    ori.w   #I2C_LAST,d0            ; Master NACKs final byte, then STOP.
.receive:
    bsr     i2c_issue
    tst.l   d0
    bne     .return
    move.w  I2C_DATA,d1
    move.b  d1,(a2)+
    subq.w  #1,d4
    bne     .loop
.return:
    rts

; Write D3.W bytes from (A2)+ starting at register D2.B.
; Clobbers D0-D1/D4; preserves D2-D3/D5-D7. A2 advances on success.
ds3231_write:
    bsr     ds3231_pointer
    tst.l   d0
    bne     .return
    move.w  d3,d4
.loop:
    moveq   #0,d1
    move.b  (a2)+,d1
    move.w  d1,I2C_DATA
    moveq   #I2C_TRIGGER,d0
    cmpi.w  #1,d4
    bne     .send
    ori.w   #I2C_LAST,d0
.send:
    bsr     i2c_issue
    tst.l   d0
    bne     .return
    subq.w  #1,d4
    bne     .loop
.return:
    rts

; Read registers $00-$12 in one burst. Reads do not clear status flags.
ds3231_snapshot:
    moveq   #0,d2
    moveq   #19,d3
    lea     rtc_raw,a2
    bra     ds3231_read

; D0.B packed BCD -> D0.W binary; D1=0 valid, 3 invalid. Clobbers D2.
ds3231_from_bcd:
    moveq   #0,d1
    move.b  d0,d1
    andi.w  #$0f,d1
    cmpi.w  #9,d1
    bhi     .invalid
    andi.w  #$00f0,d0
    lsr.w   #4,d0
    cmpi.w  #9,d0
    bhi     .invalid
    mulu.w  #10,d0
    add.w   d1,d0
    moveq   #0,d1
    rts
.invalid:
    moveq   #3,d1
    rts

; D0.B binary 0-99 -> D0.W BCD. Clobbers D1.
ds3231_to_bcd:
    andi.l  #$ff,d0
    divu.w  #10,d0
    move.w  d0,d1
    lsl.w   #4,d1
    swap    d0
    or.w    d1,d0
    andi.w  #$ff,d0
    rts

; Decode snapshot into the existing binary calendar layout at A1.
; Support both 12/24-hour RTC formats, years 2000-2099; reject century=1.
; Return D0=0/3; clobbers D0-D4/A0/A2, preserves A1.
ds3231_decode:
    btst    #7,rtc_raw+5
    bne     .invalid
    lea     rtc_raw,a2
    lea     ds3231_fields(pc),a0
    moveq   #6,d4
.field:
    moveq   #0,d0
    move.b  (a2)+,d0
    cmpi.w  #4,d4                   ; Hours is the third register.
    bne     .ordinary
    move.w  d0,d3
    btst    #6,d3
    bne     .hour12
    andi.w  #$3f,d0
    bra     .ordinary
.hour12:
    andi.w  #$1f,d0
    bsr     ds3231_from_bcd
    tst.w   d1
    bne     .invalid
    tst.w   d0
    beq     .invalid
    cmpi.w  #12,d0
    bhi     .invalid
    bne     .pm
    moveq   #0,d0
.pm:
    btst    #5,d3
    beq     .store
    addi.w  #12,d0
    bra     .store
.ordinary:
    bsr     ds3231_from_bcd
    tst.w   d1
    bne     .invalid
.store:
    moveq   #0,d1
    move.b  (a0)+,d1
    move.b  d0,0(a1,d1.w)
    dbra    d4,.field
    cmpi.b  #1,DT_WEEKDAY(a1)
    blo     .invalid
    cmpi.b  #7,DT_WEEKDAY(a1)
    bhi     .invalid
    bsr     rtc_validate_datetime
    tst.l   d0
    bne     .invalid
    rts
.invalid:
    moveq   #3,d0
    rts

; Write validated binary calendar A1, enable battery oscillator, then clear OSF.
; Does not modify alarm configuration, aging trim or the EEPROM.
ds3231_set_datetime:
    bsr     rtc_validate_datetime
    tst.l   d0
    bne     .invalid
    bsr     rtc_set_weekday
    lea     ds3231_fields(pc),a0
    lea     rtc_raw,a2
    moveq   #6,d4
.encode:
    moveq   #0,d1
    move.b  (a0)+,d1
    moveq   #0,d0
    move.b  0(a1,d1.w),d0
    bsr     ds3231_to_bcd
    move.b  d0,(a2)+
    dbra    d4,.encode
    moveq   #0,d2
    moveq   #7,d3
    lea     rtc_raw,a2
    bsr     ds3231_write
    tst.l   d0
    bne     .return
    ; Read control/status, preserve settings and clear only EOSC/OSF.
    moveq   #$0e,d2
    moveq   #2,d3
    lea     rtc_raw+$0e,a2
    bsr     ds3231_read
    tst.l   d0
    bne     .return
    andi.b  #$7f,rtc_raw+$0e        ; EOSC=0: run from backup battery too.
    andi.b  #$7f,rtc_raw+$0f        ; OSF=0: entered time is now valid.
    ori.b   #$03,rtc_raw+$0f        ; Writing 1 preserves A1F/A2F flags.
    moveq   #$0e,d2
    moveq   #2,d3
    lea     rtc_raw+$0e,a2
    bra     ds3231_write
.invalid:
    moveq   #3,d0
.return:
    rts

; DS3231 register order differs from the former DS1302 snapshot order.
ds3231_fields:
    dc.b DT_SECONDS,DT_MINUTES,DT_HOURS,DT_WEEKDAY,DT_DATE,DT_MONTH,DT_YEAR
    even

; Binary calendar validation/printing reused from rtc_datetime.asm.
DT_SECONDS      equ     0
DT_MINUTES      equ     1
DT_HOURS        equ     2
DT_DATE         equ     3
DT_MONTH        equ     4
DT_WEEKDAY      equ     5
DT_YEAR         equ     6
DT_SIZE         equ     7


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

