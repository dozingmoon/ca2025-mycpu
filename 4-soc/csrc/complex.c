// SPDX-License-Identifier: MIT
// Simple correlated branch benchmark
// Tests branch predictor's ability to use history correlation

#define OUTER 50
#define INNER 20

// Pattern 1: Two loops with correlated behavior
// Inner loop always follows outer loop in the same way
__attribute__((noinline))
int correlated_loops(void)
{
    int sum = 0;
    for (int i = 0; i < OUTER; i++) {
        // First inner loop
        for (int j = 0; j < INNER; j++) {
            if (j & 1) sum += 1;
            else sum -= 1;
        }
        // Second inner loop (same pattern, different location)
        for (int k = 0; k < INNER; k++) {
            if (k & 1) sum += 2;
            else sum -= 2;
        }
    }
    return sum;
}

// Pattern 2: Sequential if-else chains that create unique history
__attribute__((noinline))
int sequential_branches(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        // Each branch creates a different history pattern
        if (i & 1) sum += 1;
        if (i & 2) sum += 2;
        if (i & 4) sum += 4;
        if (i & 8) sum += 8;
        if ((i & 3) == 0) sum += 10;
        if ((i & 3) == 1) sum += 20;
        if ((i & 3) == 2) sum += 30;
        if ((i & 3) == 3) sum += 40;
    }
    return sum;
}

// Pattern 3: Alternating function calls with internal loops
__attribute__((noinline))
int func_a(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        if (i < n/2) sum += i;
        else sum -= i;
    }
    return sum;
}

__attribute__((noinline))
int func_b(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        if (i >= n/2) sum += i;
        else sum -= i;
    }
    return sum;
}

__attribute__((noinline))
int func_c(int n)
{
    int sum = 0;
    for (int i = 0; i < n; i++) {
        if ((i & 3) < 2) sum += i;
        else sum -= i;
    }
    return sum;
}

int main()
{
    int result = 0;
    
    // Phase 1: Correlated loops
    result += correlated_loops();
    
    // Phase 2: Sequential branches
    for (int i = 0; i < 10; i++) {
        result += sequential_branches(32);
    }
    
    // Phase 3: Interleaved function calls
    // This creates varied global history patterns
    for (int i = 0; i < 30; i++) {
        result += func_a(8);
        result += func_b(8);
        result += func_c(8);
    }
    
    // Store result
    *(volatile int *) (4) = result;
    
    // Signal completion
    *(volatile int *) (0x104) = 0x0F;
    *(volatile int *) (0x100) = 0xCAFEF00D;
    
    return 0;
}
