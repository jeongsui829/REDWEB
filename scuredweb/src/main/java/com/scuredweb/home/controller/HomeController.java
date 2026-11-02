package com.scuredweb.home.controller;

import com.scuredweb.board.service.BoardService;
import com.scuredweb.common.exception.DataAccessException;
import com.scuredweb.common.util.ViewUtil;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.List;

@WebServlet(urlPatterns = {"", "/home"})
public class HomeController extends HttpServlet {
    private final BoardService boardService = new BoardService();

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        try {
            request.setAttribute("latestPosts", boardService.getLatestPosts());
        } catch (DataAccessException exception) {
            request.setAttribute("latestPosts", List.of());
            request.setAttribute("latestPostsLoadError", true);
        }
        try {
            request.setAttribute("trendingPosts", boardService.getTrendingPosts());
        } catch (DataAccessException exception) {
            request.setAttribute("trendingPosts", List.of());
            request.setAttribute("trendingPostsLoadError", true);
        }
        try {
            request.setAttribute("boards", boardService.getBoards());
        } catch (DataAccessException exception) {
            request.setAttribute("boards", List.of());
            request.setAttribute("boardsLoadError", true);
        }
        try {
            request.setAttribute("popularTags", boardService.getPopularTags());
        } catch (DataAccessException exception) {
            request.setAttribute("popularTags", List.of());
            request.setAttribute("popularTagsLoadError", true);
        }
        ViewUtil.forward(request, response, "home/index");
    }
}


