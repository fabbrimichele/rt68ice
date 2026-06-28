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
;   - Fully reentrant (no stack digit usage)
;   - Uses RAM buffer (define `bin_to_dec_buf DS.B 11`)
;   - Safe for ROM-resident code
; ----------------------------------------------------------
bin_to_dec:
        MOVEM.L D1-D2/A0-A1,-(SP)

        LEA     bin_to_dec_buf(PC),A1     ; A1 = end of buffer
        ADD.L   #10,A1             ; point to last byte

        MOVEQ   #0,D2              ; digit count

        TST.L   D0
        BNE     .convert

        MOVE.B  #'0',(A1)
        MOVEQ   #1,D2
        BRA     .print

.convert:
.loop:
        MOVE.L  D0,D1
        DIVU    #10,D1            ; quotient in low word, remainder in high

        MOVE.W  D1,D0             ; quotient
        SWAP    D1
        MOVE.W  D1,D2             ; remainder

        ADDI.B  #'0',D2
        MOVE.B  D2,(A1)

        SUBQ.L  #1,A1
        ADDQ.W  #1,D2

        TST.W   D0
        BNE     .loop

        ADDQ.L  #1,A1             ; adjust to first digit

.print:
.print_loop:
        MOVE.B  (A1)+,D0
        BSR     put_chr
        DBRA    D2,.print_loop

        MOVEM.L (SP)+,D1-D2/A0-A1
        RTS