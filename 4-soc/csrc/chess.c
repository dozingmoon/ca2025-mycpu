// SPDX-License-Identifier: MIT
// Chess move validator - simulates sjeng-like game tree patterns

#define EMPTY  0
#define PAWN   1
#define KNIGHT 2
#define BISHOP 3
#define ROOK   4
#define QUEEN  5
#define KING   6
#define WHITE  8
#define BLACK  16

int board[64] = {
    ROOK|WHITE, KNIGHT|WHITE, BISHOP|WHITE, QUEEN|WHITE, KING|WHITE, BISHOP|WHITE, KNIGHT|WHITE, ROOK|WHITE,
    PAWN|WHITE, PAWN|WHITE, PAWN|WHITE, PAWN|WHITE, PAWN|WHITE, PAWN|WHITE, PAWN|WHITE, PAWN|WHITE,
    EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
    EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
    EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
    EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY, EMPTY,
    PAWN|BLACK, PAWN|BLACK, PAWN|BLACK, PAWN|BLACK, PAWN|BLACK, PAWN|BLACK, PAWN|BLACK, PAWN|BLACK,
    ROOK|BLACK, KNIGHT|BLACK, BISHOP|BLACK, QUEEN|BLACK, KING|BLACK, BISHOP|BLACK, KNIGHT|BLACK, ROOK|BLACK
};

int abs(int x) { return x < 0 ? -x : x; }

int is_valid_move(int from, int to) {
    if (from < 0 || from > 63 || to < 0 || to > 63) return 0;
    if (from == to) return 0;
    
    int piece = board[from] & 7;
    int color = board[from] & 24;
    int target_color = board[to] & 24;
    
    if (piece == EMPTY) return 0;
    if (color == target_color && board[to] != EMPTY) return 0;
    
    int from_row = from / 8, from_col = from % 8;
    int to_row = to / 8, to_col = to % 8;
    int dr = to_row - from_row, dc = to_col - from_col;
    
    if (piece == PAWN) {
        int dir = (color == WHITE) ? 1 : -1;
        if (dc == 0 && dr == dir && board[to] == EMPTY) return 1;
        if (dc == 0 && dr == 2*dir && from_row == (color == WHITE ? 1 : 6) && board[to] == EMPTY) return 1;
        if (abs(dc) == 1 && dr == dir && board[to] != EMPTY) return 1;
        return 0;
    }
    if (piece == KNIGHT) {
        return (abs(dr) == 2 && abs(dc) == 1) || (abs(dr) == 1 && abs(dc) == 2);
    }
    if (piece == BISHOP) {
        if (abs(dr) != abs(dc)) return 0;
        return 1;
    }
    if (piece == ROOK) {
        if (dr != 0 && dc != 0) return 0;
        return 1;
    }
    if (piece == QUEEN) {
        if (dr != 0 && dc != 0 && abs(dr) != abs(dc)) return 0;
        return 1;
    }
    if (piece == KING) {
        return abs(dr) <= 1 && abs(dc) <= 1;
    }
    return 0;
}

int main() {
    int valid_count = 0;
    
    // Check all possible moves on the board (heavy branching)
    for (int iter = 0; iter < 2; iter++) {
        for (int from = 0; from < 64; from++) {
            for (int to = 0; to < 64; to++) {
                if (is_valid_move(from, to)) {
                    valid_count++;
                }
            }
        }
    }
    
    *(int *)(4) = valid_count;
    
    // Expected: some reasonable number of valid moves
    if (valid_count > 0) {
        *(volatile int *)(0x104) = 0x0F;
    } else {
        *(volatile int *)(0x104) = 0x01;
    }
    
    *(volatile int *)(0x100) = 0xCAFEF00D;
    return 0;
}
