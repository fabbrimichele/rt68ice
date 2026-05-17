# Hardware settings
TARGET = rt68ice
TOP = Rt68IceTopLevel
SCALA_PACKAGE = rt68ice
VERILOG_SOURCES = hw/gen/$(TOP).v
MERGED_VHDL = hw/gen/mergeRTL.vhd
MERGED_VERILOG = hw/gen/mergeRTL.v
# TODO
##WAVE_FILE = simWorkspace/Blink/test/wave.fst
# ECP5 Specifics
DEVICE  = --25k
PACKAGE = --package CABGA256
LPF     = hw/constraints/$(TOP).lpf

#Software settings
ASM_SRC_DIR = sw/fw/asm
BIN_GEN_DIR = hw/gen
HEX_SPINAL_DIR = hw/spinal/rt68ice/memory
HEX_CLASS_DIR = target/scala-2.13/classes/rt68ice/memory
ASSEMBLIES = blink mem_test stack_test serial_hello serial_echo monitor

.PHONY: all clean rom prog prog-flash view-wave

all: $(TARGET).bit

# 1. Generate Verilog from SpinalHDL (The bridge)
$(VERILOG_SOURCES) $(MERGED_VHDL): hw/spinal/rt68ice/*.scala rom
	sbt "runMain $(SCALA_PACKAGE).$(TOP)Verilog"

# 2. Synthesis
$(TARGET).json: $(VERILOG_TOP) $(MERGED_VHDL)
	#yosys -p "synth_ecp5 -json $@" $(VERILOG_SOURCES)
	yosys -m ghdl -p "ghdl -C --std=08 -C -fsynopsys -C --latches $(MERGED_VHDL) -e TG68KdotC_Kernel; \
		ghdl -C --std=08 -C -fsynopsys -C --latches $(MERGED_VHDL) -e T16450; \
		read_verilog $(VERILOG_SOURCES) $(MERGED_VERILOG); \
		synth_ecp5 -top $(TOP) -json $@"

# 3. Place & Route (Using the LPF file!)
$(TARGET).config: $(TARGET).json
	nextpnr-ecp5 $(DEVICE) $(PACKAGE) --speed 6 --json $< --textcfg $@ --lpf $(LPF) --freq 30

# 4. Bitstream
$(TARGET).bit: $(TARGET).config
	ecppack $< $@

# 5. Load to FPGA
prog: # $(TARGET).bit
	#openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b $<
	openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b $(TARGET).bit

# 5. Load to FLASH (permanent)
prog-flash: $(TARGET).bit
	openFPGALoader -f -c cmsisdap --vid=0x1d50 --pid=0x602b $<

serial-open:
	picocom -b 19200 /dev/ttyACM0

reset:
	openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b --reset

view-wave: simWorkspace/Blink/test/wave.fst
	@if [ -f $(WAVE_FILE) ]; then \
		gtkwave $(WAVE_FILE) & \
	else \
		echo "Error: Waveform file not found. Run simulation first."; \
	fi

# TODO: simplify this, why am I creating an hex file? Can't I just load binary file into the Mem16bit?
ROM_HEX_FILES = $(patsubst %, $(HEX_CLASS_DIR)/%.hex, $(ASSEMBLIES))
rom: $(ROM_HEX_FILES)

$(HEX_CLASS_DIR)/%.hex: $(ASM_SRC_DIR)/%.asm
	@echo "----------------------------------------------"
	@echo "- Assembling and Converting '$*'"
	@echo "----------------------------------------------"
	# Assemble the 68000 code to an ELF object file
	vasmm68k_mot -Felf $< -o $(BIN_GEN_DIR)/$*.o
	# Link object file
	vlink -T $(ASM_SRC_DIR)/fw.ld -b rawbin1 -M$(BIN_GEN_DIR)/$*.sym -o $(BIN_GEN_DIR)/$*.bin $(BIN_GEN_DIR)/$*.o
	# Convert binary to a two-byte-per-line hex file, convert to uppercase
	xxd -p -c 2 $(BIN_GEN_DIR)/$*.bin | awk '{print toupper($$0)}' > $(HEX_SPINAL_DIR)/$*.hex
	# Ensure the destination directory exists
	mkdir -p $(HEX_CLASS_DIR)
	# Copy the hex file to the Scala classes path for resource loading
	cp $(HEX_SPINAL_DIR)/$*.hex $@

clean:
	rm -rf *.json *.config *.bit hw/gen/*.v hw/gen/*.vhd target hw/spinal/rt68ice/memory/*.hex
