package com.scuredweb.common.constant;

public final class ViewPath {
    public static final String PREFIX = "/WEB-INF/views/";
    public static final String SUFFIX = ".jsp";

    private ViewPath() {
    }

    public static String of(String viewName) {
        return PREFIX + viewName + SUFFIX;
    }
}


