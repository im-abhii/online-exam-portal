// anti-cheat.js - Exam anti-cheating mechanisms

document.addEventListener('DOMContentLoaded', () => {
    
    if (typeof EXAM_DATA === 'undefined') return;

    const attemptId = EXAM_DATA.attemptId;
    let warningCount = EXAM_DATA.warningCount || 0;
    const maxWarnings = EXAM_DATA.maxWarnings || 3;

    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    // Prevent context menu (right click)
    document.addEventListener('contextmenu', e => e.preventDefault());

    // Prevent common keyboard shortcuts
    document.addEventListener('keydown', e => {
        // Prevent F12, Ctrl+Shift+I, Ctrl+Shift+J, Ctrl+U
        if (e.key === 'F12' || 
           (e.ctrlKey && e.shiftKey && (e.key === 'I' || e.key === 'J' || e.key === 'i' || e.key === 'j')) || 
           (e.ctrlKey && (e.key === 'U' || e.key === 'u' || e.key === 'C' || e.key === 'c' || e.key === 'V' || e.key === 'v'))) {
            e.preventDefault();
        }
    });

    // Detect visibility change (tab switch)
    document.addEventListener('visibilitychange', () => {
        if (document.hidden) {
            triggerWarning();
        }
    });

    // Detect window blur
    window.addEventListener('blur', () => {
        // Adding a small timeout to avoid false positives with alerts/modals
        setTimeout(() => {
            if (document.activeElement === document.body || document.activeElement === null) {
                // If it's truly blurred from the window, not just an input within the window
                triggerWarning();
            }
        }, 100);
    });

    function triggerWarning() {
        fetch(`/exam/warning/${attemptId}`, {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        })
        .then(res => res.json())
        .then(data => {
            warningCount = data.warningCount;
            if (data.shouldSubmit) {
                // Auto submit exam
                alert(`Maximum warnings (${maxWarnings}) reached. Your exam is being automatically submitted.`);
                localStorage.removeItem('exam_end_' + attemptId);
                
                fetch(`/exam/auto-submit/${attemptId}`, {
                    method: 'POST',
                    headers: { [csrfHeader]: csrfToken }
                }).then(r => r.json()).then(d => {
                    if (d.success && d.redirectUrl) window.location.href = d.redirectUrl;
                });
            } else {
                // Show warning modal
                const modalEl = document.getElementById('warningModal');
                if (modalEl) {
                    const countEl = document.getElementById('warningCountText');
                    if (countEl) countEl.textContent = `Warning ${warningCount} of ${maxWarnings}`;
                    new bootstrap.Modal(modalEl).show();
                } else {
                    alert(`WARNING: You have switched tabs or windows. This is warning ${warningCount} of ${maxWarnings}. Continued violations will result in automatic submission.`);
                }
            }
        }).catch(err => console.error("Error triggering warning:", err));
    }
});
