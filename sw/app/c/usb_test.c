// This shows how to use the mouse dx and dy registers.
// The code is an example and is incomplete.

// Software reading pattern
static uint8_t prev_dx = 0;
static uint8_t prev_dy = 0;

void poll_mouse(void) {
    uint8_t curr_dx = USB1_MOUSE_DX_REG & 0xFF;
    uint8_t curr_dy = USB1_MOUSE_DY_REG & 0xFF;

    // Signed delta calculation naturally handles 8-bit overflow/wrap-around
    int8_t delta_x = (int8_t)(curr_dx - prev_dx);
    int8_t delta_y = (int8_t)(curr_dy - prev_dy);

    prev_dx = curr_dx;
    prev_dy = curr_dy;

    // Apply delta_x and delta_y to mouse cursor coordinates...
}