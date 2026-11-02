document.addEventListener("DOMContentLoaded", () => {
    document.querySelectorAll(".reply-toggle[data-target]").forEach((button) => {
        button.addEventListener("click", () => {
            const form = document.getElementById(button.dataset.target);
            if (!form) return;
            form.hidden = !form.hidden;
            button.textContent = form.hidden ? "답글 열기" : "답글 닫기";
            if (!form.hidden) form.querySelector("textarea")?.focus();
        });
    });
});
