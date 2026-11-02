function updateClock() {
    const clockTargets = document.querySelectorAll("#liveClock");
    const dateTargets = document.querySelectorAll("#liveDate");
    if (clockTargets.length === 0 || dateTargets.length === 0) return;
    const now = new Date();
    const timeText = now.toLocaleTimeString("ko-KR");
    const dateText = now.toLocaleDateString("ko-KR", {
        year: "numeric",
        month: "long",
        day: "numeric",
        weekday: "short"
    });
    clockTargets.forEach((target) => {
        target.textContent = timeText;
    });
    dateTargets.forEach((target) => {
        target.textContent = dateText;
    });
}

updateClock();
setInterval(updateClock, 1000);
