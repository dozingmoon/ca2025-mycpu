// SPDX-License-Identifier: MIT
// Pattern matching - simulates gobmk-like Go game patterns

#define BOARD_SIZE 9
#define EMPTY 0
#define BLACK 1
#define WHITE 2

int board[BOARD_SIZE * BOARD_SIZE];

// Pattern templates (3x3 patterns)
int patterns[][9] = {
    {0,1,0, 1,0,1, 0,1,0},  // Cross pattern
    {1,1,1, 1,0,1, 1,1,1},  // Surrounded
    {0,0,1, 0,1,0, 1,0,0},  // Diagonal
    {1,0,1, 0,0,0, 1,0,1},  // Corners
    {0,1,0, 0,1,0, 0,1,0},  // Vertical line
    {0,0,0, 1,1,1, 0,0,0},  // Horizontal line
};
#define NUM_PATTERNS 6

int match_pattern(int row, int col, int *pattern) {
    if (row < 1 || row > BOARD_SIZE - 2) return 0;
    if (col < 1 || col > BOARD_SIZE - 2) return 0;
    
    int matches = 1;
    for (int dr = -1; dr <= 1; dr++) {
        for (int dc = -1; dc <= 1; dc++) {
            int idx = (row + dr) * BOARD_SIZE + (col + dc);
            int pat_idx = (dr + 1) * 3 + (dc + 1);
            int expected = pattern[pat_idx];
            int actual = board[idx];
            
            // Pattern 0 = don't care, 1 = must match BLACK
            if (expected == 1 && actual != BLACK) {
                matches = 0;
            }
        }
    }
    return matches;
}

void init_board(int seed) {
    for (int i = 0; i < BOARD_SIZE * BOARD_SIZE; i++) {
        // xorshift PRNG (no multiply needed)
        seed ^= seed << 13;
        seed ^= seed >> 17;
        seed ^= seed << 5;
        board[i] = seed & 3;
        if (board[i] == 3) board[i] = 0;
    }
}

int main() {
    int total_matches = 0;
    
    // Multiple board configurations
    for (int config = 0; config < 1; config++) {
        init_board(config * 17 + 42);
        
        // Check all patterns at all positions
        for (int row = 0; row < BOARD_SIZE; row++) {
            for (int col = 0; col < BOARD_SIZE; col++) {
                for (int p = 0; p < NUM_PATTERNS; p++) {
                    if (match_pattern(row, col, patterns[p])) {
                        total_matches++;
                    }
                }
            }
        }
    }
    
    *(int *)(4) = total_matches;
    
    if (total_matches >= 0) {
        *(volatile int *)(0x104) = 0x0F;
    } else {
        *(volatile int *)(0x104) = 0x01;
    }
    
    *(volatile int *)(0x100) = 0xCAFEF00D;
    return 0;
}
