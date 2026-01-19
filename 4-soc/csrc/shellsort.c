// SPDX-License-Identifier: MIT
// Shell Sort benchmark - efficient O(n log n) iterative sort
// Used as alternative to Quicksort to avoid recursion stack issues

#ifndef SIZE
#define SIZE 50
#endif

#include "bubblesort_data.h"

void shellsort(int arr[], int n) {
    // Start with a big gap, then reduce the gap
    for (int gap = n/2; gap > 0; gap /= 2) {
        // Do a gapped insertion sort for this gap size.
        // The first gap elements a[0..gap-1] are already in gapped order
        // keep adding one more element until the entire array is
        // gap sorted 
        for (int i = gap; i < n; i += 1) {
            // add a[i] to the elements that have been gap sorted
            // save a[i] in temp and make a hole at position i
            int temp = arr[i];
            
            // shift earlier gap-sorted elements up until the correct 
            // location for a[i] is found
            int j;
            for (j = i; j >= gap && arr[j - gap] > temp; j -= gap)
                arr[j] = arr[j - gap];
            
            // put temp (the original a[i]) in its correct location
            arr[j] = temp;
        }
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
    shellsort(data, SIZE);
    
    if (verify(data, SIZE)) {
        *(volatile int *)(0x104) = 0x0F;
    } else {
        *(volatile int *)(0x104) = 0x01;
    }
    
    *(volatile int *)(0x100) = 0xCAFEF00D;
    return 0;
}
