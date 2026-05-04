package com.xduo.springbootinit.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * SQL 工具

 */
public class SqlUtils {

    /**
     * 校验排序字段是否合法（防止 SQL 注入）
     *
     * @param sortField
     * @return
     */
    public static boolean validSortField(String sortField) {
        if (StringUtils.isBlank(sortField)) {
            return false;
        }
        String normalizedSortField = StringUtils.trim(sortField);
        if (StringUtils.equalsAnyIgnoreCase(normalizedSortField, "undefined", "null")) {
            return false;
        }
        if (StringUtils.containsAny(normalizedSortField, "=", "(", ")", " ")) {
            return false;
        }
        return normalizedSortField.matches("^[A-Za-z0-9_]+$");
    }
}
