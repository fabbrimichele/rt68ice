#!/usr/bin/env python3

import os
import sys
import struct
import argparse
from PIL import Image

def convert_jpeg_to_interleaved_8bp(image_path, output_bin_path, output_pal_path,
                                    img_load_addr=0x100000, pal_load_addr=0x10000):
    """
    Converts a JPEG image to a 320x240 8-bitplane word-interleaved binary file
    and a 32-bit xxRRGGBB palette file. Both files are prefixed with a
    custom 8-byte big-endian header for a 68000 loader.

    Header structure (8 bytes):
        [0:4] -> 32-bit Target Load Address
        [4:8] -> 32-bit Raw Payload Length (excluding header)
    """
    # 1. Load image and force resize to target resolution
    print(f"Loading '{image_path}'...")
    img = Image.open(image_path).convert('RGB')
    img = img.resize((320, 240), Image.Resampling.LANCZOS)

    # 2. Quantize down to an adaptive 256-color palette
    print("Quantizing to 256 colors...")
    img_indexed = img.convert('P', palette=Image.Palette.ADAPTIVE, colors=256)

    # 3. Extract and generate the 32-bit palette payload (xxRRGGBB)
    raw_palette = img_indexed.getpalette()[:768]
    pal_payload = bytearray()

    print("Formatting palette to 32-bit big-endian longwords (xxRRGGBB)...")
    for i in range(0, len(raw_palette), 3):
        r = raw_palette[i]
        g = raw_palette[i+1]
        b = raw_palette[i+2]

        # Combine into a 32-bit integer: 0x00RRGGBB
        color_longword = (r << 16) | (g << 8) | b
        pal_payload.extend(struct.pack('>I', color_longword))

    pal_payload_len = len(pal_payload) # Should be 1024 bytes

    # Prepend 8-byte header to palette
    pal_header = struct.pack('>II', pal_load_addr, pal_payload_len)

    with open(output_pal_path, 'wb') as pal_file:
        pal_file.write(pal_header + pal_payload)

    print(f"Saved palette to: {output_pal_path}")
    print(f"  -> Header: Load Address = 0x{pal_load_addr:08X}, Length = {pal_payload_len} bytes")
    print(f"  -> Total file size (with header): {len(pal_header) + pal_payload_len} bytes")

    # 4. Process pixels into interleaved bitplanes payload
    width, height = img_indexed.size
    pixels = list(img_indexed.getdata())
    img_payload = bytearray()

    print("Processing interleaved bitplanes...")
    for y in range(height):
        row_offset = y * width

        # Process the row in chunks of 16 pixels
        for x_block in range(0, width, 16):
            block_pixels = pixels[row_offset + x_block : row_offset + x_block + 16]

            # Generate 8 interleaved words for this specific 16-pixel block
            for plane in range(8):
                word_val = 0
                for bit_idx in range(16):
                    pixel_color_idx = block_pixels[bit_idx]

                    # Extract the specific bit for the current plane
                    bit = (pixel_color_idx >> plane) & 1

                    # Map to big-endian order: bit_idx 0 is MSB (bit 15), bit_idx 15 is LSB (bit 0)
                    if bit:
                        word_val |= (1 << (15 - bit_idx))

                # Pack as an unsigned 16-bit big-endian integer ('>H')
                img_payload.extend(struct.pack('>H', word_val))

    img_payload_len = len(img_payload) # Should be 76800 bytes

    # Prepend 8-byte header to image binary
    img_header = struct.pack('>II', img_load_addr, img_payload_len)

    with open(output_bin_path, 'wb') as bin_file:
        bin_file.write(img_header + img_payload)

    print(f"Saved video data to: {output_bin_path}")
    print(f"  -> Header: Load Address = 0x{img_load_addr:08X}, Length = {img_payload_len} bytes")
    print(f"  -> Total file size (with header): {len(img_header) + img_payload_len} bytes")
    print("Conversion complete!")


if __name__ == "__main__":
    # Setup argument parser for cleaner CLI handling
    parser = argparse.ArgumentParser(description="Convert a JPEG image to 68000 8bpp interleaved bitplanes.")
    parser.add_argument("input_image", help="Path to the input JPEG image.")
    parser.add_argument("-o", "--outdir", default=".",
                        help="Destination folder for the generated .bin files. (Default: current directory)")

    args = parser.parse_args()

    INPUT_IMAGE = args.input_image
    OUT_DIR = args.outdir

    # Verify the file actually exists
    if not os.path.exists(INPUT_IMAGE):
        print(f"Error: '{INPUT_IMAGE}' not found.")
        sys.exit(1)

    # Ensure the destination directory exists
    os.makedirs(OUT_DIR, exist_ok=True)

    # Extract the base filename (e.g., "kitten" from "folder/kitten.jpeg")
    base_name = os.path.splitext(os.path.basename(INPUT_IMAGE))[0]

    # Construct the dynamic output filenames using the destination directory
    OUTPUT_BIN = os.path.join(OUT_DIR, f"{base_name}_320x240_8bpp.bin")
    OUTPUT_PAL = os.path.join(OUT_DIR, f"{base_name}_palette.bin")

    # Set your custom 68000 target memory locations here if needed
    IMAGE_TARGET_ADDRESS = 0x00100000
    PALETTE_TARGET_ADDRESS = 0x00010000

    convert_jpeg_to_interleaved_8bp(
        INPUT_IMAGE,
        OUTPUT_BIN,
        OUTPUT_PAL,
        img_load_addr=IMAGE_TARGET_ADDRESS,
        pal_load_addr=PALETTE_TARGET_ADDRESS
    )