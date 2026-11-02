package com.scuredweb.common.util;

import com.scuredweb.common.constant.ViewPath;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

public final class ViewUtil {
    private ViewUtil() {
    }

    public static void forward(HttpServletRequest request, HttpServletResponse response, String viewName)
            throws ServletException, IOException {
        RequestDispatcher dispatcher = request.getRequestDispatcher(ViewPath.of(viewName));
        dispatcher.forward(request, response);
    }
}


