; ----------------------------------------------------------
; bin_to_dec
; Reentrant unsigned 32-bit decimal printer
;
; Input:
;   D0.L = value
;
; Requires:
;   put_chr(char in D0.B)
;
; Notes:
;   - Prints without a RAM digit buffer.
;   - Uses repeated subtraction of decimal powers, so it supports
;     the full unsigned 32-bit range on plain 68000.
; ----------------------------------------------------------
bin_to_dec:
        MOVEM.L D1-D4/A0,-(SP)

        MOVE.L  D0,D1             ; D1 = remaining value
        LEA     .powers(PC),A0
        MOVEQ   #9,D4             ; 10 decimal powers, DBRA count
        MOVEQ   #0,D3             ; nonzero digit printed flag

.power_loop:
        MOVE.L  (A0)+,D2          ; D2 = current decimal power
        MOVEQ   #0,D0             ; D0 = digit for this power

.digit_loop:
        CMP.L   D2,D1
        BLO.S   .emit_digit       ; remaining value < current power
        SUB.L   D2,D1
        ADDQ.B  #1,D0
        BRA.S   .digit_loop

.emit_digit:
        TST.B   D3
        BNE.S   .print_digit
        TST.B   D0
        BNE.S   .start_printing
        TST.W   D4
        BNE.S   .next_power       ; Skip leading zeroes before final digit

.start_printing:
        MOVEQ   #1,D3

.print_digit:
        ADDI.B  #'0',D0
        BSR     put_chr

.next_power:
        DBRA    D4,.power_loop

        MOVEM.L (SP)+,D1-D4/A0
        RTS

.powers:
        DC.L    1000000000
        DC.L    100000000
        DC.L    10000000
        DC.L    1000000
        DC.L    100000
        DC.L    10000
        DC.L    1000
        DC.L    100
        DC.L    10
        DC.L    1
