; ------------------------------
; ROM Monitor (ROM version)
; ------------------------------
    org    $4000          ; ROM Start Address

; ------------------------------
; Initial Reset SP and PC in Vector Table
; ------------------------------
    dc.l RAM_END            ; Reset Stack Pointer (SP, SP move downward far from SO_RAM)
    dc.l start              ; Reset Program counter (PC) (point to the beginning of code)

; ------------------------------
; Program code
; ------------------------------
start:
    jsr     init_vector_table
    jsr     uart_init
    lea     msg_title,a0
    bsr     put_str

mon_entry:
new_cmd:
    lea     IN_BUF,a5       ; a5 = current buffer position
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    move.b  #'>',d0
    bsr     put_chr
loop:
    bsr     get_chr

    cmp.b   #CR,d0          ; Check for Enter
    beq     PROCESS_CMD     ; then process command
    cmp.b   #BS,d0          ; Check for Backspace
    beq     BS_HANDLER
    cmp.b   #DEL,d0          ; Check for Backspace
    beq     BS_HANDLER

    cmp.l   #IN_BUF_END,a5  ; Check if buffer is full
    beq     BUFFER_FULL

    bsr     put_chr         ; print character
    move.b  d0,(a5)+        ; Store d0 into buffer, then increment a5
    bra     loop

; --------------------------------------
; Backspace Handler
; --------------------------------------
BS_HANDLER:
    ; 1. Check if the buffer is empty
    cmp.l   #IN_BUF,a5          ; Compare current pointer (a5) to start of buffer
    beq     loop                ; If a5 == IN_BUF, buffer is empty (do nothing)

    ; 2. Correct the buffer pointer
    SUBQ.L  #1,a5               ; Decrement a5: move pointer back one position

    ; 3. Correct the terminal display (echo the standard sequence)
    ; Send BS (0x08) to move cursor left
    move.b  #BS,d0
    bsr     put_chr

    ; Send Space (0x20) to erase the character
    move.b  #' ',d0
    bsr     put_chr

    ; Send BS (0x08) again to move cursor back to erased position
    move.b  #BS,d0
    bsr     put_chr

    bra     loop                ; Continue input loop

; --------------------------------------
; Buffer Full Handler
; --------------------------------------
BUFFER_FULL:
    ; Send BEL (7) once to alert the user that the buffer is full
    move.b  #BEL,d0
    bsr     put_chr

    bsr     get_chr             ; Get the next character
    ; Check 1: Enter pressed (CR)
    cmp.b   #CR,d0
    beq     PROCESS_CMD         ; Yes, go process the command
    ; Check 2: Backspace or Delete
    cmp.b   #BS,d0
    beq     BS_HANDLER

    ; Discard all other input
    bra     BUFFER_FULL

PROCESS_CMD:
    move.b  #0,(a5)         ; Null-terminate the string in the buffer
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr

    ; Parse DUMP
    bsr     PARSE_DUMP
    btst    #0,d0
    bne     DUMP_CMD        ; d0.0 = 1 execute DUMP

    ; Parse WRITE
    bsr     PARSE_WRITE
    btst    #0,d0
    bne     WRITE_CMD       ; d0.0 = 1 execute WRITE

    ; Parse HELP
    bsr     PARSE_HELP
    btst    #0,d0
    bne     HELP_CMD        ; d0.0 = 1 execute HELP

    ; Parse LOAD
    bsr     PARSE_LOAD
    btst    #0,d0
    bne     LOAD_CMD        ; d0.0 = 1 execute LOAD

    ; Parse RUN
    bsr     PARSE_RUN
    btst    #0,d0
    bne     RUN_CMD         ; d0.0 = 1 execute RUN

    ; Parse FBCLR
    bsr     PARSE_FBCLR
    btst    #0,d0
    bne     FBCLR_CMD       ; d0.0 = 1 execute FBCLR

UNKNOWN_CMD:
    ; Print error message
    lea     msg_unknown,a0
    bsr     put_str
    bra     new_cmd

; a1 - Dump address
DUMP_CMD:
    MOVE.W  #(8-1),d1       ; Print 8 lines
DUMP_LINE:
    move.l  a1,d0
    bsr     bin_to_hex        ; Print address
    move.b  #':',d0
    bsr     put_chr
    MOVE.W  #(8-1),d2       ; Print 8 cells
DUMP_CELL:
    move.b  #' ',d0
    bsr     put_chr
    MOVE.W  (a1)+,d0
    bsr     bin_to_hex_w      ; Print mem value
    DBRA    d2,DUMP_CELL    ; Decrement d1, branch if d1 is NOT -1

    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    DBRA    d1,DUMP_LINE    ; Decrement d1, branch if d1 is NOT -1
    bra     new_cmd

; d1 - Value to be written
; a1 - Write address
WRITE_CMD:
    MOVE.W  d1,(a1)       ; Move only 16 bits (argument is 32 bit long)
    bra     new_cmd

HELP_CMD:
    lea     msg_help,a0
    bsr     put_str
    bra     new_cmd

; -------------------------------------------------------------------------
; Load from UART a binary content to memory.
;
; PROTOCOL: Length-Prefixed Binary (Big-Endian)
;
; HEADER (8 bytes, sent first):
; [32-bit Load Address] (a0)
; [32-bit Content Length] (d2)
;
; BODY:
; [L bytes of raw binary content]
;
; Example (File Content in Hex Bytes):
; 00 00 08 10 ; Load Address: $00000810
; 00 00 00 02 ; Content Length: 2 bytes (it doesn't include the headers)
; 55 55       ; Actual Content: Two bytes ($55, $55)
; GTKTerm format:
; 00;00;08;10;00;00;00;02;55;55
;
; Actual program to write to LED and then loop
; Address (Hex) Instruction Opcode (Hex)    Comment
; 0810          bra loop    60FE            Branch Always back to $0810 (FE=−2).
; GTKTerm format: 00;00;08;10;00;00;00;02;60;FE
; TODO: it doesn't swith the leds on!
; Address (Hex) Instruction             Opcode (Hex)    Comment
; 0900          move.b #$0A,$00010000   13FC            Opcode for move.b Immediate to Absolute Long
; 0902          000A                    000A            16-bit Immediate Data (where the assembler places the 0A)
; 0904          00010000                0001 0000       32-bit destination address
; 0908          bra loop                60FE            Branch Always back to $0818
; GTKTerm format:
; 00;00;09;00;00;00;00;0A;13;FC;00;0A;00;01;00;00;60;FE
; -------------------------------------------------------------------------
LOAD_CMD:
    lea     msg_loading,a0
    bsr     put_str

    ; Read header start address (32 bits)
    jsr     READ_32BIT_WORD     ; Result in d1.L
    move.l  d1,a0               ; a0 start address
    ; Read content length
    jsr     READ_32BIT_WORD     ; Result in d1.L
                                ; d1 content lenght
    CMP     #0,d1
    beq     LOA_CMD_DONE        ; If d1 = 0, exit
    SUBQ.L  #1,d1               ; Decrement counter (required by DBRA)

    ; Read content
LOA_CMD_LOOP:
    jsr     get_chr             ; Read byte from UART to d0
    move.b  d0,(a0)+            ; Copy read byte to memory
    DBRA    d1,LOA_CMD_LOOP     ; Decrement d1, if != -1 exit

LOA_CMD_DONE:
    lea     msg_load_done,a0
    bsr     put_str
    bra     new_cmd

; TODO: Load - Add checksum at the end
; TODO: Load - Print the address where the program has been loaded
;              or save it and change RUN to start from there

RUN_CMD:
    ; JUMP to the specified address
    JMP     (a1)

FBCLR_CMD:
    lea     FB_START,a0         ; Framebuffer pointer
    MOVE.W  #((FB_LEN/2)-1),d1  ; Framebuffer size in words - 1 (DBRA)
    MOVE.W  #0,d0
FBCLR_CMD_LOOP:
    MOVE.W  d0,(a0)+            ; Clear FB
    DBRA    d1,FBCLR_CMD_LOOP   ; Decrement d1, if != -1 loop
    bra     new_cmd             ; Done

; ------------------------------------------------------------
; PARSE_DUMP: Checks for 'DUMP' and extracts address argument.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
PARSE_DUMP:
    movem.l a0,-(SP)
    lea     DUMP_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_DMP_DONE        ; Exit on failure

    jsr     CHECK_SEP           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_DMP_DONE        ; Exit on failure

    bsr    hex_to_bin            ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_DMP_DONE        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     CHECK_TRAIL         ; Check for trailing junk
                                ; d0.0 returned with result flag
PRS_DMP_DONE:
    movem.l (SP)+,a0
    rts

; ------------------------------------------------------------
; PARSE_WRITE: Checks for 'WRITE' and extracts address arguments.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit address.
; - d1: If successful, contains the 32-bit value.
; ------------------------------------------------------------
PARSE_WRITE:
    movem.l a0,-(SP)
    lea     WRITE_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_WRT_DONE        ; Exit on failure

    jsr     CHECK_SEP           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_WRT_DONE        ; Exit on failure

    bsr     hex_to_bin           ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_WRT_DONE        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     CHECK_SEP           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_WRT_DONE        ; Exit on failure

    bsr     hex_to_bin          ; Parse 2st argument (32 bits), d1 contains result
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_WRT_DONE        ; Exit on failure

    jsr     CHECK_TRAIL         ; Check for trailing junk
                                ; d0.0 returned with result flag
PRS_WRT_DONE:
    movem.l (SP)+,a0
    rts

; ------------------------------------------------------------
; PARSE_HELP: Checks for 'HELP', no arguments
; Output
; - d0.0: 1 if 'HELP' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_HELP:
    movem.l a0,-(SP)
    lea     HELP_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_HLP_DONE        ; Exit on failure

    jsr     CHECK_TRAIL         ; Check for trailing junk
                                ; d0.0 returned with result flag
PRS_HLP_DONE:
    movem.l (SP)+,a0
    rts

; ------------------------------------------------------------
; PARSE_LOAD: Checks for 'LOAD', no arguments
; Output
; - d0.0: 1 if 'LOAD' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_LOAD:
    movem.l a0,-(SP)
    lea     LOAD_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_LOA_DONE        ; Exit on failure

    ;jsr     CHECK_SEP           ; Check for separator
    ;btst    #0,d0               ; d0.0 equals 0, failure
    ;beq     PRS_LOA_DONE        ; Exit on failure

    jsr     CHECK_TRAIL         ; Check for trailing junk
                                ; d0.0 returned with result flag
PRS_LOA_DONE:
    movem.l (SP)+,a0
    rts

; ------------------------------------------------------------
; PARSE_RUN: Checks for 'RUN' and extracts address argument.
; Output
; - d0.0: 1 if 'RUN' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
PARSE_RUN:
    movem.l a0,-(SP)
    lea     RUN_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_RUN_DONE        ; Exit on failure

    jsr     CHECK_SEP           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_RUN_DONE        ; Exit on failure

    bsr     hex_to_bin          ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_RUN_DONE        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     CHECK_TRAIL         ; Check for trailing junk
                                ; d0.0 returned with result flag
PRS_RUN_DONE:
    movem.l (SP)+,a0
    rts

; ------------------------------------------------------------
; PARSE_FBCLR: Checks for 'FBCLR', no arguments
; Output
; - d0.0: 1 if 'FBCLR' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_FBCLR:
    movem.l a0,-(SP)
    lea     FBCLR_STR,a1
    lea     IN_BUF,a0

    jsr     CHECK_CMD           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     PRS_FBCLR_DONE      ; Exit on failure

    jsr     CHECK_TRAIL         ; Check for trailing junk

PRS_FBCLR_DONE:
    movem.l (SP)+,a0
    rts


; ------------------------------------------------------------
; CHECK_CMD
; Input
; - a0: Points to the buffer.
; - a1: Points to the start of the command (NULL terminated)
;       to be compared to (e.g. DUMP_STR).
; Output
; - d0.0: 1 if command found, 0 otherwise.
; - a0: Points to character in the buffer after the command.
; ------------------------------------------------------------
CHECK_CMD:
    movem.l d1/d2/d3/a1,-(SP)
    move.l #1,d0

CHK_CMD_LOOP:
    move.b  (a1)+,d3
    cmp.b   #NUL,d3
    beq     CHK_CMD_DONE
    move.b  (a0)+,d2
    cmp.b   d3,d2
    bne     CHK_CMD_FAIL
    bra     CHK_CMD_LOOP

CHK_CMD_FAIL:
    CLR.L   d0

CHK_CMD_DONE:
    movem.l (SP)+,d1/d2/d3/a1
    rts

; ------------------------------------------------------------
; CHECK_SEP
; Check for separator and skip whitespace to find argument.
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if separator found, 0 otherwise.
; - a0: Points to character in the buffer after the argument.
; ------------------------------------------------------------
CHECK_SEP:
    movem.l d2,-(SP)
    move.l #1,d0

    ; Check for separator (Space)
    move.b  (a0),d2             ; Peek at the next character
    cmp.b   #' ',d2             ; Must be a space
    bne     CHK_SEP_FAIL        ; Fail if not space

    ; Skip whitespace to find argument
CHK_SEP_SKIP_WS:
    cmp.b   #' ',(a0)+          ; Check for space, and advance a0
    beq     CHK_SEP_SKIP_WS     ; Loop while space
    SUBQ.L  #1,a0               ; a0 advanced one too far, backtrack
    bra     CHK_SEP_DONE

CHK_SEP_FAIL:
    CLR.L   d0

CHK_SEP_DONE:
    movem.l (SP)+,d2
    rts

; ------------------------------------------------------------
; CHECK_TRAIL
; Check for trailing junk (should be called after all arguments).
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if string clean, 0 otherwise.
; ------------------------------------------------------------
CHECK_TRAIL:
    movem.l d2,-(SP)
    move.l #1,d0

CHK_TRL_LOOP:
    move.b  (a0)+,d2            ; Peek at the character
    TST.B   d2                  ; Is it NULL?
    beq     CHK_TRL_DONE        ; End of line, SUCCESS
    cmp.b   #' ',d2             ; Is it a space?
    bne     CHK_TRL_FAIL        ; If it's *anything else* (like 'X' in 'C000X'), it's junk.
    bra     CHK_TRL_LOOP        ; continue until end of line

CHK_TRL_FAIL:
    CLR.L   d0

CHK_TRL_DONE:
    movem.l (SP)+,d2
    rts

; ------------------------------
; TRAP handlers
; ------------------------------
TRAP_14_HANDLER:
    move.l  #SP_START,SP
    JMP     mon_entry

; ------------------------------
; Libraries
; ------------------------------
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conversions.asm'

; -------------------------------------------------------------
; READ_32BIT_WORD: Reads 4 bytes from UART and assembles into d1.L
; Input: None
; Output: d1.L = 32-bit value
; Uses: get_chr (assumed to return 8-bit char in d0.B)
; -------------------------------------------------------------
READ_32BIT_WORD:
    movem.l d0/d2,-(SP)     ; Save d0 (used for get_chr) and d2 (used for loop counter)

    MOVEQ   #4-1,d2         ; d2 = 3 (loop 4 times for 4 bytes)
    CLR.L   d1              ; d1 = Accumulator (cleared for the 32-bit result)

READ_LOOP:
    bsr     get_chr         ; d0.B = Get one byte from the serial port

    ; 1. Shift the current result (d1) left by 8 bits (makes room for the new byte)
    LSL.L   #8,d1

    ; 2. OR the new byte (d0.B) into the least significant position of d1
    OR.B    d0,d1

    DBRA    d2,READ_LOOP    ; Loop 4 times total (d2 counts down from 3)

    movem.l (SP)+,d0/d2      ; Restore registers
    rts


init_vector_table:
    move.l  #TRAP_14_HANDLER,VT_TRAP_14
    rts


; ------------------------------
; ROM Data Section
; ------------------------------

; Messages
msg_title       dc.b    'RT68F Monitor v0.1',CR,LF,NUL
msg_unknown     dc.b    'Error: Unknown command or syntax',CR,LF,NUL
msg_help        dc.b    'DUMP  <ADDR>       - Dump from ADDR (HEX)',CR,LF
                dc.b    'WRITE <ADDR> <VAL> - Write to ADDR (HEX) the VALUE (HEX)',CR,LF
                dc.b    'LOAD               - Load from UART to memory',CR,LF
                dc.b    'RUN   <ADDR>       - Run program at ADDR (HEX)',CR,LF
                dc.b    'FBCLR              - Clear framebuffer',CR,LF
                dc.b    'HELP               - Print this list of commands',CR,LF
                dc.b    NUL
msg_loading     dc.b    'Loading...',CR,LF,NUL
msg_load_done   dc.b    'Done.',CR,LF,NUL

; Commands
; They must be null terminated
DUMP_STR        dc.b    'DUMP',NUL
WRITE_STR       dc.b    'WRITE',NUL
HELP_STR        dc.b    'HELP',NUL
LOAD_STR        dc.b    'LOAD',NUL
RUN_STR         dc.b    'RUN',NUL,NUL
FBCLR_STR       dc.b    'FBCLR',NUL

; ===========================
; Constants
; ===========================
MON_MEM_LEN     equ 256                     ; RAM allocated for the monitor

; Memory Map
RAM_START       equ $00000400               ; Start of RAM address (after the vector table)
RAM_END         equ $00004000               ; End of RAM address (+1)
SP_START        equ (RAM_END-MON_MEM_LEN)   ; After SP, allocates monitor RAM
MON_MEM_START   equ SP_START                ;
FB_START        equ $00200000               ; Start of Framebuffer (TODO)
FB_END          equ $0020FA01               ; End of Framebuffer (+1)
FB_LEN          equ (FB_END-FB_START)       ; Framebuffer length
; NOTE: do not remove spaces around +

; Vector Table
VT_TRAP_14      equ $B8

; Monitor RAM
; Allocated after the stack point, if the monitor needs
; more memory it's sufficient to move the stack pointer
; Buffer
IN_BUF          equ MON_MEM_START           ; IN_BUF start after the stack pointer
IN_BUF_LEN      equ 80                      ; BUFFER LEN should be less than MON_MEM_LEN equ
IN_BUF_END      equ IN_BUF+IN_BUF_LEN       ;

; Program Constants
DLY_VAL         equ 1333333     ; Delay iterations, 1.33 million = 0.5 sec at 32MHz
