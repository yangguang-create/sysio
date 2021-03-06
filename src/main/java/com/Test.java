package com;

import java.util.Arrays;

public class Test {

    public static void f1(int[] arr, int b, int c) {
        int temp = arr[b] + arr[c];
        arr[b] = temp - arr[b];
        arr[c] = temp - arr[c];
    }

    public static void f2(int[] arr, int b, int c) {
        if (b >= c) {
            return;
        }
        int temp1 = b;
        int temp2 = c;
        while (true) {
            while (temp2 > temp1 && arr[temp2] > arr[b]) {
                temp2 = temp2 - 1;
            }
            while (temp1 < temp2 && arr[temp1] <= arr[b]) {
                temp1 = temp1 + 1;
            }
            if (temp1 < temp2) {
                f1(arr, temp1, temp2);
            } else {
                break;
            }
        }
        //1:补全代码1
        f1(arr, b, temp1);
        //2：补全代码2
        f2(arr, b, temp1 - 1);
        //3：补全代码3
        f2(arr, temp1 + 1, c);
    }

    public static void main(String[] args) {
        int[] a = {7, 3, 4, 56, 723, 3, 4, 6, 33};
        System.out.println(Arrays.toString(a));
        f2(a, 0, a.length - 1);
        System.out.println(Arrays.toString(a));
    }

}
