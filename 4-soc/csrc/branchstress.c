// SPDX-License-Identifier: MIT
// branchstress.c - Comprehensive Branch Predictor Benchmark
//
// This benchmark stresses various aspects of branch prediction:
// 1. Correlated branches (outcome depends on prior branches)
// 2. Data-dependent unpredictable branches (pseudo-random)
// 3. Nested loops with varying trip counts
// 4. Indirect function calls via function pointers
// 5. Alternating patterns (TNTN...)
// 6. Bimodal patterns (mostly taken with occasional flips)
// 7. History-length stress (long correlated sequences)

#define ITERATIONS 20

// Simple LFSR for pseudo-random numbers (no hardware multiply needed)
static unsigned int lfsr = 0xACE1u;
static unsigned int lfsr_next(void) {
    unsigned int bit = ((lfsr >> 0) ^ (lfsr >> 2) ^ (lfsr >> 3) ^ (lfsr >> 5)) & 1u;
    lfsr = (lfsr >> 1) | (bit << 15);
    return lfsr;
}

// ============================================================================
// Pattern 1: Correlated branches - outcome depends on prior branches
// A global-history predictor should learn these correlations
// ============================================================================
__attribute__((noinline))
int correlated_branches(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        int a = (i & 1);      // Branch A: alternating
        int b = (i & 2) >> 1; // Branch B: period 4
        int c = (i & 4) >> 2; // Branch C: period 8
        
        // This branch depends on A, B, C outcomes
        // Pattern: if (A XOR B) AND C
        if ((a ^ b) && c) {
            sum += 10;
        } else {
            sum -= 5;
        }
        
        // Another correlated branch: if A AND (NOT B)
        if (a && !b) {
            sum += 3;
        } else {
            sum -= 1;
        }
    }
    return sum;
}

// ============================================================================
// Pattern 2: Data-dependent unpredictable branches (pseudo-random)
// These should be hard for any predictor (~50% accuracy expected)
// ============================================================================
__attribute__((noinline))
int random_branches(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        unsigned int r = lfsr_next();
        
        // Bit 0 of LFSR is pseudo-random
        if (r & 1) {
            sum += 1;
        } else {
            sum -= 1;
        }
        
        // Bit 3 of LFSR
        if (r & 8) {
            sum += 2;
        }
    }
    return sum;
}

// ============================================================================
// Pattern 3: Nested loops with varying trip counts
// Tests loop predictor or BTB's ability to handle multiple loop exits
// ============================================================================
__attribute__((noinline))
int nested_loops(int outer, int inner_base) {
    int sum = 0;
    for (int i = 0; i < outer; i++) {
        // Inner loop trip count varies with outer index
        int inner_limit = inner_base + (i & 3);
        for (int j = 0; j < inner_limit; j++) {
            sum += j;
            
            // Early exit on specific condition
            if (j == inner_base && (i & 1)) {
                break;
            }
        }
    }
    return sum;
}

// ============================================================================
// Pattern 4: Indirect function calls via function pointers
// Tests indirect branch target buffer (IBTB)
// ============================================================================
typedef int (*func_ptr_t)(int);

__attribute__((noinline))
int func_add(int x) { return x + 1; }

__attribute__((noinline))
int func_sub(int x) { return x - 1; }

__attribute__((noinline))
int func_mul2(int x) { return x + x; }

__attribute__((noinline))
int func_div2(int x) { return x >> 1; }

__attribute__((noinline))
int indirect_calls(int n) {
    // Array of function pointers
    func_ptr_t funcs[4] = { func_add, func_sub, func_mul2, func_div2 };
    
    int result = 100;
    for (int i = 0; i < n; i++) {
        // Select function based on iteration (predictable pattern)
        int idx = i & 3;
        result = funcs[idx](result);
    }
    return result;
}

// ============================================================================
// Pattern 5: Alternating pattern (TNTNTN...)
// Simple but requires predictor to learn 2-cycle pattern
// ============================================================================
__attribute__((noinline))
int alternating_pattern(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        if (i & 1) {  // T, N, T, N, T, N, ...
            sum += 1;
        } else {
            sum -= 1;
        }
    }
    return sum;
}

// ============================================================================
// Pattern 6: Bimodal pattern (mostly taken with occasional not-taken)
// Tests 2-bit counter hysteresis
// ============================================================================
__attribute__((noinline))
int bimodal_pattern(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        // Taken 7 out of 8 times
        if ((i & 7) != 7) {
            sum += 1;
        } else {
            sum -= 100; // Occasional penalty
        }
    }
    return sum;
}

// ============================================================================
// Pattern 7: Long history correlation
// Tests predictor's ability to use long history (perceptron advantage)
// ============================================================================
__attribute__((noinline))
int long_history_pattern(int n) {
    int sum = 0;
    int history = 0; // 8-bit local history
    
    for (int i = 0; i < n; i++) {
        // Current branch depends on bits 0, 2, 5 of 8-cycle history
        int h0 = (history >> 0) & 1;
        int h2 = (history >> 2) & 1;
        int h5 = (history >> 5) & 1;
        
        int taken = h0 ^ h2 ^ h5;
        
        if (taken) {
            sum += 1;
        } else {
            sum -= 1;
        }
        
        // Update history (shift in current outcome)
        history = ((history << 1) | taken) & 0xFF;
    }
    return sum;
}

// ============================================================================
// Pattern 8: Switch-case (multiple indirect branches)
// Stresses BTB with many branch targets from same PC
// ============================================================================
__attribute__((noinline))
int switch_pattern(int n) {
    int sum = 0;
    for (int i = 0; i < n; i++) {
        int val = i & 7;
        switch (val) {
            case 0: sum += 1; break;
            case 1: sum += 2; break;
            case 2: sum += 3; break;
            case 3: sum += 5; break;
            case 4: sum += 8; break;
            case 5: sum += 13; break;
            case 6: sum += 21; break;
            case 7: sum += 34; break;
        }
    }
    return sum;
}

// ============================================================================
// Main: Run all patterns
// ============================================================================
int main(void) {
    int result = 0;
    
    // Reset LFSR to known state for reproducibility
    lfsr = 0xACE1u;
    
    // Phase 1: Correlated branches (global history helps)
    for (int i = 0; i < ITERATIONS; i++) {
        result += correlated_branches(64);
    }
    
    // Phase 2: Random branches (should be ~50% accuracy)
    for (int i = 0; i < ITERATIONS; i++) {
        result += random_branches(64);
    }
    
    // Phase 3: Nested loops (varying trip counts)
    for (int i = 0; i < ITERATIONS; i++) {
        result += nested_loops(16, 8);
    }
    
    // Phase 4: Indirect function calls
    for (int i = 0; i < ITERATIONS; i++) {
        result += indirect_calls(32);
    }
    
    // Phase 5: Alternating pattern
    for (int i = 0; i < ITERATIONS; i++) {
        result += alternating_pattern(128);
    }
    
    // Phase 6: Bimodal pattern
    for (int i = 0; i < ITERATIONS; i++) {
        result += bimodal_pattern(128);
    }
    
    // Phase 7: Long history
    for (int i = 0; i < ITERATIONS; i++) {
        result += long_history_pattern(128);
    }
    
    // Phase 8: Switch-case
    for (int i = 0; i < ITERATIONS; i++) {
        result += switch_pattern(64);
    }
    
    // Store result (prevent optimization)
    *(volatile int *)(4) = result;
    
    // Signal completion
    *(volatile int *)(0x104) = 0x0F;
    *(volatile int *)(0x100) = 0xCAFEF00D;
    
    return 0;
}
