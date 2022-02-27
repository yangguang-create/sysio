package com;

public class Question3 {
    public static void main(String[] args) {
        int[] nums = new int[]{-2,1,2,3,4,-1,5,-6};
        System.out.println("findMaxSumInSubArray1(nums) = " + findMaxSumInSubArray1(nums));
        System.out.println("findMaxSumInSubArray2(nums) = " + findMaxSumInSubArray2(nums));
        System.out.println(new Question3().maxSubArray(nums));//14
    }

    //o(n^3)
    public static int findMaxSumInSubArray1(int[] arr) {
        int max = Integer.MIN_VALUE;
        for (int i = 0; i < arr.length; i++) {
            for (int j = i; j < arr.length; j++) {
                int sum = 0;
                for (int k = i; k < j; k++) {
                    sum = arr[k] + sum;
                }
                max = Math.max(max, sum);
            }
        }
        return max;
    }

    //dp[i]表示以arr[i]结尾的连续子数组的最大和.
    //如果dp[i-1] > 0 , dp[i] = dp[i-1] + arr[i] 的和才能更大.
    //如果dp[i-1] <= 0 , dp[i] = dp[i-1] + arr[i] 的和会变的更小.此时以i结尾的子数组的和的最大值，就是arr[i].
    public static int findMaxSumInSubArray2(int[] arr) {
        if (arr.length == 0) {
            return 0;
        }
        int max = Integer.MIN_VALUE;
        int[] dp = new int[arr.length];
        dp[0] = arr[0];
        for (int i = 1; i < arr.length; i++) {
            if (dp[i - 1] > 0) {
                dp[i] = dp[i - 1] + arr[i];
            } else {
                dp[i] = arr[i];
            }
            max = Math.max(max, dp[i]);
        }
        return max;
    }

    /**
     * dp的思路是，前面算的不能白算，必须要对后面的值有增益，
     * 也就是以前面的那个数结尾的子数组的和的最大值要为正，否则，
     * 对以后面那个数结尾的子数组的和最大值是没有帮助的。只然后后面的额更小。
     * @param nums
     * @return
     */
    public static int maxSubArray(int[] nums) {
        //动态规划：dp[i] = Math.max(dp[i-1]+nums[i],nums[i]) res = Math.max(res,dp)
        // ===> 可能中间状态存在最大值
        int len = nums.length;
        //int[] dp = new int[len];//dp[i]表示以i为结尾的数组最大连续子数组和
        //dp[0]  = nums[0];
        int res = nums[0];
        int dp = nums[0];
        for (int i = 1; i < len; i++) {
            dp = Math.max(dp+nums[i], nums[i]);
            res = Math.max(res, dp);
        }
        return res;
    }
}