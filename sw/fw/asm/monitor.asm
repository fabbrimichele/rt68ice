; ------------------------------
; ROM Monitor (ROM version)
; ------------------------------
    section .text, code

; ------------------------------
; Initial Reset sp and PC in Vector Table
; ------------------------------
    dc.l _stack_top         ; Reset Stack Pointer (sp, sp move downward far from SO_RAM)
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
    beq     process_cmd     ; then process command
    cmp.b   #BS,d0          ; Check for Backspace
    beq     bs_handler
    cmp.b   #DEL,d0          ; Check for Backspace
    beq     bs_handler

    cmp.l   #IN_BUF_END,a5  ; Check if buffer is full
    beq     buffer_full

    bsr     put_chr         ; print character
    move.b  d0,(a5)+        ; Store d0 into buffer, then increment a5
    bra     loop

; --------------------------------------
; Backspace Handler
; --------------------------------------
bs_handler:
    ; 1. Check if the buffer is empty
    cmp.l   #IN_BUF,a5          ; Compare current pointer (a5) to start of buffer
    beq     loop                ; If a5 == IN_BUF, buffer is empty (do nothing)

    ; 2. Correct the buffer pointer
    subq.l  #1,a5               ; Decrement a5: move pointer back one position

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
buffer_full:
    ; Send BEL (7) once to alert the user that the buffer is full
    move.b  #BEL,d0
    bsr     put_chr

    bsr     get_chr             ; Get the next character
    ; Check 1: Enter pressed (CR)
    cmp.b   #CR,d0
    beq     process_cmd         ; Yes, go process the command
    ; Check 2: Backspace or Delete
    cmp.b   #BS,d0
    beq     bs_handler

    ; Discard all other input
    bra     buffer_full

process_cmd:
    move.b  #0,(a5)         ; Null-terminate the string in the buffer
    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr

    ; Parse DUMP
    bsr     parse_dump
    btst    #0,d0
    bne     dump_cmd        ; d0.0 = 1 execute DUMP

    ; Parse WRITE
    bsr     parse_write
    btst    #0,d0
    bne     write_cmd       ; d0.0 = 1 execute WRITE

    ; Parse HELP
    bsr     parse_help
    btst    #0,d0
    bne     help_cmd        ; d0.0 = 1 execute HELP

    ; Parse LOAD
    bsr     parse_load
    btst    #0,d0
    bne     load_cmd        ; d0.0 = 1 execute LOAD

    ; Parse RUN
    bsr     parse_run
    btst    #0,d0
    bne     run_cmd         ; d0.0 = 1 execute RUN

    ; Parse FBCLR
    bsr     parse_fbclr
    btst    #0,d0
    bne     fbclr_cmd       ; d0.0 = 1 execute FBCLR

unknown_cmd:
    ; Print error message
    lea     msg_unknown,a0
    bsr     put_str
    bra     new_cmd

; a1 - Dump address
dump_cmd:
    move.w  #(8-1),d1       ; Print 8 lines
dump_line:
    move.l  a1,d0
    bsr     bin_to_hex        ; Print address
    move.b  #':',d0
    bsr     put_chr
    move.w  #(8-1),d2       ; Print 8 cells
dump_cell:
    move.b  #' ',d0
    bsr     put_chr
    move.w  (a1)+,d0
    bsr     bin_to_hex_w      ; Print mem value
    dbra    d2,dump_cell    ; Decrement d1, branch if d1 is NOT -1

    move.b  #CR,d0
    bsr     put_chr
    move.b  #LF,d0
    bsr     put_chr
    dbra    d1,dump_line    ; Decrement d1, branch if d1 is NOT -1
    bra     new_cmd

; d1 - Value to be written
; a1 - Write address
write_cmd:
    move.w  d1,(a1)       ; Move only 16 bits (argument is 32 bit long)
    bra     new_cmd

help_cmd:
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
load_cmd:
    lea     msg_loading,a0
    bsr     put_str

    ; Read header start address (32 bits)
    jsr     read_32bit_word     ; Result in d1.L
    move.l  d1,a0               ; a0 start address
    ; Read content length
    jsr     read_32bit_word     ; Result in d1.L
                                ; d1 content lenght
    cmp     #0,d1
    beq     loa_cmd_done        ; If d1 = 0, exit
    subq.l  #1,d1               ; Decrement counter (required by dbra)

    ; Read content
loa_cmd_loop:
    jsr     get_chr             ; Read byte from UART to d0
    move.b  d0,(a0)+            ; Copy read byte to memory
    dbra    d1,loa_cmd_loop     ; Decrement d1, if != -1 exit

loa_cmd_done:
    lea     msg_load_done,a0
    bsr     put_str
    bra     new_cmd

; TODO: Load - Add checksum at the end
; TODO: Load - Print the address where the program has been loaded
;              or save it and change RUN to start from there

run_cmd:
    ; JUMP to the specified address
    jmp     (a1)

fbclr_cmd:
    lea     FB_START,a0         ; Framebuffer pointer
    move.w  #((FB_LEN/2)-1),d1  ; Framebuffer size in words - 1 (dbra)
    move.w  #0,d0
fbclr_cmd_loop:
    move.w  d0,(a0)+            ; Clear FB
    dbra    d1,fbclr_cmd_loop   ; Decrement d1, if != -1 loop
    bra     new_cmd             ; Done

; ------------------------------------------------------------
; parse_dump: Checks for 'DUMP' and extracts address argument.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
parse_dump:
    movem.l a0,-(sp)
    lea     dump_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure

    bsr    hex_to_bin            ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_dmp_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_dmp_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_write: Checks for 'WRITE' and extracts address arguments.
; Output
; - d0.0: 1 if 'DUMP' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit address.
; - d1: If successful, contains the 32-bit value.
; ------------------------------------------------------------
parse_write:
    movem.l a0,-(sp)
    lea     write_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    bsr     hex_to_bin           ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    bsr     hex_to_bin          ; Parse 2st argument (32 bits), d1 contains result
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_wrt_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_wrt_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_help: Checks for 'HELP', no arguments
; Output
; - d0.0: 1 if 'HELP' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_help:
    movem.l a0,-(sp)
    lea     help_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_hlp_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_hlp_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_load: Checks for 'LOAD', no arguments
; Output
; - d0.0: 1 if 'LOAD' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_load:
    movem.l a0,-(sp)
    lea     load_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     prs_loa_done        ; Exit on failure

    ;jsr     check_sep           ; Check for separator
    ;btst    #0,d0               ; d0.0 equals 0, failure
    ;beq     prs_loa_done        ; Exit on failure

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
prs_loa_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_run: Checks for 'RUN' and extracts address argument.
; Output
; - d0.0: 1 if 'RUN' found and address parsed, 0 otherwise.
; - a1: If successful, contains the 32-bit starting address.
; ------------------------------------------------------------
parse_run:
    movem.l a0,-(sp)
    lea     run_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure

    jsr     check_sep           ; Check for separator
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure

    bsr     hex_to_bin          ; Parse 1st argument (32 bits)
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_run_done        ; Exit on failure
    move.l  d1,a1               ; Move the parsed address from d0 into a1

    jsr     check_trail         ; Check for trailing junk
                                ; d0.0 returned with result flag
psr_run_done:
    movem.l (sp)+,a0
    rts

; ------------------------------------------------------------
; parse_fbclr: Checks for 'FBCLR', no arguments
; Output
; - d0.0: 1 if 'FBCLR' found and address parsed, 0 otherwise.
; ------------------------------------------------------------
parse_fbclr:
    movem.l a0,-(sp)
    lea     fbclr_str,a1
    lea     IN_BUF,a0

    jsr     check_cmd           ; Chek expected command
    btst    #0,d0               ; d0.0 equals 0, failure
    beq     psr_fbclr_done      ; Exit on failure

    jsr     check_trail         ; Check for trailing junk

psr_fbclr_done:
    movem.l (sp)+,a0
    rts


; ------------------------------------------------------------
; check_cmd
; Input
; - a0: Points to the buffer.
; - a1: Points to the start of the command (NULL terminated)
;       to be compared to (e.g. dump_str).
; Output
; - d0.0: 1 if command found, 0 otherwise.
; - a0: Points to character in the buffer after the command.
; ------------------------------------------------------------
check_cmd:
    movem.l d1/d2/d3/a1,-(sp)
    move.l #1,d0

chk_cmd_loop:
    move.b  (a1)+,d3
    cmp.b   #NUL,d3
    beq     chk_cmd_done
    move.b  (a0)+,d2
    cmp.b   d3,d2
    bne     chk_cmd_fail
    bra     chk_cmd_loop

chk_cmd_fail:
    clr.l   d0

chk_cmd_done:
    movem.l (sp)+,d1/d2/d3/a1
    rts

; ------------------------------------------------------------
; check_sep
; Check for separator and skip whitespace to find argument.
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if separator found, 0 otherwise.
; - a0: Points to character in the buffer after the argument.
; ------------------------------------------------------------
check_sep:
    movem.l d2,-(sp)
    move.l #1,d0

    ; Check for separator (Space)
    move.b  (a0),d2             ; Peek at the next character
    cmp.b   #' ',d2             ; Must be a space
    bne     chk_sep_fail        ; Fail if not space

    ; Skip whitespace to find argument
chk_sep_skip_ws:
    cmp.b   #' ',(a0)+          ; Check for space, and advance a0
    beq     chk_sep_skip_ws     ; Loop while space
    subq.l  #1,a0               ; a0 advanced one too far, backtrack
    bra     chk_sep_done

chk_sep_fail:
    clr.l   d0

chk_sep_done:
    movem.l (sp)+,d2
    rts

; ------------------------------------------------------------
; check_trail
; Check for trailing junk (should be called after all arguments).
; Input
; - a0: Points to character in the buffer after the command.
; Output
; - d0.0: 1 if string clean, 0 otherwise.
; ------------------------------------------------------------
check_trail:
    movem.l d2,-(sp)
    move.l #1,d0

chk_trl_loop:
    move.b  (a0)+,d2            ; Peek at the character
    tst.b   d2                  ; Is it NULL?
    beq     chk_trl_done        ; End of line, SUCCESS
    cmp.b   #' ',d2             ; Is it a space?
    bne     chk_trl_fail        ; If it's *anything else* (like 'X' in 'C000X'), it's junk.
    bra     chk_trl_loop        ; continue until end of line

chk_trl_fail:
    clr.l   d0

chk_trl_done:
    movem.l (sp)+,d2
    rts

; ------------------------------
; TRAP handlers
; ------------------------------
trap_14_handler:
    move.l  #SP_START,sp
    jmp     mon_entry

; ------------------------------
; Libraries
; ------------------------------
    include '../../lib/asm/console_io_uart.asm'
    include '../../lib/asm/conversions.asm'

; -------------------------------------------------------------
; read_32bit_word: Reads 4 bytes from UART and assembles into d1.L
; Input: None
; Output: d1.L = 32-bit value
; Uses: get_chr (assumed to return 8-bit char in d0.B)
; -------------------------------------------------------------
read_32bit_word:
    movem.l d0/d2,-(sp)     ; Save d0 (used for get_chr) and d2 (used for loop counter)

    moveq   #4-1,d2         ; d2 = 3 (loop 4 times for 4 bytes)
    clr.l   d1              ; d1 = Accumulator (cleared for the 32-bit result)

read_loop:
    bsr     get_chr         ; d0.B = Get one byte from the serial port

    ; 1. Shift the current result (d1) left by 8 bits (makes room for the new byte)
    lsl.l   #8,d1

    ; 2. OR the new byte (d0.B) into the least significant position of d1
    or.b    d0,d1

    dbra    d2,read_loop    ; Loop 4 times total (d2 counts down from 3)

    movem.l (sp)+,d0/d2      ; Restore registers
    rts


init_vector_table:
    move.l  #trap_14_handler,VT_TRAP_14
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
dump_str        dc.b    'DUMP',NUL
write_str       dc.b    'WRITE',NUL
help_str        dc.b    'HELP',NUL
load_str        dc.b    'LOAD',NUL
run_str         dc.b    'RUN',NUL,NUL
fbclr_str       dc.b    'FBCLR',NUL

; ===========================
; RAM Data Section (bootloader mem)
; ===========================
; TODO: this is OK for a generic program
;       the monitor needs to have a different memory layout
;       RAM:
;       - memory to load programs
;       - SP top
;       - monitor working area (256 bytes)
    section .bss
IN_BUF:
    ds.b    80
IN_BUF_END:

; ===========================
; Constants
; ===========================
MON_MEM_LEN     equ 256                     ; RAM allocated for the monitor

; Memory Map
RAM_END         equ $00004000               ; End of RAM address (+1)
SP_START        equ (RAM_END-MON_MEM_LEN)   ; After sp, allocates monitor RAM
MON_MEM_START   equ SP_START                ;
FB_START        equ $00200000               ; Start of Framebuffer (TODO)
FB_END          equ $0020FA01               ; End of Framebuffer (+1)
FB_LEN          equ (FB_END-FB_START)       ; Framebuffer length
; NOTE: do not remove spaces around +

; Vector Table
VT_TRAP_14      equ $B8

; Program Constants
DLY_VAL         equ 1333333     ; Delay iterations, 1.33 million = 0.5 sec at 32MHz
