#!/usr/bin/env python3

import os
import struct
from PIL import Image

def convert_jpeg_to_interleaved_8bp(image_path, output_bin_path, output_pal_path):
    """
    Converts a JPEG image to a 320x240 8-bitplane word-interleaved binary file
    suitable for big-endian 68000/FPGA video pipelines.

    Layout per 16-pixel block:
        Word 0: Plane 0 (Bits 15->0 for pixels 0->15)
        Word 1: Plane 1
        ...
        Word 7: Plane 7

    Palette output:
        1024 bytes total (256 colors * 4 bytes).
        Format per color: 32-bit Big-Endian Longword (0x00RRGGBB)
    """
    # 1. Load image and force resize to target resolution
    print(f"Loading '{image_path}'...")
    img = Image.open(image_path).convert('RGB')
    img = img.resize((320, 240), Image.Resampling.LANCZOS)

    # 2. Quantize down to an adaptive 256-color palette
    print("Quantizing to 256 colors...")
    img_indexed = img.convert('P', palette=Image.Palette.ADAPTIVE, colors=256)

    # 3. Extract and save the palette as 32-bit longwords (xxRRGGBB)
    raw_palette = img_indexed.getpalette()[:768]
    pal_data_32bit = bytearray()

    print("Formatting palette to 32-bit big-endian longwords (xxRRGGBB)...")
    for i in range(0, len(raw_palette), 3):
        r = raw_palette[i]
        g = raw_palette[i+1]
        b = raw_palette[i+2]

        # Combine into a 32-bit integer: 0x00RRGGBB
        # Big-endian packing ('>I') ensures it writes to memory as: [0x00, R, G, B]
        color_longword = (r << 16) | (g << 8) | b
        pal_data_32bit.extend(struct.pack('>I', color_longword))

    with open(output_pal_path, 'wb') as pal_file:
        pal_file.write(pal_data_32bit)
    print(f"Saved 32-bit palette to: {output_pal_path} ({len(pal_data_32bit)} bytes)")

    # 4. Process pixels into interleaved bitplanes
    width, height = img_indexed.size
    pixels = list(img_indexed.getdata())
    video_data = bytearray()

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
                video_data.extend(struct.pack('>H', word_val))

    # 5. Save the raw frame buffer binary
    with open(output_bin_path, 'wb') as bin_file:
        bin_file.write(video_data)

    expected_size = (320 * 240 * 8) // 8
    print(f"Saved raw interleaved video data to: {output_bin_path}")
    print(f"Total size: {len(video_data)} bytes (Expected: {expected_size} bytes)")
    print("Conversion complete!")

if __name__ == "__main__":
    # Example usage configuration
    INPUT_JPEG = "input.jpg"
    OUTPUT_BIN = "screen_320x240_8bpp.bin"
    OUTPUT_PAL = "palette_256.pal"

    if os.path.exists(INPUT_JPEG):
        convert_jpeg_to_interleaved_8bp(INPUT_JPEG, OUTPUT_BIN, OUTPUT_PAL)
    else:
        print(f"Error: '{INPUT_JPEG}' not found. Please place a JPEG file in the same directory or update the path.")