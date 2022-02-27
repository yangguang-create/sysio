package com;

import java.util.Arrays;

public class Solution {

    public static boolean no_name(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        char[] charA = a.toCharArray();
        char[] charB = b.toCharArray();
        for (int i = 0; i < charB.length; i++) {
            if (charA[0] == charB[i]) {
                return no_name(f1(a, 0), f1(b, i));
            }
        }
        return charB.length == 0;
    }

    public static String f1(String s1, int index) {
        char[] chars = new char[s1.length() - 1];
        int d = 0;
        char[] charArray = s1.toCharArray();
        for (int k = 0; k < charArray.length; k++) {
            if (k == index) {
                d = 1;
            } else {
                chars[k - d] = charArray[k];
            }
        }
        return new String(chars);
    }

    public static String sort(String s) {
        char[] charArray = s.toCharArray();
        Arrays.sort(charArray);
        return new String(charArray);
    }

    public static boolean isAnagram(String s,String t) {
        if (s.length() != t.length()) {
            return false;
        }
        return sort(s).equals(sort(t));
    }

    public static void main(String[] args) {
        String s1 = "abc,rff,123,你好。";
        String s2 = "abc你好。,rff,123x";
        System.out.println(isAnagram(s1, s2));
    }
}
