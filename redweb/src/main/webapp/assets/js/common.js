document.addEventListener("DOMContentLoaded", () => {
    const searchShell = document.querySelector(".header-search-shell");
    const bellShell = document.querySelector(".header-bell-shell");
    const searchToggle = document.querySelector(".search-toggle");
    const inlineSearchInput = document.querySelector('.header-search-inline input[name="keyword"]');
    const bellToggle = document.querySelector(".bell-toggle");
    const contextMenu = document.getElementById("userContextMenu");
    const profileLink = document.getElementById("contextProfileLink");
    const messageLink = document.getElementById("contextMessageLink");
    const reportLink = document.getElementById("contextReportLink");
    const header = document.querySelector(".site-header");
    const contextPath = header?.dataset.contextPath || "";
    const loggedIn = header?.dataset.loggedIn === "true";
    const adminUser = header?.dataset.admin === "true";
    const myNickname = (header?.dataset.nickname || "").trim().toLowerCase();
    const detailPage = document.body.classList.contains("board-detail-page");

    const boardMenuToggle = document.querySelector("[data-board-menu-toggle]");
    const boardMenu = boardMenuToggle
        ? document.getElementById(boardMenuToggle.getAttribute("aria-controls"))
        : null;

    boardMenuToggle?.addEventListener("click", () => {
        if (!boardMenu) {
            return;
        }
        const expanded = boardMenuToggle.getAttribute("aria-expanded") === "true";
        boardMenuToggle.setAttribute("aria-expanded", String(!expanded));
        boardMenu.hidden = expanded;
    });

    document.querySelectorAll("form[data-confirm]").forEach((form) => {
        form.addEventListener("submit", (event) => {
            if (!window.confirm(form.dataset.confirm || "진행하시겠습니까?")) {
                event.preventDefault();
            }
        });
    });

    if (contextMenu) {
        contextMenu.hidden = true;
    }

    searchToggle?.addEventListener("click", (event) => {
        event.stopPropagation();
        searchShell?.classList.toggle("open");
        bellShell?.classList.remove("open");
        bellToggle?.setAttribute("aria-expanded", "false");
        if (searchShell?.classList.contains("open")) {
            searchShell.querySelector("input")?.focus();
        }
    });

    bellToggle?.addEventListener("click", (event) => {
        event.stopPropagation();
        bellShell?.classList.toggle("open");
        bellToggle.setAttribute("aria-expanded", String(bellShell?.classList.contains("open")));
        searchShell?.classList.remove("open");
        if (bellShell?.classList.contains("open")) {
            const readUrl = bellShell.dataset.readUrl;
            if (readUrl) {
                fetch(readUrl, {
                    method: "POST",
                    headers: {
                        "X-Requested-With": "XMLHttpRequest"
                    }
                }).finally(() => {
                    bellShell.querySelector(".icon-badge")?.remove();
                });
            }
        }
    });

    if (detailPage) {
        document.querySelectorAll(".user-menu-trigger").forEach((trigger) => {
            const openUserMenu = (event) => {
                event.preventDefault();
                const nickname = trigger.dataset.user;
                if (!nickname || !contextMenu || !profileLink || !messageLink || !reportLink) {
                    return;
                }

                profileLink.href = `${contextPath}/profile?nickname=${encodeURIComponent(nickname)}`;
                messageLink.href = `${contextPath}/messages/compose?to=${encodeURIComponent(nickname)}`;
                reportLink.href = `${contextPath}/report/user?nickname=${encodeURIComponent(nickname)}`;

                const targetIsMe = loggedIn && myNickname && nickname.trim().toLowerCase() === myNickname;
                messageLink.hidden = targetIsMe;
                messageLink.classList.toggle("disabled", !loggedIn || targetIsMe);
                reportLink.hidden = adminUser;
                reportLink.hidden = adminUser || targetIsMe;
                reportLink.classList.toggle("disabled", !loggedIn || adminUser || targetIsMe);

                contextMenu.hidden = false;
                contextMenu.style.left = `${event.clientX}px`;
                contextMenu.style.top = `${event.clientY}px`;
            };

            trigger.addEventListener("contextmenu", openUserMenu);
            trigger.addEventListener("click", openUserMenu);
        });
    }

    document.addEventListener("click", () => {
        searchShell?.classList.remove("open");
        bellShell?.classList.remove("open");
        bellToggle?.setAttribute("aria-expanded", "false");
        if (contextMenu) {
            contextMenu.hidden = true;
        }
    });

    contextMenu?.addEventListener("click", (event) => {
        event.stopPropagation();
    });

    document.addEventListener("scroll", () => {
        if (contextMenu) {
            contextMenu.hidden = true;
        }
    }, true);

    window.addEventListener("resize", () => {
        if (contextMenu) {
            contextMenu.hidden = true;
        }
    });

    document.addEventListener("keydown", (event) => {
        const target = event.target;
        const isEditing = target instanceof HTMLElement
            && (target.isContentEditable || /^(INPUT|TEXTAREA|SELECT)$/.test(target.tagName));
        if (event.key === "/" && !event.ctrlKey && !event.metaKey && !event.altKey && !isEditing && inlineSearchInput) {
            event.preventDefault();
            inlineSearchInput.focus();
            inlineSearchInput.select();
            return;
        }

        if (event.key === "Escape") {
            searchShell?.classList.remove("open");
            bellShell?.classList.remove("open");
            bellToggle?.setAttribute("aria-expanded", "false");
            if (contextMenu) {
                contextMenu.hidden = true;
            }
        }
    });

    searchShell?.addEventListener("click", (event) => {
        event.stopPropagation();
    });

    bellShell?.addEventListener("click", (event) => {
        event.stopPropagation();
    });
    const onlineWidgets = Array.from(document.querySelectorAll("[data-online-widget]"));
    const setOnlineText = (widget, selector, value) => {
        widget.querySelectorAll(selector).forEach((target) => {
            target.textContent = value;
        });
    };
    const replaceOnlineUserList = (widget, users, fallbackText) => {
        widget.querySelectorAll("[data-online-users]").forEach((container) => {
            if (users.length === 0) {
                const empty = document.createElement("span");
                empty.className = "online-empty";
                empty.textContent = fallbackText;
                container.replaceChildren(empty);
                return;
            }
            const items = users.map((user) => {
                const item = document.createElement("span");
                item.className = "online-user";
                item.textContent = `@${user.name}`;
                const badge = document.createElement("em");
                badge.textContent = "USER";
                item.append(" ", badge);
                return item;
            });
            container.replaceChildren(...items);
        });
    };
    const renderOnlineUsers = (data) => {
        const activeSessionCount = Number(data.activeSessionCount ?? data.count ?? 0);
        const authenticatedSessionCount = Number(data.authenticatedSessionCount ?? 0);
        const guestSessionCount = Number(data.guestSessionCount ?? 0);
        const users = Array.isArray(data.users)
            ? data.users.filter((user) => user?.authenticated === true && typeof user.name === "string" && user.name.trim())
            : [];
        const userListVisible = data.userListVisible === true;

        onlineWidgets.forEach((widget) => {
            setOnlineText(widget, "[data-online-count]", String(activeSessionCount));
            setOnlineText(widget, "[data-authenticated-session-count]", String(authenticatedSessionCount));
            setOnlineText(widget, "[data-guest-session-count]", String(guestSessionCount));
            setOnlineText(widget, "[data-online-guest-summary]", `게스트 ${guestSessionCount}세션`);
            replaceOnlineUserList(widget, users,
                userListVisible ? "접속 중인 로그인 회원이 없습니다." : "로그인 후 회원 목록을 확인할 수 있습니다.");
        });
    };
    const renderOnlineError = () => {
        onlineWidgets.forEach((widget) => {
            setOnlineText(widget, "[data-online-count]", "--");
            setOnlineText(widget, "[data-authenticated-session-count]", "--");
            setOnlineText(widget, "[data-guest-session-count]", "--");
            setOnlineText(widget, "[data-online-guest-summary]", "게스트 --세션");
            replaceOnlineUserList(widget, [], "접속 정보를 불러올 수 없습니다.");
        });
    };
    const refreshOnlineUsers = () => {
        if (onlineWidgets.length === 0) {
            return;
        }
        fetch(`${contextPath}/api/online-users`, {
            cache: "no-store",
            headers: { "Accept": "application/json" }
        })
            .then((response) => response.ok ? response.json() : Promise.reject())
            .then(renderOnlineUsers)
            .catch(renderOnlineError);
    };
    if (onlineWidgets.length > 0) {
        refreshOnlineUsers();
        if (window.__redwebOnlineHeartbeatIntervalId == null) {
            window.__redwebOnlineHeartbeatIntervalId = window.setInterval(refreshOnlineUsers, 15000);
        }
    }
});
