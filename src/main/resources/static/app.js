// Estado global de la aplicación
let currentSession = null;
let currentQuestionIndex = 0;
let userAnswers = {}; // questionId -> { selectedOptions, timeSpentSeconds, flagged }
let questionStartTimestamp = null;
let examTimerInterval = null;
let elapsedSeconds = 0;
let totalTimeLimitSeconds = null;

let allTopics = [];
let allQuestions = [];
let allMaterials = [];
let dueFlashcards = [];
let currentFlashcardIndex = 0;

document.addEventListener('DOMContentLoaded', () => {
    loadTopics();
    loadDashboard();
    setupModalInteractions();
});

// =================== TOAST NOTIFICATIONS & CONFIRM DIALOG ===================
function showToast(message, type = 'info', durationMs = 3800) {
    const container = document.getElementById('toast-container');
    if (!container) return;

    const toast = document.createElement('div');
    toast.className = 'toast-item pointer-events-auto flex items-center justify-between gap-3 p-3.5 rounded-2xl border shadow-2xl backdrop-blur-md transition-all text-xs font-medium';

    let icon = 'ℹ️';
    let borderClass = 'bg-slate-900/95 border-slate-700/80 text-slate-200';

    if (type === 'success') {
        icon = '✅';
        borderClass = 'bg-slate-900/95 border-emerald-500/40 text-emerald-300 shadow-emerald-500/10';
    } else if (type === 'error') {
        icon = '❌';
        borderClass = 'bg-slate-900/95 border-rose-500/40 text-rose-300 shadow-rose-500/10';
    } else if (type === 'warning') {
        icon = '⚠️';
        borderClass = 'bg-slate-900/95 border-amber-500/40 text-amber-300 shadow-amber-500/10';
    }

    toast.className += ` ${borderClass}`;
    toast.innerHTML = `
        <div class="flex items-center gap-2.5">
            <span class="text-base select-none">${icon}</span>
            <span class="leading-snug">${message}</span>
        </div>
        <button class="text-slate-400 hover:text-white p-1 cursor-pointer transition select-none text-sm font-bold leading-none">✕</button>
    `;

    const closeBtn = toast.querySelector('button');
    const dismiss = () => {
        toast.style.opacity = '0';
        toast.style.transform = 'translateX(100%) scale(0.95)';
        setTimeout(() => toast.remove(), 200);
    };

    closeBtn.onclick = dismiss;
    container.appendChild(toast);

    if (durationMs > 0) {
        setTimeout(dismiss, durationMs);
    }
}

// =================== IMPECCABLE MOTION HELPERS ===================
function animateCounter(elementId, targetValue, durationMs = 500, decimals = 1, suffix = '') {
    const el = document.getElementById(elementId);
    if (!el) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        el.textContent = targetValue.toFixed(decimals) + suffix;
        return;
    }

    const start = performance.now();
    const startValue = 0;

    function step(timestamp) {
        const elapsed = timestamp - start;
        const progress = Math.min(elapsed / durationMs, 1);
        // Easing: natural deceleration cubic-bezier(0.16, 1, 0.3, 1)
        const ease = 1 - Math.pow(1 - progress, 3);
        const current = startValue + (targetValue - startValue) * ease;
        el.textContent = current.toFixed(decimals) + suffix;

        if (progress < 1) {
            requestAnimationFrame(step);
        } else {
            el.textContent = targetValue.toFixed(decimals) + suffix;
        }
    }

    requestAnimationFrame(step);
}

// =================== MODAL & OVERLAY SYSTEM (IMPECCABLE MOTION) ===================
function openModal(id) {
    const modal = document.getElementById(id);
    if (!modal) return;
    modal.classList.remove('hidden', 'backdrop-exit');
    modal.classList.add('modal-backdrop-anim');
    const box = modal.querySelector('.modal-box');
    if (box) {
        box.classList.remove('modal-exit');
    }
}

function closeModal(id) {
    const modal = document.getElementById(id);
    if (!modal || modal.classList.contains('hidden')) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        modal.classList.add('hidden');
        return;
    }

    const box = modal.querySelector('.modal-box');
    modal.classList.add('backdrop-exit');
    if (box) box.classList.add('modal-exit');

    setTimeout(() => {
        modal.classList.add('hidden');
        modal.classList.remove('backdrop-exit');
        if (box) box.classList.remove('modal-exit');
    }, 120);
}

let confirmResolveHandler = null;

function showConfirmDialog(title, message, options = {}) {
    return new Promise((resolve) => {
        confirmResolveHandler = resolve;
        const modal = document.getElementById('modal-confirm');
        document.getElementById('confirm-title').textContent = title;
        document.getElementById('confirm-message').textContent = message;
        document.getElementById('confirm-subtitle').textContent = options.subtitle || 'Por favor confirma esta acción para continuar';
        
        const actionBtn = document.getElementById('confirm-btn-action');
        actionBtn.textContent = options.confirmText || 'Confirmar';
        if (options.danger) {
            actionBtn.className = 'px-4 py-2 text-xs font-semibold rounded-xl bg-rose-600 hover:bg-rose-500 active:scale-[0.98] text-white transition cursor-pointer shadow-sm shadow-rose-500/20';
        } else {
            actionBtn.className = 'px-4 py-2 text-xs font-semibold rounded-xl bg-blue-600 hover:bg-blue-500 active:scale-[0.98] text-white transition cursor-pointer shadow-sm shadow-blue-500/20';
        }
        openModal('modal-confirm');
    });
}

function closeConfirmModal(result) {
    const modal = document.getElementById('modal-confirm');
    if (!modal) return;

    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
        modal.classList.add('hidden');
        if (confirmResolveHandler) {
            confirmResolveHandler(result);
            confirmResolveHandler = null;
        }
        return;
    }

    const box = modal.querySelector('.modal-box');
    modal.classList.add('backdrop-exit');
    if (box) box.classList.add('modal-exit');

    setTimeout(() => {
        modal.classList.add('hidden');
        modal.classList.remove('backdrop-exit');
        if (box) box.classList.remove('modal-exit');
        if (confirmResolveHandler) {
            confirmResolveHandler(result);
            confirmResolveHandler = null;
        }
    }, 120);
}

function setupModalInteractions() {
    // Cerrar al pulsar tecla Escape
    document.addEventListener('keydown', (e) => {
        if (e.key === 'Escape') {
            const modals = document.querySelectorAll('[id^="modal-"]:not(.hidden)');
            modals.forEach(m => closeModal(m.id));
        }
    });

    // Cerrar al hacer clic en el backdrop exterior del modal
    document.querySelectorAll('[id^="modal-"]').forEach(modalEl => {
        modalEl.addEventListener('click', (e) => {
            if (e.target === modalEl) {
                closeModal(modalEl.id);
            }
        });
    });
}

// =================== NAVEGACIÓN POR PESTAÑAS (VIEW TRANSITIONS) ===================
function switchTab(tabId) {
    const views = [
        'dashboard', 'exam-modes', 'exam-runner', 'exam-result',
        'search-builder', 'resource-inspector', 'flashcards',
        'questions', 'materials'
    ];

    views.forEach(v => {
        const el = document.getElementById(`view-${v}`);
        if (el) el.classList.add('hidden');
        const btn = document.getElementById(`btn-tab-${v}`);
        if (btn) btn.classList.remove('active');
    });

    const activeView = document.getElementById(`view-${tabId}`);
    if (activeView) {
        activeView.classList.remove('hidden');
        // Trigger subtle view entrance animation
        activeView.classList.remove('view-enter');
        void activeView.offsetWidth; // force reflow for retriggering
        activeView.classList.add('view-enter');
    }

    const activeBtn = document.getElementById(`btn-tab-${tabId}`);
    if (activeBtn) activeBtn.classList.add('active');

    // Acciones específicas por pestaña
    if (tabId === 'dashboard') loadDashboard();
    if (tabId === 'flashcards') loadFlashcards();
    if (tabId === 'questions') loadQuestions();
    if (tabId === 'materials') loadMaterials();
}

// =================== TEMAS DEL TEMARIO ===================
async function loadTopics() {
    try {
        const resp = await fetch('/api/topics');
        if (resp.ok) {
            allTopics = await resp.json();
            populateTopicSelects();
        }
    } catch (e) {
        console.error('Error cargando temas:', e);
    }
}

function populateTopicSelects() {
    const selects = ['modal-topic-id', 'q-topic-select', 'upload-topic-id', 'new-q-topic-id', 'mat-topic-select', 'import-url-topic-id'];
    selects.forEach(selectId => {
        const el = document.getElementById(selectId);
        if (!el) return;
        const firstOption = el.options[0];
        el.innerHTML = '';
        if (firstOption && selectId !== 'upload-topic-id' && selectId !== 'new-q-topic-id' && selectId !== 'import-url-topic-id') el.appendChild(firstOption);

        allTopics.forEach(t => {
            const opt = document.createElement('option');
            opt.value = t.id;
            opt.textContent = `[ID ${t.id}] ${t.name} (${t.examWeightPercent}%)`;
            el.appendChild(opt);
        });
    });
}

// =================== PANEL DE CONTROL (DASHBOARD) ===================
async function loadDashboard() {
    try {
        const resp = await fetch('/api/dashboard');
        if (!resp.ok) return;
        const data = await resp.json();

        // 1. Métricas Principales (Animadas con Impeccable Motion)
        animateCounter('dash-readiness-score', data.readinessScore, 500, 1);
        animateCounter('dash-mastered-count', data.masteredQuestionsCount, 400, 0);
        animateCounter('dash-total-questions', data.totalQuestionsCount, 400, 0);
        animateCounter('dash-errors-count', data.pendingErrorsCount, 400, 0);
        animateCounter('dash-avg-time', data.averageTimePerQuestionSeconds, 450, 1);

        // Badge de Readiness
        const badge = document.getElementById('dash-readiness-badge');
        badge.className = 'mt-2 inline-block px-2.5 py-0.5 rounded-full text-[11px] font-bold ';
        if (data.readinessStatus === 'EXCELLENT') {
            badge.className += 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
            badge.textContent = 'Preparado para Aprobar (≥80%)';
        } else if (data.readinessStatus === 'ON_TRACK') {
            badge.className += 'bg-amber-500/20 text-amber-400 border border-amber-500/30';
            badge.textContent = 'En Buen Camino (65-79%)';
        } else if (data.readinessStatus === 'NEEDS_WORK') {
            badge.className += 'bg-rose-500/20 text-rose-400 border border-rose-500/30';
            badge.textContent = 'Necesita Refuerzo (<65%)';
        } else {
            badge.className += 'bg-slate-700 text-slate-300';
            badge.textContent = 'Iniciando Preparación';
        }

        // Badge en el menú para Flashcards pendientes
        const fcBadge = document.getElementById('badge-due-count');
        if (data.dueReviewsCount > 0) {
            fcBadge.textContent = data.dueReviewsCount;
            fcBadge.classList.remove('hidden');
        } else {
            fcBadge.classList.add('hidden');
        }

        // 2. Mapa de Calor por temas (Barras de progreso animadas)
        const grid = document.getElementById('topics-heatmap-grid');
        grid.innerHTML = '';
        data.topicHeatmaps.forEach(t => {
            const card = document.createElement('div');
            card.className = 'bg-slate-900/60 p-4 rounded-xl border border-slate-700/60 flex flex-col justify-between';

            let colorBadge = 'bg-slate-700 text-slate-300';
            let barColor = 'bg-slate-600';
            if (t.statusColor === 'GREEN') {
                colorBadge = 'bg-emerald-500/20 text-emerald-400 border border-emerald-500/30';
                barColor = 'bg-emerald-500';
            } else if (t.statusColor === 'AMBER') {
                colorBadge = 'bg-amber-500/20 text-amber-400 border border-amber-500/30';
                barColor = 'bg-amber-500';
            } else if (t.statusColor === 'RED') {
                colorBadge = 'bg-rose-500/20 text-rose-400 border border-rose-500/30';
                barColor = 'bg-rose-500';
            }

            card.innerHTML = `
                <div>
                    <div class="flex items-center justify-between gap-2 mb-1.5">
                        <span class="text-xs font-bold text-white truncate">${t.name}</span>
                        <span class="text-[10px] px-2 py-0.5 rounded-full font-bold ${colorBadge}">${t.accuracyPercent.toFixed(1)}%</span>
                    </div>
                    <div class="flex items-center justify-between text-[11px] text-slate-400 mb-2">
                        <span>Peso oficial: ${t.weightPercent}%</span>
                        <span>${t.correctAnswered}/${t.totalAnswered} aciertos</span>
                    </div>
                    <div class="w-full bg-slate-800 rounded-full h-1.5 overflow-hidden">
                        <div class="${barColor} h-1.5 rounded-full progress-bar-smooth" style="width: 0%" data-target-width="${Math.min(100, t.accuracyPercent)}%"></div>
                    </div>
                </div>
                <div class="mt-3 pt-2 border-t border-slate-800/80 flex justify-between items-center text-[10px] text-slate-400">
                    <span>⏱ ${t.averageTimeSeconds}s / preg</span>
                    <button onclick="startExamWithTopic(${t.topicId})" class="text-blue-400 hover:text-blue-300 font-semibold cursor-pointer">Practicar Tema →</button>
                </div>
            `;
            grid.appendChild(card);
        });

        // Disparar animación suave de las barras de progreso
        requestAnimationFrame(() => {
            grid.querySelectorAll('.progress-bar-smooth').forEach(bar => {
                bar.style.width = bar.getAttribute('data-target-width');
            });
        });

        // 3. Tabla de Intentos Recientes
        const table = document.getElementById('recent-attempts-table');
        if (data.recentAttempts && data.recentAttempts.length > 0) {
            table.innerHTML = '';
            data.recentAttempts.forEach(a => {
                const tr = document.createElement('tr');
                const isPass = (a.scorePercent || 0) >= 80.0;
                tr.innerHTML = `
                    <td class="py-2.5 px-4 font-mono text-xs text-blue-400 font-bold">${a.mode}</td>
                    <td class="py-2.5 px-4 text-xs text-slate-300">${formatDate(a.finishedAt || a.startedAt)}</td>
                    <td class="py-2.5 px-4 font-bold ${isPass ? 'text-emerald-400' : 'text-rose-400'}">${(a.scorePercent || 0).toFixed(1)}%</td>
                    <td class="py-2.5 px-4 text-xs">${a.correctQuestions}/${a.totalQuestions}</td>
                    <td class="py-2.5 px-4">
                        <span class="px-2 py-0.5 text-[10px] font-bold rounded-full ${isPass ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                            ${isPass ? 'APROBADO' : 'SUSPENSO'}
                        </span>
                    </td>
                `;
                table.appendChild(tr);
            });
        } else {
            table.innerHTML = `<tr><td colspan="5" class="py-8 text-center text-slate-500 text-xs">
                <div class="flex flex-col items-center justify-center gap-1.5">
                    <span class="text-2xl opacity-60">🕒</span>
                    <span class="font-medium text-slate-400">No hay intentos registrados aún</span>
                    <button onclick="startExamWithMode('SIMULATION')" class="text-blue-400 hover:text-blue-300 font-semibold underline text-xs mt-1 cursor-pointer">Iniciar primer simulacro oficial →</button>
                </div>
            </td></tr>`;
        }
    } catch (e) {
        console.error('Error cargando dashboard:', e);
    }
}

function formatDate(dateStr) {
    if (!dateStr) return '-';
    const d = new Date(dateStr);
    return d.toLocaleDateString() + ' ' + d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
}

// =================== MOTOR DE SIMULACIÓN Y EXÁMENES ===================
function openNewExamModal() {
    openModal('modal-new-exam');
}

function startExamWithTopic(topicId) {
    executeStartExam({
        mode: 'TUTOR',
        questionCount: 10,
        topicId: topicId
    });
}

function startExamWithMode(mode) {
    executeStartExam({
        mode: mode,
        questionCount: mode === 'SIMULATION' ? 15 : 10,
        topicId: null
    });
}

function executeStartModalExam() {
    const mode = document.getElementById('modal-exam-mode').value;
    const count = parseInt(document.getElementById('modal-question-count').value, 10);
    const topicIdVal = document.getElementById('modal-topic-id').value;
    const topicId = topicIdVal ? parseInt(topicIdVal, 10) : null;

    closeModal('modal-new-exam');
    executeStartExam({ mode, questionCount: count, topicId });
}

async function executeStartExam(requestPayload) {
    try {
        const resp = await fetch('/api/exams/start', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(requestPayload)
        });

        if (!resp.ok) {
            showToast('No se pudo iniciar la sesión de examen.', 'error');
            return;
        }

        currentSession = await resp.json();
        currentQuestionIndex = 0;
        userAnswers = {};

        // Inicializar respuestas vacías
        currentSession.questions.forEach(q => {
            userAnswers[q.id] = {
                selectedOptions: '',
                timeSpentSeconds: 0,
                flagged: false
            };
        });

        // Configurar temporizador
        if (examTimerInterval) clearInterval(examTimerInterval);
        elapsedSeconds = 0;
        if (currentSession.timeLimitMinutes) {
            totalTimeLimitSeconds = currentSession.timeLimitMinutes * 60;
        } else {
            totalTimeLimitSeconds = null;
        }

        examTimerInterval = setInterval(updateTimerDisplay, 1000);

        switchTab('exam-runner');
        renderCurrentQuestion(true);
        renderQuestionGrid();
        showToast('Examen iniciado. ¡Mucho éxito!', 'info', 2500);
    } catch (e) {
        console.error('Error iniciando examen:', e);
        showToast('Error de conexión al iniciar el examen.', 'error');
    }
}

function updateTimerDisplay() {
    elapsedSeconds++;
    const timerEl = document.getElementById('runner-timer');
    if (!timerEl) return;

    if (totalTimeLimitSeconds !== null) {
        const remaining = Math.max(0, totalTimeLimitSeconds - elapsedSeconds);
        const mins = Math.floor(remaining / 60);
        const secs = remaining % 60;
        timerEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
        if (remaining <= 0) {
            clearInterval(examTimerInterval);
            showToast('¡Tiempo agotado! Entregando examen automáticamente...', 'warning', 5000);
            submitExam();
        }
    } else {
        const mins = Math.floor(elapsedSeconds / 60);
        const secs = elapsedSeconds % 60;
        timerEl.textContent = `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }
}

function renderCurrentQuestion(isNavigating = false) {
    if (!currentSession || !currentSession.questions) return;
    const q = currentSession.questions[currentQuestionIndex];
    if (!q) return;

    questionStartTimestamp = Date.now();

    document.getElementById('runner-mode-badge').textContent = currentSession.mode;
    document.getElementById('runner-current-idx').textContent = (currentQuestionIndex + 1);
    document.getElementById('runner-total-count').textContent = currentSession.totalQuestions;
    document.getElementById('runner-topic-name').textContent = q.topicName || 'General';
    
    const stemEl = document.getElementById('runner-stem');
    stemEl.textContent = q.stem;

    // Snippet
    const snipContainer = document.getElementById('runner-snippet-container');
    const snip = document.getElementById('runner-snippet');
    if (q.codeSnippet && q.codeSnippet.trim()) {
        snip.textContent = q.codeSnippet;
        snipContainer.classList.remove('hidden');
    } else {
        snipContainer.classList.add('hidden');
    }

    // Flag button con micro-interacción
    const ans = userAnswers[q.id];
    const flagBtn = document.getElementById('runner-flag-btn');
    if (ans && ans.flagged) {
        flagBtn.className = 'px-3 py-1.5 text-xs font-bold rounded-lg border border-amber-500 bg-amber-500/20 text-amber-300 flex items-center gap-1.5 active:scale-[0.98] transition cursor-pointer';
    } else {
        flagBtn.className = 'px-3 py-1.5 text-xs font-medium rounded-lg border border-slate-600 text-slate-300 hover:bg-slate-700 active:scale-[0.98] flex items-center gap-1.5 transition cursor-pointer';
    }

    // Opciones con micro-interacción Impeccable
    const optionsContainer = document.getElementById('runner-options-list');
    optionsContainer.innerHTML = '';
    const selected = ans ? ans.selectedOptions : '';

    q.options.forEach(opt => {
        const isSelected = selected === opt.label;
        const btn = document.createElement('button');
        btn.onclick = () => selectOption(opt.label);
        btn.className = `option-btn w-full text-left p-4 rounded-xl border flex items-start gap-3.5 cursor-pointer ${
            isSelected 
                ? 'bg-blue-600/20 border-blue-500 text-white shadow-md shadow-blue-500/10' 
                : 'bg-slate-900/60 border-slate-700/80 text-slate-300 hover:bg-slate-800/80'
        }`;

        btn.innerHTML = `
            <span class="w-7 h-7 rounded-lg flex items-center justify-center font-bold text-xs shrink-0 transition-transform ${
                isSelected ? 'bg-blue-600 text-white scale-105' : 'bg-slate-800 text-slate-400'
            }">${opt.label}</span>
            <div class="text-sm leading-relaxed">${opt.text}</div>
        `;
        optionsContainer.appendChild(btn);
    });

    // Transición espacial de cambio de pregunta (180ms)
    if (isNavigating) {
        stemEl.classList.remove('question-enter');
        optionsContainer.classList.remove('question-enter');
        void stemEl.offsetWidth;
        stemEl.classList.add('question-enter');
        optionsContainer.classList.add('question-enter');
    }

    // Modo Tutor feedback con reveal animado
    const tutorBox = document.getElementById('runner-tutor-feedback');
    if (currentSession.mode === 'TUTOR' && selected) {
        tutorBox.classList.remove('hidden');
        tutorBox.classList.remove('feedback-reveal');
        void tutorBox.offsetWidth;
        tutorBox.classList.add('feedback-reveal');
        document.getElementById('runner-tutor-explanation').textContent = q.explanation || '';
        const linkEl = document.getElementById('runner-tutor-link');
        if (q.fhirSpecUrl) {
            linkEl.href = q.fhirSpecUrl;
            linkEl.classList.remove('hidden');
        } else {
            linkEl.classList.add('hidden');
        }
    } else {
        tutorBox.classList.add('hidden');
    }

    // Deshabilitar navegación en extremos
    document.getElementById('runner-prev-btn').disabled = (currentQuestionIndex === 0);
    document.getElementById('runner-next-btn').disabled = (currentQuestionIndex === currentSession.questions.length - 1);

    renderQuestionGrid();
}

function selectOption(label) {
    const q = currentSession.questions[currentQuestionIndex];
    if (!q) return;

    accumulateTime(q.id);
    userAnswers[q.id].selectedOptions = label;
    renderCurrentQuestion(false);
    renderQuestionGrid();
}

function toggleFlagCurrentQuestion() {
    const q = currentSession.questions[currentQuestionIndex];
    if (!q) return;

    userAnswers[q.id].flagged = !userAnswers[q.id].flagged;
    renderCurrentQuestion(false);
    renderQuestionGrid();
}

function accumulateTime(questionId) {
    if (questionStartTimestamp && userAnswers[questionId]) {
        const diffSeconds = Math.round((Date.now() - questionStartTimestamp) / 1000);
        userAnswers[questionId].timeSpentSeconds += diffSeconds;
    }
    questionStartTimestamp = Date.now();
}

function prevQuestion() {
    if (currentQuestionIndex > 0) {
        const q = currentSession.questions[currentQuestionIndex];
        accumulateTime(q.id);
        currentQuestionIndex--;
        renderCurrentQuestion(true);
    }
}

function nextQuestion() {
    if (currentQuestionIndex < currentSession.questions.length - 1) {
        const q = currentSession.questions[currentQuestionIndex];
        accumulateTime(q.id);
        currentQuestionIndex++;
        renderCurrentQuestion(true);
    }
}

function jumpToQuestion(idx) {
    const q = currentSession.questions[currentQuestionIndex];
    accumulateTime(q.id);
    currentQuestionIndex = idx;
    renderCurrentQuestion(true);
}

function renderQuestionGrid() {
    const grid = document.getElementById('runner-question-grid');
    grid.innerHTML = '';

    currentSession.questions.forEach((q, idx) => {
        const ans = userAnswers[q.id];
        const isCurrent = (idx === currentQuestionIndex);
        const isAnswered = ans && ans.selectedOptions !== '';
        const isFlagged = ans && ans.flagged;

        let badgeClass = 'bg-slate-800 text-slate-400 border border-slate-700';
        if (isCurrent) {
            badgeClass = 'ring-2 ring-blue-500 bg-blue-600 text-white font-bold';
        } else if (isFlagged) {
            badgeClass = 'bg-amber-500/20 text-amber-400 border border-amber-500/50 font-bold';
        } else if (isAnswered) {
            badgeClass = 'bg-emerald-600/30 text-emerald-300 border border-emerald-500/40 font-bold';
        }

        const btn = document.createElement('button');
        btn.onclick = () => jumpToQuestion(idx);
        btn.className = `w-7 h-7 text-xs rounded-md transition flex items-center justify-center ${badgeClass}`;
        btn.textContent = (idx + 1);
        grid.appendChild(btn);
    });
}

async function confirmSubmitExam() {
    const unanswered = Object.values(userAnswers).filter(a => a.selectedOptions === '').length;
    let msg = '¿Deseas finalizar y entregar el examen ahora?';
    if (unanswered > 0) {
        msg = `Tienes ${unanswered} pregunta(s) sin responder de ${currentSession.questions.length}. ¿Deseas entregar el examen igualmente?`;
    }
    const ok = await showConfirmDialog('Entregar Examen', msg, {
        confirmText: 'Entregar Ahora',
        danger: unanswered > 0,
        subtitle: unanswered > 0 ? 'Existen preguntas en blanco' : 'Revisión completada'
    });
    if (ok) {
        submitExam();
    }
}

async function submitExam() {
    if (examTimerInterval) clearInterval(examTimerInterval);
    const q = currentSession.questions[currentQuestionIndex];
    if (q) accumulateTime(q.id);

    const answersList = Object.keys(userAnswers).map(qid => ({
        questionId: parseInt(qid, 10),
        selectedOptions: userAnswers[qid].selectedOptions,
        timeSpentSeconds: userAnswers[qid].timeSpentSeconds,
        flagged: userAnswers[qid].flagged
    }));

    try {
        const resp = await fetch('/api/exams/submit', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                attemptId: currentSession.attemptId,
                answers: answersList
            })
        });

        if (!resp.ok) {
            showToast('Error al procesar la entrega del examen.', 'error');
            return;
        }

        const result = await resp.json();
        showToast('Examen corregido y calificado con éxito.', 'success');
        showExamResult(result);
    } catch (e) {
        console.error('Error enviando examen:', e);
        showToast('Error de conexión al enviar el examen.', 'error');
    }
}

function showExamResult(result) {
    switchTab('exam-result');

    // Score counter animated with Impeccable natural deceleration (550ms)
    animateCounter('result-score-percent', result.scorePercent, 550, 1);
    document.getElementById('result-correct-count').textContent = result.correctCount;
    document.getElementById('result-total-count').textContent = result.totalCount;

    const mins = Math.round(result.totalTimeSeconds / 60);
    document.getElementById('result-duration').textContent = `${mins} min`;

    const statusBadge = document.getElementById('result-status-badge');
    statusBadge.classList.remove('celebration-pop');
    void statusBadge.offsetWidth; // re-trigger celebration pop
    if (result.passed) {
        statusBadge.textContent = 'APROBADO (≥80% Umbral Seguro)';
        statusBadge.className = 'inline-block px-3 py-1 rounded-full text-xs font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 celebration-pop';
    } else {
        statusBadge.textContent = 'NO SUPERADO (<80%)';
        statusBadge.className = 'inline-block px-3 py-1 rounded-full text-xs font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30 celebration-pop';
    }

    const container = document.getElementById('result-reviews-container');
    container.innerHTML = '';

    result.reviews.forEach((r, idx) => {
        const card = document.createElement('div');
        card.className = `p-6 rounded-2xl border ${r.correct ? 'bg-slate-800/60 border-emerald-500/30' : 'bg-slate-800/80 border-rose-500/40'} space-y-4`;

        let optionsHtml = '';
        r.options.forEach(opt => {
            const isUserSelected = (r.selectedOptions === opt.label);
            const isCorrectOption = Boolean(opt.correct);

            let optBorder = 'border-slate-800 bg-slate-900/40 text-slate-400';
            let badgeTag = '';

            if (isCorrectOption) {
                optBorder = 'border-emerald-500/50 bg-emerald-950/30 text-slate-200';
                badgeTag = '<span class="text-[10px] font-bold text-emerald-400 bg-emerald-500/20 px-2 py-0.5 rounded">Respuesta Correcta</span>';
            } else if (isUserSelected) {
                optBorder = 'border-rose-500/50 bg-rose-950/30 text-slate-200';
                badgeTag = '<span class="text-[10px] font-bold text-rose-400 bg-rose-500/20 px-2 py-0.5 rounded">Tu Elección (Incorrecta)</span>';
            }

            optionsHtml += `
                <div class="p-3 rounded-xl border ${optBorder} text-xs space-y-1">
                    <div class="flex items-center justify-between">
                        <span class="font-bold text-white">${opt.label}. ${opt.text}</span>
                        ${badgeTag}
                    </div>
                    ${opt.distractorReason ? `<p class="text-slate-400 text-[11px] pt-1"><em>${opt.distractorReason}</em></p>` : ''}
                </div>
            `;
        });

        card.innerHTML = `
            <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-blue-400">Pregunta ${idx + 1} - ${r.topicName}</span>
                <span class="px-2 py-0.5 rounded-full font-bold ${r.correct ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                    ${r.correct ? '✓ Correcta' : '✗ Incorrecta'}
                </span>
            </div>

            <div class="text-sm font-semibold text-white leading-relaxed">${r.stem}</div>

            ${r.codeSnippet ? `<pre class="bg-slate-950 p-3 rounded-xl border border-slate-800 text-xs font-mono text-emerald-400 overflow-x-auto">${r.codeSnippet}</pre>` : ''}

            <div class="space-y-2">
                ${optionsHtml}
            </div>

            <div class="p-3 bg-slate-900 rounded-xl border border-slate-700/60 text-xs space-y-1.5">
                <div class="font-bold text-blue-400 uppercase tracking-wide">Explicación Oficial:</div>
                <p class="text-slate-300 leading-relaxed">${r.explanation}</p>
                ${r.fhirSpecUrl ? `<a href="${r.fhirSpecUrl}" target="_blank" class="text-blue-400 hover:underline block pt-1">Ver en hl7.org/fhir →</a>` : ''}
            </div>

            <div class="flex justify-end pt-2">
                <button onclick="createMirrorVariant(${r.questionId})" class="px-3.5 py-1.5 text-xs font-medium rounded-lg bg-indigo-600/30 hover:bg-indigo-600/50 text-indigo-300 border border-indigo-500/30 transition flex items-center gap-1.5">
                    ✨ Generar Variante Espejo (Clone & Twist)
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}

async function createMirrorVariant(questionId) {
    try {
        const resp = await fetch(`/api/questions/${questionId}/variant`, { method: 'POST' });
        if (resp.ok) {
            showToast('¡Variante espejo generada con éxito y añadida al banco de preguntas!', 'success');
        } else {
            showToast('No se pudo generar la variante espejo.', 'error');
        }
    } catch (e) {
        console.error('Error generando variante:', e);
        showToast('Error de red al generar la variante.', 'error');
    }
}

// =================== FHIR SEARCH BUILDER ===================
function loadSearchChallenge(num) {
    const input = document.getElementById('search-query-input');
    const expected = document.getElementById('search-expected-type');
    if (num === 1) {
        expected.value = 'Observation';
        input.value = 'GET [base]/Observation?code=http://loinc.org|15074-8&status=final&_include=Observation:subject';
    } else if (num === 2) {
        expected.value = 'Patient';
        input.value = 'GET [base]/Patient?name:exact=Garcia&birthdate=ge1980-01-01';
    } else if (num === 3) {
        expected.value = 'Condition';
        input.value = 'GET [base]/Condition?clinical-status:missing=false&patient=123';
    } else if (num === 4) {
        expected.value = 'Patient';
        input.value = 'GET [base]/Patient?_id=123&_revinclude=Provenance:target';
    }
    validateSearchQuery();
}

async function validateSearchQuery() {
    const query = document.getElementById('search-query-input').value;
    const expected = document.getElementById('search-expected-type').value;

    try {
        const resp = await fetch('/api/fhir/validate-search', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ queryUrl: query, expectedResourceType: expected })
        });

        if (!resp.ok) return;
        const res = await resp.json();

        const out = document.getElementById('search-validation-output');
        out.classList.remove('hidden');

        let issuesHtml = '';
        if (res.issues && res.issues.length > 0) {
            issuesHtml = `
                <div class="p-3 rounded-lg bg-rose-950/40 border border-rose-500/30 text-rose-300 text-xs space-y-1">
                    <strong class="font-bold">⚠️ Problemas Detectados:</strong>
                    <ul class="list-disc pl-4 space-y-0.5">
                        ${res.issues.map(i => `<li>${i}</li>`).join('')}
                    </ul>
                </div>
            `;
        }

        let positiveHtml = '';
        if (res.positiveFindings && res.positiveFindings.length > 0) {
            positiveHtml = `
                <div class="p-3 rounded-lg bg-emerald-950/40 border border-emerald-500/30 text-emerald-300 text-xs space-y-1">
                    <strong class="font-bold">✓ Hallazgos y Modificadores Válidos:</strong>
                    <ul class="list-disc pl-4 space-y-0.5">
                        ${res.positiveFindings.map(p => `<li>${p}</li>`).join('')}
                    </ul>
                </div>
            `;
        }

        let paramsHtml = '';
        if (res.parsedParameters) {
            paramsHtml = Object.entries(res.parsedParameters).map(([k, v]) => `
                <div class="flex items-center gap-2 font-mono text-xs">
                    <span class="text-blue-400 font-bold">${k}</span> = <span class="text-slate-300 bg-slate-800 px-2 py-0.5 rounded">${v.join(', ')}</span>
                </div>
            `).join('');
        }

        out.innerHTML = `
            <div class="flex items-center justify-between border-b border-slate-800 pb-2">
                <span class="text-xs font-bold text-white">Resultado de Validación FHIR REST</span>
                <span class="px-2.5 py-0.5 rounded-full text-[11px] font-bold ${res.valid ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                    ${res.valid ? 'SINTAXIS CORRECTA' : 'CONTIENE ERRORES'}
                </span>
            </div>
            ${issuesHtml}
            ${positiveHtml}
            <div class="space-y-1.5 pt-1">
                <div class="text-xs font-semibold text-slate-400">Parámetros Desglosados:</div>
                <div class="space-y-1 bg-slate-950 p-3 rounded-lg border border-slate-800">
                    ${paramsHtml || '<span class="text-xs text-slate-500">Sin parámetros de búsqueda</span>'}
                </div>
            </div>
            <div class="text-xs font-mono text-slate-400 pt-1">
                Forma Canónica: <span class="text-emerald-400">${res.canonicalForm}</span>
            </div>
        `;
    } catch (e) {
        console.error('Error validando búsqueda:', e);
    }
}

// =================== RESOURCE INSPECTOR (HAPI FHIR) ===================
function loadSampleResource(type) {
    const input = document.getElementById('resource-json-input');
    if (type === 'patient') {
        input.value = JSON.stringify({
            "resourceType": "Patient",
            "id": "pat-1",
            "active": true,
            "name": [{ "use": "official", "family": "García", "given": ["Carlos"] }],
            "gender": "male",
            "birthDate": "1985-04-12"
        }, null, 2);
    } else if (type === 'observation_error') {
        input.value = JSON.stringify({
            "resourceType": "Observation",
            "id": "obs-bad",
            "code": {
                "coding": [{ "system": "http://loinc.org", "code": "8867-4", "display": "Heart rate" }]
            },
            "valueQuantity": { "value": 75, "unit": "beats/min" }
            // Falta status (cardinalidad 1..1)
        }, null, 2);
    } else if (type === 'bundle') {
        input.value = JSON.stringify({
            "resourceType": "Bundle",
            "id": "bundle-tx",
            "type": "transaction",
            "entry": [
                {
                    "fullUrl": "urn:uuid:61ebe359-bfdc-4613-8bf2-c5e300945f0a",
                    "resource": {
                        "resourceType": "Patient",
                        "name": [{ "family": "Pérez" }]
                    },
                    "request": { "method": "POST", "url": "Patient" }
                }
            ]
        }, null, 2);
    }
    validateResourceJson();
}

async function validateResourceJson() {
    const json = document.getElementById('resource-json-input').value;
    const out = document.getElementById('resource-validation-output');

    try {
        const resp = await fetch('/api/fhir/validate-resource', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ resourceJson: json })
        });

        if (!resp.ok) return;
        const res = await resp.json();

        let issuesHtml = '';
        if (res.issues && res.issues.length > 0) {
            issuesHtml = `
                <div class="p-3 mb-3 rounded-lg bg-rose-950/40 border border-rose-500/30 text-rose-300 text-xs space-y-1">
                    <strong class="font-bold">Infracciones y Advertencias:</strong>
                    <ul class="list-disc pl-4 space-y-0.5">
                        ${res.issues.map(i => `<li>${i}</li>`).join('')}
                    </ul>
                </div>
            `;
        }

        out.innerHTML = `
            <div class="mb-3 flex items-center justify-between border-b border-slate-800 pb-2">
                <span class="font-bold text-white">Tipo de Recurso: ${res.resourceType || 'Desconocido'}</span>
                <span class="px-2 py-0.5 rounded text-[10px] font-bold ${res.valid ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                    ${res.valid ? 'VALIDACIÓN EXITOSA' : 'INVALIDO'}
                </span>
            </div>
            <p class="text-xs text-slate-300 mb-3">${res.summary}</p>
            ${issuesHtml}
            ${res.formattedJson ? `
                <div class="text-[11px] text-slate-400 mb-1 font-sans">JSON Normalizado HAPI FHIR:</div>
                <pre class="text-emerald-400 text-xs overflow-x-auto whitespace-pre-wrap">${res.formattedJson}</pre>
            ` : ''}
        `;
    } catch (e) {
        console.error('Error validando recurso:', e);
    }
}

// =================== FLASHCARDS (SM-2) ===================
async function loadFlashcards() {
    try {
        const resp = await fetch('/api/srs/due');
        if (!resp.ok) return;
        dueFlashcards = await resp.json();
        currentFlashcardIndex = 0;

        document.getElementById('flashcard-due-number').textContent = dueFlashcards.length;

        if (dueFlashcards.length === 0) {
            const cardContent = document.getElementById('fc-card-content');
            const emptyState = document.getElementById('fc-empty-state');
            if (cardContent) cardContent.classList.add('hidden');
            if (emptyState) emptyState.classList.remove('hidden');
            return;
        }

        const cardContent = document.getElementById('fc-card-content');
        const emptyState = document.getElementById('fc-empty-state');
        if (cardContent) cardContent.classList.remove('hidden');
        if (emptyState) emptyState.classList.add('hidden');

        renderCurrentFlashcard();
    } catch (e) {
        console.error('Error cargando flashcards:', e);
    }
}

function renderCurrentFlashcard() {
    const card = dueFlashcards[currentFlashcardIndex];
    if (!card) return;

    document.getElementById('fc-topic-name').textContent = card.topicName;
    document.getElementById('fc-interval').textContent = card.intervalDays;
    document.getElementById('fc-ef').textContent = card.easinessFactor.toFixed(2);
    document.getElementById('fc-stem').textContent = card.stem;

    const snip = document.getElementById('fc-snippet');
    if (card.codeSnippet) {
        snip.textContent = card.codeSnippet;
        snip.classList.remove('hidden');
    } else {
        snip.classList.add('hidden');
    }

    document.getElementById('fc-correct-text').textContent = `${card.correctOptionLabel}. ${card.correctOptionText}`;
    document.getElementById('fc-explanation').textContent = card.explanation;
    
    const link = document.getElementById('fc-link');
    if (card.fhirSpecUrl) {
        link.href = card.fhirSpecUrl;
        link.classList.remove('hidden');
    } else {
        link.classList.add('hidden');
    }

    // Ocultar trasera inicialmente y resetear animación
    const backFace = document.getElementById('fc-back-face');
    backFace.classList.add('hidden');
    backFace.classList.remove('card-flip-reveal');
    document.getElementById('fc-flip-btn').classList.remove('hidden');
}

function flipFlashcard() {
    const backFace = document.getElementById('fc-back-face');
    backFace.classList.remove('hidden');
    backFace.classList.remove('card-flip-reveal');
    void backFace.offsetWidth;
    backFace.classList.add('card-flip-reveal');
    document.getElementById('fc-flip-btn').classList.add('hidden');
}

async function rateFlashcard(rating) {
    const card = dueFlashcards[currentFlashcardIndex];
    if (!card) return;

    const contentEl = document.getElementById('fc-card-content');
    const isReduced = window.matchMedia('(prefers-reduced-motion: reduce)').matches;
    if (contentEl && !isReduced) {
        contentEl.classList.add('card-exit');
    }

    try {
        await fetch(`/api/srs/review/${card.questionId}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ rating: rating })
        });

        setTimeout(() => {
            currentFlashcardIndex++;
            if (currentFlashcardIndex < dueFlashcards.length) {
                renderCurrentFlashcard();
                if (contentEl && !isReduced) {
                    contentEl.classList.remove('card-exit');
                    contentEl.classList.add('card-enter');
                    setTimeout(() => contentEl.classList.remove('card-enter'), 200);
                }
            } else {
                if (contentEl) contentEl.classList.remove('card-exit');
                loadFlashcards(); // refrescar
            }
        }, isReduced ? 0 : 140);
    } catch (e) {
        console.error('Error calificando flashcard:', e);
        if (contentEl) contentEl.classList.remove('card-exit');
    }
}

// =================== BANCO DE PREGUNTAS ===================
async function loadQuestions() {
    try {
        const resp = await fetch('/api/questions');
        if (resp.ok) {
            allQuestions = await resp.json();
            renderQuestionsList(allQuestions);
        }
    } catch (e) {
        console.error('Error cargando banco:', e);
    }
}

function filterQuestions() {
    const kw = document.getElementById('q-search-input').value.toLowerCase();
    const topicId = document.getElementById('q-topic-select').value;

    const filtered = allQuestions.filter(q => {
        const matchKw = !kw || q.stem.toLowerCase().includes(kw) || q.explanation.toLowerCase().includes(kw);
        const matchTopic = !topicId || (q.topic && q.topic.id == topicId);
        return matchKw && matchTopic;
    });

    renderQuestionsList(filtered);
}

function renderQuestionsList(questions) {
    const container = document.getElementById('questions-list-container');
    container.innerHTML = '';

    if (questions.length === 0) {
        container.innerHTML = `
            <div class="p-8 rounded-2xl bg-slate-900/60 border border-slate-800 text-center space-y-3">
                <div class="w-12 h-12 mx-auto rounded-full bg-slate-800/80 flex items-center justify-center text-xl">🔍</div>
                <h4 class="text-sm font-semibold text-white">No se encontraron preguntas</h4>
                <p class="text-xs text-slate-400 max-w-sm mx-auto">Prueba a cambiar el filtro de búsqueda o el tema seleccionado, o añade una nueva pregunta con el botón superior.</p>
            </div>
        `;
        return;
    }

    questions.forEach(q => {
        const card = document.createElement('div');
        card.className = 'bg-slate-800 p-5 rounded-2xl border border-slate-700 space-y-3';

        let optionsHtml = '';
        if (q.options) {
            optionsHtml = q.options.map(o => `
                <div class="text-xs flex items-center gap-2 ${o.correct ? 'text-emerald-400 font-bold' : 'text-slate-400'}">
                    <span>${o.optionLabel}.</span>
                    <span>${o.optionText}</span>
                    ${o.correct ? '<span>✓</span>' : ''}
                </div>
            `).join('');
        }

        card.innerHTML = `
            <div class="flex items-center justify-between text-xs">
                <span class="font-semibold text-blue-400">${q.topic ? q.topic.name : 'General'}</span>
                <div class="flex items-center gap-2">
                    <span class="px-2 py-0.5 rounded bg-slate-700 text-slate-300 text-[10px] font-mono">${q.source}</span>
                    <span class="px-2 py-0.5 rounded bg-slate-700 text-slate-300 text-[10px]">Dificultad: ${q.difficulty}/3</span>
                </div>
            </div>

            <div class="text-sm font-semibold text-white leading-relaxed">${q.stem}</div>

            ${q.codeSnippet ? `<pre class="bg-slate-950 p-3 rounded-xl border border-slate-800 text-xs font-mono text-emerald-400 whitespace-pre-wrap">${q.codeSnippet}</pre>` : ''}

            <div class="space-y-1.5 pl-2 border-l-2 border-slate-700">
                ${optionsHtml}
            </div>

            <div class="pt-2 flex justify-between items-center text-xs">
                <span class="text-slate-400">${q.fhirSpecUrl ? `<a href="${q.fhirSpecUrl}" target="_blank" class="text-blue-400 hover:underline">hl7.org/fhir →</a>` : ''}</span>
                <button onclick="createMirrorVariant(${q.id})" class="px-3 py-1 rounded bg-indigo-600/30 hover:bg-indigo-600/50 text-indigo-300 border border-indigo-500/30 text-xs font-medium transition">
                    ✨ Clonar y Generar Variante
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}

// =================== MATERIALES & PDFBOX ===================
// =================== MATERIALES & PDFBOX ===================
async function loadMaterials() {
    try {
        const resp = await fetch('/api/materials');
        if (resp.ok) {
            allMaterials = await resp.json();
            renderMaterials(allMaterials);
        }
    } catch (e) {
        console.error('Error cargando materiales:', e);
    }
}

function filterMaterials() {
    const kw = document.getElementById('mat-search-input').value.toLowerCase();
    const topicId = document.getElementById('mat-topic-select').value;

    const filtered = allMaterials.filter(m => {
        const matchKw = !kw || m.title.toLowerCase().includes(kw) || m.contentText.toLowerCase().includes(kw);
        const matchTopic = !topicId || (m.topic && m.topic.id == topicId);
        return matchKw && matchTopic;
    });

    renderMaterials(filtered);
}

function renderMaterials(list) {
    const container = document.getElementById('materials-container');
    container.innerHTML = '';

    if (list.length === 0) {
        container.innerHTML = `
            <div class="p-8 rounded-2xl bg-slate-900/60 border border-slate-800 text-center space-y-3">
                <div class="w-12 h-12 mx-auto rounded-full bg-slate-800/80 flex items-center justify-center text-xl">📑</div>
                <h4 class="text-sm font-semibold text-white">No se encontraron documentos indexados</h4>
                <p class="text-xs text-slate-400 max-w-sm mx-auto">No hay apuntes ni documentación técnica que coincidan con los filtros aplicados. Puedes importar una URL oficial de HL7 FHIR o subir un PDF de tu curso.</p>
            </div>
        `;
        return;
    }

    list.forEach(m => {
        const card = document.createElement('div');
        card.className = 'bg-slate-900/70 p-5 rounded-2xl border border-slate-700/80 space-y-3 hover:border-slate-600 transition';
        const wordsCount = m.contentText ? m.contentText.trim().split(/\s+/).length : 0;
        const topicName = m.topic ? m.topic.name : 'General';
        const topicId = m.topic ? m.topic.id : null;

        // Determinar si es URL externa o PDF con archivo físico
        const isUrl = m.referenceUrlOrPage && (m.referenceUrlOrPage.startsWith('http://') || m.referenceUrlOrPage.startsWith('https://'));
        const hasPdfFile = Boolean(m.filePath);
        const isPdf = hasPdfFile || m.sourceType === 'COURSE_PDF' || (m.referenceUrlOrPage && m.referenceUrlOrPage.toLowerCase().includes('.pdf'));

        // Extract a clean, single-paragraph preview excerpt (max 240 chars)
        const cleanContent = (m.contentText || '').trim().replace(/\s+/g, ' ');
        const excerpt = cleanContent.length > 240 ? cleanContent.substring(0, 240) + '...' : cleanContent;

        // Construir botones según el tipo de documento
        let actionButtonsHtml = '';

        if (hasPdfFile) {
            actionButtonsHtml += `
                <button onclick="openPdfViewer(${m.id})" class="px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold transition shadow-md shadow-rose-600/30 flex items-center gap-1.5 cursor-pointer">
                    📄 Ver PDF Original
                </button>
            `;
        } else if (isPdf && isUrl) {
            actionButtonsHtml += `
                <button onclick="window.open('${m.referenceUrlOrPage}', '_blank')" class="px-3.5 py-2 rounded-xl bg-rose-600 hover:bg-rose-500 text-white font-bold transition shadow-md shadow-rose-600/30 flex items-center gap-1.5 cursor-pointer">
                    📄 Ver PDF Remoto ↗
                </button>
            `;
        }

        if (isUrl) {
            actionButtonsHtml += `
                <button onclick="window.open('${m.referenceUrlOrPage}', '_blank')" class="px-3.5 py-2 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-bold transition shadow-md shadow-blue-600/30 flex items-center gap-1.5 cursor-pointer">
                    🌐 Abrir Web Original ↗
                </button>
            `;
        }

        actionButtonsHtml += `
            <button onclick="openViewMaterialModal(${m.id})" class="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold transition shadow-md shadow-indigo-600/30 flex items-center gap-1.5 cursor-pointer">
                📖 Ver Texto Extraído
            </button>
        `;

        if (topicId) {
            actionButtonsHtml += `
                <button onclick="startExamWithTopic(${topicId})" class="px-3.5 py-2 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold transition shadow-md shadow-emerald-600/30 flex items-center gap-1.5 cursor-pointer">
                    🎯 Practicar Preguntas
                </button>
            `;
        }

        card.innerHTML = `
            <div class="flex items-center justify-between text-xs">
                <span class="font-bold text-white text-sm">${m.title}</span>
                <div class="flex items-center gap-2">
                    <span class="px-2 py-0.5 rounded ${hasPdfFile ? 'bg-rose-500/20 text-rose-400 border border-rose-500/30' : isUrl ? 'bg-blue-500/20 text-blue-400 border border-blue-500/30' : 'bg-slate-700 text-slate-300'} font-mono text-[10px] font-bold">
                        ${hasPdfFile ? 'PDF (PDFBox)' : m.sourceType}
                    </span>
                    <span class="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px] font-mono">${wordsCount} palabras</span>
                </div>
            </div>
            
            <div class="flex flex-wrap items-center gap-2 text-xs">
                <span class="text-blue-400 font-semibold">📁 Tema: ${topicName}</span>
                ${isUrl ? `
                    <span class="text-slate-600">•</span>
                    <a href="${m.referenceUrlOrPage}" target="_blank" class="text-blue-400 hover:underline flex items-center gap-1 font-mono text-[11px]">
                        🔗 ${m.referenceUrlOrPage.length > 55 ? m.referenceUrlOrPage.substring(0, 55) + '...' : m.referenceUrlOrPage} ↗
                    </a>
                ` : (m.filePath ? `
                    <span class="text-slate-600">•</span>
                    <span class="text-slate-400 font-mono text-[11px]">📁 Archivo local guardado</span>
                ` : '')}
            </div>

            <div class="bg-slate-950/70 p-3.5 rounded-xl border border-slate-800 text-xs text-slate-300 font-mono leading-relaxed overflow-hidden">
                ${excerpt || '<span class="text-slate-500 italic">(Sin contenido textual disponible)</span>'}
            </div>

            <div class="flex flex-wrap items-center justify-between gap-2 pt-3 border-t border-slate-800 text-xs">
                <div class="flex flex-wrap items-center gap-2">
                    ${actionButtonsHtml}
                </div>
                <button onclick="deleteMaterial(${m.id})" class="px-3 py-1.5 rounded-lg bg-slate-800 hover:bg-rose-950/60 text-slate-400 hover:text-rose-300 border border-slate-700 text-xs font-semibold transition cursor-pointer">
                    🗑️ Eliminar
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}

let activeMaterialText = '';
let currentPdfId = null;

function openPdfViewer(id) {
    const mat = allMaterials.find(m => m.id === id);
    if (!mat) return;

    currentPdfId = id;
    const fileUrl = `/api/materials/${id}/file`;

    document.getElementById('pdf-viewer-title').textContent = mat.title;
    document.getElementById('pdf-viewer-topic').textContent = mat.topic ? mat.topic.name : 'General';

    const downloadBtn = document.getElementById('pdf-viewer-btn-download');
    downloadBtn.href = fileUrl;
    const safeTitle = (mat.title || 'documento').replace(/[^a-zA-Z0-9._-]/g, '_');
    downloadBtn.setAttribute('download', safeTitle.toLowerCase().endsWith('.pdf') ? safeTitle : safeTitle + '.pdf');

    const frame = document.getElementById('pdf-viewer-frame');
    frame.src = fileUrl;

    openModal('modal-pdf-viewer');
}

function openCurrentPdfInTab() {
    if (currentPdfId) {
        window.open(`/api/materials/${currentPdfId}/file`, '_blank');
    }
}

function closePdfViewer() {
    const frame = document.getElementById('pdf-viewer-frame');
    if (frame) frame.src = 'about:blank';
    closeModal('modal-pdf-viewer');
    currentPdfId = null;
}

function openViewMaterialModal(id) {
    const mat = allMaterials.find(m => m.id === id);
    if (!mat) return;

    activeMaterialText = mat.contentText;
    document.getElementById('view-mat-title').textContent = mat.title;
    document.getElementById('view-mat-topic').textContent = mat.topic ? mat.topic.name : 'General';
    const words = mat.contentText ? mat.contentText.trim().split(/\s+/).length : 0;
    document.getElementById('view-mat-words').textContent = `${words} palabras extraídas`;
    document.getElementById('view-mat-content').textContent = mat.contentText;

    const linkElem = document.getElementById('view-mat-link');
    if (mat.referenceUrlOrPage && (mat.referenceUrlOrPage.startsWith('http://') || mat.referenceUrlOrPage.startsWith('https://'))) {
        linkElem.href = mat.referenceUrlOrPage;
        linkElem.classList.remove('hidden');
    } else {
        linkElem.classList.add('hidden');
    }

    const pdfBtn = document.getElementById('view-mat-btn-pdf');
    if (mat.filePath) {
        pdfBtn.onclick = () => {
            closeModal('modal-view-material');
            openPdfViewer(mat.id);
        };
        pdfBtn.classList.remove('hidden');
    } else {
        pdfBtn.classList.add('hidden');
    }

    const practiceBtn = document.getElementById('view-mat-btn-practice');
    if (mat.topic && mat.topic.id) {
        practiceBtn.onclick = () => {
            closeModal('modal-view-material');
            startExamWithTopic(mat.topic.id);
        };
        practiceBtn.classList.remove('hidden');
    } else {
        practiceBtn.classList.add('hidden');
    }

    openModal('modal-view-material');
}

function copyMaterialText() {
    if (!activeMaterialText) return;
    navigator.clipboard.writeText(activeMaterialText)
        .then(() => showToast('¡Texto del documento copiado al portapapeles!', 'success'))
        .catch(() => showToast('No se pudo copiar el texto.', 'error'));
}

async function deleteMaterial(id) {
    const ok = await showConfirmDialog('Eliminar Documento', '¿Estás seguro de que deseas eliminar este documento indexado? Se eliminará tanto el texto del buscador como el archivo en disco.', {
        confirmText: 'Eliminar',
        danger: true,
        subtitle: 'Acción irreversible'
    });
    if (!ok) return;

    try {
        const resp = await fetch(`/api/materials/${id}`, { method: 'DELETE' });
        if (resp.ok) {
            showToast('Documento indexado eliminado correctamente.', 'success');
            await loadMaterials();
        } else {
            showToast('Error al eliminar el material.', 'error');
        }
    } catch (e) {
        console.error('Error eliminando material:', e);
        showToast('Error de red al eliminar el material.', 'error');
    }
}

function switchMaterialInputMode(mode) {
    const urlForm = document.getElementById('import-url-form');
    const pdfForm = document.getElementById('upload-pdf-form');
    const urlBtn = document.getElementById('tab-mat-url-btn');
    const pdfBtn = document.getElementById('tab-mat-pdf-btn');

    if (mode === 'url') {
        urlForm.classList.remove('hidden');
        pdfForm.classList.add('hidden');
        urlBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-blue-600 text-white transition flex items-center gap-1.5 cursor-pointer';
        pdfBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-slate-700 text-slate-300 hover:text-white transition flex items-center gap-1.5 cursor-pointer';
    } else {
        urlForm.classList.add('hidden');
        pdfForm.classList.remove('hidden');
        pdfBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 text-white transition flex items-center gap-1.5 cursor-pointer';
        urlBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-slate-700 text-slate-300 hover:text-white transition flex items-center gap-1.5 cursor-pointer';
    }
}

function loadSampleFhirUrl() {
    document.getElementById('import-mat-url').value = 'https://www.hl7.org/fhir/references.html';
    document.getElementById('import-url-title').value = 'HL7 FHIR R4 - References & Resolution';
    
    // Auto-select topic 'RESOURCES' (ID 3) if available
    const topicSelect = document.getElementById('import-url-topic-id');
    for (let i = 0; i < topicSelect.options.length; i++) {
        if (topicSelect.options[i].text.includes('Recursos') || topicSelect.options[i].value === '3') {
            topicSelect.selectedIndex = i;
            break;
        }
    }
}

async function importUrlMaterial(event) {
    event.preventDefault();
    const urlInput = document.getElementById('import-mat-url');
    const topicSelect = document.getElementById('import-url-topic-id');
    const titleInput = document.getElementById('import-url-title');
    const statusSpan = document.getElementById('import-url-status');
    const submitBtn = document.getElementById('btn-import-url-submit');

    const url = urlInput.value.trim();
    if (!url) {
        showToast('Por favor introduce una URL válida.', 'warning');
        return;
    }

    statusSpan.classList.remove('hidden');
    submitBtn.disabled = true;
    submitBtn.classList.add('opacity-50');

    try {
        const resp = await fetch('/api/materials/url', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                url: url,
                topicId: topicSelect.value,
                title: titleInput.value.trim() || null
            })
        });

        if (resp.ok) {
            showToast('¡Contenido descargado e indexado con éxito desde la URL!', 'success');
            urlInput.value = '';
            titleInput.value = '';
            await loadMaterials();
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast('Error descargando la URL: ' + (err.error || 'Verifica la URL y la conexión.'), 'error');
        }
    } catch (e) {
        console.error('Error importando URL:', e);
        showToast('Error en la petición: ' + e.message, 'error');
    } finally {
        statusSpan.classList.add('hidden');
        submitBtn.disabled = false;
        submitBtn.classList.remove('opacity-50');
    }
}

async function uploadPdf(event) {
    event.preventDefault();
    const fileInput = document.getElementById('upload-file');
    const topicSelect = document.getElementById('upload-topic-id');
    const titleInput = document.getElementById('upload-title');
    const submitBtn = event.target.querySelector('button[type="submit"]');

    if (!fileInput.files || fileInput.files.length === 0) {
        showToast('Por favor selecciona un archivo PDF.', 'warning');
        return;
    }

    const originalBtnText = submitBtn.innerHTML;
    submitBtn.disabled = true;
    submitBtn.innerHTML = '<span class="animate-spin mr-1.5 inline-block">⏳</span> Indexando con Apache PDFBox...';
    submitBtn.classList.add('opacity-75');

    const formData = new FormData();
    formData.append('file', fileInput.files[0]);
    formData.append('topicId', topicSelect.value);
    if (titleInput.value) {
        formData.append('title', titleInput.value);
    }

    try {
        const resp = await fetch('/api/materials/upload', {
            method: 'POST',
            body: formData
        });

        if (resp.ok) {
            showToast('¡PDF procesado e indexado con Apache PDFBox con éxito!', 'success');
            fileInput.value = '';
            titleInput.value = '';
            await loadMaterials();
        } else {
            showToast('Error al procesar el archivo PDF.', 'error');
        }
    } catch (e) {
        console.error('Error subiendo PDF:', e);
        showToast('Error de red al subir el PDF.', 'error');
    } finally {
        submitBtn.disabled = false;
        submitBtn.innerHTML = originalBtnText;
        submitBtn.classList.remove('opacity-75');
    }
}

// =================== GESTIÓN DE TEMAS Y PREGUNTAS (MODALES) ===================
function openCreateTopicModal() {
    openModal('modal-create-topic');
}

async function submitCreateTopic() {
    const code = document.getElementById('new-topic-code').value.trim().toUpperCase();
    const name = document.getElementById('new-topic-name').value.trim();
    const weight = parseFloat(document.getElementById('new-topic-weight').value || '10');

    if (!code || !name) {
        showToast('Por favor indica el código y el nombre del tema.', 'warning');
        return;
    }

    try {
        const resp = await fetch('/api/topics', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ code, name, examWeightPercent: weight, studyStatus: 'PENDING' })
        });
        if (resp.ok) {
            closeModal('modal-create-topic');
            document.getElementById('new-topic-code').value = '';
            document.getElementById('new-topic-name').value = '';
            await loadTopics();
            await loadDashboard();
            showToast('¡Nuevo tema añadido al Syllabus con éxito!', 'success');
        } else {
            showToast('Error al crear el tema (verifica que el código no esté duplicado).', 'error');
        }
    } catch (e) {
        console.error('Error creando tema:', e);
        showToast('Error de red al crear el tema.', 'error');
    }
}

function openCreateQuestionModal() {
    openModal('modal-create-question');
}

async function submitCreateQuestion() {
    const topicId = parseInt(document.getElementById('new-q-topic-id').value);
    const difficulty = parseInt(document.getElementById('new-q-difficulty').value);
    const stem = document.getElementById('new-q-stem').value.trim();
    const codeSnippet = document.getElementById('new-q-snippet').value.trim() || null;
    const explanation = document.getElementById('new-q-explanation').value.trim();
    const fhirSpecUrl = document.getElementById('new-q-url').value.trim() || null;
    const correctRadio = document.querySelector('input[name="new-q-correct"]:checked');
    const correctLabel = correctRadio ? correctRadio.value : 'A';

    const optA = document.getElementById('new-q-opt-a').value.trim();
    const optB = document.getElementById('new-q-opt-b').value.trim();
    const optC = document.getElementById('new-q-opt-c').value.trim();
    const optD = document.getElementById('new-q-opt-d').value.trim();

    if (!stem || !explanation || !optA || !optB) {
        showToast('Completa al menos el enunciado, la explicación y las opciones A y B.', 'warning');
        return;
    }

    const options = [
        { optionLabel: 'A', optionText: optA, correct: correctLabel === 'A', distractorReason: document.getElementById('new-q-why-a').value.trim() || null },
        { optionLabel: 'B', optionText: optB, correct: correctLabel === 'B', distractorReason: document.getElementById('new-q-why-b').value.trim() || null }
    ];
    if (optC) {
        options.push({ optionLabel: 'C', optionText: optC, correct: correctLabel === 'C', distractorReason: document.getElementById('new-q-why-c').value.trim() || null });
    }
    if (optD) {
        options.push({ optionLabel: 'D', optionText: optD, correct: correctLabel === 'D', distractorReason: document.getElementById('new-q-why-d').value.trim() || null });
    }

    const payload = {
        topicId,
        difficulty,
        stem,
        codeSnippet,
        explanation,
        fhirSpecUrl,
        questionType: 'SINGLE_CHOICE',
        source: 'MANUAL',
        options
    };

    try {
        const resp = await fetch('/api/questions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (resp.ok) {
            closeModal('modal-create-question');
            document.getElementById('new-q-stem').value = '';
            document.getElementById('new-q-snippet').value = '';
            document.getElementById('new-q-explanation').value = '';
            document.getElementById('new-q-opt-a').value = '';
            document.getElementById('new-q-opt-b').value = '';
            document.getElementById('new-q-opt-c').value = '';
            document.getElementById('new-q-opt-d').value = '';
            await loadQuestions();
            await loadDashboard();
            showToast('¡Pregunta creada y añadida al banco y al sistema SM-2!', 'success');
        } else {
            showToast('Error al guardar la pregunta.', 'error');
        }
    } catch (e) {
        console.error('Error creando pregunta:', e);
        showToast('Error de red al guardar la pregunta.', 'error');
    }
}

function openImportModal() {
    openModal('modal-import-json');
}

function loadSampleImportJson() {
    const sample = [
        {
            "topicId": 2,
            "stem": "¿Qué parámetro de resultado en una búsqueda FHIR R4 permite solicitar al servidor únicamente el número total de coincidencias sin devolver los recursos?",
            "codeSnippet": "GET [base]/Observation?_summary=count",
            "explanation": "El parámetro _summary=count indica al servidor que devuelva un Bundle con el atributo total poblado pero sin entradas entry.",
            "fhirSpecUrl": "https://hl7.org/fhir/R4/search.html#summary",
            "difficulty": 2,
            "questionType": "SINGLE_CHOICE",
            "source": "MANUAL",
            "options": [
                { "optionLabel": "A", "optionText": "_count=0", "correct": false, "distractorReason": "_count=0 es equivalente en algunos servidores, pero el parámetro estándar para resumen de conteo es _summary=count." },
                { "optionLabel": "B", "optionText": "_summary=count", "correct": true, "distractorReason": "Correcto: _summary=count devuelve únicamente el total de recursos coincidentes." },
                { "optionLabel": "C", "optionText": "_total=only", "correct": false, "distractorReason": "_total controla la precisión del conteo (none, estimate, accurate), no existe el valor 'only'." },
                { "optionLabel": "D", "optionText": "_elements=total", "correct": false, "distractorReason": "_elements filtra elementos raíz de los recursos devueltos, no el metadato del Bundle." }
            ]
        }
    ];
    document.getElementById('import-json-textarea').value = JSON.stringify(sample, null, 2);
}

async function submitImportJson() {
    const jsonText = document.getElementById('import-json-textarea').value.trim();
    if (!jsonText) {
        showToast('Pega un JSON válido para importar.', 'warning');
        return;
    }

    try {
        const resp = await fetch('/api/questions/import', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: jsonText
        });
        const data = await resp.json();
        if (resp.ok && data.success) {
            closeModal('modal-import-json');
            document.getElementById('import-json-textarea').value = '';
            await loadQuestions();
            await loadDashboard();
            showToast(`¡Importación exitosa! Se añadieron ${data.importedCount} preguntas al banco.`, 'success');
        } else {
            showToast('Error en el formato JSON: ' + (data.error || 'Verifica la estructura.'), 'error');
        }
    } catch (e) {
        showToast('Error al procesar el JSON: ' + e.message, 'error');
    }
}

