// SPDX-License-Identifier: MIT
// Simplified sorting benchmark for debugging

#ifndef SIZE
#define SIZE 20
#endif

#include "bubblesort_data.h"

// Simple insertion sort - no recursion, simpler logic
void insertionsort(int arr[], int n) {
    for (int i = 1; i < n; i++) {
        int key = arr[i];
        int j = i - 1;
        
        while (j >= 0 && arr[j] > key) {
            arr[j + 1] = arr[j];
            j--;
        }
        arr[j + 1] = key;
    }
}

int verify(int *arr, int n) {
    for (int i = 0; i < n - 1; i++) {
        if (arr[i] > arr[i + 1])
            return 0;
    }
    return 1;
}

int main() {
    insertionsort(data, SIZE);
    
    if (verify(data, SIZE)) {
        *(volatile int *)(0x104) = 0x0F;
    } else {
        *(volatile int *)(0x104) = 0x01;
    }
    
    *(volatile int *)(0x100) = 0xCAFEF00D;
    return 0;
}
