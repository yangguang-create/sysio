package com;

import java.util.Arrays;

public class QuickSort {
    static void quickSort(int arr[], int L, int R) {
        if (L >= R) {
            return;
        }
        int left = L;
        int right = R;
        int pivot = arr[left];
        while (left < right) {
            while (left < right && arr[right] >= pivot) {
                right--;
            }
            if (left < right) {
                arr[left] = arr[right];
            }
            while (left < right && arr[left] <= pivot) {
                left++;
            }
            if (left < right) {
                arr[right] = arr[left];
            }
            if (left >= right) {
                arr[left] = pivot;
            }
        }
        quickSort(arr, L, right - 1);
        quickSort(arr, right + 1, R);
    }
    public static void main(String[] args) {
        int[] a = {7, 3, 4, 56, 723, 3, 4, 6, 33};
        System.out.println(Arrays.toString(a));
        quickSort(a, 0, a.length - 1);
        System.out.println(Arrays.toString(a));
    }
}
