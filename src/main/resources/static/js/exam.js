// exam.js - Core engine for the online examination window

document.addEventListener('DOMContentLoaded', () => {
    // State variables
    let currentIndex = 0;
    let questions = EXAM_DATA.questions || [];
    let answers = EXAM_DATA.answers || {};
    let totalQuestions = questions.length;
    let remainingSeconds = EXAM_DATA.remainingSeconds || 0;
    let attemptId = EXAM_DATA.attemptId;
    let timerInterval = null;

    // DOM Elements
    const elements = {
        questionNumber: document.getElementById('questionNumber'),
        questionMarks: document.getElementById('questionMarks'),
        questionText: document.getElementById('questionText'),
        optionsContainer: document.getElementById('optionsContainer'),
        paletteContainer: document.getElementById('paletteContainer'),
        btnPrev: document.getElementById('btnPrev'),
        btnNext: document.getElementById('btnNext'),
        btnReview: document.getElementById('btnReview'),
        btnClear: document.getElementById('btnClear'),
        btnSubmit: document.getElementById('btnSubmit'),
        timerDisplay: document.getElementById('timerDisplay'),
        answeredCount: document.getElementById('answeredCount'),
        unansweredCount: document.getElementById('unansweredCount'),
        reviewCount: document.getElementById('reviewCount'),
        confirmSubmitBtn: document.getElementById('confirmSubmitBtn')
    };

    // CSRF Tokens for AJAX
    const csrfToken = document.querySelector('meta[name="_csrf"]').getAttribute('content');
    const csrfHeader = document.querySelector('meta[name="_csrf_header"]').getAttribute('content');

    // Initialize Exam
    initExam();

    function initExam() {
        if (totalQuestions === 0) return;

        // Ensure answers state object exists
        questions.forEach(q => {
            if (!answers[q.id]) {
                answers[q.id] = { selectedOption: null, markedForReview: false, visited: false };
            }
        });

        // Setup Timer using localStorage for persistence
        setupTimer();

        // Build Question Palette
        buildPalette();

        // Render first question
        renderQuestion(0);

        // Attach Event Listeners
        attachEventListeners();
    }

    function setupTimer() {
        const storageKey = 'exam_end_' + attemptId;
        let endTimeStr = localStorage.getItem(storageKey);
        let endTime;

        if (endTimeStr) {
            endTime = parseInt(endTimeStr, 10);
            remainingSeconds = Math.max(0, Math.floor((endTime - Date.now()) / 1000));
        } else {
            endTime = Date.now() + (remainingSeconds * 1000);
            localStorage.setItem(storageKey, endTime.toString());
        }

        updateTimerDisplay();
        timerInterval = setInterval(() => {
            remainingSeconds--;
            updateTimerDisplay();

            if (remainingSeconds <= 0) {
                clearInterval(timerInterval);
                autoSubmitExam();
            }
        }, 1000);
    }

    function updateTimerDisplay() {
        if (remainingSeconds <= 0) {
            elements.timerDisplay.textContent = "00:00";
            return;
        }

        const m = Math.floor(remainingSeconds / 60);
        const s = remainingSeconds % 60;
        elements.timerDisplay.textContent = 
            (m < 10 ? '0' : '') + m + ':' + 
            (s < 10 ? '0' : '') + s;

        if (remainingSeconds < 300) { // < 5 mins
            elements.timerDisplay.classList.add('danger');
        }
    }

    function buildPalette() {
        elements.paletteContainer.innerHTML = '';
        questions.forEach((q, index) => {
            const btn = document.createElement('button');
            btn.className = 'palette-btn';
            btn.textContent = index + 1;
            btn.onclick = () => navigateTo(index);
            elements.paletteContainer.appendChild(btn);
        });
        updatePaletteColors();
    }

    function updatePaletteColors() {
        const buttons = elements.paletteContainer.children;
        for (let i = 0; i < totalQuestions; i++) {
            const qId = questions[i].id;
            const ans = answers[qId];
            const btn = buttons[i];
            
            btn.className = 'palette-btn'; // reset

            if (ans.markedForReview) {
                btn.classList.add('marked-review');
            } else if (ans.selectedOption) {
                btn.classList.add('answered');
            } else if (ans.visited) {
                btn.classList.add('not-answered');
            } else {
                btn.classList.add('not-visited');
            }

            if (i === currentIndex) {
                btn.classList.add('current');
            }
        }
    }

    function renderQuestion(index) {
        currentIndex = index;
        const q = questions[currentIndex];
        const ans = answers[q.id];

        // Mark as visited
        ans.visited = true;

        // Update UI Text
        elements.questionNumber.textContent = `Question ${currentIndex + 1} of ${totalQuestions}`;
        elements.questionMarks.textContent = `Marks: ${q.marks}`;
        elements.questionText.textContent = q.text;

        // Render Options
        elements.optionsContainer.innerHTML = '';
        q.options.forEach(opt => {
            const card = document.createElement('div');
            card.className = 'option-card';
            if (ans.selectedOption === opt.label) {
                card.classList.add('selected');
            }

            const labelDiv = document.createElement('div');
            labelDiv.className = 'option-label';
            labelDiv.textContent = opt.label;

            const textDiv = document.createElement('div');
            textDiv.className = 'option-text';
            textDiv.textContent = opt.text;

            card.appendChild(labelDiv);
            card.appendChild(textDiv);

            card.onclick = () => selectOption(opt.label, opt.key);
            elements.optionsContainer.appendChild(card);
        });

        // Update Buttons
        elements.btnPrev.disabled = (currentIndex === 0);
        
        if (currentIndex === totalQuestions - 1) {
            elements.btnNext.textContent = 'Save';
            elements.btnNext.classList.remove('btn-primary');
            elements.btnNext.classList.add('btn-success');
        } else {
            elements.btnNext.textContent = 'Save & Next';
            elements.btnNext.classList.remove('btn-success');
            elements.btnNext.classList.add('btn-primary');
        }

        if (ans.markedForReview) {
            elements.btnReview.textContent = 'Unmark Review';
            elements.btnReview.classList.remove('btn-outline-warning');
            elements.btnReview.classList.add('btn-warning', 'text-white');
        } else {
            elements.btnReview.textContent = 'Mark for Review';
            elements.btnReview.classList.remove('btn-warning', 'text-white');
            elements.btnReview.classList.add('btn-outline-warning');
        }

        updatePaletteColors();
    }

    function selectOption(displayLabel, originalKey) {
        const qId = questions[currentIndex].id;
        answers[qId].selectedOption = displayLabel;
        
        // Update UI
        const cards = elements.optionsContainer.children;
        for (let i = 0; i < cards.length; i++) {
            cards[i].classList.remove('selected');
            if (questions[currentIndex].options[i].label === displayLabel) {
                cards[i].classList.add('selected');
            }
        }

        updatePaletteColors();
        saveAnswerAjax(qId, displayLabel, originalKey, answers[qId].markedForReview);
    }

    function toggleReview() {
        const qId = questions[currentIndex].id;
        answers[qId].markedForReview = !answers[qId].markedForReview;
        
        // Re-render button state and palette
        renderQuestion(currentIndex);
        
        // Find original key if something is selected
        let originalKey = null;
        if (answers[qId].selectedOption) {
            const opt = questions[currentIndex].options.find(o => o.label === answers[qId].selectedOption);
            if (opt) originalKey = opt.key;
        }

        saveAnswerAjax(qId, answers[qId].selectedOption, originalKey, answers[qId].markedForReview);
    }

    function clearResponse() {
        const qId = questions[currentIndex].id;
        answers[qId].selectedOption = null;
        
        renderQuestion(currentIndex);
        saveAnswerAjax(qId, null, null, answers[qId].markedForReview);
    }

    function navigateTo(index) {
        if (index >= 0 && index < totalQuestions) {
            renderQuestion(index);
        }
    }

    function saveAnswerAjax(qId, displayLabel, originalKey, marked) {
        // Prepare payload: server expects 'selectedOption' to be the displayLabel (or the originalKey depending on backend impl)
        // Actually, ExamController expects selectedOption to be displayLabel, and it maps it.
        const payload = {
            attemptId: attemptId,
            questionId: qId,
            selectedOption: displayLabel,
            markedForReview: marked
        };

        fetch('/exam/save-answer', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json',
                [csrfHeader]: csrfToken
            },
            body: JSON.stringify(payload)
        }).catch(err => console.error("Error auto-saving answer:", err));
    }

    function updateSubmitSummary() {
        let ansCount = 0;
        let unansCount = 0;
        let revCount = 0;

        Object.values(answers).forEach(ans => {
            if (ans.selectedOption) ansCount++;
            else unansCount++;
            
            if (ans.markedForReview) revCount++;
        });

        elements.answeredCount.textContent = ansCount;
        elements.unansweredCount.textContent = unansCount;
        elements.reviewCount.textContent = revCount;
    }

    function autoSubmitExam() {
        localStorage.removeItem('exam_end_' + attemptId);
        
        fetch(`/exam/auto-submit/${attemptId}`, {
            method: 'POST',
            headers: { [csrfHeader]: csrfToken }
        })
        .then(res => res.json())
        .then(data => {
            if (data.success && data.redirectUrl) {
                window.location.href = data.redirectUrl;
            }
        })
        .catch(err => {
            console.error("Auto-submit failed, falling back to normal submit", err);
            document.getElementById('submitForm').submit();
        });
    }

    function attachEventListeners() {
        elements.btnPrev.addEventListener('click', () => navigateTo(currentIndex - 1));
        elements.btnNext.addEventListener('click', () => navigateTo(currentIndex + 1));
        elements.btnReview.addEventListener('click', toggleReview);
        elements.btnClear.addEventListener('click', clearResponse);
        
        elements.btnSubmit.addEventListener('click', () => {
            updateSubmitSummary();
            new bootstrap.Modal(document.getElementById('submitModal')).show();
        });

        elements.confirmSubmitBtn.addEventListener('click', () => {
            elements.confirmSubmitBtn.disabled = true;
            elements.confirmSubmitBtn.innerHTML = '<span class="spinner-border spinner-border-sm" role="status" aria-hidden="true"></span> Submitting...';
            localStorage.removeItem('exam_end_' + attemptId);
            document.getElementById('submitForm').submit();
        });
    }
});
