TARGET = rt68ice
TOP = Rt68IceTopLevel
SCALA_PACKAGE = rt68ice
VERILOG_SOURCES = hw/gen/$(TOP).v

# TODO
##WAVE_FILE = simWorkspace/Blink/test/wave.fst

# ECP5 Specifics
DEVICE  = --25k
PACKAGE = --package CABGA256
LPF     = hw/constraints/$(TOP).lpf

all: $(TARGET).bit

# 1. Generate Verilog from SpinalHDL (The bridge)
$(VERILOG_SOURCES): hw/spinal/playground/*.scala
	sbt "runMain $(SCALA_PACKAGE).$(TOP)Verilog"

# 2. Synthesis
$(TARGET).json: $(VERILOG_SOURCES)
	yosys -p "synth_ecp5 -json $@" $(VERILOG_SOURCES)

# 3. Place & Route (Using the LPF file!)
$(TARGET).config: $(TARGET).json
	nextpnr-ecp5 $(DEVICE) $(PACKAGE) --speed 6 --json $< --textcfg $@ --lpf $(LPF) --freq 65

# 4. Bitstream
$(TARGET).bit: $(TARGET).config
	ecppack $< $@

# 5. Load to FPGA
prog: $(TARGET).bit
	# openFPGALoader -b icesugar_pro $<
	openFPGALoader -c cmsisdap --vid=0x1d50 --pid=0x602b $<

# 5. Load to FLASH (permanent)
prog-flash: $(TARGET).bit
	openFPGALoader -f -c cmsisdap --vid=0x1d50 --pid=0x602b $<

view-wave: simWorkspace/Blink/test/wave.fst
	@if [ -f $(WAVE_FILE) ]; then \
		gtkwave $(WAVE_FILE) & \
	else \
		echo "Error: Waveform file not found. Run simulation first."; \
	fi
clean:
	rm -f *.json *.config *.bit hw/gen/*.v