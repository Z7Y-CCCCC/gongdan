package com.heihu.workorder.util;

public final class InventoryQuantities {
    private InventoryQuantities() { }

    public static int add(int current, int increment) {
        long result = (long) current + increment;
        if (result < 0 || result > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("库存数量超出系统支持范围");
        }
        return (int) result;
    }
}
