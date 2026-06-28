; ----------------------------------------------------------
; bin_to_dec: D0.L is converted to decimal ASCII and printed
; ----------------------------------------------------------
bin_to_dec:
    MOVEM.L D1/D2,-(SP)     ; Save working registers
    MOVEQ   #0,D2           ; D2 will be our digit counter

.div_loop:
    CLR.L   D1              ; Ensure upper 16 bits are clear for DIVU
    DIVU    #10,D0          ; D0 = Quotient (lower), Remainder (upper)

    ; --- 1. Isolate the remainder (the digit) ---
    MOVE.L  D0,D1           ; Copy result
    SWAP    D1              ; Put Remainder into lower 16 bits
    ANDI.L  #$F,D1          ; Mask just in case (though remainder is <10)

    ; --- 2. Convert to ASCII ---
    ADDI.B  #'0',D1         ; Add ASCII offset '0'

    ; --- 3. Push to Stack to reverse order later ---
    MOVE.W  D1,-(SP)        ; Push ASCII char to stack
    ADDQ.W  #1,D2           ; Increment digit counter

    ; --- 4. Prepare for next iteration ---
    CLR.W   D0              ; Clear high word (old remainder)
    SWAP    D0              ; Restore Quotient to lower word
    TST.W   D0              ; Is quotient 0?
    BNE.S   .div_loop       ; If not, continue

    ; --- 5. Print loop ---
.print_loop:
    MOVE.W  (SP)+,D0        ; Pop digit off stack (this reverses the order!)
    BSR     put_chr         ; Display the character
    DBRA    D2,.print_loop  ; Loop until all digits printed

    MOVEM.L (SP)+,D1/D2     ; Restore registers
    RTS