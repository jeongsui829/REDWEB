// Preserve the target editor's raw HTML submission without the previous toolbar.
document.addEventListener("DOMContentLoaded", () => {
    const form = document.getElementById("postEditForm");
    const editor = document.getElementById("richEditor");
    const content = document.getElementById("postContent");
    form.addEventListener("submit", (event) => {
        const clone = editor.cloneNode(true);
        clone.querySelectorAll(".editor-image").forEach((figure) => {
            figure.replaceWith(document.createTextNode("[[image:" + figure.dataset.imageName + "]]"));
        });
        content.value = clone.innerHTML.trim();
        if (!content.value) {
            event.preventDefault();
            editor.focus();
        }
    });
});
