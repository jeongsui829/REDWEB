package com.redweb.home.controller;

import com.redweb.board.service.BoardService;
import com.redweb.common.util.ViewUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

@WebServlet(urlPatterns = {"/", "/home"})
public class HomeController extends HttpServlet {
    private final BoardService boardService = new BoardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        request.setAttribute("latestPosts", boardService.getLatestPosts());
        request.setAttribute("trendingPosts", boardService.getTrendingPosts());
        request.setAttribute("boards", boardService.getBoards());
        request.setAttribute("popularTags", boardService.getPopularTags());
        ViewUtil.forward(request, response, "home/index");
    }
}

