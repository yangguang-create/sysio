package com;

import java.util.HashMap;
import java.util.Map;

/**
 * @ClassName Question4
 * @Description TODO
 * @Author yangPei
 * @Date 2022-02-27-14:02
 * @Version 1.0
 */
public class Question4 {

    public static void main(String[] args) {
        System.out.println(new Question4().isPermutation("1234", "4312"));
    }

    public boolean isPermutation(String a, String b) {
        //未判空
        if (a.length() != b.length()) {
            return false;
        }
        char[] ca = a.toCharArray();
        char[] cb = b.toCharArray();
        Map<Character, Integer> cntMap1 = new HashMap<>(16);
        Map<Character, Integer> cntMap2 = new HashMap<>(16);
        //统计每种字符的频率
        for (int i = 0; i < a.length(); i++) {
            cntMap1.put(ca[i], cntMap1.getOrDefault(ca[i], 0) + 1);
            cntMap2.put(cb[i], cntMap2.getOrDefault(cb[i], 0) + 1);
        }
        //判断b是否为a的排列
        for (int i = 0; i < a.length(); i++) {
            if (!cntMap1.get(ca[i]).equals(cntMap2.get(ca[i]))) {
                return false;
            }
        }
        return true;
    }
}
