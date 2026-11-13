// Estado global de la aplicación
let currentUser = null;
let isMandatoryPasswordChange = false;
let editingQuestionId = null;

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

// =================== CENTRAL FETCH WRAPPER (CSRF + 401/403 UNIFICADO) ===================
const nativeFetch = window.fetch.bind(window);

function getCsrfTokenFromCookie() {
    const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
    return match ? decodeURIComponent(match[1]) : null;
}

async function apiFetch(url, options = {}) {
    const opts = { ...options };
    opts.credentials = opts.credentials || 'same-origin';

    const method = (opts.method || 'GET').toUpperCase();
    const headers = new Headers(opts.headers || {});

    if (['POST', 'PUT', 'DELETE', 'PATCH'].includes(method)) {
        const csrfToken = getCsrfTokenFromCookie();
        if (csrfToken && !headers.has('X-XSRF-TOKEN')) {
            headers.set('X-XSRF-TOKEN', csrfToken);
        }
    }
    opts.headers = headers;

    const resp = await nativeFetch(url, opts);

    const urlStr = typeof url === 'string' ? url : (url.url || '');
    const isAuthProbe = urlStr.includes('/api/auth/me') || urlStr.includes('/api/auth/login');

    if (resp.status === 401 && !isAuthProbe) {
        currentUser = null;
        showLoginScreen('Tu sesión ha expirado o no estás autenticado. Por favor inicia sesión.');
    } else if (resp.status === 403 && !isAuthProbe) {
        const cloned = resp.clone();
        cloned.json().then(data => {
            if (data && data.mustChangePassword) {
                openChangePasswordModal(true);
            }
        }).catch(() => {});
    }

    return resp;
}

// Interceptar todas las llamadas a window.fetch hacia /api/** para garantizar envío de X-XSRF-TOKEN y manejo 401/403
window.fetch = function(input, init) {
    const urlStr = typeof input === 'string' ? input : (input && input.url ? input.url : '');
    if (urlStr.startsWith('/api/')) {
        return apiFetch(input, init);
    }
    return nativeFetch(input, init);
};

document.addEventListener('DOMContentLoaded', () => {
    setupModalInteractions();
    initAuthAndBootstrapApp();
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
function openMaterialsSection(subview = 'docs') {
    switchTab('materials', subview);
}

function switchTab(tabId, materialSubview = null) {
    if (tabId.startsWith('admin-') && (!currentUser || currentUser.role !== 'ADMIN')) {
        showToast('Acceso restringido a administradores.', 'error');
        return;
    }

    const views = [
        'dashboard', 'exam-modes', 'exam-runner', 'exam-result',
        'search-builder', 'resource-inspector', 'flashcards',
        'questions', 'materials',
        'admin-users', 'admin-results', 'admin-aggregate', 'admin-audit'
    ];

    views.forEach(v => {
        const el = document.getElementById(`view-${v}`);
        if (el) el.classList.add('hidden');
        const btn = document.getElementById(`btn-tab-${v}`);
        if (btn) btn.classList.remove('active');
    });
    const cardsSidebarBtn = document.getElementById('btn-tab-study-cards');
    if (cardsSidebarBtn) cardsSidebarBtn.classList.remove('active');

    const activeView = document.getElementById(`view-${tabId}`);
    if (activeView) {
        activeView.classList.remove('hidden');
        // Trigger subtle view entrance animation
        activeView.classList.remove('view-enter');
        void activeView.offsetWidth; // force reflow for retriggering
        activeView.classList.add('view-enter');
    }

    if (tabId === 'materials') {
        const targetSub = materialSubview || activeMaterialSubview || 'docs';
        switchMaterialSubview(targetSub);
        if (targetSub === 'cards' && cardsSidebarBtn) {
            cardsSidebarBtn.classList.add('active');
        } else {
            const activeBtn = document.getElementById('btn-tab-materials');
            if (activeBtn) activeBtn.classList.add('active');
        }
        loadMaterials();
    } else {
        const activeBtn = document.getElementById(`btn-tab-${tabId}`);
        if (activeBtn) activeBtn.classList.add('active');
    }

    // Acciones específicas por pestaña
    if (tabId === 'dashboard') loadDashboard();
    if (tabId === 'flashcards') loadFlashcards();
    if (tabId === 'questions') loadQuestions();
    if (tabId === 'admin-users') loadAdminUsers(0);
    if (tabId === 'admin-results') loadAdminUsersKpiList();
    if (tabId === 'admin-aggregate') loadAdminAggregateView();
    if (tabId === 'admin-audit') loadAdminAuditLogs(0);
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
    const selects = [
        'modal-topic-id', 'q-topic-select', 'upload-topic-id', 'new-q-topic-id',
        'mat-topic-select', 'import-url-topic-id', 'import-md-topic-id',
        'import-json-topic-id', 'import-cards-topic-id'
    ];
    selects.forEach(selectId => {
        const el = document.getElementById(selectId);
        if (!el) return;
        const firstOption = el.options[0];
        el.innerHTML = '';
        if (
            firstOption &&
            selectId !== 'upload-topic-id' &&
            selectId !== 'new-q-topic-id' &&
            selectId !== 'import-url-topic-id' &&
            selectId !== 'import-md-topic-id' &&
            selectId !== 'import-cards-topic-id'
        ) {
            el.appendChild(firstOption);
        }

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
                    <td class="py-2.5 px-4 flex items-center gap-2">
                        <span class="px-2 py-0.5 text-[10px] font-bold rounded-full ${isPass ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                            ${isPass ? 'APROBADO' : 'SUSPENSO'}
                        </span>
                        <button onclick="openAttemptReviewModal(${a.id})" class="px-2 py-0.5 rounded bg-slate-900 hover:bg-slate-700 text-blue-300 border border-slate-700 text-[10px] font-semibold transition cursor-pointer">
                            👁️ Ver
                        </button>
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
    const matIdVal = document.getElementById('modal-material-id') ? document.getElementById('modal-material-id').value : '';
    const materialId = matIdVal ? parseInt(matIdVal, 10) : null;

    closeModal('modal-new-exam');
    executeStartExam({ mode, questionCount: count, topicId, materialId });
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

    const matBadge = document.getElementById('runner-material-badge');
    if (matBadge) {
        const materialTitle = q.materialTitle || (currentSession ? currentSession.materialTitle : null);
        if (materialTitle) {
            matBadge.textContent = `📄 ${materialTitle}`;
            matBadge.classList.remove('hidden');
        } else {
            matBadge.classList.add('hidden');
        }
    }
    
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

        const canEditQ = canModifyItem(q);
        const isOwnQ = currentUser && q.ownerId === currentUser.id;
        const ownerBadge = isOwnQ
            ? '<span class="px-2 py-0.5 rounded bg-emerald-500/15 text-emerald-300 border border-emerald-500/30 text-[10px] font-semibold">👤 Creada por ti</span>'
            : `<span class="px-2 py-0.5 rounded bg-slate-900 text-slate-400 text-[10px] font-mono">Autor #${q.ownerId || 1}</span>`;

        card.innerHTML = `
            <div class="flex flex-wrap items-center justify-between gap-2 text-xs">
                <span class="font-semibold text-blue-400">${q.topic ? q.topic.name : 'General'}</span>
                <div class="flex flex-wrap items-center gap-1.5">
                    ${ownerBadge}
                    <span class="px-2 py-0.5 rounded bg-slate-700 text-slate-300 text-[10px] font-mono">${q.source}</span>
                    <span class="px-2 py-0.5 rounded bg-slate-700 text-slate-300 text-[10px]">Dificultad: ${q.difficulty}/3</span>
                </div>
            </div>

            <div class="text-sm font-semibold text-white leading-relaxed">${q.stem}</div>

            ${q.codeSnippet ? `<pre class="bg-slate-950 p-3 rounded-xl border border-slate-800 text-xs font-mono text-emerald-400 whitespace-pre-wrap">${q.codeSnippet}</pre>` : ''}

            <div class="space-y-1.5 pl-2 border-l-2 border-slate-700">
                ${optionsHtml}
            </div>

            <div class="pt-2 flex flex-wrap justify-between items-center gap-2 text-xs">
                <span class="text-slate-400">${q.fhirSpecUrl ? `<a href="${q.fhirSpecUrl}" target="_blank" class="text-blue-400 hover:underline">hl7.org/fhir →</a>` : ''}</span>
                <div class="flex items-center gap-2">
                    <button onclick="createMirrorVariant(${q.id})" class="px-3 py-1 rounded bg-indigo-600/30 hover:bg-indigo-600/50 text-indigo-300 border border-indigo-500/30 text-xs font-medium transition cursor-pointer">
                        ✨ Clonar y Generar Variante
                    </button>
                    ${canEditQ ? `
                        <button onclick="openEditQuestionModal(${q.id})" class="px-2.5 py-1 rounded bg-slate-700 hover:bg-slate-600 text-slate-200 text-xs font-medium transition cursor-pointer">
                            ✏️ Editar
                        </button>
                        <button onclick="deleteQuestionById(${q.id})" class="px-2.5 py-1 rounded bg-rose-950/60 hover:bg-rose-900/80 text-rose-300 border border-rose-500/30 text-xs font-medium transition cursor-pointer">
                            🗑️
                        </button>
                    ` : ''}
                </div>
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
            filterMaterials();
            populateMaterialSelects();
            if (typeof renderStudyCardsSection === 'function') {
                renderStudyCardsSection();
            }
        }
    } catch (e) {
        console.error('Error cargando materiales:', e);
    }
}

function populateMaterialSelects() {
    const selects = ['modal-material-id', 'new-q-material-id', 'import-json-material-id'];
    selects.forEach(selectId => {
        const el = document.getElementById(selectId);
        if (!el) return;
        const firstOption = el.options[0];
        el.innerHTML = '';
        if (firstOption) el.appendChild(firstOption);

        allMaterials.forEach(m => {
            const opt = document.createElement('option');
            opt.value = m.id;
            const topicPrefix = m.topic ? `[${m.topic.code || m.topic.name}] ` : '';
            opt.textContent = `${topicPrefix}${m.title}`;
            el.appendChild(opt);
        });
    });
}

let activeMaterialTypeFilter = 'ALL';
let activeMaterialSubview = 'docs';

function isStudyCardsMaterial(m) {
    if (!m) return false;
    if (m.sourceType === 'STUDY_CARDS') return true;
    const text = m.contentText || '';
    return text.includes('### Ficha');
}

function getMaterialKind(m) {
    if (isStudyCardsMaterial(m)) return 'STUDY_CARDS';
    const isMarkdown = m.sourceType === 'MARKDOWN' || (m.filePath && m.filePath.toLowerCase().endsWith('.md')) || (m.referenceUrlOrPage && m.referenceUrlOrPage.toLowerCase().endsWith('.md'));
    if (isMarkdown) return 'MARKDOWN';
    const hasPdfFile = Boolean(m.filePath && m.filePath.toLowerCase().endsWith('.pdf'));
    const isPdf = hasPdfFile || m.sourceType === 'COURSE_PDF' || (m.referenceUrlOrPage && m.referenceUrlOrPage.toLowerCase().includes('.pdf'));
    if (isPdf) return 'PDF';
    return 'URL';
}

function toggleMaterialUploadDrawer(forceState) {
    const drawer = document.getElementById('mat-upload-drawer');
    const icon = document.getElementById('btn-toggle-upload-icon');
    const label = document.getElementById('btn-toggle-upload-label');
    const btn = document.getElementById('btn-toggle-upload-drawer');
    if (!drawer) return;

    const isHidden = drawer.classList.contains('hidden');
    const shouldOpen = typeof forceState === 'boolean' ? forceState : isHidden;

    if (shouldOpen) {
        drawer.classList.remove('hidden');
        if (icon) icon.textContent = '✕';
        if (label) label.textContent = 'Cerrar Panel';
        if (btn) btn.className = 'px-4 py-2 rounded-xl text-xs font-bold bg-slate-700 hover:bg-slate-600 text-slate-200 transition flex items-center justify-center gap-2 cursor-pointer shrink-0';
    } else {
        drawer.classList.add('hidden');
        if (icon) icon.textContent = '＋';
        if (label) label.textContent = 'Añadir Documento';
        if (btn) btn.className = 'px-4 py-2 rounded-xl text-xs font-bold bg-blue-600 hover:bg-blue-500 active:scale-[0.98] text-white transition flex items-center justify-center gap-2 cursor-pointer shadow-lg shadow-blue-600/25 shrink-0';
    }
}

function filterMaterialsByType(type) {
    activeMaterialTypeFilter = type || 'ALL';
    const types = ['ALL', 'PDF', 'MARKDOWN', 'URL', 'STUDY_CARDS'];
    types.forEach(t => {
        const btn = document.getElementById(`mat-filter-${t}`);
        if (!btn) return;
        if (t === activeMaterialTypeFilter) {
            btn.className = 'px-3 py-1.5 rounded-xl text-xs font-bold bg-blue-600 text-white transition cursor-pointer shrink-0 flex items-center gap-1 shadow-sm shadow-blue-600/30';
        } else {
            btn.className = 'px-3 py-1.5 rounded-xl text-xs font-semibold bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700/80 transition cursor-pointer shrink-0 flex items-center gap-1';
        }
    });
    filterMaterials();
}

function filterMaterials() {
    const searchEl = document.getElementById('mat-search-input');
    const topicEl = document.getElementById('mat-topic-select');
    const kw = searchEl ? searchEl.value.toLowerCase().trim() : '';
    const topicId = topicEl ? topicEl.value : '';

    const filtered = allMaterials.filter(m => {
        const idStr = `#${m.id}`;
        const matchKw = !kw ||
            (m.title && m.title.toLowerCase().includes(kw)) ||
            (m.contentText && m.contentText.toLowerCase().includes(kw)) ||
            idStr.includes(kw) ||
            String(m.id) === kw;
        const matchTopic = !topicId || (m.topic && m.topic.id == topicId);
        const matchType = activeMaterialTypeFilter === 'ALL' || getMaterialKind(m) === activeMaterialTypeFilter;
        return matchKw && matchTopic && matchType;
    });

    renderMaterials(filtered);
}

function openPrimaryMaterialView(id) {
    const m = allMaterials.find(item => item.id === id);
    if (!m) return;
    if (getMaterialKind(m) === 'STUDY_CARDS') {
        openStudyCardDeckInPresentation(m.id);
        return;
    }
    const hasPdfFile = Boolean(m.filePath && m.filePath.toLowerCase().endsWith('.pdf'));
    if (hasPdfFile) {
        openPdfViewer(m.id);
    } else {
        openViewMaterialModal(m.id);
    }
}

function renderMaterials(list) {
    const container = document.getElementById('materials-container');
    if (!container) return;
    container.innerHTML = '';

    const countBadge = document.getElementById('mat-total-count-badge');
    if (countBadge) {
        countBadge.textContent = `${allMaterials.length}`;
    }

    const cardsList = allMaterials.filter(m => isStudyCardsMaterial(m));
    const cardsBadge = document.getElementById('mat-cards-count-badge');
    if (cardsBadge) cardsBadge.textContent = `${cardsList.length}`;
    const sidebarCardsBadge = document.getElementById('badge-sidebar-cards-count');
    if (sidebarCardsBadge) sidebarCardsBadge.textContent = `${cardsList.length}`;

    if (list.length === 0) {
        container.innerHTML = `
            <div class="lg:col-span-2 p-10 rounded-2xl bg-slate-900/50 border border-slate-800 text-center space-y-3">
                <div class="w-12 h-12 mx-auto rounded-2xl bg-slate-800/80 flex items-center justify-center text-xl">📑</div>
                <h4 class="text-sm font-bold text-white">No se encontraron materiales</h4>
                <p class="text-xs text-slate-400 max-w-md mx-auto">No hay apuntes ni documentos que coincidan con la búsqueda actual. Prueba a cambiar los filtros o pulsa <strong>"＋ Añadir Documento"</strong> arriba a la derecha.</p>
            </div>
        `;
        return;
    }

    list.forEach(m => {
        const card = document.createElement('div');
        card.className = 'group bg-slate-800/55 hover:bg-slate-800/90 p-5 rounded-2xl border border-slate-700/70 hover:border-slate-600 transition flex flex-col justify-between gap-4 shadow-sm';

        const wordsCount = m.contentText ? m.contentText.trim().split(/\s+/).length : 0;
        const topicName = m.topic ? m.topic.name : 'General';
        const kind = getMaterialKind(m);
        const hasPdfFile = Boolean(m.filePath && m.filePath.toLowerCase().endsWith('.pdf'));
        const isUrl = Boolean(m.referenceUrlOrPage && (m.referenceUrlOrPage.startsWith('http://') || m.referenceUrlOrPage.startsWith('https://')));

        // Extract a clean, readable 2-line preview (strip markdown symbols for cleaner reading)
        const cleanContent = (m.contentText || '')
            .replace(/[#*`>_~-]{2,}/g, ' ')
            .replace(/\s+/g, ' ')
            .trim();
        const excerpt = cleanContent.length > 180 ? cleanContent.substring(0, 180) + '…' : cleanContent;

        let typeIcon = '📄';
        let typeBadgeClass = 'bg-slate-700/80 text-slate-300 border-slate-600';
        let typeLabel = 'DOC';
        let primaryReadLabel = '📖 Leer Texto';

        if (kind === 'STUDY_CARDS') {
            typeIcon = '🗂️';
            typeBadgeClass = 'bg-indigo-500/15 text-indigo-300 border-indigo-500/30';
            typeLabel = 'FICHAS';
            primaryReadLabel = '🗂️ Ver Presentación';
        } else if (kind === 'MARKDOWN') {
            typeIcon = '📝';
            typeBadgeClass = 'bg-emerald-500/15 text-emerald-300 border-emerald-500/30';
            typeLabel = 'MARKDOWN';
            primaryReadLabel = '📖 Leer Apunte';
        } else if (kind === 'PDF') {
            typeIcon = '📄';
            typeBadgeClass = 'bg-rose-500/15 text-rose-300 border-rose-500/30';
            typeLabel = 'PDF';
            primaryReadLabel = hasPdfFile ? '📄 Abrir PDF' : '📖 Leer Texto';
        } else {
            typeIcon = '🌐';
            typeBadgeClass = 'bg-blue-500/15 text-blue-300 border-blue-500/30';
            typeLabel = 'WEB';
            primaryReadLabel = '📖 Leer Texto';
        }

        // Utility icon button (external URL or direct file download)
        let utilityBtnHtml = '';
        if (isUrl) {
            utilityBtnHtml = `
                <a href="${m.referenceUrlOrPage}" target="_blank" rel="noopener" title="Abrir web original en nueva pestaña" class="w-7 h-7 rounded-lg bg-slate-900/70 hover:bg-slate-700 text-slate-400 hover:text-blue-300 border border-slate-700/70 flex items-center justify-center text-xs transition cursor-pointer">
                    ↗
                </a>
            `;
        } else if (m.filePath || kind === 'MARKDOWN' || kind === 'STUDY_CARDS') {
            utilityBtnHtml = `
                <a href="/api/materials/${m.id}/file?download=true" download title="Descargar archivo original" class="w-7 h-7 rounded-lg bg-slate-900/70 hover:bg-slate-700 text-slate-400 hover:text-emerald-300 border border-slate-700/70 flex items-center justify-center text-xs transition cursor-pointer">
                    ⬇
                </a>
            `;
        }

        card.innerHTML = `
            <div class="space-y-2.5">
                <!-- Card Top Row: Type Icon + Title + Unobtrusive Utilities -->
                <div class="flex items-start justify-between gap-3">
                    <div class="flex items-start gap-3 min-w-0">
                        <button type="button" onclick="openPrimaryMaterialView(${m.id})" title="${primaryReadLabel}" class="w-10 h-10 rounded-xl border ${typeBadgeClass} flex items-center justify-center text-lg shrink-0 cursor-pointer hover:scale-105 transition">
                            ${typeIcon}
                        </button>
                        <div class="min-w-0">
                            <button type="button" onclick="openPrimaryMaterialView(${m.id})" class="text-left font-bold text-white hover:text-blue-400 text-sm leading-snug line-clamp-1 transition cursor-pointer" title="${m.title}">
                                ${m.title}
                            </button>
                            <div class="flex flex-wrap items-center gap-1.5 mt-1 text-[11px] text-slate-400">
                                <span class="px-1.5 py-0.5 rounded font-mono text-[10px] font-bold bg-indigo-500/15 text-indigo-300 border border-indigo-500/30" title="Identificador del documento para importar preguntas JSON">ID #${m.id}</span>
                                <span class="px-1.5 py-0.5 rounded font-mono text-[10px] font-semibold border ${typeBadgeClass}">${typeLabel}</span>
                                <span class="text-slate-600">•</span>
                                <span class="text-blue-400 font-medium truncate max-w-[180px]" title="${topicName}">${topicName}</span>
                                <span class="text-slate-600">•</span>
                                <span class="text-slate-500 font-mono text-[10px]">${wordsCount.toLocaleString()} pal.</span>
                            </div>
                        </div>
                    </div>

                    <!-- Subtle Secondary Utility Icons -->
                    <div class="flex items-center gap-1 shrink-0">
                        ${utilityBtnHtml}
                        ${canModifyItem(m) ? `
                            <button type="button" onclick="openEditMaterialModal(${m.id})" title="Editar documento" class="w-7 h-7 rounded-lg bg-slate-900/70 hover:bg-slate-700 text-slate-400 hover:text-white border border-slate-700/70 flex items-center justify-center text-xs transition cursor-pointer">
                                ✏️
                            </button>
                            <button type="button" onclick="deleteMaterial(${m.id})" title="Eliminar documento" class="w-7 h-7 rounded-lg bg-slate-900/70 hover:bg-rose-950/80 text-slate-500 hover:text-rose-400 border border-slate-700/70 hover:border-rose-500/40 flex items-center justify-center text-xs transition cursor-pointer">
                                🗑️
                            </button>
                        ` : ''}
                    </div>
                </div>

                <!-- Clean 2-Line Summary Preview -->
                <p class="text-xs text-slate-400 leading-relaxed line-clamp-2 pl-13">
                    ${excerpt || '<span class="italic text-slate-500">Sin vista previa de texto disponible.</span>'}
                </p>
            </div>

            <!-- Clear 3-Action Footer Hierarchy -->
            <div class="flex items-center justify-between gap-2 pt-3 border-t border-slate-700/60 text-xs">
                <div class="flex items-center gap-1.5">
                    <button type="button" onclick="openPrimaryMaterialView(${m.id})" class="px-3 py-1.5 rounded-xl bg-slate-900 hover:bg-slate-700 text-slate-200 border border-slate-700 font-semibold transition flex items-center gap-1.5 cursor-pointer">
                        ${primaryReadLabel}
                    </button>
                    ${hasPdfFile ? `
                        <button type="button" onclick="openViewMaterialModal(${m.id})" title="Ver texto plano extraído por PDFBox" class="px-2.5 py-1.5 rounded-xl bg-slate-900/70 hover:bg-slate-700 text-slate-400 hover:text-slate-200 border border-slate-700/70 font-medium transition cursor-pointer">
                            📝 Texto
                        </button>
                    ` : ''}
                    <button type="button" onclick="openMaterialStatsModal(${m.id})" title="Ver estadísticas e historial de evolución" class="px-3 py-1.5 rounded-xl bg-slate-900/70 hover:bg-slate-700 text-sky-300 border border-slate-700/70 font-semibold transition flex items-center gap-1.5 cursor-pointer">
                        📊 Estadísticas
                    </button>
                </div>

                <button type="button" onclick="openPracticeMaterialModal(${m.id})" class="px-3.5 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 active:scale-[0.98] text-white font-bold transition shadow-md shadow-blue-600/20 flex items-center gap-1.5 cursor-pointer shrink-0">
                    🎯 Practicar
                </button>
            </div>
        `;
        container.appendChild(card);
    });
}

let activeMaterialText = '';
let currentPdfId = null;

// =================== RENDERIZADO MARKDOWN Y MODO DE VISTA ===================
function renderMarkdown(md) {
    if (!md) return '<p class="text-slate-500 italic">Documento sin contenido textual.</p>';

    // Si marked.js está cargado globalmente en window
    if (typeof window.marked !== 'undefined') {
        try {
            if (typeof window.marked.parse === 'function') {
                return window.marked.parse(md);
            } else if (typeof window.marked === 'function') {
                return window.marked(md);
            }
        } catch (e) {
            console.warn('Error procesando markdown con marked.js, usando parser fallback:', e);
        }
    }

    // Parser fallback nativo resiliente (soporta headers, código, listas, negritas, links, citas)
    function escapeHtml(str) {
        return str
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;')
            .replace(/"/g, '&quot;')
            .replace(/'/g, '&#039;');
    }

    let raw = escapeHtml(md);

    // Bloques de código ```lang ... ```
    raw = raw.replace(/```([a-z0-9_-]*)\n([\s\S]*?)```/g, (match, lang, code) => {
        return `<pre><code class="language-${lang}">${code.trim()}</code></pre>`;
    });

    // Código en línea `...`
    raw = raw.replace(/`([^`]+)`/g, '<code>$1</code>');

    // Encabezados (#, ##, ###, ####)
    raw = raw.replace(/^#### (.*$)/gim, '<h4>$1</h4>');
    raw = raw.replace(/^### (.*$)/gim, '<h3>$1</h3>');
    raw = raw.replace(/^## (.*$)/gim, '<h2>$1</h2>');
    raw = raw.replace(/^# (.*$)/gim, '<h1>$1</h1>');

    // Negrita y cursiva
    raw = raw.replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>');
    raw = raw.replace(/\*([^*]+)\*/g, '<em>$1</em>');

    // Blockquotes
    raw = raw.replace(/^\> (.*$)/gim, '<blockquote>$1</blockquote>');

    // Listas desordenadas
    raw = raw.replace(/^\s*-\s+(.*$)/gim, '<ul><li>$1</li></ul>');
    raw = raw.replace(/<\/ul>\s*<ul>/g, '');

    // Enlaces [texto](url)
    raw = raw.replace(/\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)/g, '<a href="$2" target="_blank" rel="noopener">$1</a>');

    // Párrafos dobles
    raw = raw.replace(/\n\n+/g, '</p><p>');
    return `<p>${raw}</p>`;
}

function setMarkdownViewMode(mode) {
    const renderedDiv = document.getElementById('view-mat-rendered');
    const rawContentDiv = document.getElementById('view-mat-content');
    const previewBtn = document.getElementById('btn-md-preview-mode');
    const rawBtn = document.getElementById('btn-md-raw-mode');

    if (mode === 'preview') {
        if (renderedDiv) renderedDiv.classList.remove('hidden');
        if (rawContentDiv) rawContentDiv.classList.add('hidden');
        if (previewBtn) previewBtn.className = 'px-3 py-1 rounded-lg font-semibold bg-blue-600 text-white transition cursor-pointer shadow-sm';
        if (rawBtn) rawBtn.className = 'px-3 py-1 rounded-lg font-semibold text-slate-400 hover:text-white transition cursor-pointer';
    } else {
        if (renderedDiv) renderedDiv.classList.add('hidden');
        if (rawContentDiv) rawContentDiv.classList.remove('hidden');
        if (rawBtn) rawBtn.className = 'px-3 py-1 rounded-lg font-semibold bg-blue-600 text-white transition cursor-pointer shadow-sm';
        if (previewBtn) previewBtn.className = 'px-3 py-1 rounded-lg font-semibold text-slate-400 hover:text-white transition cursor-pointer';
    }
}

function openPdfViewer(id) {
    const mat = allMaterials.find(m => m.id === id);
    if (!mat) return;

    currentPdfId = id;
    const fileUrl = `/api/materials/${id}/file`;

    document.getElementById('pdf-viewer-title').textContent = mat.title;
    document.getElementById('pdf-viewer-topic').textContent = mat.topic ? mat.topic.name : 'General';
    const pdfIdBadge = document.getElementById('pdf-viewer-id-badge');
    if (pdfIdBadge) pdfIdBadge.textContent = `ID #${mat.id}`;

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

function openCurrentPdfStats() {
    if (currentPdfId) {
        const id = currentPdfId;
        closePdfViewer();
        openMaterialStatsModal(id);
    }
}

function openCurrentPdfExtractedText() {
    if (currentPdfId) {
        const id = currentPdfId;
        closePdfViewer();
        openViewMaterialModal(id);
    }
}

function openCurrentPdfPractice() {
    if (currentPdfId) {
        const id = currentPdfId;
        closePdfViewer();
        openPracticeMaterialModal(id);
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

    activeMaterialText = mat.contentText || '';
    const isMarkdown = mat.sourceType === 'MARKDOWN' || (mat.filePath && mat.filePath.toLowerCase().endsWith('.md')) || (mat.referenceUrlOrPage && mat.referenceUrlOrPage.toLowerCase().endsWith('.md'));
    const hasPdfFile = Boolean(mat.filePath && mat.filePath.toLowerCase().endsWith('.pdf'));
    const isPdf = !isMarkdown && (hasPdfFile || mat.sourceType === 'COURSE_PDF' || (mat.referenceUrlOrPage && mat.referenceUrlOrPage.toLowerCase().includes('.pdf')));

    document.getElementById('view-mat-title').textContent = mat.title;
    document.getElementById('view-mat-topic').textContent = mat.topic ? mat.topic.name : 'General';
    const viewIdBadge = document.getElementById('view-mat-id-badge');
    if (viewIdBadge) viewIdBadge.textContent = `ID #${mat.id}`;
    const words = activeMaterialText ? activeMaterialText.trim().split(/\s+/).length : 0;
    document.getElementById('view-mat-words').textContent = `${words} palabras extraídas`;

    // Configurar Badge de Tipo en la cabecera del modal
    const typeBadge = document.getElementById('view-mat-type-badge');
    if (typeBadge) {
        if (isMarkdown) {
            typeBadge.className = 'px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-emerald-500/20 text-emerald-300 border border-emerald-500/30';
            typeBadge.textContent = '📝 MARKDOWN';
        } else if (hasPdfFile || isPdf) {
            typeBadge.className = 'px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-rose-500/20 text-rose-400 border border-rose-500/30';
            typeBadge.textContent = '📄 PDF (PDFBox)';
        } else {
            typeBadge.className = 'px-2 py-0.5 rounded text-[10px] font-bold font-mono bg-blue-500/20 text-blue-400 border border-blue-500/30';
            typeBadge.textContent = '🌐 WEB URL';
        }
    }

    // Contenedores de contenido y toggle Renderizado / Raw
    const mdToggle = document.getElementById('view-mat-md-toggle');
    const renderedDiv = document.getElementById('view-mat-rendered');
    const rawContentDiv = document.getElementById('view-mat-content');

    rawContentDiv.textContent = activeMaterialText;

    if (isMarkdown) {
        if (mdToggle) {
            mdToggle.classList.remove('hidden');
            mdToggle.classList.add('flex');
        }
        if (renderedDiv) {
            renderedDiv.innerHTML = renderMarkdown(activeMaterialText);
        }
        setMarkdownViewMode('preview');
    } else {
        if (mdToggle) {
            mdToggle.classList.add('hidden');
            mdToggle.classList.remove('flex');
        }
        if (renderedDiv) renderedDiv.classList.add('hidden');
        if (rawContentDiv) rawContentDiv.classList.remove('hidden');
    }

    // Enlace a URL original si aplica
    const linkElem = document.getElementById('view-mat-link');
    if (mat.referenceUrlOrPage && (mat.referenceUrlOrPage.startsWith('http://') || mat.referenceUrlOrPage.startsWith('https://'))) {
        linkElem.href = mat.referenceUrlOrPage;
        linkElem.classList.remove('hidden');
    } else {
        linkElem.classList.add('hidden');
    }

    // Botón Visor PDF
    const pdfBtn = document.getElementById('view-mat-btn-pdf');
    if (hasPdfFile) {
        pdfBtn.onclick = () => {
            closeModal('modal-view-material');
            openPdfViewer(mat.id);
        };
        pdfBtn.classList.remove('hidden');
    } else {
        pdfBtn.classList.add('hidden');
    }

    // Botón Descargar Archivo (.md, .pdf o texto)
    const downloadBtn = document.getElementById('view-mat-btn-download');
    if (downloadBtn) {
        const fileUrl = `/api/materials/${mat.id}/file?download=true`;
        downloadBtn.href = fileUrl;
        const safeTitle = (mat.title || 'documento').replace(/[^a-zA-Z0-9._-]/g, '_');
        const ext = isMarkdown ? '.md' : (hasPdfFile || isPdf ? '.pdf' : '.txt');
        downloadBtn.setAttribute('download', safeTitle.toLowerCase().endsWith(ext) ? safeTitle : safeTitle + ext);
        downloadBtn.innerHTML = `📥 Descargar ${isMarkdown ? '.md' : (hasPdfFile || isPdf ? 'PDF' : 'Archivo')}`;
        downloadBtn.classList.remove('hidden');
    }

    // Botón Practicar este Documento
    const practiceBtn = document.getElementById('view-mat-btn-practice');
    if (practiceBtn) {
        practiceBtn.onclick = () => {
            closeModal('modal-view-material');
            openPracticeMaterialModal(mat.id);
        };
        practiceBtn.classList.remove('hidden');
    }

    // Botón Estadísticas y Evolución
    const statsBtn = document.getElementById('view-mat-btn-stats');
    if (statsBtn) {
        statsBtn.onclick = () => {
            closeModal('modal-view-material');
            openMaterialStatsModal(mat.id);
        };
        statsBtn.classList.remove('hidden');
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
    const mdForm = document.getElementById('import-md-form');
    const urlBtn = document.getElementById('tab-mat-url-btn');
    const pdfBtn = document.getElementById('tab-mat-pdf-btn');
    const mdBtn = document.getElementById('tab-mat-md-btn');
    const badgeHint = document.getElementById('mat-input-badge-hint');

    const inactiveClass = 'px-3.5 py-1.5 rounded-lg text-xs font-bold text-slate-300 hover:text-white transition flex items-center gap-1.5 cursor-pointer';
    urlBtn.className = inactiveClass;
    pdfBtn.className = inactiveClass;
    if (mdBtn) mdBtn.className = inactiveClass;

    urlForm.classList.add('hidden');
    pdfForm.classList.add('hidden');
    if (mdForm) mdForm.classList.add('hidden');

    if (mode === 'url') {
        urlForm.classList.remove('hidden');
        urlBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-blue-600 text-white transition flex items-center gap-1.5 cursor-pointer';
        if (badgeHint) badgeHint.textContent = 'HTML Web / PDF Remoto';
    } else if (mode === 'pdf') {
        pdfForm.classList.remove('hidden');
        pdfBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-rose-600 text-white transition flex items-center gap-1.5 cursor-pointer';
        if (badgeHint) badgeHint.textContent = 'PDF Local / Apache PDFBox';
    } else if (mode === 'markdown') {
        if (mdForm) mdForm.classList.remove('hidden');
        if (mdBtn) mdBtn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-emerald-600 text-white transition flex items-center gap-1.5 cursor-pointer';
        if (badgeHint) badgeHint.textContent = 'Markdown (.md) / Editor Enriquecido';
    }
}

let currentMarkdownSubmode = 'file';

function toggleMarkdownSubmode(submode) {
    currentMarkdownSubmode = submode;
    const fileContainer = document.getElementById('md-file-input-container');
    const textContainer = document.getElementById('md-text-input-container');
    const fileBtn = document.getElementById('md-mode-file-btn');
    const textBtn = document.getElementById('md-mode-text-btn');

    if (submode === 'file') {
        if (fileContainer) fileContainer.classList.remove('hidden');
        if (textContainer) textContainer.classList.add('hidden');
        if (fileBtn) fileBtn.className = 'px-3 py-1 rounded-lg text-xs font-bold bg-blue-600 text-white transition cursor-pointer';
        if (textBtn) textBtn.className = 'px-3 py-1 rounded-lg text-xs font-bold bg-slate-800 text-slate-300 hover:text-white transition cursor-pointer';
    } else {
        if (fileContainer) fileContainer.classList.add('hidden');
        if (textContainer) textContainer.classList.remove('hidden');
        if (textBtn) textBtn.className = 'px-3 py-1 rounded-lg text-xs font-bold bg-blue-600 text-white transition cursor-pointer';
        if (fileBtn) fileBtn.className = 'px-3 py-1 rounded-lg text-xs font-bold bg-slate-800 text-slate-300 hover:text-white transition cursor-pointer';
    }
}

function loadSampleMarkdown() {
    toggleMarkdownSubmode('text');

    const sampleMd = `# HL7 FHIR R4: Resumen de Arquitectura y Recursos

## 1. Paradigmas de Intercambio
FHIR define cuatro paradigmas principales para la interoperabilidad en salud:
- **RESTful API**: Operaciones síncronas CRUD sobre URLs de recursos (\`GET\`, \`POST\`, \`PUT\`, \`DELETE\`).
- **Mensajería (Messaging)**: Orientado a eventos, empaquetado en \`Bundle\` de tipo \`message\`.
- **Documentos (Documents)**: Conjunto clínico coherente e inmutable con recurso raíz \`Composition\` y \`Bundle\` tipo \`document\`.
- **Servicios / Operaciones**: Invocación de lógica RPC personalizada como \`$everything\`, \`$validate\`, \`$meta\`.

## 2. Estructura Estándar de un Recurso
Cada recurso FHIR comparte los siguientes componentes clave:
1. **Identificador Lógico (\`id\`)**: Clave primaria en el servidor FHIR.
2. **Metadatos (\`meta\`)**: \`versionId\`, marca temporal \`lastUpdated\` y perfiles \`profile\`.
3. **Texto Narrativo (\`text\`)**: Fragmento XHTML legible por humanos para garantizar la seguridad clínica.
4. **Extensiones**: Soporte estándar para campos personalizados sin romper interoperabilidad.

\`\`\`json
{
  "resourceType": "Patient",
  "id": "ejemplo-01",
  "active": true,
  "name": [{
    "use": "official",
    "family": "Fernández",
    "given": ["Carlos"]
  }],
  "gender": "male",
  "birthDate": "1980-06-15"
}
\`\`\`

> **Buenas Prácticas**: Utiliza siempre los códigos oficiales de ValueSets terminológicos (LOINC, SNOMED CT, RxNorm) en los atributos \`code\`.
`;

    const titleInput = document.getElementById('import-md-title');
    const textInput = document.getElementById('import-md-text');
    if (titleInput) titleInput.value = 'HL7 FHIR R4 - Resumen de Arquitectura y Recursos';
    if (textInput) textInput.value = sampleMd;

    // Auto-select topic 'RESOURCES' (ID 3) si existe
    const topicSelect = document.getElementById('import-md-topic-id');
    if (topicSelect) {
        for (let i = 0; i < topicSelect.options.length; i++) {
            if (topicSelect.options[i].text.includes('Recursos') || topicSelect.options[i].value === '3') {
                topicSelect.selectedIndex = i;
                break;
            }
        }
    }
    showToast('Plantilla Markdown cargada en el editor.', 'info', 2000);
}

async function submitMarkdownMaterial(event) {
    event.preventDefault();
    const topicSelect = document.getElementById('import-md-topic-id');
    const titleInput = document.getElementById('import-md-title');
    const fileInput = document.getElementById('import-md-file');
    const textInput = document.getElementById('import-md-text');
    const statusSpan = document.getElementById('import-md-status');
    const submitBtn = document.getElementById('btn-import-md-submit');

    const topicId = topicSelect.value;
    const title = titleInput.value.trim();

    if (!topicId) {
        showToast('Por favor selecciona un tema asociado.', 'warning');
        return;
    }

    if (currentMarkdownSubmode === 'file') {
        if (!fileInput.files || fileInput.files.length === 0) {
            showToast('Por favor selecciona un archivo Markdown (.md).', 'warning');
            return;
        }

        const formData = new FormData();
        formData.append('file', fileInput.files[0]);
        formData.append('topicId', topicId);
        if (title) {
            formData.append('title', title);
        }

        if (statusSpan) statusSpan.classList.remove('hidden');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.classList.add('opacity-50');
        }

        try {
            const resp = await fetch('/api/materials/markdown/file', {
                method: 'POST',
                body: formData
            });

            if (resp.ok) {
                showToast('¡Archivo Markdown subido e indexado con éxito!', 'success');
                fileInput.value = '';
                titleInput.value = '';
                toggleMaterialUploadDrawer(false);
                await loadMaterials();
            } else {
                const err = await resp.json().catch(() => ({}));
                showToast('Error al subir el archivo Markdown: ' + (err.error || 'Verifica el archivo.'), 'error');
            }
        } catch (e) {
            console.error('Error subiendo markdown file:', e);
            showToast('Error de red al subir el archivo Markdown.', 'error');
        } finally {
            if (statusSpan) statusSpan.classList.add('hidden');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.classList.remove('opacity-50');
            }
        }
    } else {
        const contentText = textInput.value.trim();
        if (!contentText) {
            showToast('Por favor escribe o pega contenido Markdown en el editor.', 'warning');
            return;
        }

        if (!title) {
            showToast('Por favor introduce un título para el documento Markdown.', 'warning');
            return;
        }

        if (statusSpan) statusSpan.classList.remove('hidden');
        if (submitBtn) {
            submitBtn.disabled = true;
            submitBtn.classList.add('opacity-50');
        }

        try {
            const resp = await fetch('/api/materials/markdown/text', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    topicId: topicId,
                    title: title,
                    content: contentText,
                    contentText: contentText
                })
            });

            if (resp.ok) {
                showToast('¡Documento Markdown guardado e indexado con éxito!', 'success');
                textInput.value = '';
                titleInput.value = '';
                toggleMaterialUploadDrawer(false);
                await loadMaterials();
            } else {
                const err = await resp.json().catch(() => ({}));
                showToast('Error al guardar Markdown: ' + (err.error || 'Verifica los campos.'), 'error');
            }
        } catch (e) {
            console.error('Error guardando markdown text:', e);
            showToast('Error de red al guardar el documento Markdown.', 'error');
        } finally {
            if (statusSpan) statusSpan.classList.add('hidden');
            if (submitBtn) {
                submitBtn.disabled = false;
                submitBtn.classList.remove('opacity-50');
            }
        }
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
            toggleMaterialUploadDrawer(false);
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
            toggleMaterialUploadDrawer(false);
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
    editingQuestionId = null;
    const modal = document.getElementById('modal-create-question');
    if (modal) {
        const titleEl = modal.querySelector('h3');
        if (titleEl) titleEl.textContent = 'Crear Nueva Pregunta';
    }
    document.getElementById('new-q-stem').value = '';
    document.getElementById('new-q-snippet').value = '';
    document.getElementById('new-q-explanation').value = '';
    document.getElementById('new-q-url').value = '';
    document.getElementById('new-q-opt-a').value = '';
    document.getElementById('new-q-opt-b').value = '';
    document.getElementById('new-q-opt-c').value = '';
    document.getElementById('new-q-opt-d').value = '';
    document.getElementById('new-q-why-a').value = '';
    document.getElementById('new-q-why-b').value = '';
    document.getElementById('new-q-why-c').value = '';
    document.getElementById('new-q-why-d').value = '';
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
    const matIdVal = document.getElementById('new-q-material-id') ? document.getElementById('new-q-material-id').value : '';
    const materialId = matIdVal ? parseInt(matIdVal, 10) : null;

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
        materialId,
        stem,
        codeSnippet,
        explanation,
        fhirSpecUrl,
        questionType: 'SINGLE_CHOICE',
        source: 'MANUAL',
        options
    };

    const isEdit = editingQuestionId != null;
    const endpoint = isEdit ? `/api/questions/${editingQuestionId}` : '/api/questions';
    const method = isEdit ? 'PUT' : 'POST';

    try {
        const resp = await fetch(endpoint, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (resp.ok) {
            closeModal('modal-create-question');
            editingQuestionId = null;
            await loadQuestions();
            await loadDashboard();
            showToast(isEdit ? '¡Pregunta actualizada correctamente!' : '¡Pregunta creada y añadida al banco y al sistema SM-2!', 'success');
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'Error al guardar la pregunta.', 'error');
        }
    } catch (e) {
        console.error('Error guardando pregunta:', e);
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
            "materialId": 1,
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

    const topicSelect = document.getElementById('import-json-topic-id');
    const matSelect = document.getElementById('import-json-material-id');
    const defaultTopicId = topicSelect && topicSelect.value ? topicSelect.value : null;
    const defaultMaterialId = matSelect && matSelect.value ? matSelect.value : null;

    let url = '/api/questions/import';
    const params = [];
    if (defaultTopicId) params.push(`defaultTopicId=${encodeURIComponent(defaultTopicId)}`);
    if (defaultMaterialId) params.push(`defaultMaterialId=${encodeURIComponent(defaultMaterialId)}`);
    if (params.length > 0) url += '?' + params.join('&');

    try {
        const resp = await fetch(url, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: jsonText
        });
        const data = await resp.json();
        if (resp.ok && data.success) {
            closeModal('modal-import-json');
            document.getElementById('import-json-textarea').value = '';
            if (topicSelect) topicSelect.value = '';
            if (matSelect) matSelect.value = '';
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

// =================== PRÁCTICA DE APUNTE O DOCUMENTO CONCRETO ===================
let activePracticeMaterialId = null;
let activePracticeMode = 'TUTOR';
let activePracticeQuestionCount = 10;

async function openPracticeMaterialModal(id) {
    const mat = allMaterials.find(m => m.id === id);
    if (!mat) return;

    activePracticeMaterialId = id;
    activePracticeMode = 'TUTOR';
    activePracticeQuestionCount = 10;

    selectPracticeMaterialMode('TUTOR');
    selectPracticeQuestionCount(10);

    const titleEl = document.getElementById('practice-mat-title');
    const badgeEl = document.getElementById('practice-mat-badge');
    const idBadgeEl = document.getElementById('practice-mat-id-badge');
    const topicEl = document.getElementById('practice-mat-topic');
    const linkedEl = document.getElementById('practice-mat-linked-count');
    const totalEl = document.getElementById('practice-mat-total-count');

    if (titleEl) titleEl.textContent = mat.title;
    if (badgeEl) badgeEl.textContent = mat.sourceType || 'DOCUMENTO';
    if (idBadgeEl) idBadgeEl.textContent = `ID #${mat.id}`;
    if (topicEl) topicEl.textContent = mat.topic ? mat.topic.name : 'General';
    if (linkedEl) linkedEl.textContent = 'Cargando preguntas vinculadas...';
    if (totalEl) totalEl.textContent = 'Cargando banco...';

    openModal('modal-practice-material');

    try {
        const resp = await fetch(`/api/materials/${id}/practice-info`);
        if (resp.ok) {
            const info = await resp.json();
            if (linkedEl) linkedEl.textContent = `${info.linkedQuestionsCount} pregunta${info.linkedQuestionsCount === 1 ? '' : 's'}`;
            const tot = info.totalRelevantCount != null ? info.totalRelevantCount : (info.relevantQuestionsCount || 0);
            if (totalEl) totalEl.textContent = `${tot} preguntas en este tema`;
        }
    } catch (e) {
        console.error('Error cargando info de práctica del documento:', e);
    }
}

function selectPracticeMaterialMode(mode) {
    activePracticeMode = mode;
    const tutorBtn = document.getElementById('practice-mode-tutor-btn');
    const simBtn = document.getElementById('practice-mode-sim-btn');

    if (mode === 'TUTOR') {
        if (tutorBtn) {
            tutorBtn.className = 'p-3 rounded-xl border bg-blue-600/20 border-blue-500 text-white text-left cursor-pointer transition';
            const desc = tutorBtn.querySelector('div:last-child');
            if (desc) desc.className = 'text-[10px] text-slate-300 mt-1';
        }
        if (simBtn) {
            simBtn.className = 'p-3 rounded-xl border bg-slate-900 border-slate-700 text-slate-400 hover:text-white text-left cursor-pointer transition';
            const desc = simBtn.querySelector('div:last-child');
            if (desc) desc.className = 'text-[10px] text-slate-400 mt-1';
        }
    } else {
        if (simBtn) {
            simBtn.className = 'p-3 rounded-xl border bg-blue-600/20 border-blue-500 text-white text-left cursor-pointer transition';
            const desc = simBtn.querySelector('div:last-child');
            if (desc) desc.className = 'text-[10px] text-slate-300 mt-1';
        }
        if (tutorBtn) {
            tutorBtn.className = 'p-3 rounded-xl border bg-slate-900 border-slate-700 text-slate-400 hover:text-white text-left cursor-pointer transition';
            const desc = tutorBtn.querySelector('div:last-child');
            if (desc) desc.className = 'text-[10px] text-slate-400 mt-1';
        }
    }
}

function selectPracticeQuestionCount(cnt) {
    activePracticeQuestionCount = cnt;
    const buttons = document.querySelectorAll('.practice-count-btn');
    buttons.forEach(btn => {
        const text = btn.textContent.trim();
        const isMatch = (cnt === 'all' && text === 'Todas') || (String(cnt) === text);
        if (isMatch) {
            btn.className = 'practice-count-btn active py-2 text-center rounded-xl bg-blue-600 text-white font-semibold cursor-pointer';
        } else {
            btn.className = 'practice-count-btn py-2 text-center rounded-xl bg-slate-900 border border-slate-700 hover:border-blue-500 text-slate-300 font-semibold cursor-pointer';
        }
    });
}

function startPracticeForActiveMaterial() {
    if (!activePracticeMaterialId) return;

    const count = activePracticeQuestionCount === 'all' ? 50 : parseInt(activePracticeQuestionCount, 10);
    closeModal('modal-practice-material');

    executeStartExam({
        mode: activePracticeMode,
        questionCount: count,
        materialId: activePracticeMaterialId,
        topicId: null
    });
}

async function generateQuestionForActiveMaterial() {
    if (!activePracticeMaterialId) return;

    const btn = document.getElementById('btn-practice-generate-q');
    const status = document.getElementById('practice-generate-status');
    const linkedEl = document.getElementById('practice-mat-linked-count');

    if (btn) btn.classList.add('hidden');
    if (status) status.classList.remove('hidden');

    try {
        const resp = await fetch(`/api/materials/${activePracticeMaterialId}/generate-question`, {
            method: 'POST'
        });

        if (resp.ok) {
            const newQ = await resp.json();
            showToast('¡Pregunta generada e indexada para este documento con éxito!', 'success');
            
            // Refrescar conteo en modal
            const infoResp = await fetch(`/api/materials/${activePracticeMaterialId}/practice-info`);
            if (infoResp.ok) {
                const info = await infoResp.json();
                if (linkedEl) linkedEl.textContent = `${info.linkedQuestionsCount} pregunta${info.linkedQuestionsCount === 1 ? '' : 's'}`;
            }
            // Recargar banco de preguntas
            loadQuestions();
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No se pudo generar la pregunta a partir del texto.', 'error');
        }
    } catch (e) {
        console.error('Error generando pregunta:', e);
        showToast('Error de conexión al generar la pregunta.', 'error');
    } finally {
        if (btn) btn.classList.remove('hidden');
        if (status) status.classList.add('hidden');
    }
}

// =================== ESTADÍSTICAS Y EVOLUCIÓN DE DOCUMENTOS ===================
let materialStatsChartInstance = null;

function formatDurationSeconds(totalSec) {
    if (!totalSec || totalSec <= 0) return '0m 0s';
    const m = Math.floor(totalSec / 60);
    const s = Math.round(totalSec % 60);
    if (m === 0) return `${s}s`;
    return `${m}m ${s}s`;
}

async function openMaterialStatsModal(materialId) {
    const mat = allMaterials ? allMaterials.find(m => m.id === materialId) : null;

    // Resetear vistas del modal a estado de carga
    document.getElementById('mat-stats-id-badge').textContent = `ID #${materialId}`;
    document.getElementById('mat-stats-title').textContent = mat ? mat.title : 'Cargando estadísticas...';
    document.getElementById('mat-stats-topic').textContent = mat && mat.topic ? `Tema: ${mat.topic.name}` : 'Tema: General';
    document.getElementById('mat-stats-questions-count').textContent = 'Calculando preguntas...';

    const typeBadge = document.getElementById('mat-stats-type-badge');
    if (typeBadge) {
        const srcType = mat ? mat.sourceType : 'DOCUMENTO';
        typeBadge.textContent = srcType;
    }

    // Configurar botones de práctica
    const practiceBtn = document.getElementById('mat-stats-btn-practice');
    if (practiceBtn) {
        practiceBtn.onclick = () => {
            closeModal('modal-material-stats');
            openPracticeMaterialModal(materialId);
        };
    }
    const emptyPracticeBtn = document.getElementById('mat-stats-btn-empty-practice');
    if (emptyPracticeBtn) {
        emptyPracticeBtn.onclick = () => {
            closeModal('modal-material-stats');
            openPracticeMaterialModal(materialId);
        };
    }

    // KPIs iniciales
    document.getElementById('mat-kpi-attempts').textContent = '...';
    document.getElementById('mat-kpi-avg').textContent = '...';
    document.getElementById('mat-kpi-best').textContent = '...';
    document.getElementById('mat-kpi-latest').textContent = '...';
    document.getElementById('mat-kpi-passrate').textContent = '...';
    document.getElementById('mat-kpi-time').textContent = '...';

    openModal('modal-material-stats');

    try {
        const resp = await fetch(`/api/materials/${materialId}/stats`);
        if (!resp.ok) {
            showToast('No se pudieron obtener las estadísticas de este documento.', 'error');
            return;
        }

        const stats = await resp.json();

        // 1. Cabecera
        document.getElementById('mat-stats-title').textContent = stats.materialTitle || 'Documento';
        document.getElementById('mat-stats-topic').textContent = `Tema: ${stats.topicName || 'General'}`;
        document.getElementById('mat-stats-questions-count').textContent = `${stats.linkedQuestionsCount} preguntas vinculadas`;

        // 2. KPIs
        document.getElementById('mat-kpi-attempts').textContent = stats.totalAttempts;

        const avgEl = document.getElementById('mat-kpi-avg');
        avgEl.textContent = `${stats.averageScore.toFixed(1)}%`;
        if (stats.averageScore >= 80.0) {
            avgEl.className = 'text-xl font-extrabold text-emerald-400 mt-1 block font-mono';
        } else if (stats.averageScore >= 65.0) {
            avgEl.className = 'text-xl font-extrabold text-amber-400 mt-1 block font-mono';
        } else {
            avgEl.className = 'text-xl font-extrabold text-rose-400 mt-1 block font-mono';
        }

        document.getElementById('mat-kpi-best').textContent = stats.bestScore != null ? `${stats.bestScore.toFixed(1)}%` : '-';
        document.getElementById('mat-kpi-latest').textContent = stats.latestScore != null ? `${stats.latestScore.toFixed(1)}%` : '-';
        document.getElementById('mat-kpi-passrate').textContent = `${stats.passRatePercent.toFixed(1)}%`;
        document.getElementById('mat-kpi-passcount').textContent = `${stats.passedAttempts} de ${stats.totalAttempts} (≥80%)`;
        document.getElementById('mat-kpi-time').textContent = formatDurationSeconds(stats.averageTimeSeconds);

        // 3. Gráfico y Estado Vacío
        const emptyState = document.getElementById('mat-stats-empty-state');
        const chartWrapper = document.getElementById('mat-chart-wrapper');
        const chartLegend = document.getElementById('mat-chart-legend');
        const attemptsSection = document.getElementById('mat-attempts-section');

        if (stats.totalAttempts === 0) {
            if (emptyState) emptyState.classList.remove('hidden');
            if (chartWrapper) chartWrapper.classList.add('hidden');
            if (chartLegend) chartLegend.classList.add('hidden');
            if (attemptsSection) attemptsSection.classList.add('hidden');
        } else {
            if (emptyState) emptyState.classList.add('hidden');
            if (chartWrapper) chartWrapper.classList.remove('hidden');
            if (chartLegend) chartLegend.classList.remove('hidden');
            if (attemptsSection) attemptsSection.classList.remove('hidden');

            renderMaterialEvolutionChart(stats.evolution);
            renderMaterialAttemptsTable(stats.evolution);
        }

        // 4. Desglose de preguntas del documento
        renderMaterialQuestionsTable(stats.questionPerformance, materialId);

    } catch (e) {
        console.error('Error cargando estadísticas del material:', e);
        showToast('Error de conexión al cargar estadísticas.', 'error');
    }
}

function renderMaterialAttemptsTable(evolution) {
    const tbody = document.getElementById('mat-attempts-table-body');
    const badge = document.getElementById('mat-attempts-badge');
    if (!tbody) return;

    if (badge) badge.textContent = `${evolution.length} sesió${evolution.length === 1 ? 'n' : 'nes'}`;
    tbody.innerHTML = '';

    // Mostrar intentos en orden inverso (más recientes primero en la tabla)
    const reversed = [...evolution].reverse();

    reversed.forEach(p => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-900/60 transition';
        const isPass = p.passed;
        const passBadge = isPass
            ? '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">APROBADO</span>'
            : '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">SUSPENSO</span>';

        tr.innerHTML = `
            <td class="py-2.5 px-3 font-bold text-sky-400">#${p.attemptNumber}</td>
            <td class="py-2.5 px-3 text-slate-300 text-[11px] font-sans">${p.formattedDate || p.startedAt}</td>
            <td class="py-2.5 px-3 text-slate-400 font-sans text-[11px]"><span class="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300">${p.mode}</span></td>
            <td class="py-2.5 px-3 font-bold ${isPass ? 'text-emerald-400' : 'text-rose-400'}">${p.scorePercent.toFixed(1)}%</td>
            <td class="py-2.5 px-3 text-slate-300">${p.correctCount} / ${p.totalCount}</td>
            <td class="py-2.5 px-3 text-slate-400 font-sans text-[11px]">${formatDurationSeconds(p.durationSeconds)}</td>
            <td class="py-2.5 px-3">${passBadge}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderMaterialQuestionsTable(questions, materialId) {
    const tbody = document.getElementById('mat-questions-table-body');
    if (!tbody) return;

    tbody.innerHTML = '';

    if (!questions || questions.length === 0) {
        tbody.innerHTML = `
            <tr>
                <td colspan="5" class="py-6 text-center text-slate-500 text-xs">
                    No hay preguntas explícitamente vinculadas a este documento todavía.
                    <button onclick="closeModal('modal-material-stats'); openPracticeMaterialModal(${materialId})" class="text-blue-400 hover:text-blue-300 font-semibold underline ml-1 cursor-pointer">
                        Generar preguntas con IA →
                    </button>
                </td>
            </tr>
        `;
        return;
    }

    questions.forEach(q => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-900/60 transition';

        let statusBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-slate-800 text-slate-400">SIN INTENTOS</span>';
        if (q.status === 'DOMINADA') {
            statusBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">🌟 DOMINADA (≥80%)</span>';
        } else if (q.status === 'EN_PROGRESO') {
            statusBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-amber-500/20 text-amber-400 border border-amber-500/30">⚖️ EN PROGRESO</span>';
        } else if (q.status === 'A_REFORZAR') {
            statusBadge = '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">⚠️ A REFORZAR</span>';
        }

        const stemText = q.stem ? (q.stem.length > 90 ? q.stem.substring(0, 90) + '...' : q.stem) : 'Pregunta #' + q.questionId;

        tr.innerHTML = `
            <td class="py-2.5 px-3 font-mono text-[11px] font-bold text-indigo-400">#${q.questionId}</td>
            <td class="py-2.5 px-3 font-sans text-xs text-slate-200">${stemText}</td>
            <td class="py-2.5 px-3 text-center font-mono text-xs text-slate-300">${q.timesAnswered > 0 ? `${q.timesAnswered} veces (${q.timesCorrect} aciertos)` : '-'}</td>
            <td class="py-2.5 px-3 text-center font-mono font-bold ${q.timesAnswered > 0 ? (q.accuracyPercent >= 80 ? 'text-emerald-400' : (q.accuracyPercent >= 50 ? 'text-amber-400' : 'text-rose-400')) : 'text-slate-500'}">
                ${q.timesAnswered > 0 ? q.accuracyPercent.toFixed(1) + '%' : '-'}
            </td>
            <td class="py-2.5 px-3 text-right">${statusBadge}</td>
        `;
        tbody.appendChild(tr);
    });
}

function renderMaterialEvolutionChart(evolution) {
    const canvas = document.getElementById('material-stats-chart');
    const svgContainer = document.getElementById('mat-chart-svg-container');
    if (!canvas) return;

    if (materialStatsChartInstance) {
        materialStatsChartInstance.destroy();
        materialStatsChartInstance = null;
    }

    if (!evolution || evolution.length === 0) return;

    // Si Chart.js está disponible
    if (typeof Chart !== 'undefined') {
        canvas.classList.remove('hidden');
        if (svgContainer) svgContainer.classList.add('hidden');

        const ctx = canvas.getContext('2d');
        const gradient = ctx.createLinearGradient(0, 0, 0, 260);
        gradient.addColorStop(0, 'rgba(56, 189, 248, 0.35)');
        gradient.addColorStop(1, 'rgba(56, 189, 248, 0.02)');

        const labels = evolution.map(p => `Intento #${p.attemptNumber}`);
        const scores = evolution.map(p => p.scorePercent);
        const passThreshold = evolution.map(() => 80.0);

        materialStatsChartInstance = new Chart(ctx, {
            type: 'line',
            data: {
                labels: labels,
                datasets: [
                    {
                        label: 'Puntuación (%)',
                        data: scores,
                        borderColor: '#38bdf8',
                        backgroundColor: gradient,
                        borderWidth: 3,
                        pointBackgroundColor: evolution.map(p => p.passed ? '#10b981' : '#f43f5e'),
                        pointBorderColor: '#0f172a',
                        pointBorderWidth: 2,
                        pointRadius: 6,
                        pointHoverRadius: 8,
                        tension: 0.25,
                        fill: true
                    },
                    {
                        label: 'Umbral Aprobado (80%)',
                        data: passThreshold,
                        borderColor: 'rgba(16, 185, 129, 0.75)',
                        borderWidth: 2,
                        borderDash: [6, 6],
                        pointRadius: 0,
                        fill: false
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                animation: {
                    duration: 500,
                    easing: 'easeOutQuart'
                },
                interaction: {
                    intersect: false,
                    mode: 'index'
                },
                scales: {
                    y: {
                        min: 0,
                        max: 100,
                        grid: {
                            color: 'rgba(51, 65, 85, 0.4)'
                        },
                        ticks: {
                            color: '#94a3b8',
                            font: { family: 'JetBrains Mono', size: 11 },
                            stepSize: 20,
                            callback: v => v + '%'
                        }
                    },
                    x: {
                        grid: {
                            color: 'rgba(51, 65, 85, 0.2)'
                        },
                        ticks: {
                            color: '#94a3b8',
                            font: { family: 'Plus Jakarta Sans', size: 11 }
                        }
                    }
                },
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        backgroundColor: 'rgba(15, 23, 42, 0.95)',
                        titleColor: '#ffffff',
                        bodyColor: '#cbd5e1',
                        borderColor: 'rgba(56, 189, 248, 0.4)',
                        borderWidth: 1,
                        padding: 12,
                        cornerRadius: 12,
                        displayColors: false,
                        callbacks: {
                            title: (items) => {
                                const idx = items[0].dataIndex;
                                const p = evolution[idx];
                                return `Intento #${p.attemptNumber} • ${p.formattedDate}`;
                            },
                            label: (item) => {
                                const p = evolution[item.dataIndex];
                                return [
                                    `🎯 Puntuación: ${p.scorePercent}% (${p.passed ? 'APROBADO' : 'SUSPENSO'})`,
                                    `📝 Aciertos: ${p.correctCount} de ${p.totalCount}`,
                                    `⏱️ Duración: ${formatDurationSeconds(p.durationSeconds)}`,
                                    `📋 Modo: ${p.mode}`
                                ];
                            }
                        }
                    }
                }
            }
        });
    } else {
        renderFallbackSvgChart(evolution);
    }
}

function renderFallbackSvgChart(evolution) {
    const canvas = document.getElementById('material-stats-chart');
    const svgContainer = document.getElementById('mat-chart-svg-container');
    if (!svgContainer) return;

    if (canvas) canvas.classList.add('hidden');
    svgContainer.classList.remove('hidden');

    const width = 800;
    const height = 260;
    const padding = { top: 20, right: 30, bottom: 40, left: 50 };
    const chartW = width - padding.left - padding.right;
    const chartH = height - padding.top - padding.bottom;

    const n = evolution.length;
    const stepX = n > 1 ? chartW / (n - 1) : chartW / 2;

    const points = evolution.map((p, i) => {
        const x = n > 1 ? padding.left + (i * stepX) : padding.left + (chartW / 2);
        const y = padding.top + chartH - (p.scorePercent / 100 * chartH);
        return { x, y, p, i };
    });

    const passY = padding.top + chartH - (80 / 100 * chartH);

    let polylinePoints = points.map(pt => `${pt.x},${pt.y}`).join(' ');
    let areaPoints = `${points[0].x},${padding.top + chartH} ` + polylinePoints + ` ${points[points.length - 1].x},${padding.top + chartH}`;

    let svgHtml = `
        <svg viewBox="0 0 ${width} ${height}" class="w-full h-full overflow-visible font-mono text-xs">
            <defs>
                <linearGradient id="svgGradient" x1="0" y1="0" x2="0" y2="1">
                    <stop offset="0%" stop-color="#38bdf8" stop-opacity="0.35"/>
                    <stop offset="100%" stop-color="#38bdf8" stop-opacity="0.0"/>
                </linearGradient>
            </defs>
            <!-- Eje y cuadrícula -->
            <line x1="${padding.left}" y1="${padding.top}" x2="${width - padding.right}" y2="${padding.top}" stroke="#334155" stroke-dasharray="4" opacity="0.4"/>
            <line x1="${padding.left}" y1="${padding.top + chartH * 0.5}" x2="${width - padding.right}" y2="${padding.top + chartH * 0.5}" stroke="#334155" stroke-dasharray="4" opacity="0.4"/>
            <line x1="${padding.left}" y1="${padding.top + chartH}" x2="${width - padding.right}" y2="${padding.top + chartH}" stroke="#334155" opacity="0.6"/>

            <!-- Línea Meta 80% Aprobado -->
            <line x1="${padding.left}" y1="${passY}" x2="${width - padding.right}" y2="${passY}" stroke="#10b981" stroke-width="1.5" stroke-dasharray="5 5" opacity="0.8"/>
            <text x="${width - padding.right - 5}" y="${passY - 6}" fill="#10b981" font-size="10" text-anchor="end" font-weight="bold">Meta 80%</text>

            <!-- Ticks Eje Y -->
            <text x="${padding.left - 8}" y="${padding.top + 4}" fill="#94a3b8" font-size="10" text-anchor="end">100%</text>
            <text x="${padding.left - 8}" y="${padding.top + chartH * 0.5 + 4}" fill="#94a3b8" font-size="10" text-anchor="end">50%</text>
            <text x="${padding.left - 8}" y="${padding.top + chartH + 4}" fill="#94a3b8" font-size="10" text-anchor="end">0%</text>

            <!-- Área rellena con gradiente -->
            <polygon points="${areaPoints}" fill="url(#svgGradient)"/>

            <!-- Línea de evolución -->
            <polyline fill="none" stroke="#38bdf8" stroke-width="3" points="${polylinePoints}" stroke-linecap="round" stroke-linejoin="round"/>

            <!-- Puntos de datos interactivos -->
            ${points.map(pt => `
                <g class="cursor-pointer group">
                    <circle cx="${pt.x}" cy="${pt.y}" r="6" fill="${pt.p.passed ? '#10b981' : '#f43f5e'}" stroke="#0f172a" stroke-width="2"/>
                    <text x="${pt.x}" y="${padding.top + chartH + 20}" fill="#94a3b8" font-size="10" text-anchor="middle">#${pt.p.attemptNumber}</text>
                    <title>Intento #${pt.p.attemptNumber}: ${pt.p.scorePercent}% (${pt.p.passed ? 'Aprobado' : 'Suspenso'}) - ${pt.p.formattedDate}</title>
                </g>
            `).join('')}
        </svg>
    `;

    svgContainer.innerHTML = svgHtml;
}

// =================== FICHAS DE APRENDIZAJE (PRESENTACIÓN INTERACTIVA & ADJUNTAR PREGUNTAS) ===================
let activeStudyCardDeckId = null;
let activeStudyCardParsedDeck = null;
let activeStudyCardSlideIndex = 0;
let isStudyCardsUploadOpen = false;
let activeStudyCardsInputSubmode = 'text';
let isStudyCardsFocusMode = false;

let activeAttachQuestionsDeckId = null;
let activeAttachQuestionsTab = 'linked';
let selectedBankQuestionIds = new Set();

const SAMPLE_STUDY_CARDS_MARKDOWN = `---

## 1. La idea general

### Ficha 1 · ¿De qué trata esta página?
**Respuesta:** Explica las **5 formas** que hay de mover datos con FHIR.
💡 Es como elegir transporte: tren, avión, coche… Cada uno sirve mejor según el viaje.

Las cinco formas:
1. **API REST**
2. **Mensajes** (*Messaging*)
3. **Documentos** (*Documents*)
4. **Servicios** (*Services / SOA*)
5. **Base de datos** (*Persistent store*)

### Ficha 2 · ¿Hay que elegir solo una?
**Respuesta:** **No.** Se pueden usar varias a la vez. La mejor depende de **cómo está montado tu sistema**.
⚠️ Ninguna "arregla" los defectos de otra. Mensajes no sustituye a REST ni REST a mensajes: son distintas.

### Ficha 3 · Resumen en una tabla
| Forma | Se parece a… | Se usa cuando… |
|---|---|---|
| REST | Ir a una ventanilla y pedir o dejar datos al momento | Quieres consultar o guardar datos concretos |
| Mensajes | Enviar una carta a otra organización | Las organizaciones casi no comparten procesos |
| Documentos | Enviar un informe cerrado y firmado | Quieres que lo lea una persona tal cual se emitió |
| Servicios | Una empresa con departamentos que se piden cosas | Tu arquitectura es por servicios |
| Base de datos | El archivador donde guardas todo | Quieres guardar los datos ya en formato FHIR |

---

## 2. API REST

### Ficha 4 · ¿Qué es?
**Respuesta:** Una forma de hablar entre un **cliente** y un **servidor**. El cliente puede **crear, leer, modificar, borrar, buscar y ejecutar operaciones**.
💡 Sirve para **enviar** datos (*push*) y para **pedirlos** (*pull*).

### Ficha 5 · ¿Qué más incluye?
**Respuesta:** Permite trabajo **asíncrono** (pides algo y la respuesta llega después) y consultas con **GraphQL**.

### Ficha 6 · 🧩 Caso de uso: la app del paciente
*Una app móvil muestra al paciente sus alergias.*
**Respuesta:** **REST.** La app pregunta "dame las alergias del paciente 123" y el servidor contesta al momento.
Se suele añadir **SMART App Launch** para controlar quién entra.

### Ficha 7 · 🧩 Caso de uso: dos sistemas que ya se conocen
*Dos hospitales de la misma red, con acuerdos firmados, comparten datos.*
**Respuesta:** **REST.** La página dice que va bien cuando ya hay **acuerdos de gobernanza** (reglas pactadas entre los participantes).

---

## 3. Mensajes (*Messaging*)

### Ficha 8 · ¿Qué es?
**Respuesta:** Enviar **mensajes** de un sistema a otro, que se encaminan hasta su destino.
💡 Como mandar una carta: tú envías, no necesitas ir a la ventanilla del otro.
Puede funcionar **encima de REST** o con otra tecnología de mensajería.

### Ficha 9 · 🎯 ¿Cuándo es buena idea?
**Respuesta:** Cuando se intercambia entre **organizaciones muy distintas** que **casi no comparten procesos**.
Frase de la página: puede ser *"más adecuado para el intercambio entre organizaciones dispares con bajo nivel de integración de procesos"*.

### Ficha 10 · 🧩 Caso de uso: laboratorio externo
*Un laboratorio de otra empresa avisa a varios hospitales de que hay un resultado. Cada hospital usa un sistema diferente.*
**Respuesta:** **Mensajes.** El laboratorio envía el mensaje y no necesita conocer cómo funciona cada hospital por dentro.

### Ficha 11 · 🧩 Para ti: HL7 v2 y HealthConnect
*Tú ya mueves ADT u ORU con un motor de integración.*
**Respuesta:** Los mensajes de FHIR son **parecidos en idea**: un evento sale de un sistema y llega a otros. Es la forma más cercana a lo que ya conoces de HL7 v2.

---`;

function switchMaterialSubview(subview) {
    activeMaterialSubview = subview === 'cards' ? 'cards' : 'docs';

    const docsSubview = document.getElementById('mat-subview-docs');
    const cardsSubview = document.getElementById('mat-subview-cards');
    const docsActions = document.getElementById('mat-top-actions-docs');
    const cardsActions = document.getElementById('mat-top-actions-cards');
    const docsBtn = document.getElementById('btn-mat-subview-docs');
    const cardsBtn = document.getElementById('btn-mat-subview-cards');

    const sidebarMatBtn = document.getElementById('btn-tab-materials');
    const sidebarCardsBtn = document.getElementById('btn-tab-study-cards');

    if (activeMaterialSubview === 'cards') {
        if (docsSubview) docsSubview.classList.add('hidden');
        if (cardsSubview) cardsSubview.classList.remove('hidden');
        if (docsActions) docsActions.classList.add('hidden');
        if (cardsActions) cardsActions.classList.remove('hidden');

        if (cardsBtn) {
            cardsBtn.className = 'px-4 py-2 rounded-lg text-xs font-bold transition bg-amber-500 text-slate-950 shadow-sm cursor-pointer flex items-center space-x-2';
        }
        if (docsBtn) {
            docsBtn.className = 'px-4 py-2 rounded-lg text-xs font-bold transition text-slate-400 hover:text-white cursor-pointer flex items-center space-x-2';
        }

        const viewMat = document.getElementById('view-materials');
        if (viewMat && !viewMat.classList.contains('hidden')) {
            if (sidebarMatBtn) sidebarMatBtn.classList.remove('active');
            if (sidebarCardsBtn) sidebarCardsBtn.classList.add('active');
        }

        renderStudyCardsSection();
    } else {
        if (cardsSubview) cardsSubview.classList.add('hidden');
        if (docsSubview) docsSubview.classList.remove('hidden');
        if (cardsActions) cardsActions.classList.add('hidden');
        if (docsActions) docsActions.classList.remove('hidden');

        if (docsBtn) {
            docsBtn.className = 'px-4 py-2 rounded-lg text-xs font-bold transition bg-blue-600 text-white shadow-sm cursor-pointer flex items-center space-x-2';
        }
        if (cardsBtn) {
            cardsBtn.className = 'px-4 py-2 rounded-lg text-xs font-bold transition text-slate-400 hover:text-white cursor-pointer flex items-center space-x-2';
        }

        const viewMat = document.getElementById('view-materials');
        if (viewMat && !viewMat.classList.contains('hidden')) {
            if (sidebarCardsBtn) sidebarCardsBtn.classList.remove('active');
            if (sidebarMatBtn) sidebarMatBtn.classList.add('active');
        }
    }
}

function openStudyCardDeckInPresentation(id) {
    activeStudyCardDeckId = id;
    switchMaterialSubview('cards');
    selectStudyCardDeck(id);
}

function toggleStudyCardsUploadDrawer(forceState) {
    const drawer = document.getElementById('cards-upload-drawer');
    const icon = document.getElementById('btn-toggle-cards-upload-icon');
    const label = document.getElementById('btn-toggle-cards-upload-label');
    if (!drawer) return;

    isStudyCardsUploadOpen = typeof forceState === 'boolean' ? forceState : !isStudyCardsUploadOpen;

    if (isStudyCardsUploadOpen) {
        drawer.classList.remove('hidden');
        if (icon) icon.textContent = '✕';
        if (label) label.textContent = 'Cerrar formulario';
        const titleInput = document.getElementById('import-cards-title');
        if (titleInput) setTimeout(() => titleInput.focus(), 80);
    } else {
        drawer.classList.add('hidden');
        if (icon) icon.textContent = '➕';
        if (label) label.textContent = 'Cargar Nuevas Fichas (.md)';
    }
}

function toggleStudyCardsInputSubmode(submode) {
    activeStudyCardsInputSubmode = submode === 'file' ? 'file' : 'text';
    const textContainer = document.getElementById('cards-text-input-container');
    const fileContainer = document.getElementById('cards-file-input-container');
    const textBtn = document.getElementById('cards-mode-text-btn');
    const fileBtn = document.getElementById('cards-mode-file-btn');

    if (activeStudyCardsInputSubmode === 'file') {
        if (textContainer) textContainer.classList.add('hidden');
        if (fileContainer) fileContainer.classList.remove('hidden');
        if (fileBtn) fileBtn.className = 'px-3 py-1 rounded-md text-xs font-semibold bg-amber-500 text-slate-950 transition cursor-pointer';
        if (textBtn) textBtn.className = 'px-3 py-1 rounded-md text-xs font-semibold text-slate-400 hover:text-white transition cursor-pointer';
    } else {
        if (fileContainer) fileContainer.classList.add('hidden');
        if (textContainer) textContainer.classList.remove('hidden');
        if (textBtn) textBtn.className = 'px-3 py-1 rounded-md text-xs font-semibold bg-amber-500 text-slate-950 transition cursor-pointer';
        if (fileBtn) fileBtn.className = 'px-3 py-1 rounded-md text-xs font-semibold text-slate-400 hover:text-white transition cursor-pointer';
        updateStudyCardsLiveCount();
    }
}

function loadSampleStudyCardsMarkdown() {
    toggleStudyCardsInputSubmode('text');
    const titleInput = document.getElementById('import-cards-title');
    const textArea = document.getElementById('import-cards-text');
    if (titleInput && !titleInput.value.trim()) {
        titleInput.value = '5 Formas de Mover Datos con FHIR (Paradigmas)';
    }
    if (textArea) {
        textArea.value = SAMPLE_STUDY_CARDS_MARKDOWN;
    }
    updateStudyCardsLiveCount();
    showToast('Ejemplo de 11 fichas cargado en el editor.', 'info');
}

function updateStudyCardsLiveCount() {
    const textArea = document.getElementById('import-cards-text');
    const badge = document.getElementById('cards-live-preview-badge');
    if (!badge) return;
    const raw = textArea ? textArea.value : '';
    const parsed = parseStudyCardsMarkdown(raw);
    badge.textContent = `${parsed.chapters.length} capítulo${parsed.chapters.length === 1 ? '' : 's'} · ${parsed.cards.length} ficha${parsed.cards.length === 1 ? '' : 's'} detectada${parsed.cards.length === 1 ? '' : 's'}`;
}

function previewStudyCardsFile(event) {
    const fileInput = event.target;
    const file = fileInput && fileInput.files ? fileInput.files[0] : null;
    if (!file) return;

    const titleInput = document.getElementById('import-cards-title');
    if (titleInput && !titleInput.value.trim()) {
        titleInput.value = file.name.replace(/\.(md|markdown|txt)$/i, '');
    }

    const reader = new FileReader();
    reader.onload = (e) => {
        const content = e.target.result || '';
        const parsed = parseStudyCardsMarkdown(content);
        const badge = document.getElementById('cards-live-preview-badge');
        if (badge) {
            badge.textContent = `${parsed.chapters.length} capítulo${parsed.chapters.length === 1 ? '' : 's'} · ${parsed.cards.length} ficha${parsed.cards.length === 1 ? '' : 's'} en archivo`;
        }
    };
    reader.readAsText(file);
}

async function submitStudyCardsMaterial(event) {
    if (event && event.preventDefault) event.preventDefault();
    const titleEl = document.getElementById('import-cards-title');
    const topicEl = document.getElementById('import-cards-topic-id') || document.getElementById('import-cards-topic');
    const statusEl = document.getElementById('import-cards-status');
    const submitBtn = document.getElementById('btn-import-cards-submit');

    const title = titleEl ? titleEl.value.trim() : '';
    const topicId = topicEl ? topicEl.value : '';

    if (!title) {
        showToast('Indica un título para la colección de fichas.', 'error');
        return;
    }

    if (statusEl) statusEl.classList.remove('hidden');
    if (submitBtn) submitBtn.disabled = true;

    try {
        let resp;
        if (activeStudyCardsInputSubmode === 'file') {
            const fileInput = document.getElementById('import-cards-file');
            if (!fileInput || !fileInput.files.length) {
                showToast('Selecciona un archivo Markdown (.md).', 'error');
                return;
            }
            const formData = new FormData();
            formData.append('title', title);
            formData.append('file', fileInput.files[0]);
            formData.append('sourceType', 'STUDY_CARDS');
            if (topicId) formData.append('topicId', topicId);

            resp = await fetch('/api/materials/markdown/file', {
                method: 'POST',
                body: formData
            });
        } else {
            const textEl = document.getElementById('import-cards-text');
            const contentText = textEl ? textEl.value : '';
            if (!contentText || !contentText.trim()) {
                showToast('Pega el contenido Markdown de las fichas.', 'error');
                return;
            }
            const parsed = parseStudyCardsMarkdown(contentText);
            if (parsed.cards.length === 0) {
                showToast('No se detectaron fichas con formato ### Título. Revisa que los títulos de cada ficha empiecen con ###.', 'error');
                return;
            }
            resp = await fetch('/api/materials/markdown/text', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    title,
                    contentText,
                    topicId: topicId ? Number(topicId) : null,
                    sourceType: 'STUDY_CARDS'
                })
            });
        }

        if (resp && resp.ok) {
            const created = await resp.json();
            if (titleEl) titleEl.value = '';
            const textEl = document.getElementById('import-cards-text');
            if (textEl) textEl.value = '';
            const fileEl = document.getElementById('import-cards-file');
            if (fileEl) fileEl.value = '';
            updateStudyCardsLiveCount();

            toggleStudyCardsUploadDrawer(false);
            activeStudyCardDeckId = created.id;
            await loadMaterials();
            selectStudyCardDeck(created.id);
            showToast(`¡Colección "${created.title}" cargada y lista para presentar!`, 'success');
        } else {
            const err = resp ? await resp.json().catch(() => ({})) : {};
            showToast('Error al guardar las fichas: ' + (err.error || 'Verifica los datos'), 'error');
        }
    } catch (e) {
        showToast('Error de red al guardar las fichas: ' + e.message, 'error');
    } finally {
        if (statusEl) statusEl.classList.add('hidden');
        if (submitBtn) submitBtn.disabled = false;
    }
}

/**
 * Parsea el formato Markdown de Fichas de Estudio:
 * - Títulos ## -> Capítulos
 * - Títulos ### -> Fichas individuales
 * - Ignora separadores ---
 */
function parseStudyCardsMarkdown(markdownText) {
    if (!markdownText || !markdownText.trim()) {
        return { chapters: [], cards: [] };
    }

    const lines = markdownText.replace(/\r\n/g, '\n').split('\n');
    const chapters = [];
    const cards = [];

    let currentChapter = null;
    let currentCard = null;
    let bodyBuffer = [];

    const flushCard = () => {
        if (!currentCard) return;
        const bodyMd = bodyBuffer.join('\n').trim();
        currentCard.bodyMarkdown = bodyMd;
        currentCard.bodyHtml = renderStudyCardMarkdownBody(bodyMd);
        cards.push(currentCard);
        if (currentChapter) {
            currentChapter.cards.push(currentCard);
        }
        currentCard = null;
        bodyBuffer = [];
    };

    for (let i = 0; i < lines.length; i++) {
        const rawLine = lines[i];
        const trimmed = rawLine.trim();

        // Ignorar líneas divisoras ---
        if (/^-{3,}$/.test(trimmed)) {
            continue;
        }

        // Detectar Capítulo: ## Título (pero no ###)
        const chapterMatch = rawLine.match(/^\s*##\s+([^#].*)$/);
        if (chapterMatch) {
            flushCard();
            const chapTitle = cleanInlineMarkdownTitle(chapterMatch[1].trim());
            currentChapter = {
                index: chapters.length + 1,
                title: chapTitle,
                rawTitle: chapterMatch[1].trim(),
                cards: []
            };
            chapters.push(currentChapter);
            continue;
        }

        // Detectar Ficha: ### Ficha X · Pregunta o ### Título
        const cardMatch = rawLine.match(/^\s*###\s+(.*)$/);
        if (cardMatch) {
            flushCard();
            if (!currentChapter) {
                currentChapter = {
                    index: 1,
                    title: 'General',
                    rawTitle: 'General',
                    cards: []
                };
                chapters.push(currentChapter);
            }

            const fullCardHeading = cardMatch[1].trim();
            // Separar "Ficha X · ¿Pregunta?" si usa punto medio · o : o -
            let badgeLabel = `Ficha ${cards.length + 1}`;
            let cardTitle = fullCardHeading;

            const dotSplit = fullCardHeading.match(/^(Ficha\s+\d+)\s*[·•:\-–—]\s*(.+)$/i);
            if (dotSplit) {
                badgeLabel = dotSplit[1].trim();
                cardTitle = dotSplit[2].trim();
            }

            currentCard = {
                index: cards.length,
                number: cards.length + 1,
                badgeLabel: badgeLabel,
                title: cleanInlineMarkdownTitle(cardTitle),
                rawHeading: fullCardHeading,
                chapterIndex: currentChapter.index,
                chapterTitle: currentChapter.title,
                bodyMarkdown: '',
                bodyHtml: ''
            };
            continue;
        }

        if (currentCard) {
            bodyBuffer.push(rawLine);
        }
    }

    flushCard();

    return { chapters, cards };
}

function cleanInlineMarkdownTitle(text) {
    if (!text) return '';
    return text
        .replace(/\*\*(.+?)\*\*/g, '$1')
        .replace(/\*(.+?)\*/g, '$1')
        .replace(/`(.+?)`/g, '$1');
}

/**
 * Renderiza el cuerpo Markdown de una ficha con diseño enriquecido para:
 * - **Respuesta:** destacada en caja esmeralda/azul
 * - 💡 Consejos / Analogías en caja ámbar
 * - ⚠️ Advertencias en caja rosa/ámbar
 * - Tablas Markdown estilizadas
 * - Listas numeradas y con viñetas
 */
function renderStudyCardMarkdownBody(md) {
    if (!md) return '<p class="text-slate-400 italic">Sin contenido adicional en esta ficha.</p>';

    const lines = md.split('\n');
    let html = '';
    let i = 0;

    while (i < lines.length) {
        const line = lines[i];
        const trimmed = line.trim();

        if (!trimmed) {
            i++;
            continue;
        }

        // 1. Detectar Tabla Markdown
        if (trimmed.startsWith('|') && trimmed.endsWith('|') && i + 1 < lines.length && /^\s*\|[\s\-:|]+\|\s*$/.test(lines[i + 1])) {
            const headerCells = trimmed.slice(1, -1).split('|').map(c => c.trim());
            i += 2; // saltar cabecera y separador
            const rows = [];
            while (i < lines.length && lines[i].trim().startsWith('|') && lines[i].trim().endsWith('|')) {
                const rowCells = lines[i].trim().slice(1, -1).split('|').map(c => c.trim());
                rows.push(rowCells);
                i++;
            }

            html += `<div class="overflow-x-auto my-3"><table><thead><tr>`;
            headerCells.forEach(h => {
                html += `<th>${formatInlineMarkdown(h)}</th>`;
            });
            html += `</tr></thead><tbody>`;
            rows.forEach(r => {
                html += `<tr>`;
                r.forEach((cell, idx) => {
                    const weightClass = idx === 0 ? 'font-bold text-amber-300 whitespace-nowrap' : 'text-slate-200';
                    html += `<td class="${weightClass}">${formatInlineMarkdown(cell)}</td>`;
                });
                html += `</tr>`;
            });
            html += `</tbody></table></div>`;
            continue;
        }

        // 2. Detectar Lista Numerada
        if (/^\d+\.\s+/.test(trimmed)) {
            html += `<ol class="list-decimal pl-6 space-y-1.5 my-3 text-slate-200">`;
            while (i < lines.length && /^\d+\.\s+/.test(lines[i].trim())) {
                const itemText = lines[i].trim().replace(/^\d+\.\s+/, '');
                html += `<li class="pl-1">${formatInlineMarkdown(itemText)}</li>`;
                i++;
            }
            html += `</ol>`;
            continue;
        }

        // 3. Detectar Lista con Viñetas
        if (/^[-*+]\s+/.test(trimmed)) {
            html += `<ul class="list-disc pl-6 space-y-1.5 my-3 text-slate-200">`;
            while (i < lines.length && /^[-*+]\s+/.test(lines[i].trim())) {
                const itemText = lines[i].trim().replace(/^[-*+]\s+/, '');
                html += `<li class="pl-1">${formatInlineMarkdown(itemText)}</li>`;
                i++;
            }
            html += `</ul>`;
            continue;
        }

        // 4. Detectar bloque **Respuesta:**
        if (/^\*\*Respuesta:\*\*/i.test(trimmed)) {
            const restOfAnswer = trimmed.replace(/^\*\*Respuesta:\*\*\s*/i, '');
            html += `
                <div class="study-card-answer-box">
                    <div class="flex items-start gap-2.5">
                        <span class="px-2 py-0.5 rounded-md bg-emerald-500/20 text-emerald-300 border border-emerald-500/30 text-[11px] font-extrabold uppercase tracking-wider shrink-0 mt-0.5">Respuesta</span>
                        <div class="text-slate-100 font-medium leading-relaxed">${formatInlineMarkdown(restOfAnswer)}</div>
                    </div>
                </div>
            `;
            i++;
            continue;
        }

        // 5. Detectar bloque 💡 Consejo / Idea clave
        if (trimmed.startsWith('💡')) {
            const tipText = trimmed.replace(/^💡\s*/, '');
            html += `
                <div class="study-card-tip-box flex items-start gap-3">
                    <span class="text-xl shrink-0 leading-none mt-0.5">💡</span>
                    <div class="text-amber-100/95 text-sm leading-relaxed">${formatInlineMarkdown(tipText)}</div>
                </div>
            `;
            i++;
            continue;
        }

        // 6. Detectar bloque ⚠️ Advertencia / Importante
        if (trimmed.startsWith('⚠️')) {
            const warnText = trimmed.replace(/^⚠️\s*/, '');
            html += `
                <div class="study-card-warn-box flex items-start gap-3">
                    <span class="text-xl shrink-0 leading-none mt-0.5">⚠️</span>
                    <div class="text-rose-100/95 text-sm leading-relaxed">${formatInlineMarkdown(warnText)}</div>
                </div>
            `;
            i++;
            continue;
        }

        // 7. Párrafo de contexto en cursiva (*Caso de uso...*)
        if (/^\*[^*]+\*$/.test(trimmed)) {
            const italicContent = trimmed.slice(1, -1);
            html += `
                <div class="bg-slate-800/75 border-l-4 border-indigo-400 px-4 py-2.5 rounded-r-xl my-2.5 text-indigo-200 italic text-sm">
                    ${formatInlineMarkdown(italicContent)}
                </div>
            `;
            i++;
            continue;
        }

        // 8. Párrafo estándar
        html += `<p>${formatInlineMarkdown(trimmed)}</p>`;
        i++;
    }

    return html;
}

function formatInlineMarkdown(text) {
    if (!text) return '';
    // Escapar HTML básico primero
    let safe = text
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;');

    // Código inline `code`
    safe = safe.replace(/`([^`]+)`/g, '<code class="px-1.5 py-0.5 rounded bg-slate-800 text-amber-300 font-mono text-[0.9em] border border-slate-700">$1</code>');
    // Negrita **texto**
    safe = safe.replace(/\*\*([^*]+)\*\*/g, '<strong class="font-bold text-white">$1</strong>');
    // Cursiva *texto*
    safe = safe.replace(/\*([^*]+)\*/g, '<em class="italic text-sky-200">$1</em>');

    return safe;
}

/**
 * Renderiza el selector superior de colecciones de fichas y actualiza la presentación activa
 */
function renderStudyCardsSection() {
    const decksContainer = document.getElementById('study-cards-decks-container');
    const pillsStrip = document.getElementById('study-cards-pills-strip');
    const quickSelect = document.getElementById('sc-deck-quick-select');
    const countBadge = document.getElementById('study-cards-decks-count-badge');
    const catalogBadge = document.getElementById('cards-catalog-badge');
    const presentationWrapper = document.getElementById('study-cards-presentation-wrapper');
    const topicSelect = document.getElementById('cards-catalog-topic-select');

    const studyCardDecks = (allMaterials || []).filter(m => isStudyCardsMaterial(m));

    if (countBadge) {
        countBadge.textContent = `${studyCardDecks.length} colecci${studyCardDecks.length === 1 ? 'ón' : 'ones'}`;
    }
    if (catalogBadge) {
        catalogBadge.textContent = String(studyCardDecks.length);
    }

    if (topicSelect && topicSelect.options.length <= 1) {
        topicSelect.innerHTML = '<option value="">Todos los Temas</option>';
        const seenTopics = new Map();
        studyCardDecks.forEach(d => {
            if (d.topic && !seenTopics.has(d.topic.id)) {
                seenTopics.set(d.topic.id, d.topic.name);
            }
        });
        seenTopics.forEach((name, id) => {
            const opt = document.createElement('option');
            opt.value = String(id);
            opt.textContent = name;
            topicSelect.appendChild(opt);
        });
    }

    if (studyCardDecks.length === 0) {
        if (decksContainer) {
            decksContainer.innerHTML = `
                <div class="col-span-full bg-slate-900/50 border border-dashed border-slate-800 rounded-2xl p-8 text-center">
                    <span class="text-4xl block mb-2">🗂️</span>
                    <p class="text-white font-bold text-sm">Aún no hay colecciones de Fichas de Aprendizaje</p>
                    <p class="text-slate-400 text-xs mt-1 mb-4">Carga tus apuntes con capítulos (##) y fichas (###) para estudiarlos como presentación interactiva.</p>
                    <button onclick="toggleStudyCardsUploadDrawer(true); loadSampleStudyCardsMarkdown();" class="px-4 py-2 rounded-xl bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold text-xs cursor-pointer transition">
                        ✨ Cargar Fichas de Ejemplo FHIR
                    </button>
                </div>
            `;
        }
        if (pillsStrip) pillsStrip.innerHTML = '';
        if (quickSelect) quickSelect.innerHTML = '<option value="">Sin colecciones</option>';
        if (presentationWrapper) presentationWrapper.classList.add('hidden');
        return;
    }

    if (presentationWrapper) presentationWrapper.classList.remove('hidden');

    // Si no hay mazo seleccionado o ya no existe, elegir el primero
    if (!activeStudyCardDeckId || !studyCardDecks.some(d => d.id === activeStudyCardDeckId)) {
        activeStudyCardDeckId = studyCardDecks[0].id;
        activeStudyCardSlideIndex = 0;
    }

    // 1. Poblar el Quick Select Dropdown nativo
    if (quickSelect) {
        quickSelect.innerHTML = '';
        studyCardDecks.forEach(deck => {
            const opt = document.createElement('option');
            opt.value = String(deck.id);
            const parsed = parseStudyCardsMarkdown(deck.contentText || '');
            opt.textContent = `🗂️ ${deck.title} (${parsed.cards.length} fichas)`;
            if (deck.id === activeStudyCardDeckId) {
                opt.selected = true;
            }
            quickSelect.appendChild(opt);
        });
    }

    // 2. Poblar la tira horizontal de píldoras rápidas (1 clic)
    if (pillsStrip) {
        pillsStrip.innerHTML = '';
        studyCardDecks.forEach(deck => {
            const isSelected = deck.id === activeStudyCardDeckId;
            const parsed = parseStudyCardsMarkdown(deck.contentText || '');
            const pill = document.createElement('button');
            pill.type = 'button';
            pill.dataset.deckId = String(deck.id);
            pill.className = isSelected
                ? 'px-3.5 py-1.5 rounded-xl text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/50 shadow-sm shrink-0 flex items-center gap-1.5 cursor-pointer transition'
                : 'px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-slate-900/80 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 shrink-0 flex items-center gap-1.5 cursor-pointer transition';
            pill.onclick = () => selectStudyCardDeck(deck.id);
            pill.innerHTML = `
                <span>🗂️</span>
                <span class="max-w-[200px] truncate">${deck.title}</span>
                <span class="px-1.5 py-0.2 rounded text-[10px] font-mono ${isSelected ? 'bg-amber-500/30 text-amber-200' : 'bg-slate-800 text-slate-400'}">${parsed.cards.length}</span>
            `;
            pillsStrip.appendChild(pill);

            if (isSelected) {
                setTimeout(() => {
                    pill.scrollIntoView({ inline: 'nearest', behavior: 'smooth' });
                }, 50);
            }
        });
    }

    // 3. Poblar las tarjetas del Catálogo Completo (en el modal/popover)
    filterStudyCardsCatalog();

    selectStudyCardDeck(activeStudyCardDeckId, true);
}

/**
 * Filtra las tarjetas mostradas en el Catálogo Modal según el buscador y el tema
 */
function filterStudyCardsCatalog() {
    const decksContainer = document.getElementById('study-cards-decks-container');
    if (!decksContainer) return;

    const searchInput = document.getElementById('cards-catalog-search-input');
    const topicSelect = document.getElementById('cards-catalog-topic-select');
    const query = (searchInput ? searchInput.value : '').trim().toLowerCase();
    const topicFilter = topicSelect ? topicSelect.value : '';

    const studyCardDecks = (allMaterials || []).filter(m => isStudyCardsMaterial(m));
    const filtered = studyCardDecks.filter(deck => {
        if (topicFilter && (!deck.topic || String(deck.topic.id) !== topicFilter)) {
            return false;
        }
        if (query) {
            const titleMatch = (deck.title || '').toLowerCase().includes(query);
            const idMatch = String(deck.id).includes(query);
            const topicMatch = deck.topic && deck.topic.name.toLowerCase().includes(query);
            const contentMatch = (deck.contentText || '').toLowerCase().includes(query);
            return titleMatch || idMatch || topicMatch || contentMatch;
        }
        return true;
    });

    decksContainer.innerHTML = '';
    if (filtered.length === 0) {
        decksContainer.innerHTML = `
            <div class="col-span-full py-8 text-center text-slate-400 text-xs">
                🔍 No se encontraron colecciones de fichas con ese criterio de búsqueda.
            </div>
        `;
        return;
    }

    filtered.forEach(deck => {
        const isSelected = deck.id === activeStudyCardDeckId;
        const parsed = parseStudyCardsMarkdown(deck.contentText || '');
        const linkedCount = deck.linkedQuestionsCount || 0;
        const topicName = deck.topic ? deck.topic.name : 'General';

        const cardEl = document.createElement('div');
        cardEl.className = isSelected
            ? 'p-3.5 rounded-2xl border-2 border-amber-500/80 bg-gradient-to-br from-amber-500/15 via-slate-900 to-slate-900 shadow-lg shadow-amber-950/20 cursor-pointer transition flex flex-col justify-between'
            : 'p-3.5 rounded-2xl border border-slate-800/90 bg-slate-900/80 hover:bg-slate-800 hover:border-slate-700 cursor-pointer transition flex flex-col justify-between';

        cardEl.onclick = () => {
            selectStudyCardDeck(deck.id);
            closeStudyCardsCatalogModal();
        };

        cardEl.innerHTML = `
            <div>
                <div class="flex items-center justify-between gap-2 mb-1.5">
                    <div class="flex items-center gap-1.5">
                        <span class="px-2 py-0.5 rounded-md text-[10px] font-bold uppercase tracking-wider bg-amber-500/20 text-amber-300 border border-amber-500/30">🗂️ ${parsed.cards.length} Fichas</span>
                        <span class="px-1.5 py-0.5 rounded-md text-[10px] font-bold font-mono bg-slate-800 text-slate-300 border border-slate-700">ID #${deck.id}</span>
                    </div>
                    <button onclick="event.stopPropagation(); deleteMaterial(${deck.id})" title="Eliminar colección" class="w-6 h-6 rounded-lg text-slate-500 hover:text-rose-400 hover:bg-rose-500/10 flex items-center justify-center transition text-xs cursor-pointer">🗑️</button>
                </div>
                <h4 class="font-bold text-white text-xs leading-snug line-clamp-2">${deck.title}</h4>
                <p class="text-[10px] text-slate-400 mt-1 truncate">📚 ${topicName} · ${parsed.chapters.length} capítulo${parsed.chapters.length === 1 ? '' : 's'}</p>
            </div>
            <div class="mt-2.5 pt-2 border-t border-slate-800/80 flex items-center justify-between gap-2">
                <div class="flex items-center gap-1.5">
                    <span class="text-[10px] font-semibold ${linkedCount > 0 ? 'text-emerald-400' : 'text-slate-500'}">
                        ${linkedCount > 0 ? `✅ ${linkedCount} preg.` : '⚪ 0 preg.'}
                    </span>
                    ${(deck.commentsCount || 0) > 0 ? `<span class="text-[10px] font-semibold text-indigo-300">· 💬 ${deck.commentsCount}</span>` : ''}
                </div>
                <span class="text-[10px] font-bold ${isSelected ? 'text-amber-400' : 'text-slate-400'}">
                    ${isSelected ? '● Proyectando' : 'Seleccionar →'}
                </span>
            </div>
        `;
        decksContainer.appendChild(cardEl);
    });
}

/**
 * Control del modal/popover del Catálogo Completo
 */
function toggleStudyCardsCatalogModal() {
    const modal = document.getElementById('modal-cards-catalog');
    if (!modal) return;
    if (modal.classList.contains('hidden')) {
        modal.classList.remove('hidden');
        filterStudyCardsCatalog();
        const input = document.getElementById('cards-catalog-search-input');
        if (input) setTimeout(() => input.focus(), 100);
    } else {
        closeStudyCardsCatalogModal();
    }
}

function closeStudyCardsCatalogModal() {
    const modal = document.getElementById('modal-cards-catalog');
    if (modal) modal.classList.add('hidden');
}

/**
 * Avanza o retrocede de colección en 1 solo clic con los botones ◀ / ▶
 */
function stepStudyCardDeck(direction) {
    const studyCardDecks = (allMaterials || []).filter(m => isStudyCardsMaterial(m));
    if (studyCardDecks.length === 0) return;
    const currentIndex = studyCardDecks.findIndex(d => d.id === activeStudyCardDeckId);
    let nextIndex = currentIndex + direction;
    if (nextIndex < 0) nextIndex = studyCardDecks.length - 1;
    if (nextIndex >= studyCardDecks.length) nextIndex = 0;
    selectStudyCardDeck(studyCardDecks[nextIndex].id);
}

let activeDeckComments = [];
let activeDeckCommentsLoadedForId = null;
let isCardCommentComposerOpen = false;
let isCardCommentPreviewVisible = false;
let editingCardCommentId = null;

function selectStudyCardDeck(id, preserveSlideIndex = false) {
    const deck = (allMaterials || []).find(m => m.id === id);
    if (!deck) return;

    const changedDeck = activeStudyCardDeckId !== id;
    activeStudyCardDeckId = id;
    activeStudyCardParsedDeck = parseStudyCardsMarkdown(deck.contentText || '');

    if (changedDeck || !preserveSlideIndex) {
        activeStudyCardSlideIndex = 0;
        editingCardCommentId = null;
        toggleStudyCardCommentComposer(false);
    }
    if (activeStudyCardSlideIndex >= activeStudyCardParsedDeck.cards.length) {
        activeStudyCardSlideIndex = Math.max(0, activeStudyCardParsedDeck.cards.length - 1);
    }

    // Actualizar barra superior del mazo activo
    const titleEl = document.getElementById('sc-active-deck-title');
    const idEl = document.getElementById('sc-active-deck-id');
    const topicEl = document.getElementById('sc-active-deck-topic');
    const metaEl = document.getElementById('sc-active-deck-meta');
    const qBadgeEl = document.getElementById('sc-attached-q-badge');
    const testBtnEl = document.getElementById('sc-btn-start-test');

    if (titleEl) titleEl.textContent = deck.title;
    if (idEl) idEl.textContent = `ID #${deck.id}`;
    if (topicEl) topicEl.textContent = deck.topic ? deck.topic.name : 'General';
    if (metaEl) {
        const totalComments = deck.commentsCount || 0;
        const commentsSuffix = totalComments > 0 ? ` · 💬 ${totalComments} comentario${totalComments === 1 ? '' : 's'}` : '';
        metaEl.textContent = `${activeStudyCardParsedDeck.chapters.length} capítulo${activeStudyCardParsedDeck.chapters.length === 1 ? '' : 's'} · ${activeStudyCardParsedDeck.cards.length} ficha${activeStudyCardParsedDeck.cards.length === 1 ? '' : 's'}${commentsSuffix}`;
    }

    const linkedCount = deck.linkedQuestionsCount || 0;
    if (qBadgeEl) qBadgeEl.textContent = String(linkedCount);

    if (testBtnEl) {
        if (linkedCount > 0) {
            testBtnEl.className = 'px-4 py-2 rounded-xl text-xs font-bold bg-emerald-600 hover:bg-emerald-500 text-white shadow-lg shadow-emerald-600/20 transition cursor-pointer flex items-center gap-1.5';
            testBtnEl.title = `Realizar prueba con las ${linkedCount} preguntas configuradas en estas fichas`;
        } else {
            testBtnEl.className = 'px-4 py-2 rounded-xl text-xs font-bold bg-slate-800 hover:bg-slate-700 text-slate-300 border border-slate-700 transition cursor-pointer flex items-center gap-1.5';
            testBtnEl.title = 'Configura o adjunta preguntas primero para activar la prueba de estas fichas';
        }
    }

    // Sincronizar el selector desplegable rápido y las píldoras horizontales si cambiamos de deck
    const quickSelect = document.getElementById('sc-deck-quick-select');
    if (quickSelect && quickSelect.value !== String(id)) {
        quickSelect.value = String(id);
    }

    const pillsStrip = document.getElementById('study-cards-pills-strip');
    if (pillsStrip) {
        pillsStrip.querySelectorAll('button[data-deck-id]').forEach(b => {
            const isSel = b.dataset.deckId === String(id);
            b.className = isSel
                ? 'px-3.5 py-1.5 rounded-xl text-xs font-bold bg-amber-500/20 text-amber-300 border border-amber-500/50 shadow-sm shrink-0 flex items-center gap-1.5 cursor-pointer transition'
                : 'px-3.5 py-1.5 rounded-xl text-xs font-semibold bg-slate-900/80 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 shrink-0 flex items-center gap-1.5 cursor-pointer transition';
            if (isSel) {
                b.scrollIntoView({ inline: 'nearest', behavior: 'smooth' });
            }
        });
    }

    // Refrescar borde activo en las tarjetas de colección sin recursión
    const decksContainer = document.getElementById('study-cards-decks-container');
    if (decksContainer && changedDeck) {
        filterStudyCardsCatalog();
    }

    renderStudyCardsChaptersTree();
    renderStudyCardSlide();

    if (changedDeck || activeDeckCommentsLoadedForId !== id) {
        loadStudyCardComments(id);
    }
}

function renderStudyCardsChaptersTree() {
    const treeEl = document.getElementById('sc-chapters-tree');
    const progressTextEl = document.getElementById('sc-seen-progress-badge') || document.getElementById('sc-deck-progress-text');
    const progressBarEl = document.getElementById('sc-deck-progress-bar');
    if (!treeEl || !activeStudyCardParsedDeck) return;

    const totalCards = activeStudyCardParsedDeck.cards.length;
    const currentPos = totalCards > 0 ? activeStudyCardSlideIndex + 1 : 0;
    const pct = totalCards > 0 ? Math.round((currentPos / totalCards) * 100) : 0;

    if (progressTextEl) progressTextEl.textContent = `${currentPos} / ${totalCards}`;
    if (progressBarEl) progressBarEl.style.width = `${pct}%`;

    treeEl.innerHTML = '';

    activeStudyCardParsedDeck.chapters.forEach(chap => {
        const chapBlock = document.createElement('div');
        chapBlock.className = 'mb-3 last:mb-0';

        const isCurrentChapter = chap.cards.some(c => c.index === activeStudyCardSlideIndex);

        let cardsButtonsHtml = '';
        chap.cards.forEach(c => {
            const isCurrent = c.index === activeStudyCardSlideIndex;
            const cardCommentsCount = (activeDeckComments || []).filter(cm => cm.cardIndex === c.index).length;
            const commentBadgeHtml = cardCommentsCount > 0
                ? `<span class="ml-auto px-1.5 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-mono text-[9px] font-bold shrink-0" title="${cardCommentsCount} comentario(s)">💬 ${cardCommentsCount}</span>`
                : '';

            const btnClass = isCurrent
                ? 'w-full text-left px-3 py-2 rounded-xl bg-amber-500/20 border border-amber-500/40 text-amber-200 font-bold text-xs flex items-center gap-2 transition cursor-pointer shadow-sm'
                : 'w-full text-left px-3 py-1.5 rounded-xl hover:bg-slate-800/70 text-slate-400 hover:text-slate-200 text-xs flex items-center gap-2 transition cursor-pointer';

            cardsButtonsHtml += `
                <button onclick="goToStudyCard(${c.index})" class="${btnClass}">
                    <span class="w-5 h-5 rounded-md ${isCurrent ? 'bg-amber-500 text-slate-950' : 'bg-slate-800 text-slate-400'} font-mono text-[10px] font-bold flex items-center justify-center shrink-0">${c.number}</span>
                    <span class="truncate">${c.title}</span>
                    ${commentBadgeHtml}
                </button>
            `;
        });

        chapBlock.innerHTML = `
            <div class="px-2.5 py-1.5 text-[11px] font-extrabold uppercase tracking-wider ${isCurrentChapter ? 'text-amber-400' : 'text-slate-400'} flex items-center justify-between">
                <span class="truncate" title="${chap.title}">${chap.title}</span>
                <span class="text-[10px] font-mono text-slate-500 ml-1 shrink-0">${chap.cards.length}</span>
            </div>
            <div class="space-y-1 mt-1 pl-1">
                ${cardsButtonsHtml}
            </div>
        `;
        treeEl.appendChild(chapBlock);
    });
}

function renderStudyCardSlide() {
    if (!activeStudyCardParsedDeck) return;
    const cards = activeStudyCardParsedDeck.cards;
    const total = cards.length;

    const chapterPillEl = document.getElementById('sc-slide-chapter-badge') || document.getElementById('sc-slide-chapter-pill');
    const counterPillEl = document.getElementById('sc-slide-counter') || document.getElementById('sc-slide-counter-pill');
    const badgeEl = document.getElementById('sc-slide-ficha-pill') || document.getElementById('sc-slide-ficha-badge');
    const titleEl = document.getElementById('sc-slide-title');
    const bodyEl = document.getElementById('sc-slide-body');
    const prevBtn = document.getElementById('sc-btn-prev');
    const nextBtn = document.getElementById('sc-btn-next');
    const finishTestBtn = document.getElementById('sc-btn-finish-test');
    const dotsEl = document.getElementById('sc-progress-segments') || document.getElementById('sc-slide-dots');
    const slideCardEl = document.getElementById('sc-slide-card');

    if (total === 0) {
        if (chapterPillEl) chapterPillEl.textContent = 'Sin capítulos';
        if (counterPillEl) counterPillEl.textContent = 'Ficha 0 de 0';
        if (badgeEl) badgeEl.textContent = 'Sin fichas';
        if (titleEl) titleEl.textContent = 'No se encontraron fichas (###) en este documento';
        if (bodyEl) bodyEl.innerHTML = '<p class="text-slate-400">Asegúrate de que cada ficha tenga un encabezado de nivel 3 (<code>### Ficha 1 · Título</code>).</p>';
        return;
    }

    const card = cards[activeStudyCardSlideIndex] || cards[0];

    // Animación sutil de transición de diapositiva
    if (slideCardEl) {
        slideCardEl.classList.remove('opacity-100', 'translate-y-0');
        slideCardEl.classList.add('opacity-95');
        setTimeout(() => {
            slideCardEl.classList.remove('opacity-95');
            slideCardEl.classList.add('opacity-100', 'translate-y-0');
        }, 60);
    }

    if (chapterPillEl) chapterPillEl.textContent = `📂 ${card.chapterTitle}`;
    if (counterPillEl) counterPillEl.textContent = `Ficha ${card.number} de ${total}`;
    if (badgeEl) badgeEl.textContent = card.badgeLabel;
    if (titleEl) titleEl.textContent = card.title;
    if (bodyEl) bodyEl.innerHTML = card.bodyHtml;

    if (prevBtn) {
        prevBtn.disabled = activeStudyCardSlideIndex <= 0;
    }
    const isLastSlide = activeStudyCardSlideIndex >= total - 1;
    if (nextBtn) {
        nextBtn.disabled = isLastSlide;
        nextBtn.innerHTML = isLastSlide
            ? `<span>Última Ficha</span><span>✓</span>`
            : `<span>Ficha Siguiente</span><span>→</span>`;
    }
    if (finishTestBtn) {
        if (isLastSlide) {
            finishTestBtn.classList.remove('hidden');
            finishTestBtn.classList.add('flex');
        } else {
            finishTestBtn.classList.add('hidden');
            finishTestBtn.classList.remove('flex');
        }
    }

    // Segmentos interactivos inferiores
    if (dotsEl) {
        dotsEl.innerHTML = cards.map((c, idx) => {
            const isCurrent = idx === activeStudyCardSlideIndex;
            const isPast = idx < activeStudyCardSlideIndex;
            const hasComments = (activeDeckComments || []).some(cm => cm.cardIndex === idx);
            const cls = isCurrent
                ? 'h-2.5 w-8 rounded-full bg-amber-400 transition-all cursor-pointer shadow-sm shadow-amber-400/50'
                : (isPast
                    ? `h-2 w-3 rounded-full ${hasComments ? 'bg-indigo-400/80' : 'bg-amber-500/40'} hover:bg-amber-400/70 transition-all cursor-pointer`
                    : `h-2 w-2.5 rounded-full ${hasComments ? 'bg-indigo-500/60' : 'bg-slate-800'} hover:bg-slate-600 transition-all cursor-pointer`);
            return `<button onclick="goToStudyCard(${idx})" title="${c.badgeLabel}: ${c.title}${hasComments ? ' (Con comentarios)' : ''}" class="${cls}"></button>`;
        }).join('');
    }

    renderStudyCardsChaptersTree();
    renderActiveStudyCardComments();
}

function prevStudyCard() {
    if (!activeStudyCardParsedDeck || activeStudyCardSlideIndex <= 0) return;
    activeStudyCardSlideIndex--;
    renderStudyCardSlide();
}

function nextStudyCard() {
    if (!activeStudyCardParsedDeck) return;
    if (activeStudyCardSlideIndex < activeStudyCardParsedDeck.cards.length - 1) {
        activeStudyCardSlideIndex++;
        renderStudyCardSlide();
    }
}

function goToStudyCard(index) {
    if (!activeStudyCardParsedDeck) return;
    if (index >= 0 && index < activeStudyCardParsedDeck.cards.length) {
        activeStudyCardSlideIndex = index;
        renderStudyCardSlide();
    }
}

function toggleStudyCardsFocusMode() {
    const sidebarCol = document.getElementById('sc-chapters-sidebar') || document.getElementById('sc-sidebar-col');
    const stageCol = document.getElementById('sc-slide-column') || document.getElementById('sc-stage-col');
    const focusBtn = document.getElementById('sc-btn-focus') || document.getElementById('sc-btn-focus-mode');
    if (!sidebarCol || !stageCol) return;

    isStudyCardsFocusMode = !isStudyCardsFocusMode;
    if (isStudyCardsFocusMode) {
        sidebarCol.classList.add('hidden');
        stageCol.className = 'lg:col-span-12 flex flex-col';
        if (focusBtn) focusBtn.textContent = '🗗 Mostrar Índice';
    } else {
        sidebarCol.classList.remove('hidden');
        stageCol.className = 'lg:col-span-8 flex flex-col';
        if (focusBtn) focusBtn.textContent = '⛶ Ampliar';
    }
}

function handleStudyCardsTestClick() {
    if (!activeStudyCardDeckId) return;
    const deck = (allMaterials || []).find(m => m.id === activeStudyCardDeckId);
    if (!deck) return;

    const linkedCount = deck.linkedQuestionsCount || 0;
    if (linkedCount === 0) {
        showToast('Esta colección de fichas aún no tiene preguntas adjuntas. Adjunta o genera preguntas primero.', 'info');
        openAttachQuestionsModal(deck.id);
        return;
    }

    openPracticeMaterialModal(deck.id);
}

// Navegación por teclado (← y →) cuando la presentación de fichas está en pantalla y no hay modales ni inputs activos
document.addEventListener('keydown', (e) => {
    const viewMat = document.getElementById('view-materials');
    if (!viewMat || viewMat.classList.contains('hidden') || activeMaterialSubview !== 'cards') return;

    const tag = (e.target && e.target.tagName) ? e.target.tagName.toLowerCase() : '';
    if (tag === 'input' || tag === 'textarea' || tag === 'select' || (e.target && e.target.isContentEditable)) return;

    // Ignorar si hay algún modal abierto
    const openModals = document.querySelectorAll('[id^="modal-"]:not(.hidden)');
    if (openModals.length > 0) return;

    if (e.key === 'ArrowRight') {
        e.preventDefault();
        nextStudyCard();
    } else if (e.key === 'ArrowLeft') {
        e.preventDefault();
        prevStudyCard();
    }
});

// =================== MODAL: ADJUNTAR Y GESTIONAR PREGUNTAS DE FICHAS ===================

async function openAttachQuestionsModal(materialId) {
    const targetId = materialId || activeStudyCardDeckId;
    if (!targetId) return;

    activeAttachQuestionsDeckId = targetId;
    selectedBankQuestionIds.clear();

    const mat = (allMaterials || []).find(m => m.id === targetId);
    const titleEl = document.getElementById('attach-q-mat-title') || document.getElementById('attach-q-deck-title');
    const idBadgeEl = document.getElementById('attach-q-mat-id-badge') || document.getElementById('attach-q-deck-id-badge');

    if (titleEl) titleEl.textContent = mat ? mat.title : `Colección #${targetId}`;
    if (idBadgeEl) idBadgeEl.textContent = `ID #${targetId}`;

    openModal('modal-attach-questions');
    switchAttachQuestionsTab('linked');
    await refreshAttachedQuestionsData();
}

function switchAttachQuestionsTab(tab) {
    activeAttachQuestionsTab = tab;
    const tabs = ['linked', 'bank', 'json', 'create'];
    tabs.forEach(t => {
        const panel = document.getElementById(`attach-panel-${t}`);
        const btn = document.getElementById(`attach-tab-${t}-btn`) || document.getElementById(`attach-tab-${t}`);
        if (t === tab) {
            if (panel) panel.classList.remove('hidden');
            if (btn) {
                btn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold bg-indigo-600 text-white transition cursor-pointer flex items-center gap-1.5';
            }
        } else {
            if (panel) panel.classList.add('hidden');
            if (btn) {
                btn.className = 'px-3.5 py-1.5 rounded-lg text-xs font-bold text-slate-300 hover:text-white transition cursor-pointer flex items-center gap-1.5';
            }
        }
    });

    if (tab === 'bank') {
        renderAttachBankQuestionsList();
    }
}

async function refreshAttachedQuestionsData() {
    if (!activeAttachQuestionsDeckId) return;

    const listEl = document.getElementById('attach-linked-list') || document.getElementById('attach-linked-questions-list');
    const countBadge = document.getElementById('attach-tab-linked-count');
    const footerStatus = document.getElementById('attach-modal-footer-status');
    const testBtn = document.getElementById('attach-modal-btn-test');

    try {
        const resp = await fetch(`/api/materials/${activeAttachQuestionsDeckId}/questions`);
        if (!resp.ok) return;
        const linkedQuestions = await resp.json();

        // Actualizar contador en memoria
        const mat = (allMaterials || []).find(m => m.id === activeAttachQuestionsDeckId);
        if (mat) {
            mat.linkedQuestionsCount = linkedQuestions.length;
        }

        if (countBadge) countBadge.textContent = String(linkedQuestions.length);

        if (footerStatus) {
            footerStatus.textContent = linkedQuestions.length > 0
                ? `✅ Prueba activa: ${linkedQuestions.length} pregunta${linkedQuestions.length === 1 ? '' : 's'} lista${linkedQuestions.length === 1 ? '' : 's'} para evaluar esta colección.`
                : '⚪ Aún no hay preguntas vinculadas. Usa las pestañas superiores para adjuntar o crear preguntas.';
        }

        if (testBtn) {
            if (linkedQuestions.length > 0) {
                testBtn.classList.remove('hidden');
            } else {
                testBtn.classList.add('hidden');
            }
        }

        if (listEl) {
            if (linkedQuestions.length === 0) {
                listEl.innerHTML = `
                    <div class="bg-slate-950/60 border border-dashed border-slate-800 rounded-2xl p-8 text-center">
                        <span class="text-3xl block mb-2">📎</span>
                        <p class="text-white font-bold text-sm">Esta colección de fichas aún no tiene preguntas adjuntas</p>
                        <p class="text-slate-400 text-xs mt-1 mb-4">Puedes vincular preguntas existentes del banco, importar un bloque JSON o crear preguntas nuevas.</p>
                        <div class="flex flex-wrap justify-center gap-2">
                            <button onclick="switchAttachQuestionsTab('bank')" class="px-3.5 py-2 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white font-bold text-xs cursor-pointer transition">
                                🏦 Seleccionar del Banco
                            </button>
                            <button onclick="switchAttachQuestionsTab('json')" class="px-3.5 py-2 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-bold text-xs cursor-pointer transition">
                                📥 Importar JSON
                            </button>
                            <button onclick="switchAttachQuestionsTab('create')" class="px-3.5 py-2 rounded-xl bg-emerald-600/20 hover:bg-emerald-600/30 text-emerald-300 border border-emerald-500/30 font-bold text-xs cursor-pointer transition">
                                ✍️ Crear / Generar con IA
                            </button>
                        </div>
                    </div>
                `;
            } else {
                listEl.innerHTML = linkedQuestions.map((q) => {
                    const correctOpt = (q.options || []).find(o => o.correct);
                    const correctLabel = correctOpt ? correctOpt.optionLabel : (q.correctAnswer || '-');
                    return `
                    <div class="p-3.5 rounded-xl bg-slate-950/80 border border-slate-800/90 hover:border-slate-700 flex items-start justify-between gap-3 transition">
                        <div class="min-w-0 flex-1">
                            <div class="flex items-center gap-2 mb-1">
                                <span class="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 font-mono text-[10px] font-bold">#${q.id}</span>
                                <span class="px-2 py-0.5 rounded bg-slate-800 text-slate-400 text-[10px] font-semibold">Dif: ${q.difficulty || 2}/3</span>
                                <span class="text-[11px] text-emerald-400 font-mono font-bold">Resp: ${correctLabel}</span>
                            </div>
                            <p class="text-xs text-slate-200 font-medium leading-relaxed">${q.stem}</p>
                        </div>
                        <button onclick="detachQuestionFromDeck(${activeAttachQuestionsDeckId}, ${q.id})" title="Desvincular de estas fichas" class="px-2.5 py-1.5 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 text-[11px] font-bold shrink-0 cursor-pointer transition">
                            Quitar
                        </button>
                    </div>
                `;
                }).join('');
            }
        }

        // Actualizar vista de Fichas si está activa
        if (activeStudyCardDeckId === activeAttachQuestionsDeckId) {
            selectStudyCardDeck(activeStudyCardDeckId, true);
        }
    } catch (e) {
        console.error('Error cargando preguntas vinculadas:', e);
    }
}

async function detachQuestionFromDeck(materialId, questionId) {
    try {
        const resp = await fetch(`/api/materials/${materialId}/questions/${questionId}`, {
            method: 'DELETE'
        });
        if (resp.ok) {
            showToast('Pregunta desvinculada de la colección de fichas.', 'info');
            await loadQuestions();
            await refreshAttachedQuestionsData();
        }
    } catch (e) {
        showToast('Error al desvincular la pregunta.', 'error');
    }
}

function renderAttachBankQuestionsList() {
    const container = document.getElementById('attach-bank-list') || document.getElementById('attach-bank-questions-list');
    const searchInput = document.getElementById('attach-bank-search');
    const selCountEl = document.getElementById('attach-bank-selected-count');
    if (!container) return;

    const query = searchInput ? searchInput.value.toLowerCase().trim() : '';

    // Mostrar preguntas que no estén ya vinculadas a esta colección
    const available = (allQuestions || []).filter(q => {
        const currentMatId = q.studyMaterial ? q.studyMaterial.id : q.materialId;
        if (currentMatId === activeAttachQuestionsDeckId) return false;
        if (!query) return true;
        const stemMatch = q.stem && q.stem.toLowerCase().includes(query);
        const topicMatch = q.topic && q.topic.name && q.topic.name.toLowerCase().includes(query);
        return stemMatch || topicMatch || String(q.id).includes(query);
    });

    if (selCountEl) {
        selCountEl.textContent = `${selectedBankQuestionIds.size} seleccionada${selectedBankQuestionIds.size === 1 ? '' : 's'}`;
    }

    if (available.length === 0) {
        container.innerHTML = `
            <div class="p-6 text-center text-slate-500 text-xs">
                No se encontraron preguntas disponibles con ese filtro (o todas están ya vinculadas a esta colección).
            </div>
        `;
        return;
    }

    container.innerHTML = available.map(q => {
        const isChecked = selectedBankQuestionIds.has(q.id);
        const topicName = q.topic ? q.topic.name : 'General';
        const correctOpt = (q.options || []).find(o => o.correct);
        const correctLabel = correctOpt ? correctOpt.optionLabel : (q.correctAnswer || '-');
        return `
            <label class="p-3 rounded-xl border ${isChecked ? 'bg-indigo-600/15 border-indigo-500/50' : 'bg-slate-950/70 border-slate-800/80 hover:border-slate-700'} flex items-start gap-3 cursor-pointer transition">
                <input type="checkbox" ${isChecked ? 'checked' : ''} onchange="toggleSelectBankQuestionForDeck(${q.id}, this.checked)" class="mt-1 rounded border-slate-600 text-indigo-600 focus:ring-indigo-500 cursor-pointer">
                <div class="min-w-0 flex-1">
                    <div class="flex items-center gap-2 mb-0.5">
                        <span class="font-mono text-[10px] font-bold text-indigo-400">#${q.id}</span>
                        <span class="text-[10px] text-slate-400 truncate">📚 ${topicName}</span>
                        <span class="text-[10px] font-mono text-emerald-400 ml-auto">Resp: ${correctLabel}</span>
                    </div>
                    <p class="text-xs text-slate-200 line-clamp-2">${q.stem}</p>
                </div>
            </label>
        `;
    }).join('');
}

function toggleSelectBankQuestionForDeck(questionId, checked) {
    if (checked) {
        selectedBankQuestionIds.add(questionId);
    } else {
        selectedBankQuestionIds.delete(questionId);
    }
    renderAttachBankQuestionsList();
}

async function attachSelectedBankQuestions() {
    if (!activeAttachQuestionsDeckId || selectedBankQuestionIds.size === 0) {
        showToast('Selecciona al menos una pregunta del banco.', 'info');
        return;
    }

    try {
        const resp = await fetch(`/api/materials/${activeAttachQuestionsDeckId}/questions/attach`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ questionIds: Array.from(selectedBankQuestionIds) })
        });
        if (resp.ok) {
            const data = await resp.json();
            selectedBankQuestionIds.clear();
            await loadQuestions();
            await refreshAttachedQuestionsData();
            switchAttachQuestionsTab('linked');
            showToast(`¡Se adjuntaron ${data.attachedCount} preguntas a las fichas!`, 'success');
        }
    } catch (e) {
        showToast('Error al adjuntar preguntas del banco.', 'error');
    }
}

function loadSampleAttachJson() {
    const textarea = document.getElementById('attach-json-textarea');
    if (!textarea) return;
    textarea.value = JSON.stringify([
        {
            "stem": "Según las fichas de paradigmas FHIR, ¿qué paradigma es el más adecuado cuando dos organizaciones casi no comparten procesos internos?",
            "explanation": "La Ficha 9 destaca que Mensajes (Messaging) es buena idea para el intercambio entre organizaciones dispares con bajo nivel de integración de procesos.",
            "difficulty": 2,
            "questionType": "SINGLE_CHOICE",
            "source": "MANUAL",
            "options": [
                { "optionLabel": "A", "optionText": "Base de datos compartida (Persistent store)", "correct": false, "distractorReason": "El almacenamiento persistente es para guardar datos internamente." },
                { "optionLabel": "B", "optionText": "Mensajes (Messaging)", "correct": true, "distractorReason": "Correcto: encamina mensajes entre organizaciones dispares." },
                { "optionLabel": "C", "optionText": "Consultas GraphQL síncronas", "correct": false, "distractorReason": "GraphQL forma parte del paradigma REST." },
                { "optionLabel": "D", "optionText": "Operaciones CRUD directas sin gobernanza", "correct": false, "distractorReason": "REST requiere acuerdos de gobernanza previos." }
            ]
        }
    ], null, 2);
}

async function submitAttachJsonQuestions() {
    if (!activeAttachQuestionsDeckId) return;
    const textarea = document.getElementById('attach-json-textarea');
    const jsonText = textarea ? textarea.value.trim() : '';
    if (!jsonText) {
        showToast('Pega un array JSON de preguntas.', 'error');
        return;
    }

    const mat = (allMaterials || []).find(m => m.id === activeAttachQuestionsDeckId);
    const topicParam = mat && mat.topic ? `&defaultTopicId=${mat.topic.id}` : '';

    try {
        const resp = await fetch(`/api/questions/import?defaultMaterialId=${activeAttachQuestionsDeckId}${topicParam}`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: jsonText
        });
        const data = await resp.json();
        if (resp.ok && data.success) {
            textarea.value = '';
            await loadQuestions();
            await refreshAttachedQuestionsData();
            switchAttachQuestionsTab('linked');
            showToast(`¡${data.importedCount} pregunta(s) importada(s) y vinculada(s) a estas fichas!`, 'success');
        } else {
            showToast('Error en el JSON: ' + (data.error || 'Revisa el formato'), 'error');
        }
    } catch (e) {
        showToast('Error al importar JSON: ' + e.message, 'error');
    }
}

async function submitCreateAttachedQuestion(event) {
    if (event && event.preventDefault) event.preventDefault();
    if (!activeAttachQuestionsDeckId) return;

    const mat = (allMaterials || []).find(m => m.id === activeAttachQuestionsDeckId);
    const stemEl = document.getElementById('attach-new-q-stem');
    const optAEl = document.getElementById('attach-new-q-opt-a') || document.getElementById('attach-new-q-a');
    const optBEl = document.getElementById('attach-new-q-opt-b') || document.getElementById('attach-new-q-b');
    const optCEl = document.getElementById('attach-new-q-opt-c') || document.getElementById('attach-new-q-c');
    const optDEl = document.getElementById('attach-new-q-opt-d') || document.getElementById('attach-new-q-d');
    const expEl = document.getElementById('attach-new-q-explanation') || document.getElementById('attach-new-q-exp');
    const correctRadio = document.querySelector('input[name="attach-new-q-correct"]:checked');
    const correctSelect = document.getElementById('attach-new-q-correct');

    const stem = stemEl ? stemEl.value.trim() : '';
    const optA = optAEl ? optAEl.value.trim() : '';
    const optB = optBEl ? optBEl.value.trim() : '';
    const optC = optCEl ? optCEl.value.trim() : '';
    const optD = optDEl ? optDEl.value.trim() : '';
    const correctLabel = correctRadio ? correctRadio.value : (correctSelect ? correctSelect.value : 'A');
    const explanation = expEl && expEl.value.trim() ? expEl.value.trim() : 'Pregunta vinculada a la colección de fichas.';

    if (!stem || !optA || !optB) {
        showToast('Completa al menos el enunciado y las opciones A y B.', 'warning');
        return;
    }

    const options = [
        { optionLabel: 'A', optionText: optA, correct: correctLabel === 'A', distractorReason: null },
        { optionLabel: 'B', optionText: optB, correct: correctLabel === 'B', distractorReason: null }
    ];
    if (optC) options.push({ optionLabel: 'C', optionText: optC, correct: correctLabel === 'C', distractorReason: null });
    if (optD) options.push({ optionLabel: 'D', optionText: optD, correct: correctLabel === 'D', distractorReason: null });

    const payload = {
        stem,
        explanation,
        difficulty: 2,
        questionType: 'SINGLE_CHOICE',
        source: 'MANUAL',
        materialId: activeAttachQuestionsDeckId,
        topicId: mat && mat.topic ? mat.topic.id : 1,
        options
    };

    try {
        const resp = await fetch('/api/questions', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(payload)
        });
        if (resp.ok) {
            if (stemEl) stemEl.value = '';
            if (optAEl) optAEl.value = '';
            if (optBEl) optBEl.value = '';
            if (optCEl) optCEl.value = '';
            if (optDEl) optDEl.value = '';
            if (expEl) expEl.value = '';
            await loadQuestions();
            await refreshAttachedQuestionsData();
            switchAttachQuestionsTab('linked');
            showToast('¡Pregunta creada y adjuntada a la colección de fichas!', 'success');
        } else {
            showToast('No se pudo guardar la pregunta.', 'error');
        }
    } catch (e) {
        showToast('Error al crear la pregunta: ' + e.message, 'error');
    }
}

async function generateQuestionFromAttachModal() {
    if (!activeAttachQuestionsDeckId) return;
    const btn = document.getElementById('btn-attach-ai-gen');
    if (btn) {
        btn.disabled = true;
        btn.textContent = '⏳ Generando con IA...';
    }

    try {
        const resp = await fetch(`/api/materials/${activeAttachQuestionsDeckId}/generate-question`, {
            method: 'POST'
        });
        if (resp.ok) {
            await loadQuestions();
            await refreshAttachedQuestionsData();
            switchAttachQuestionsTab('linked');
            showToast('¡Pregunta generada a partir del contenido de las fichas!', 'success');
        } else {
            showToast('No se pudo generar la pregunta automáticamente.', 'error');
        }
    } catch (e) {
        showToast('Error de conexión al generar pregunta.', 'error');
    } finally {
        if (btn) {
            btn.disabled = false;
            btn.textContent = '✨ Generar 1 Pregunta IA';
        }
    }
}

// =================== COMENTARIOS Y NOTAS DE AMPLIACIÓN EN FICHAS DE ESTUDIO ===================

async function loadStudyCardComments(materialId) {
    if (!materialId) return;
    try {
        const resp = await fetch(`/api/materials/${materialId}/comments`);
        if (resp.ok) {
            activeDeckComments = await resp.json();
            activeDeckCommentsLoadedForId = materialId;

            const deck = (allMaterials || []).find(m => m.id === materialId);
            if (deck) {
                deck.commentsCount = activeDeckComments.length;
                const metaEl = document.getElementById('sc-active-deck-meta');
                if (metaEl && activeStudyCardParsedDeck && activeStudyCardDeckId === materialId) {
                    const totalComments = deck.commentsCount || 0;
                    const commentsSuffix = totalComments > 0 ? ` · 💬 ${totalComments} comentario${totalComments === 1 ? '' : 's'}` : '';
                    metaEl.textContent = `${activeStudyCardParsedDeck.chapters.length} capítulo${activeStudyCardParsedDeck.chapters.length === 1 ? '' : 's'} · ${activeStudyCardParsedDeck.cards.length} ficha${activeStudyCardParsedDeck.cards.length === 1 ? '' : 's'}${commentsSuffix}`;
                }
            }

            renderStudyCardsChaptersTree();
            renderActiveStudyCardComments();
        }
    } catch (e) {
        console.error('Error cargando comentarios de fichas:', e);
    }
}

function renderActiveStudyCardComments() {
    const listEl = document.getElementById('sc-slide-comments-list');
    const countEl = document.getElementById('sc-slide-comments-count');
    if (!listEl) return;

    const currentCardComments = (activeDeckComments || []).filter(cm => cm.cardIndex === activeStudyCardSlideIndex);
    if (countEl) {
        countEl.textContent = String(currentCardComments.length);
    }

    if (currentCardComments.length === 0) {
        listEl.innerHTML = `
            <div class="bg-slate-950/50 border border-dashed border-slate-800/80 rounded-xl px-4 py-3 flex items-center justify-between gap-3">
                <span class="text-xs text-slate-400">
                    Aún no hay comentarios en esta ficha. Añade notas, ejemplos o aclaraciones en texto o Markdown.
                </span>
                <button type="button" onclick="toggleStudyCardCommentComposer(true)" class="text-xs font-bold text-indigo-400 hover:text-indigo-300 shrink-0 cursor-pointer">
                    + Añadir nota →
                </button>
            </div>
        `;
        return;
    }

    listEl.innerHTML = currentCardComments.map((cm, idx) => {
        if (editingCardCommentId === cm.id) {
            const safeRaw = (cm.contentMarkdown || '')
                .replace(/&/g, '&amp;')
                .replace(/</g, '&lt;')
                .replace(/>/g, '&gt;');
            return `
                <div class="bg-slate-950/95 border border-indigo-500/60 rounded-2xl p-4 space-y-3 shadow-lg">
                    <div class="flex items-center justify-between text-xs">
                        <span class="font-bold text-indigo-300">✏️ Editando Comentario #${idx + 1}</span>
                        <span class="text-[10px] font-mono text-slate-500">${cm.createdAt || ''}</span>
                    </div>
                    <textarea id="sc-edit-comment-textarea-${cm.id}" rows="4" class="w-full bg-slate-900 border border-slate-700 rounded-xl p-3 text-xs text-slate-100 font-mono focus:outline-none focus:border-indigo-500 leading-relaxed">${safeRaw}</textarea>
                    <div class="flex justify-end gap-2">
                        <button type="button" onclick="cancelEditStudyCardComment()" class="px-3 py-1.5 rounded-xl bg-slate-800 hover:bg-slate-700 text-slate-300 text-xs font-semibold transition cursor-pointer">
                            Cancelar
                        </button>
                        <button type="button" onclick="saveEditedStudyCardComment(${cm.id})" class="px-4 py-1.5 rounded-xl bg-indigo-600 hover:bg-indigo-500 text-white text-xs font-bold transition cursor-pointer">
                            💾 Actualizar Comentario
                        </button>
                    </div>
                </div>
            `;
        }

        const dateText = cm.updatedAt ? `Editado: ${cm.updatedAt}` : (cm.createdAt || '');
        const renderedBody = renderStudyCardMarkdownBody(cm.contentMarkdown || '');

        return `
            <div class="bg-slate-950/80 border border-slate-800/90 hover:border-slate-700/90 rounded-2xl p-4 transition space-y-2 shadow-sm">
                <div class="flex items-center justify-between gap-2 border-b border-slate-800/70 pb-2">
                    <div class="flex items-center gap-2">
                        <span class="px-2 py-0.5 rounded-md bg-indigo-500/15 text-indigo-300 border border-indigo-500/30 font-mono text-[10px] font-bold">
                            Nota #${idx + 1}
                        </span>
                        <span class="text-[11px] font-mono text-slate-500">${dateText}</span>
                    </div>
                    <div class="flex items-center gap-1.5">
                        <button type="button" onclick="startEditStudyCardComment(${cm.id})" title="Editar comentario" class="px-2 py-1 rounded-lg bg-slate-900 hover:bg-slate-800 text-slate-300 hover:text-white border border-slate-800 text-[11px] font-semibold transition cursor-pointer">
                            ✏️ Editar
                        </button>
                        <button type="button" onclick="deleteStudyCardComment(${cm.id})" title="Eliminar comentario" class="px-2 py-1 rounded-lg bg-rose-500/10 hover:bg-rose-500/20 text-rose-400 border border-rose-500/20 text-[11px] font-semibold transition cursor-pointer">
                            🗑️
                        </button>
                    </div>
                </div>
                <div class="study-card-body text-sm pt-0.5">
                    ${renderedBody}
                </div>
            </div>
        `;
    }).join('');
}

function toggleStudyCardCommentComposer(forceState) {
    const box = document.getElementById('sc-comment-composer-box');
    const icon = document.getElementById('sc-btn-toggle-comment-icon');
    const label = document.getElementById('sc-btn-toggle-comment-label');
    const textarea = document.getElementById('sc-comment-textarea');
    if (!box) return;

    isCardCommentComposerOpen = typeof forceState === 'boolean' ? forceState : !isCardCommentComposerOpen;

    if (isCardCommentComposerOpen) {
        box.classList.remove('hidden');
        if (icon) icon.textContent = '✕';
        if (label) label.textContent = 'Cerrar editor';
        if (textarea) {
            setTimeout(() => textarea.focus(), 60);
        }
    } else {
        box.classList.add('hidden');
        if (icon) icon.textContent = '➕';
        if (label) label.textContent = 'Añadir Comentario';
    }
}

function insertCardCommentSnippet(prefix, suffix, placeholder) {
    const textarea = document.getElementById('sc-comment-textarea');
    if (!textarea) return;

    const start = textarea.selectionStart || 0;
    const end = textarea.selectionEnd || 0;
    const text = textarea.value;
    const selected = text.substring(start, end) || placeholder;

    const replacement = `${prefix}${selected}${suffix}`;
    textarea.value = text.substring(0, start) + replacement + text.substring(end);
    textarea.focus();
    textarea.setSelectionRange(start + prefix.length, start + prefix.length + selected.length);
    updateCardCommentLivePreview();
}

function toggleCardCommentPreview() {
    const wrapper = document.getElementById('sc-comment-preview-wrapper');
    const btn = document.getElementById('sc-btn-comment-preview');
    if (!wrapper) return;

    isCardCommentPreviewVisible = !isCardCommentPreviewVisible;
    if (isCardCommentPreviewVisible) {
        wrapper.classList.remove('hidden');
        if (btn) btn.textContent = '🙈 Ocultar vista previa';
        updateCardCommentLivePreview();
    } else {
        wrapper.classList.add('hidden');
        if (btn) btn.textContent = '👁️ Mostrar vista previa';
    }
}

function updateCardCommentLivePreview() {
    if (!isCardCommentPreviewVisible) return;
    const textarea = document.getElementById('sc-comment-textarea');
    const previewBody = document.getElementById('sc-comment-preview-body');
    if (!previewBody) return;

    const raw = textarea ? textarea.value.trim() : '';
    previewBody.innerHTML = raw
        ? renderStudyCardMarkdownBody(raw)
        : '<p class="text-slate-500 italic text-xs">Escribe algo arriba para previsualizar el formato...</p>';
}

async function submitNewStudyCardComment() {
    if (!activeStudyCardDeckId || !activeStudyCardParsedDeck) return;
    const textarea = document.getElementById('sc-comment-textarea');
    const saveBtn = document.getElementById('sc-btn-save-comment');
    const contentMarkdown = textarea ? textarea.value.trim() : '';

    if (!contentMarkdown) {
        showToast('Escribe el contenido del comentario antes de guardar.', 'warning');
        return;
    }

    const currentCard = activeStudyCardParsedDeck.cards[activeStudyCardSlideIndex];
    const cardHeading = currentCard ? currentCard.rawHeading : `Ficha ${activeStudyCardSlideIndex + 1}`;

    if (saveBtn) saveBtn.disabled = true;

    try {
        const resp = await fetch(`/api/materials/${activeStudyCardDeckId}/comments`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                cardIndex: activeStudyCardSlideIndex,
                cardHeading: cardHeading,
                contentMarkdown: contentMarkdown
            })
        });

        if (resp.ok) {
            if (textarea) textarea.value = '';
            updateCardCommentLivePreview();
            toggleStudyCardCommentComposer(false);
            await loadStudyCardComments(activeStudyCardDeckId);
            showToast('¡Comentario añadido a la ficha!', 'success');
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No se pudo guardar el comentario.', 'error');
        }
    } catch (e) {
        showToast('Error de red al guardar el comentario: ' + e.message, 'error');
    } finally {
        if (saveBtn) saveBtn.disabled = false;
    }
}

function startEditStudyCardComment(commentId) {
    editingCardCommentId = commentId;
    renderActiveStudyCardComments();
    const editArea = document.getElementById(`sc-edit-comment-textarea-${commentId}`);
    if (editArea) {
        setTimeout(() => editArea.focus(), 50);
    }
}

function cancelEditStudyCardComment() {
    editingCardCommentId = null;
    renderActiveStudyCardComments();
}

async function saveEditedStudyCardComment(commentId) {
    if (!activeStudyCardDeckId) return;
    const editArea = document.getElementById(`sc-edit-comment-textarea-${commentId}`);
    const contentMarkdown = editArea ? editArea.value.trim() : '';

    if (!contentMarkdown) {
        showToast('El comentario no puede quedar vacío.', 'warning');
        return;
    }

    try {
        const resp = await fetch(`/api/materials/${activeStudyCardDeckId}/comments/${commentId}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ contentMarkdown })
        });
        if (resp.ok) {
            editingCardCommentId = null;
            await loadStudyCardComments(activeStudyCardDeckId);
            showToast('Comentario actualizado.', 'success');
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No se pudo actualizar el comentario.', 'error');
        }
    } catch (e) {
        showToast('Error al actualizar comentario: ' + e.message, 'error');
    }
}

async function deleteStudyCardComment(commentId) {
    if (!activeStudyCardDeckId) return;
    const confirmed = await showConfirmModal({
        title: '¿Eliminar este comentario?',
        subtitle: 'Esta nota adicional se borrará de la ficha actual',
        message: '¿Estás seguro de que deseas eliminar este comentario de la ficha?',
        confirmText: 'Sí, eliminar',
        icon: '🗑️'
    });
    if (!confirmed) return;

    try {
        const resp = await fetch(`/api/materials/${activeStudyCardDeckId}/comments/${commentId}`, {
            method: 'DELETE'
        });
        if (resp.ok) {
            if (editingCardCommentId === commentId) editingCardCommentId = null;
            await loadStudyCardComments(activeStudyCardDeckId);
            showToast('Comentario eliminado.', 'info');
        } else {
            showToast('No se pudo eliminar el comentario.', 'error');
        }
    } catch (e) {
        showToast('Error al eliminar comentario: ' + e.message, 'error');
    }
}

// Atajo Ctrl + Enter dentro del textarea de comentarios para guardar rápidamente
document.addEventListener('keydown', (e) => {
    if ((e.ctrlKey || e.metaKey) && e.key === 'Enter') {
        if (e.target && e.target.id === 'sc-comment-textarea') {
            e.preventDefault();
            submitNewStudyCardComment();
        }
    }
});

// =======================================================================
// AUTENTICACIÓN, SESIÓN DE USUARIO Y CONTROL DE PROPIEDAD
// =======================================================================

function canModifyItem(item) {
    if (!currentUser || !item) return false;
    if (currentUser.role === 'ADMIN') return true;
    return item.ownerId != null && item.ownerId === currentUser.id;
}

async function initAuthAndBootstrapApp() {
    try {
        const resp = await apiFetch('/api/auth/me');
        if (resp.ok) {
            const user = await resp.json();
            showAuthenticatedApp(user);
        } else {
            showLoginScreen();
        }
    } catch (e) {
        showLoginScreen();
    }
}

function showLoginScreen(errorMsg = null) {
    currentUser = null;
    const loginScreen = document.getElementById('auth-login-screen');
    const appShell = document.getElementById('app-authenticated-shell');
    if (appShell) appShell.classList.add('hidden');
    if (loginScreen) loginScreen.classList.remove('hidden');

    const errBox = document.getElementById('login-error-box');
    const errText = document.getElementById('login-error-text');
    if (errorMsg && errBox && errText) {
        errText.textContent = errorMsg;
        errBox.classList.remove('hidden');
    } else if (errBox) {
        errBox.classList.add('hidden');
    }
}

function showAuthenticatedApp(user) {
    currentUser = user;
    const loginScreen = document.getElementById('auth-login-screen');
    const appShell = document.getElementById('app-authenticated-shell');
    if (loginScreen) loginScreen.classList.add('hidden');
    if (appShell) appShell.classList.remove('hidden');

    // Actualizar widget de usuario en la cabecera
    const displayNameEl = document.getElementById('user-display-name');
    const usernameEl = document.getElementById('user-username-label');
    const roleBadgeEl = document.getElementById('user-role-badge');
    const avatarEl = document.getElementById('user-avatar-badge');

    if (displayNameEl) displayNameEl.textContent = user.displayName || user.username;
    if (usernameEl) usernameEl.textContent = `@${user.username}`;
    if (avatarEl) {
        const initial = (user.displayName || user.username || 'U').trim().charAt(0).toUpperCase();
        avatarEl.textContent = initial;
    }
    if (roleBadgeEl) {
        roleBadgeEl.textContent = user.role;
        if (user.role === 'ADMIN') {
            roleBadgeEl.className = 'px-1.5 py-0.2 rounded text-[9px] font-mono font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30';
        } else {
            roleBadgeEl.className = 'px-1.5 py-0.2 rounded text-[9px] font-mono font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30';
        }
    }

    // Mostrar u ocultar menú de administración según rol
    const adminSection = document.getElementById('sidebar-admin-section');
    if (adminSection) {
        if (user.role === 'ADMIN') {
            adminSection.classList.remove('hidden');
        } else {
            adminSection.classList.add('hidden');
        }
    }

    if (user.mustChangePassword) {
        openChangePasswordModal(true);
        return;
    }

    // Cargar datos iniciales del usuario autenticado
    loadTopics();
    loadDashboard();
    loadMaterials();
}

async function submitLogin(event) {
    event.preventDefault();
    const usernameInput = document.getElementById('login-username');
    const passwordInput = document.getElementById('login-password');
    const submitBtn = document.getElementById('btn-login-submit');
    const errBox = document.getElementById('login-error-box');
    const errText = document.getElementById('login-error-text');

    if (errBox) errBox.classList.add('hidden');
    if (submitBtn) submitBtn.disabled = true;

    try {
        // Asegurar cookie CSRF antes del POST /api/auth/login
        if (!getCsrfTokenFromCookie()) {
            await nativeFetch('/api/auth/me', { credentials: 'same-origin' });
        }

        const resp = await apiFetch('/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({
                username: usernameInput.value.trim(),
                password: passwordInput.value
            })
        });

        if (resp.ok) {
            const user = await resp.json();
            passwordInput.value = '';
            showToast(`Bienvenido/a, ${user.displayName || user.username}`, 'success');
            showAuthenticatedApp(user);
        } else {
            const err = await resp.json().catch(() => ({}));
            const msg = err.error || 'Usuario o contraseña incorrectos.';
            if (errBox && errText) {
                errText.textContent = msg;
                errBox.classList.remove('hidden');
            } else {
                showToast(msg, 'error');
            }
        }
    } catch (e) {
        if (errBox && errText) {
            errText.textContent = 'Error de conexión con el servidor.';
            errBox.classList.remove('hidden');
        }
    } finally {
        if (submitBtn) submitBtn.disabled = false;
    }
}

async function handleLogout() {
    try {
        await apiFetch('/api/auth/logout', { method: 'POST' });
    } catch (e) {
        // Ignorar errores de red al salir
    }
    currentUser = null;
    switchTab('dashboard');
    showLoginScreen();
    showToast('Sesión cerrada correctamente.', 'info');
}

function openChangePasswordModal(mandatory = false) {
    isMandatoryPasswordChange = Boolean(mandatory);
    document.getElementById('cp-current-password').value = '';
    document.getElementById('cp-new-password').value = '';
    document.getElementById('cp-confirm-password').value = '';

    const banner = document.getElementById('change-pwd-mandatory-banner');
    const closeBtn = document.getElementById('btn-close-change-pwd');
    const cancelBtn = document.getElementById('btn-cancel-change-pwd');

    if (isMandatoryPasswordChange) {
        if (banner) banner.classList.remove('hidden');
        if (closeBtn) closeBtn.classList.add('hidden');
        if (cancelBtn) cancelBtn.classList.add('hidden');
    } else {
        if (banner) banner.classList.add('hidden');
        if (closeBtn) closeBtn.classList.remove('hidden');
        if (cancelBtn) cancelBtn.classList.remove('hidden');
    }

    openModal('modal-change-password');
}

async function submitChangePassword(event) {
    event.preventDefault();
    const currentPassword = document.getElementById('cp-current-password').value;
    const newPassword = document.getElementById('cp-new-password').value;
    const confirmPassword = document.getElementById('cp-confirm-password').value;

    if (newPassword.length < 10) {
        showToast('La nueva contraseña debe tener al menos 10 caracteres.', 'warning');
        return;
    }
    if (newPassword !== confirmPassword) {
        showToast('La confirmación de la nueva contraseña no coincide.', 'warning');
        return;
    }

    try {
        const resp = await apiFetch('/api/auth/change-password', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ currentPassword, newPassword })
        });
        if (resp.ok) {
            const updatedUser = await resp.json();
            currentUser = updatedUser;
            isMandatoryPasswordChange = false;
            closeModal('modal-change-password');
            showToast('Contraseña actualizada correctamente.', 'success');
            loadTopics();
            loadDashboard();
            loadMaterials();
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No se pudo actualizar la contraseña.', 'error');
        }
    } catch (e) {
        showToast('Error de red al cambiar la contraseña.', 'error');
    }
}

// =================== EDICIÓN Y BORRADO CON CONTROL DE PROPIEDAD ===================
function openEditQuestionModal(questionId) {
    const q = allQuestions.find(item => item.id === questionId);
    if (!q) return;

    editingQuestionId = q.id;
    const modal = document.getElementById('modal-create-question');
    if (modal) {
        const titleEl = modal.querySelector('h3');
        if (titleEl) titleEl.textContent = `✏️ Editar Pregunta #${q.id}`;
    }

    if (q.topic && document.getElementById('new-q-topic-id')) {
        document.getElementById('new-q-topic-id').value = q.topic.id;
    }
    if (document.getElementById('new-q-difficulty')) {
        document.getElementById('new-q-difficulty').value = q.difficulty || 2;
    }
    if (document.getElementById('new-q-material-id')) {
        document.getElementById('new-q-material-id').value = q.materialId || '';
    }
    document.getElementById('new-q-stem').value = q.stem || '';
    document.getElementById('new-q-snippet').value = q.codeSnippet || '';
    document.getElementById('new-q-explanation').value = q.explanation || '';
    document.getElementById('new-q-url').value = q.fhirSpecUrl || '';

    const labels = ['a', 'b', 'c', 'd'];
    labels.forEach(l => {
        document.getElementById(`new-q-opt-${l}`).value = '';
        document.getElementById(`new-q-why-${l}`).value = '';
    });

    if (q.options) {
        q.options.forEach(o => {
            const lbl = (o.optionLabel || '').toLowerCase();
            if (labels.includes(lbl)) {
                document.getElementById(`new-q-opt-${lbl}`).value = o.optionText || '';
                document.getElementById(`new-q-why-${lbl}`).value = o.distractorReason || '';
                if (o.correct) {
                    const radio = document.querySelector(`input[name="new-q-correct"][value="${o.optionLabel}"]`);
                    if (radio) radio.checked = true;
                }
            }
        });
    }

    openModal('modal-create-question');
}

async function deleteQuestionById(questionId) {
    const confirmed = await showConfirmDialog(
        '¿Eliminar esta pregunta?',
        `Se aplicará baja lógica (soft-delete) a la pregunta #${questionId} para no alterar los resultados históricos.`,
        { confirmText: 'Eliminar', danger: true }
    );
    if (!confirmed) return;

    try {
        const resp = await apiFetch(`/api/questions/${questionId}`, { method: 'DELETE' });
        if (resp.ok) {
            showToast('Pregunta eliminada correctamente.', 'success');
            await loadQuestions();
            await loadDashboard();
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No tienes permiso para eliminar esta pregunta.', 'error');
        }
    } catch (e) {
        showToast('Error al eliminar la pregunta.', 'error');
    }
}

function openEditMaterialModal(materialId) {
    const m = allMaterials.find(item => item.id === materialId);
    if (!m) return;
    document.getElementById('edit-mat-id').value = m.id;
    document.getElementById('edit-mat-title').value = m.title || '';
    document.getElementById('edit-mat-content').value = m.contentText || '';
    openModal('modal-edit-material');
}

async function submitEditMaterialForm(event) {
    event.preventDefault();
    const id = document.getElementById('edit-mat-id').value;
    const title = document.getElementById('edit-mat-title').value.trim();
    const content = document.getElementById('edit-mat-content').value;

    try {
        const resp = await apiFetch(`/api/materials/${id}`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ title, content })
        });
        if (resp.ok) {
            closeModal('modal-edit-material');
            showToast('Material actualizado con éxito.', 'success');
            await loadMaterials();
        } else {
            const err = await resp.json().catch(() => ({}));
            showToast(err.error || 'No se pudo actualizar el material.', 'error');
        }
    } catch (e) {
        showToast('Error al actualizar el material.', 'error');
    }
}

// =======================================================================
// MÓDULOS DE ADMINISTRACIÓN (SOLO ROL ADMIN)
// =======================================================================
let adminUsersCache = [];
let selectedAdminUserId = null;

async function loadAdminUsers(page = 0) {
    const search = document.getElementById('admin-users-search')?.value || '';
    const role = document.getElementById('admin-users-role-filter')?.value || '';
    const active = document.getElementById('admin-users-active-filter')?.value || '';

    const params = new URLSearchParams({ page, size: 20 });
    if (search.trim()) params.append('search', search.trim());
    if (role) params.append('role', role);
    if (active !== '') params.append('active', active);

    try {
        const resp = await apiFetch(`/api/admin/users?${params.toString()}`);
        if (!resp.ok) return;
        const data = await resp.json();
        adminUsersCache = data.content || [];
        renderAdminUsersTable(adminUsersCache, data);
    } catch (e) {
        console.error('Error cargando usuarios:', e);
    }
}

function renderAdminUsersTable(users, pageData) {
    const tbody = document.getElementById('admin-users-table-body');
    const pagDiv = document.getElementById('admin-users-pagination');
    if (!tbody) return;

    if (!users || users.length === 0) {
        tbody.innerHTML = '<tr><td colspan="7" class="py-6 text-center text-slate-500">No se encontraron usuarios con esos filtros.</td></tr>';
        if (pagDiv) pagDiv.innerHTML = '';
        return;
    }

    tbody.innerHTML = '';
    users.forEach(u => {
        const tr = document.createElement('tr');
        tr.className = 'hover:bg-slate-900/50 transition';

        const roleBadge = u.role === 'ADMIN'
            ? '<span class="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-amber-500/20 text-amber-300 border border-amber-500/30">ADMIN</span>'
            : '<span class="px-2 py-0.5 rounded text-[10px] font-mono font-bold bg-blue-500/20 text-blue-300 border border-blue-500/30">USER</span>';

        const statusBadge = u.active
            ? '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">ACTIVO</span>'
            : '<span class="px-2 py-0.5 rounded-full text-[10px] font-bold bg-rose-500/20 text-rose-400 border border-rose-500/30">INACTIVO</span>';

        const isSelf = currentUser && currentUser.id === u.id;

        tr.innerHTML = `
            <td class="py-3 px-4 font-mono text-slate-400">#${u.id}</td>
            <td class="py-3 px-4">
                <div class="font-bold text-white">${u.displayName || u.username}</div>
                <div class="font-mono text-[11px] text-slate-400">@${u.username} ${u.mustChangePassword ? '<span class="text-amber-400">(Cambio pend.)</span>' : ''}</div>
            </td>
            <td class="py-3 px-4 font-mono text-slate-300">${u.email}</td>
            <td class="py-3 px-4">${roleBadge}</td>
            <td class="py-3 px-4">${statusBadge}</td>
            <td class="py-3 px-4 font-mono text-[11px] text-slate-400">${u.lastLoginAt || 'Nunca'}</td>
            <td class="py-3 px-4 text-right space-x-1 whitespace-nowrap">
                <button onclick="inspectUserFromUsersTable(${u.id})" class="px-2.5 py-1 rounded-lg bg-indigo-600/25 hover:bg-indigo-600/40 text-indigo-300 border border-indigo-500/30 text-[11px] font-semibold cursor-pointer" title="Ver trazabilidad y exámenes">
                    📊 Resultados
                </button>
                <button onclick="openEditUserModal(${u.id})" class="px-2.5 py-1 rounded-lg bg-slate-700 hover:bg-slate-600 text-slate-200 text-[11px] font-semibold cursor-pointer">
                    ✏️ Editar
                </button>
                <button onclick="resetAdminUserPassword(${u.id}, '${u.username}')" class="px-2.5 py-1 rounded-lg bg-amber-500/20 hover:bg-amber-500/30 text-amber-300 border border-amber-500/30 text-[11px] font-semibold cursor-pointer">
                    🔑 Reset Pass
                </button>
                ${!isSelf ? `
                    <button onclick="toggleAdminUserStatus(${u.id}, ${!u.active}, '${u.username}')" class="px-2.5 py-1 rounded-lg ${u.active ? 'bg-rose-950/60 hover:bg-rose-900 text-rose-300 border border-rose-500/30' : 'bg-emerald-950/60 hover:bg-emerald-900 text-emerald-300 border border-emerald-500/30'} text-[11px] font-semibold cursor-pointer">
                        ${u.active ? '🚫 Desactivar' : '✅ Activar'}
                    </button>
                ` : ''}
            </td>
        `;
        tbody.appendChild(tr);
    });

    if (pagDiv && pageData) {
        pagDiv.innerHTML = `
            <span>Total: <strong>${pageData.totalElements || users.length}</strong> usuarios</span>
            <span>Página ${(pageData.page || 0) + 1} de ${Math.max(1, pageData.totalPages || 1)}</span>
        `;
    }
}

function inspectUserFromUsersTable(userId) {
    switchTab('admin-results');
    selectAdminUserDetail(userId);
}

function openCreateUserModal() {
    document.getElementById('admin-user-modal-title').textContent = '➕ Crear Nuevo Usuario';
    document.getElementById('admin-form-user-id').value = '';
    document.getElementById('admin-form-username').value = '';
    document.getElementById('admin-form-username').disabled = false;
    document.getElementById('admin-form-username-group').classList.remove('hidden');
    document.getElementById('admin-form-display-name').value = '';
    document.getElementById('admin-form-email').value = '';
    document.getElementById('admin-form-role').value = 'USER';
    document.getElementById('admin-form-password').value = '';
    document.getElementById('admin-form-password-group').classList.remove('hidden');
    openModal('modal-admin-user-form');
}

function openEditUserModal(userId) {
    const u = adminUsersCache.find(item => item.id === userId);
    if (!u) return;

    document.getElementById('admin-user-modal-title').textContent = `✏️ Editar Usuario @${u.username}`;
    document.getElementById('admin-form-user-id').value = u.id;
    document.getElementById('admin-form-username').value = u.username;
    document.getElementById('admin-form-username').disabled = true;
    document.getElementById('admin-form-display-name').value = u.displayName || '';
    document.getElementById('admin-form-email').value = u.email || '';
    document.getElementById('admin-form-role').value = u.role || 'USER';
    document.getElementById('admin-form-password-group').classList.add('hidden');
    openModal('modal-admin-user-form');
}

async function submitAdminUserForm(event) {
    event.preventDefault();
    const userId = document.getElementById('admin-form-user-id').value;
    const username = document.getElementById('admin-form-username').value.trim();
    const displayName = document.getElementById('admin-form-display-name').value.trim();
    const email = document.getElementById('admin-form-email').value.trim();
    const role = document.getElementById('admin-form-role').value;
    const password = document.getElementById('admin-form-password').value;

    const isEdit = Boolean(userId);
    const url = isEdit ? `/api/admin/users/${userId}` : '/api/admin/users';
    const method = isEdit ? 'PUT' : 'POST';
    const body = isEdit
        ? { displayName, email, role }
        : { username, displayName, email, role, password: password || null };

    try {
        const resp = await apiFetch(url, {
            method,
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(body)
        });
        const data = await resp.json().catch(() => ({}));
        if (resp.ok) {
            closeModal('modal-admin-user-form');
            await loadAdminUsers(0);
            if (!isEdit && data.temporaryPassword) {
                document.getElementById('temp-pwd-username').textContent = data.user ? data.user.username : username;
                document.getElementById('temp-pwd-value').textContent = data.temporaryPassword;
                openModal('modal-temp-password');
            } else {
                showToast('Usuario actualizado correctamente.', 'success');
            }
        } else {
            showToast(data.error || 'No se pudo guardar el usuario.', 'error');
        }
    } catch (e) {
        showToast('Error de red al guardar el usuario.', 'error');
    }
}

async function toggleAdminUserStatus(userId, newActive, username) {
    const actionLabel = newActive ? 'activar' : 'desactivar';
    const confirmed = await showConfirmDialog(
        `¿Deseas ${actionLabel} al usuario @${username}?`,
        newActive
            ? `El usuario @${username} podrá volver a iniciar sesión en la plataforma.`
            : `El usuario @${username} no podrá iniciar sesión, pero se conservará todo su historial de exámenes y trazabilidad (baja lógica).`,
        { confirmText: newActive ? 'Activar' : 'Desactivar', danger: !newActive }
    );
    if (!confirmed) return;

    try {
        const resp = await apiFetch(`/api/admin/users/${userId}/status`, {
            method: 'PUT',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ active: newActive })
        });
        const data = await resp.json().catch(() => ({}));
        if (resp.ok) {
            showToast(`Usuario @${username} ${newActive ? 'activado' : 'desactivado'}.`, 'success');
            await loadAdminUsers(0);
        } else {
            showToast(data.error || 'No se pudo cambiar el estado del usuario.', 'error');
        }
    } catch (e) {
        showToast('Error de red al cambiar el estado.', 'error');
    }
}

async function resetAdminUserPassword(userId, username) {
    const confirmed = await showConfirmDialog(
        `¿Resetear contraseña de @${username}?`,
        `Se generará una nueva contraseña temporal aleatoria y se forzará el cambio de contraseña en el próximo acceso de @${username}.`,
        { confirmText: 'Generar Contraseña Temporal', danger: false }
    );
    if (!confirmed) return;

    try {
        const resp = await apiFetch(`/api/admin/users/${userId}/reset-password`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({})
        });
        const data = await resp.json().catch(() => ({}));
        if (resp.ok) {
            await loadAdminUsers(0);
            document.getElementById('temp-pwd-username').textContent = username;
            document.getElementById('temp-pwd-value').textContent = data.temporaryPassword || '';
            openModal('modal-temp-password');
        } else {
            showToast(data.error || 'No se pudo resetear la contraseña.', 'error');
        }
    } catch (e) {
        showToast('Error de red al resetear contraseña.', 'error');
    }
}

// =================== RESULTADOS Y TRAZABILIDAD POR USUARIO (ADMIN) ===================
async function loadAdminUsersKpiList() {
    const tbody = document.getElementById('admin-results-kpi-tbody');
    if (!tbody) return;

    try {
        const resp = await apiFetch('/api/admin/results/users');
        if (!resp.ok) return;
        const kpis = await resp.json();

        // Poblar también el selector de usuarios de la vista agregada
        const aggSelect = document.getElementById('admin-agg-user-filter');
        if (aggSelect) {
            const currentVal = aggSelect.value;
            aggSelect.innerHTML = '<option value="">Todos los Usuarios</option>';
            kpis.forEach(k => {
                const opt = document.createElement('option');
                opt.value = k.userId;
                opt.textContent = `${k.displayName || k.username} (@${k.username})`;
                aggSelect.appendChild(opt);
            });
            aggSelect.value = currentVal;
        }

        tbody.innerHTML = '';
        kpis.forEach(k => {
            const tr = document.createElement('tr');
            tr.className = 'hover:bg-slate-900/50 transition';

            const scoreColor = k.averageScore >= 80 ? 'text-emerald-400' : (k.averageScore >= 65 ? 'text-amber-400' : 'text-rose-400');
            const readyColor = k.readinessScore >= 80 ? 'text-emerald-400' : (k.readinessScore >= 65 ? 'text-amber-400' : 'text-blue-400');

            tr.innerHTML = `
                <td class="py-3 px-4">
                    <div class="font-bold text-white">${k.displayName || k.username}</div>
                    <div class="font-mono text-[11px] text-slate-400">@${k.username} • ${k.role}</div>
                </td>
                <td class="py-3 px-4 text-center font-mono font-bold text-white">${k.totalExams} <span class="text-[10px] text-slate-400 font-normal">(${k.passedExams} aprob.)</span></td>
                <td class="py-3 px-4 text-center font-mono font-bold ${scoreColor}">${k.totalExams > 0 ? k.averageScore.toFixed(1) + '%' : '-'}</td>
                <td class="py-3 px-4 text-center font-mono font-bold ${readyColor}">${k.readinessScore.toFixed(1)}%</td>
                <td class="py-3 px-4 text-center font-mono text-slate-300">${k.masteredQuestionsCount} <span class="text-[10px] text-amber-400">(${k.dueReviewsCount} pend.)</span></td>
                <td class="py-3 px-4 font-mono text-[11px] text-slate-300">${k.lastAttemptAt ? `${formatDate(k.lastAttemptAt)} (${k.lastAttemptScore}%)` : 'Sin intentos'}</td>
                <td class="py-3 px-4 font-mono text-[11px] text-slate-400">${k.lastLoginAt || 'Nunca'}</td>
                <td class="py-3 px-4 text-right">
                    <button onclick="selectAdminUserDetail(${k.userId})" class="px-3 py-1.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white text-xs font-bold transition cursor-pointer">
                        🔍 Ver Detalle
                    </button>
                </td>
            `;
            tbody.appendChild(tr);
        });
    } catch (e) {
        console.error('Error cargando KPIs de usuarios:', e);
    }
}

async function selectAdminUserDetail(userId) {
    selectedAdminUserId = userId;
    await reloadSelectedAdminUserDetail();
}

async function reloadSelectedAdminUserDetail() {
    if (!selectedAdminUserId) return;
    const panel = document.getElementById('admin-user-detail-panel');
    const mode = document.getElementById('admin-detail-mode-filter')?.value || '';
    const fromDate = document.getElementById('admin-detail-from-date')?.value || '';
    const toDate = document.getElementById('admin-detail-to-date')?.value || '';

    const params = new URLSearchParams();
    if (mode) params.append('mode', mode);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);

    try {
        const resp = await apiFetch(`/api/admin/results/users/${selectedAdminUserId}?${params.toString()}`);
        if (!resp.ok) return;
        const d = await resp.json();

        if (panel) {
            panel.classList.remove('hidden');
            panel.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }

        document.getElementById('admin-detail-user-name').textContent = d.user.displayName || d.user.username;
        document.getElementById('admin-detail-user-role').textContent = d.user.role;
        document.getElementById('admin-detail-user-meta').textContent = `@${d.user.username} • ${d.user.email} • Último acceso: ${d.user.lastLoginAt || 'Nunca'}`;

        // KPIs + Actividad SRS
        const kpiContainer = document.getElementById('admin-detail-kpi-cards');
        const srs = d.srsActivity || {};
        kpiContainer.innerHTML = `
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Readiness Score</span>
                <div class="text-xl font-extrabold text-blue-400 font-mono mt-1">${d.kpi.readinessScore.toFixed(1)}%</div>
            </div>
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Exámenes Totales</span>
                <div class="text-xl font-extrabold text-white font-mono mt-1">${d.kpi.totalExams}</div>
            </div>
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Nota Media</span>
                <div class="text-xl font-extrabold text-emerald-400 font-mono mt-1">${d.kpi.averageScore.toFixed(1)}%</div>
            </div>
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Tasa Aprobado</span>
                <div class="text-xl font-extrabold text-indigo-300 font-mono mt-1">${d.kpi.passRatePercent.toFixed(1)}%</div>
            </div>
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Flashcards Dominadas</span>
                <div class="text-xl font-extrabold text-emerald-300 font-mono mt-1">${srs.masteredCount || 0} / ${srs.totalTracked || 0}</div>
            </div>
            <div class="bg-slate-950/80 p-3.5 rounded-xl border border-slate-800">
                <span class="text-[10px] uppercase text-slate-400 font-semibold">Factor SM-2 (EF)</span>
                <div class="text-xl font-extrabold text-amber-300 font-mono mt-1">${srs.averageEasinessFactor || 2.5}</div>
            </div>
        `;

        // Heatmap de las 6 áreas para este usuario
        renderTopicCardsIntoGrid('admin-detail-heatmap-grid', d.dashboard.topicHeatmaps || []);

        // Tabla de intentos
        const attemptsTbody = document.getElementById('admin-detail-attempts-tbody');
        attemptsTbody.innerHTML = '';
        if (!d.attempts || d.attempts.length === 0) {
            attemptsTbody.innerHTML = '<tr><td colspan="7" class="py-6 text-center text-slate-500 font-sans">Sin intentos registrados con los filtros actuales.</td></tr>';
        } else {
            d.attempts.forEach(a => {
                const tr = document.createElement('tr');
                tr.className = 'hover:bg-slate-900/60 transition';
                tr.innerHTML = `
                    <td class="py-2 px-3 text-blue-400 font-bold">#${a.id}</td>
                    <td class="py-2 px-3 font-sans text-[11px]">${formatDate(a.finishedAt || a.startedAt)}</td>
                    <td class="py-2 px-3"><span class="px-1.5 py-0.5 rounded bg-slate-800 text-slate-300 text-[10px]">${a.mode}</span></td>
                    <td class="py-2 px-3 font-bold ${a.passed ? 'text-emerald-400' : 'text-rose-400'}">${a.scorePercent.toFixed(1)}%</td>
                    <td class="py-2 px-3">${a.correctQuestions}/${a.totalQuestions}</td>
                    <td class="py-2 px-3 font-sans text-[11px] text-slate-400">${formatDurationSeconds(a.durationSeconds)}</td>
                    <td class="py-2 px-3 text-right">
                        <button onclick="openAttemptReviewModal(${a.id}, ${d.user.id})" class="px-2.5 py-1 rounded-lg bg-indigo-600/30 hover:bg-indigo-600/50 text-indigo-200 border border-indigo-500/30 font-sans text-[11px] font-semibold cursor-pointer">
                            👁️ Corrección
                        </button>
                    </td>
                `;
                attemptsTbody.appendChild(tr);
            });
        }

        // Preguntas más falladas por este usuario
        renderFailedQuestionsList('admin-detail-failed-questions', d.mostFailedQuestions || []);
    } catch (e) {
        console.error('Error cargando detalle del usuario:', e);
    }
}

function renderTopicCardsIntoGrid(containerId, heatmaps) {
    const grid = document.getElementById(containerId);
    if (!grid) return;
    grid.innerHTML = '';
    heatmaps.forEach(t => {
        const div = document.createElement('div');
        div.className = 'bg-slate-900/70 p-3.5 rounded-xl border border-slate-800 flex flex-col justify-between gap-2';
        const colorClass = t.statusColor === 'GREEN'
            ? 'text-emerald-400'
            : (t.statusColor === 'AMBER' ? 'text-amber-400' : (t.statusColor === 'RED' ? 'text-rose-400' : 'text-slate-400'));
        const barColor = t.statusColor === 'GREEN'
            ? 'bg-emerald-500'
            : (t.statusColor === 'AMBER' ? 'bg-amber-500' : (t.statusColor === 'RED' ? 'bg-rose-500' : 'bg-slate-600'));

        div.innerHTML = `
            <div class="flex items-start justify-between gap-2">
                <span class="text-xs font-bold text-white leading-snug">${t.name}</span>
                <span class="font-mono text-xs font-extrabold ${colorClass}">${t.accuracyPercent.toFixed(1)}%</span>
            </div>
            <div class="w-full bg-slate-950 h-1.5 rounded-full overflow-hidden">
                <div class="${barColor} h-1.5 rounded-full" style="width: ${Math.min(100, t.accuracyPercent)}%"></div>
            </div>
            <div class="flex justify-between text-[10px] text-slate-400 font-mono">
                <span>${t.correctAnswered}/${t.totalAnswered} aciertos</span>
                <span>⏱ ${t.averageTimeSeconds}s</span>
            </div>
        `;
        grid.appendChild(div);
    });
}

function renderFailedQuestionsList(containerId, list) {
    const container = document.getElementById(containerId);
    if (!container) return;
    if (!list || list.length === 0) {
        container.innerHTML = '<div class="p-6 text-center text-xs text-slate-500">No se registran fallos todavía.</div>';
        return;
    }
    container.innerHTML = list.map(q => `
        <div class="p-3 rounded-xl bg-slate-900/80 border border-slate-800 space-y-1">
            <div class="flex items-center justify-between text-[11px]">
                <span class="font-mono font-bold text-indigo-400">#${q.questionId} • ${q.topicCode}</span>
                <span class="px-2 py-0.5 rounded bg-rose-500/20 text-rose-300 border border-rose-500/30 font-mono font-bold">
                    ${q.failedCount} fallos (${q.failureRatePercent}%)
                </span>
            </div>
            <p class="text-xs text-slate-200 line-clamp-2">${q.stem}</p>
        </div>
    `).join('');
}

async function openAttemptReviewModal(attemptId, userId = null) {
    const endpoint = userId
        ? `/api/admin/results/users/${userId}/attempts/${attemptId}`
        : `/api/exams/${attemptId}`;

    try {
        const resp = await apiFetch(endpoint);
        if (!resp.ok) {
            showToast('No se pudo cargar el detalle del intento.', 'error');
            return;
        }
        const review = await resp.json();

        document.getElementById('attempt-review-modal-title').textContent = `Revisión del Intento #${review.attemptId} (${review.mode})`;
        document.getElementById('attempt-review-modal-subtitle').textContent =
            `Nota: ${review.scorePercent.toFixed(1)}% • Aciertos: ${review.correctCount}/${review.totalCount} • Tiempo: ${formatDurationSeconds(review.totalTimeSeconds)}`;

        const body = document.getElementById('attempt-review-modal-body');
        body.innerHTML = (review.reviews || []).map((r, idx) => `
            <div class="p-4 rounded-2xl border ${r.correct ? 'bg-slate-950/60 border-emerald-500/30' : 'bg-slate-950/80 border-rose-500/40'} space-y-2.5 text-xs">
                <div class="flex items-center justify-between">
                    <span class="font-bold text-blue-400">Pregunta ${idx + 1} (#${r.questionId}) • ${r.topicName}</span>
                    <span class="px-2 py-0.5 rounded-full font-bold ${r.correct ? 'bg-emerald-500/20 text-emerald-400' : 'bg-rose-500/20 text-rose-400'}">
                        ${r.correct ? '✓ Correcta' : '✗ Incorrecta'} (Elegida: ${r.selectedOptions || 'Ninguna'} | Correcta: ${r.correctOptions})
                    </span>
                </div>
                <div class="text-sm font-semibold text-white">${r.stem}</div>
                <div class="p-2.5 rounded-xl bg-slate-900 border border-slate-800 text-slate-300">
                    <strong class="text-blue-400">Explicación:</strong> ${r.explanation}
                </div>
            </div>
        `).join('');

        openModal('modal-attempt-review-detail');
    } catch (e) {
        showToast('Error al abrir la revisión del intento.', 'error');
    }
}

// =================== VISTA AGREGADA GLOBAL & EXPORTACIÓN CSV (ADMIN) ===================
async function loadAdminAggregateView() {
    const userId = document.getElementById('admin-agg-user-filter')?.value || '';
    const mode = document.getElementById('admin-agg-mode-filter')?.value || '';
    const fromDate = document.getElementById('admin-agg-from-date')?.value || '';
    const toDate = document.getElementById('admin-agg-to-date')?.value || '';

    const params = new URLSearchParams();
    if (userId) params.append('userId', userId);
    if (mode) params.append('mode', mode);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);

    try {
        const resp = await apiFetch(`/api/admin/results/aggregate?${params.toString()}`);
        if (!resp.ok) return;
        const agg = await resp.json();

        const kpiDiv = document.getElementById('admin-agg-kpi-cards');
        if (kpiDiv) {
            kpiDiv.innerHTML = `
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Usuarios Activos</span>
                    <div class="text-2xl font-extrabold text-white font-mono mt-1">${agg.activeUsers} / ${agg.totalUsers}</div>
                </div>
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Exámenes Completados</span>
                    <div class="text-2xl font-extrabold text-blue-400 font-mono mt-1">${agg.totalCompletedExams}</div>
                </div>
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Nota Media Global</span>
                    <div class="text-2xl font-extrabold text-emerald-400 font-mono mt-1">${agg.globalAverageScore.toFixed(1)}%</div>
                </div>
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Tasa Aprobado Global</span>
                    <div class="text-2xl font-extrabold text-indigo-300 font-mono mt-1">${agg.globalPassRatePercent.toFixed(1)}%</div>
                </div>
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Banco de Preguntas</span>
                    <div class="text-2xl font-extrabold text-amber-300 font-mono mt-1">${agg.totalQuestionsInBank}</div>
                </div>
                <div class="bg-slate-800/80 p-4 rounded-2xl border border-slate-700/80">
                    <span class="text-[10px] uppercase text-slate-400 font-semibold">Materiales Activos</span>
                    <div class="text-2xl font-extrabold text-sky-300 font-mono mt-1">${agg.totalMaterialsCount}</div>
                </div>
            `;
        }

        renderTopicCardsIntoGrid('admin-agg-topics-grid', agg.topicAverages || []);
        renderFailedQuestionsList('admin-agg-failed-questions', agg.mostFailedQuestions || []);

        const rankTbody = document.getElementById('admin-agg-ranking-tbody');
        if (rankTbody) {
            rankTbody.innerHTML = (agg.userRanking || []).map((u, idx) => `
                <tr class="hover:bg-slate-900/50 transition">
                    <td class="py-2.5 px-3 font-mono font-bold text-amber-400">#${idx + 1}</td>
                    <td class="py-2.5 px-3">
                        <div class="font-bold text-white">${u.displayName || u.username}</div>
                        <div class="font-mono text-[10px] text-slate-400">@${u.username}</div>
                    </td>
                    <td class="py-2.5 px-3 text-center font-mono font-bold text-blue-400">${u.readinessScore.toFixed(1)}%</td>
                    <td class="py-2.5 px-3 text-center font-mono font-bold text-emerald-400">${u.averageScore.toFixed(1)}%</td>
                    <td class="py-2.5 px-3 text-center font-mono text-slate-300">${u.totalExams}</td>
                </tr>
            `).join('');
        }
    } catch (e) {
        console.error('Error cargando vista agregada:', e);
    }
}

function exportAdminResultsCsv(customParams = '') {
    const url = `/api/admin/results/export.csv${customParams ? '?' + customParams : ''}`;
    window.open(url, '_blank');
}

function exportAdminSelectedUserCsv() {
    if (!selectedAdminUserId) return;
    const mode = document.getElementById('admin-detail-mode-filter')?.value || '';
    const fromDate = document.getElementById('admin-detail-from-date')?.value || '';
    const toDate = document.getElementById('admin-detail-to-date')?.value || '';
    const params = new URLSearchParams({ userId: selectedAdminUserId });
    if (mode) params.append('mode', mode);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    exportAdminResultsCsv(params.toString());
}

function exportAdminAggregateFilteredCsv() {
    const userId = document.getElementById('admin-agg-user-filter')?.value || '';
    const mode = document.getElementById('admin-agg-mode-filter')?.value || '';
    const fromDate = document.getElementById('admin-agg-from-date')?.value || '';
    const toDate = document.getElementById('admin-agg-to-date')?.value || '';
    const params = new URLSearchParams();
    if (userId) params.append('userId', userId);
    if (mode) params.append('mode', mode);
    if (fromDate) params.append('fromDate', fromDate);
    if (toDate) params.append('toDate', toDate);
    exportAdminResultsCsv(params.toString());
}

// =================== REGISTRO DE AUDITORÍA (ADMIN) ===================
async function loadAdminAuditLogs(page = 0) {
    const tbody = document.getElementById('admin-audit-tbody');
    if (!tbody) return;
    try {
        const resp = await apiFetch(`/api/admin/audit-logs?page=${page}&size=80`);
        if (!resp.ok) return;
        const data = await resp.json();
        const logs = data.content || [];
        if (logs.length === 0) {
            tbody.innerHTML = '<tr><td colspan="6" class="py-6 text-center text-slate-500">Sin eventos de auditoría registrados.</td></tr>';
            return;
        }
        tbody.innerHTML = logs.map(l => `
            <tr class="hover:bg-slate-900/50 transition">
                <td class="py-2.5 px-4 text-slate-400 whitespace-nowrap">${l.createdAt || ''}</td>
                <td class="py-2.5 px-4 font-bold text-white">${l.actorUsername || 'SYSTEM'}</td>
                <td class="py-2.5 px-4"><span class="px-2 py-0.5 rounded bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 font-bold">${l.action}</span></td>
                <td class="py-2.5 px-4 text-slate-300">${l.targetType || '-'}${l.targetId ? ' #' + l.targetId : ''}</td>
                <td class="py-2.5 px-4 font-sans text-slate-300">${l.details || ''}</td>
                <td class="py-2.5 px-4 text-slate-500">${l.ipAddress || '-'}</td>
            </tr>
        `).join('');
    } catch (e) {
        console.error('Error cargando auditoría:', e);
    }
}




