package com;

import java.util.Arrays;

public class Test2 {

    public static void f1(int[] arr, int b, int c) {
        int temp = arr[b] + arr[c];
        arr[b] = temp - arr[b];
        arr[c] = temp - arr[c];
    }

    public static void f2(int[] arr, int b, int c) {
        if (b >= c) {
            return;
        }
        int temp1 = b;//low
        int temp2 = c;//high
        while (true) {
            while (temp1 <= c && arr[temp1] < arr[b]) {
                temp1 = temp1 + 1;
            }

            while (temp2 >= b && arr[temp2] > arr[b]) {
                temp2 = temp2 - 1;
            }
            if (temp1 < temp2) {
                f1(arr, temp1, temp2);
            } else {
                break;
            }
        }
        //1:补全代码1
        arr[b] = arr[temp1];
        //2：补全代码2
        f2(arr, b, temp2 - 1);
        //3：补全代码3
        f2(arr, temp2 + 1, c);
    }

    public static void main(String[] args) {
        int[] a = {7, 3, 4, 56, 723, 3, 4, 6, 33};
        System.out.println(Arrays.toString(a));
        f2(a, 0, a.length - 1);
        System.out.println(Arrays.toString(a));
    }

}
