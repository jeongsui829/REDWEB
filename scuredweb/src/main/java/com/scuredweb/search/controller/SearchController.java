package com.scuredweb.search.controller;

import com.scuredweb.board.service.BoardService;
import com.scuredweb.common.util.ViewUtil;
import com.scuredweb.common.util.InputValidator;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet("/search")
public class SearchController extends HttpServlet {
    private final BoardService boardService = new BoardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        String keyword = request.getParameter("keyword");
        if (keyword == null) {
            keyword = "";
        }
        if (!InputValidator.hasLength(keyword, 0, 100)) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        request.setAttribute("keyword", keyword.trim());
        request.setAttribute("results", boardService.searchPosts(keyword));
        ViewUtil.forward(request, response, "search/results");
    }
}

