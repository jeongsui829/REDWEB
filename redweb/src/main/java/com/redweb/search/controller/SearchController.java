package com.redweb.search.controller;

import com.redweb.board.service.BoardService;
import com.redweb.common.util.ViewUtil;
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

        request.setAttribute("keyword", keyword.trim());
        request.setAttribute("results", boardService.searchPosts(keyword));
        ViewUtil.forward(request, response, "search/results");
    }
}
