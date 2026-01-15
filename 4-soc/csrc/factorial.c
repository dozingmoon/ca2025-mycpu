// SPDX-License-Identifier: MIT
// MyCPU is freely redistributable under the MIT License. See the file
// "LICENSE" for information on usage and redistribution of this file.

// Software multiplication for RV32I
int mul(int a, int b) {
    int result = 0;
    while (b > 0) {
        if (b & 1) result += a;
        a <<= 1;
        b >>= 1;
    }
    return result;
}

int factorial(int n)
{
    int i;
    int result = 1;
    for (i = 2; i <= n; i++) {
        result = mul(result, i);
    }
    return result;
}

int main()
{
    *(int *) (4) = factorial(5); // Calculate 5! = 120
    *(volatile int *) (0x104) = 0x0F; // Signal success (UART_TEST_PASS)
    *(volatile int *) (0x100) = 0xCAFEF00D; // Signal completion to sim.cpp
}
