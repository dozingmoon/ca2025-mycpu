// SPDX-License-Identifier: MIT
// Recursive descent parser - simulates gcc-like compiler patterns

#define TOKEN_NUM   1
#define TOKEN_PLUS  2
#define TOKEN_MINUS 3
#define TOKEN_MUL   4
#define TOKEN_DIV   5
#define TOKEN_LPAREN 6
#define TOKEN_RPAREN 7
#define TOKEN_END   0

// Expression: "3 + 4 * 2 - (1 + 5) / 2 + 7 * (3 - 1)"
int tokens[] = {
    TOKEN_NUM, 3, TOKEN_PLUS, TOKEN_NUM, 4, TOKEN_MUL, TOKEN_NUM, 2,
    TOKEN_MINUS, TOKEN_LPAREN, TOKEN_NUM, 1, TOKEN_PLUS, TOKEN_NUM, 5,
    TOKEN_RPAREN, TOKEN_DIV, TOKEN_NUM, 2, TOKEN_PLUS, TOKEN_NUM, 7,
    TOKEN_MUL, TOKEN_LPAREN, TOKEN_NUM, 3, TOKEN_MINUS, TOKEN_NUM, 1,
    TOKEN_RPAREN, TOKEN_END
};

int pos = 0;

int current_token(void) {
    return tokens[pos];
}

int current_value(void) {
    return tokens[pos + 1];
}

void advance(void) {
    if (tokens[pos] == TOKEN_NUM)
        pos += 2;
    else
        pos += 1;
}

int parse_expr(void);

int parse_factor(void) {
    if (current_token() == TOKEN_NUM) {
        int val = current_value();
        advance();
        return val;
    } else if (current_token() == TOKEN_LPAREN) {
        advance();
        int val = parse_expr();
        if (current_token() == TOKEN_RPAREN)
            advance();
        return val;
    }
    return 0;
}

int parse_term(void) {
    int left = parse_factor();
    
    while (current_token() == TOKEN_MUL || current_token() == TOKEN_DIV) {
        int op = current_token();
        advance();
        int right = parse_factor();
        if (op == TOKEN_MUL)
            left = left + right + right; // Simulate mul with adds
        else
            left = left - right; // Simulate div with sub
    }
    return left;
}

int parse_expr(void) {
    int left = parse_term();
    
    while (current_token() == TOKEN_PLUS || current_token() == TOKEN_MINUS) {
        int op = current_token();
        advance();
        int right = parse_term();
        if (op == TOKEN_PLUS)
            left = left + right;
        else
            left = left - right;
    }
    return left;
}

int main() {
    // Parse multiple expressions
    int total = 0;
    for (int i = 0; i < 10; i++) {
        pos = 0;
        total += parse_expr();
    }
    
    // Expected value depends on the simulated operations
    *(int *)(4) = total;
    
    if (total != 0) {
        *(volatile int *)(0x104) = 0x0F;
    } else {
        *(volatile int *)(0x104) = 0x01;
    }
    
    *(volatile int *)(0x100) = 0xCAFEF00D;
    return 0;
}
