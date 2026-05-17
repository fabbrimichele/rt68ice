; ------------------------------
; ROM Monitor (ROM version)
; ------------------------------
    ORG    $4000          ; ROM Start Address

; ------------------------------
; Initial Reset SP and PC in Vector Table
; ------------------------------
    DC.L RAM_END            ; Reset Stack Pointer (SP, SP move downward far from SO_RAM)
    DC.L START              ; Reset Program counter (PC) (point to the beginning of code)

; ------------------------------
; Program code
; ------------------------------
START:
    JSR     INIT_VECTOR_TABLE
    JSR     uart_init
    LEA     MSG_TITLE,A0
    BSR     put_str

MON_ENTRY:
NEW_CMD:
    LEA     IN_BUF,A5       ; A5 = current buffer position
    MOVE.B  #CR,D0
    BSR     put_chr
    MOVE.B  #LF,D0
    BSR     put_chr
    MOVE.B  #'>',D0
    BSR     put_chr
LOOP:
    BSR     get_chr

    CMP.B   #CR,D0          ; Check for Enter
    BEQ     PROCESS_CMD     ; then process command
    CMP.B   #BS,D0          ; Check for Backspace
    BEQ     BS_HANDLER
    CMP.B   #DEL,D0          ; Check for Backspace
    BEQ     BS_HANDLER

    CMP.L   #IN_BUF_END,A5  ; Check if buffer is full
    BEQ     BUFFER_FULL

    BSR     put_chr         ; print character
    MOVE.B  D0,(A5)+        ; Store D0 into buffer, then increment A5
    BRA     LOOP

; --------------------------------------
; Backspace Handler
; --------------------------------------
BS_HANDLER:
    ; 1. Check if the buffer is empty
    CMP.L   #IN_BUF,A5          ; Compare current pointer (A5) to start of buffer
    BEQ     LOOP                ; If A5 == IN_BUF, buffer is empty (do nothing)

    ; 2. Correct the buffer pointer
    SUBQ.L  #1,A5               ; Decrement A5: move pointer back one position

    ; 3. Correct the terminal display (echo the standard sequence)
    ; Send BS (0x08) to move cursor left
    MOVE.B  #BS,D0
    BSR     put_chr

    ; Send Space (0x20) to erase the character
    MOVE.B  #' ',D0
    BSR     put_chr

    ; Send BS (0x08) again to move cursor back to erased position
    MOVE.B  #BS,D0
    BSR     put_chr

    BRA     LOOP                ; Continue input loop

; --------------------------------------
; Buffer Full Handler
; --------------------------------------
BUFFER_FULL:
    ; Send BEL (7) once to alert the user that the buffer is full
    MOVE.B  #BEL,D0
    BSR     put_chr

    BSR     get_chr             ; Get the next character
    ; Check 1: Enter pressed (CR)
    CMP.B   #CR,D0
    BEQ     PROCESS_CMD         ; Yes, go process the command
    ; Check 2: Backspace or Delete
    CMP.B   #BS,D0
    BEQ     BS_HANDLER

    ; Discard all other input
    BRA     BUFFER_FULL

PROCESS_CMD:
    MOVE.B  #0,(A5)         ; Null-terminate the string in the buffer
    MOVE.B  #CR,D0
    BSR     put_chr
    MOVE.B  #LF,D0
    BSR     put_chr

    ; Parse DUMP
    BSR     PARSE_DUMP
    BTST    #0,D0
    BNE     DUMP_CMD        ; D0.0 = 1 execute DUMP

    ; Parse WRITE
    BSR     PARSE_WRITE
    BTST    #0,D0
    BNE     WRITE_CMD       ; D0.0 = 1 execute WRITE

    ; Parse HELP
    BSR     PARSE_HELP
    BTST    #0,D0
    BNE     HELP_CMD        ; D0.0 = 1 execute HELP

    ; Parse LOAD
    BSR     PARSE_LOAD
    BTST    #0,D0
    BNE     LOAD_CMD        ; D0.0 = 1 execute LOAD

    ; Parse RUN
    BSR     PARSE_RUN
    BTST    #0,D0
    BNE     RUN_CMD         ; D0.0 = 1 execute RUN

    ; Parse FBCLR
    BSR     PARSE_FBCLR
    BTST    #0,D0
    BNE     FBCLR_CMD       ; D0.0 = 1 execute FBCLR

UNKNOWN_CMD:
    ; Print error message
    LEA     MSG_UNKNOWN,A0
    BSR     put_str
    BRA     NEW_CMD

; A1 - Dump address
DUMP_CMD:
    MOVE.W  #(8-1),D1       ; Print 8 lines
DUMP_LINE:
    MOVE.L  A1,D0
    BSR     bin_to_hex        ; Print address
    MOVE.B  #':',D0
    BSR     put_chr
    MOVE.W  #(8-1),D2       ; Print 8 cells
DUMP_CELL:
    MOVE.B  #' ',D0
    BSR     put_chr
    MOVE.W  (A1)+,D0
    BSR     bin_to_hex_w      ; Print mem value
    DBRA    D2,DUMP_CELL    ; Decrement D1, branch if D1 is NOT -1

    MOVE.B  #CR,D0
    BSR     put_chr
    MOVE.B  #LF,D0
    BSR     put_chr
    DBRA    D1,DUMP_LINE    ; Decrement D1, branch if D1 is NOT -1
    BRA     NEW_CMD

; D1 - Value to be written
; A1 - Write address
WRITE_CMD:
    MOVE.W  D1,(A1)       ; Move only 16 bits (argument is 32 bit long)
    BRA     NEW_CMD

HELP_CMD:
    LEA     MSG_HELP,A0
    BSR     put_str
    BRA     NEW_CMD

; -------------------------------------------------------------------------
; Load from UART a binary content to memory.
;
; PROTOCOL: Length-Prefixed Binary (Big-Endian)
;
; HEADER (8 bytes, sent first):
; [32-bit Load Address] (A0)
; [32-bit Content Length] (D2)
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
; 0810          BRA LOOP    60FE            Branch Always back to $0810 (FE=−2).
; GTKTerm format: 00;00;08;10;00;00;00;02;60;FE
; TODO: it doesn't swith the leds on!
; Address (Hex) Instruction             Opcode (Hex)    Comment
; 0900          MOVE.B #$0A,$00010000   13FC            Opcode for MOVE.B Immediate to Absolute Long
; 0902          000A                    000A            16-bit Immediate Data (where the assembler places the 0A)
; 0904          00010000                0001 0000       32-bit destination address
; 0908          BRA LOOP                60FE            Branch Always back to $0818
; GTKTerm format:
; 00;00;09;00;00;00;00;0A;13;FC;00;0A;00;01;00;00;60;FE
; -------------------------------------------------------------------------
LOAD_CMD:
    LEA     MSG_LOADING,A0
    BSR     put_str

    ; Read header start address (32 bits)
    JSR     READ_32BIT_WORD     ; Result in D1.L
    MOVE.L  D1,A0               ; A0 start address
    ; Read content length
    JSR     READ_32BIT_WORD     ; Result in D1.L
                                ; D1 content lenght
    CMP     #0,D1
    BEQ     LOA_CMD_DONE        ; If D1 = 0, exit
    SUBQ.L  #1,D1               ; Decrement counter (required by DBRA)

    ; Read content
LOA_CMD_LOOP:
    JSR     get_chr             ; Read byte from UART to D0
    MOVE.B  D0,(A0)+            ; Copy read byte to memory
    DBRA    D1,LOA_CMD_LOOP     ; Decrement D1, if != -1 exit

LOA_CMD_DONE:
    LEA     MSG_LOAD_DONE,A0
    BSR     put_str
    BRA     NEW_CMD

; TODO: Load - Add checksum at the end
; TODO: Load - Print the address where the program has been loaded
;              or save it and change RUN to start from there

RUN_CMD:
    ; JUMP to the specified address
    JMP     (A1)

FBCLR_CMD:
    LEA     FB_START,A0         ; Framebuffer pointer
    MOVE.W  #((FB_LEN/2)-1),D1  ; Framebuffer size in words - 1 (DBRA)
    MOVE.W  #0,D0
FBCLR_CMD_LOOP:
    MOVE.W  D0,(A0)+            ; Clear FB
    DBRA    D1,FBCLR_CMD_LOOP   ; Decrement D1, if != -1 loop
    BRA     NEW_CMD             ; Done

; ------------------------------------------------------------
; PARSE_DUMP: Checks for 'DUMP' and extracts address argument.
; Output
; - D0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - A1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
PARSE_DUMP:
    MOVEM.L A0,-(SP)
    LEA     DUMP_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_DMP_DONE        ; Exit on failure

    JSR     CHECK_SEP           ; Check for separator
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_DMP_DONE        ; Exit on failure

    BSR     hex_to_bin            ; Parse 1st argument (32 bits)
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_DMP_DONE        ; Exit on failure
    MOVE.L  D1,A1               ; Move the parsed address from D0 into A1

    JSR     CHECK_TRAIL         ; Check for trailing junk
                                ; D0.0 returned with result flag
PRS_DMP_DONE:
    MOVEM.L (SP)+,A0
    RTS

; ------------------------------------------------------------
; PARSE_WRITE: Checks for 'WRITE' and extracts address arguments.
; Output
; - D0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - A1: If successful, contains the 32-bit address.
; - D1: If successful, contains the 32-bit value.
; ------------------------------------------------------------
PARSE_WRITE:
    MOVEM.L A0,-(SP)
    LEA     WRITE_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_WRT_DONE        ; Exit on failure

    JSR     CHECK_SEP           ; Check for separator
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_WRT_DONE        ; Exit on failure

    BSR     hex_to_bin            ; Parse 1st argument (32 bits)
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_WRT_DONE        ; Exit on failure
    MOVE.L  D1,A1               ; Move the parsed address from D0 into A1

    JSR     CHECK_SEP           ; Check for separator
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_WRT_DONE        ; Exit on failure

    BSR     hex_to_bin            ; Parse 2st argument (32 bits), D1 contains result
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_WRT_DONE        ; Exit on failure

    JSR     CHECK_TRAIL         ; Check for trailing junk
                                ; D0.0 returned with result flag
PRS_WRT_DONE:
    MOVEM.L (SP)+,A0
    RTS

; ------------------------------------------------------------
; PARSE_HELP: Checks for 'HELP', no arguments
; Output
; - D0.0: 1 if 'HELP' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_HELP:
    MOVEM.L A0,-(SP)
    LEA     HELP_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_HLP_DONE        ; Exit on failure

    JSR     CHECK_TRAIL         ; Check for trailing junk
                                ; D0.0 returned with result flag
PRS_HLP_DONE:
    MOVEM.L (SP)+,A0
    RTS

; ------------------------------------------------------------
; PARSE_LOAD: Checks for 'LOAD', no arguments
; Output
; - D0.0: 1 if 'LOAD' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_LOAD:
    MOVEM.L A0,-(SP)
    LEA     LOAD_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_LOA_DONE        ; Exit on failure

    ;JSR     CHECK_SEP           ; Check for separator
    ;BTST    #0,D0               ; D0.0 equals 0, failure
    ;BEQ     PRS_LOA_DONE        ; Exit on failure

    JSR     CHECK_TRAIL         ; Check for trailing junk
                                ; D0.0 returned with result flag
PRS_LOA_DONE:
    MOVEM.L (SP)+,A0
    RTS

; ------------------------------------------------------------
; PARSE_RUN: Checks for 'RUN' and extracts address argument.
; Output
; - D0.0: 1 if 'RUN' found and address parsed, 0 otherwise.
; - A1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
PARSE_RUN:
    MOVEM.L A0,-(SP)
    LEA     RUN_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_RUN_DONE        ; Exit on failure

    JSR     CHECK_SEP           ; Check for separator
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_RUN_DONE        ; Exit on failure

    BSR     hex_to_bin            ; Parse 1st argument (32 bits)
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_RUN_DONE        ; Exit on failure
    MOVE.L  D1,A1               ; Move the parsed address from D0 into A1

    JSR     CHECK_TRAIL         ; Check for trailing junk
                                ; D0.0 returned with result flag
PRS_RUN_DONE:
    MOVEM.L (SP)+,A0
    RTS

; ------------------------------------------------------------
; PARSE_FBCLR: Checks for 'FBCLR', no arguments
; Output
; - D0.0: 1 if 'FBCLR' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
PARSE_FBCLR:
    MOVEM.L A0,-(SP)
    LEA     FBCLR_STR,A1
    LEA     IN_BUF,A0

    JSR     CHECK_CMD           ; Chek expected command
    BTST    #0,D0               ; D0.0 equals 0, failure
    BEQ     PRS_FBCLR_DONE      ; Exit on failure

    JSR     CHECK_TRAIL         ; Check for trailing junk

PRS_FBCLR_DONE:
    MOVEM.L (SP)+,A0
    RTS


; ------------------------------------------------------------
; CHECK_CMD
; Input
; - A0: Points to the buffer.
; - A1: Points to the start of the command (NULL terminated)
;       to be compared to (e.g. DUMP_STR).
; Output
; - D0.0: 1 if command found, 0 otherwise.
; - A0: Points to character in the buffer after the command.
; ------------------------------------------------------------
CHECK_CMD:
    MOVEM.L D1/D2/D3/A1,-(SP)
    MOVE.L #1,D0

CHK_CMD_LOOP:
    MOVE.B  (A1)+,D3
    CMP.B   #NUL,D3
    BEQ     CHK_CMD_DONE
    MOVE.B  (A0)+,D2
    CMP.B   D3,D2
    BNE     CHK_CMD_FAIL
    BRA     CHK_CMD_LOOP

CHK_CMD_FAIL:
    CLR.L   D0

CHK_CMD_DONE:
    MOVEM.L (SP)+,D1/D2/D3/A1
    RTS

; ------------------------------------------------------------
; CHECK_SEP
; Check for separator and skip whitespace to find argument.
; Input
; - A0: Points to character in the buffer after the command.
; Output
; - D0.0: 1 if separator found, 0 otherwise.
; - A0: Points to character in the buffer after the argument.
; ------------------------------------------------------------
CHECK_SEP:
    MOVEM.L D2,-(SP)
    MOVE.L #1,D0

    ; Check for separator (Space)
    MOVE.B  (A0),D2             ; Peek at the next character
    CMP.B   #' ',D2             ; Must be a space
    BNE     CHK_SEP_FAIL        ; Fail if not space

    ; Skip whitespace to find argument
CHK_SEP_SKIP_WS:
    CMP.B   #' ',(A0)+          ; Check for space, and advance A0
    BEQ     CHK_SEP_SKIP_WS     ; Loop while space
    SUBQ.L  #1,A0               ; A0 advanced one too far, backtrack
    BRA     CHK_SEP_DONE

CHK_SEP_FAIL:
    CLR.L   D0

CHK_SEP_DONE:
    MOVEM.L (SP)+,D2
    RTS

; ------------------------------------------------------------
; CHECK_TRAIL
; Check for trailing junk (should be called after all arguments).
; Input
; - A0: Points to character in the buffer after the command.
; Output
; - D0.0: 1 if string clean, 0 otherwise.
; ------------------------------------------------------------
CHECK_TRAIL:
    MOVEM.L D2,-(SP)
    MOVE.L #1,D0

CHK_TRL_LOOP:
    MOVE.B  (A0)+,D2            ; Peek at the character
    TST.B   D2                  ; Is it NULL?
    BEQ     CHK_TRL_DONE        ; End of line, SUCCESS
    CMP.B   #' ',D2             ; Is it a space?
    BNE     CHK_TRL_FAIL        ; If it's *anything else* (like 'X' in 'C000X'), it's junk.
    BRA     CHK_TRL_LOOP        ; continue until end of line

CHK_TRL_FAIL:
    CLR.L   D0

CHK_TRL_DONE:
    MOVEM.L (SP)+,D2
    RTS

; ------------------------------
; TRAP handlers
; ------------------------------
TRAP_14_HANDLER:
    MOVE.L  #SP_START,SP
    JMP     MON_ENTRY

; ------------------------------
; Libraries
; ------------------------------
    INCLUDE '../../lib/asm/console_io_uart.asm'
    INCLUDE '../../lib/asm/conversions.asm'

; -------------------------------------------------------------
; READ_32BIT_WORD: Reads 4 bytes from UART and assembles into D1.L
; Input: None
; Output: D1.L = 32-bit value
; Uses: get_chr (assumed to return 8-bit char in D0.B)
; -------------------------------------------------------------
READ_32BIT_WORD:
    MOVEM.L D0/D2,-(SP)     ; Save D0 (used for get_chr) and D2 (used for loop counter)

    MOVEQ   #4-1,D2         ; D2 = 3 (loop 4 times for 4 bytes)
    CLR.L   D1              ; D1 = Accumulator (cleared for the 32-bit result)

READ_LOOP:
    BSR     get_chr         ; D0.B = Get one byte from the serial port

    ; 1. Shift the current result (D1) left by 8 bits (makes room for the new byte)
    LSL.L   #8,D1

    ; 2. OR the new byte (D0.B) into the least significant position of D1
    OR.B    D0,D1

    DBRA    D2,READ_LOOP    ; Loop 4 times total (D2 counts down from 3)

    MOVEM.L (SP)+,D0/D2      ; Restore registers
    RTS


INIT_VECTOR_TABLE:
    MOVE.L  #TRAP_14_HANDLER,VT_TRAP_14
    RTS


; ------------------------------
; ROM Data Section
; ------------------------------

; Messages
MSG_TITLE       DC.B    'RT68F Monitor v0.1',CR,LF,NUL
MSG_UNKNOWN     DC.B    'Error: Unknown command or syntax',CR,LF,NUL
MSG_HELP        DC.B    'DUMP  <ADDR>       - Dump from ADDR (HEX)',CR,LF
                DC.B    'WRITE <ADDR> <VAL> - Write to ADDR (HEX) the VALUE (HEX)',CR,LF
                DC.B    'LOAD               - Load from UART to memory',CR,LF
                DC.B    'RUN   <ADDR>       - Run program at ADDR (HEX)',CR,LF
                DC.B    'FBCLR              - Clear framebuffer',CR,LF
                DC.B    'HELP               - Print this list of commands',CR,LF
                DC.B    NUL
MSG_LOADING     DC.B    'Loading...',CR,LF,NUL
MSG_LOAD_DONE   DC.B    'Done.',CR,LF,NUL

; Commands
; They must be null terminated
DUMP_STR    DC.B    'DUMP',NUL
WRITE_STR   DC.B    'WRITE',NUL
HELP_STR    DC.B    'HELP',NUL
LOAD_STR    DC.B    'LOAD',NUL
RUN_STR     DC.B    'RUN',NUL,NUL
FBCLR_STR   DC.B    'FBCLR',NUL

; ===========================
; Constants
; ===========================
MON_MEM_LEN EQU 256                     ; RAM allocated for the monitor

; Memory Map
RAM_START       EQU $00000400               ; Start of RAM address (after the vector table)
RAM_END         EQU $00004000               ; End of RAM address (+1)
SP_START        EQU (RAM_END-MON_MEM_LEN)   ; After SP, allocates monitor RAM
MON_MEM_START   EQU SP_START                ;
FB_START        EQU $00200000               ; Start of Framebuffer (TODO)
FB_END          EQU $0020FA01               ; End of Framebuffer (+1)
FB_LEN          EQU (FB_END-FB_START)       ; Framebuffer length
; NOTE: do not remove spaces around +

; Vector Table
VT_TRAP_14      EQU $B8

; Monitor RAM
; Allocated after the stack point, if the monitor needs
; more memory it's sufficient to move the stack pointer
; Buffer
IN_BUF          EQU MON_MEM_START           ; IN_BUF start after the stack pointer
IN_BUF_LEN      EQU 80                      ; BUFFER LEN should be less than MON_MEM_LEN EQU
IN_BUF_END      EQU IN_BUF+IN_BUF_LEN       ;

; Program Constants
DLY_VAL     EQU 1333333     ; Delay iterations, 1.33 million = 0.5 sec at 32MHz
