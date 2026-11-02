package com.scuredweb.error.controller;

import com.scuredweb.common.util.ViewUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/errors/404", "/errors/500"})
public class ErrorController extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String view = "/errors/500".equals(request.getServletPath()) ? "error/500" : "error/404";
        Object errorStatus = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        int status = errorStatus instanceof Integer
                ? (Integer) errorStatus
                : ("/errors/500".equals(request.getServletPath())
                ? HttpServletResponse.SC_INTERNAL_SERVER_ERROR
                : HttpServletResponse.SC_NOT_FOUND);
        response.setStatus(status);
        ViewUtil.forward(request, response, view);
    }
}

