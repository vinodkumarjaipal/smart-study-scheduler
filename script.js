// =========================================================
// GLOBAL CONFIGURATION & STATE MANAGEMENT
// =========================================================
const API_BASE_URL = "http://localhost:8080/api";
let draftSessions = []; // Temporary array to hold study subjects
let isRedirecting = false; // System lock for redirect processes

document.addEventListener("DOMContentLoaded", () => {
    checkAuthentication();
    initFormListeners();
    setupLogout();
});

// =========================================================
// SECURITY: ROUTE PROTECTION (Bulletproof Path Matching)
// =========================================================
function checkAuthentication() {
    if (isRedirecting) return;

    const isLoggedIn = localStorage.getItem("isLoggedIn");
    const path = window.location.pathname.toLowerCase();

    // Prevent unauthenticated access to the main app
    if (!isLoggedIn && (path.includes("index.html") || path.endsWith("/"))) {
        window.location.replace("login.html");
    }
    // Prevent authenticated users from going back to login/signup
    else if (isLoggedIn && (path.includes("login.html") || path.includes("signup.html"))) {
        window.location.replace("index.html");
    }
}

// =========================================================
// EVENT LISTENERS INITIALIZATION
// =========================================================
function initFormListeners() {
    // 1. LOGIN HANDLER
    const loginForm = document.getElementById("loginForm");
    if (loginForm) {
        loginForm.addEventListener("submit", async (e) => {
            e.preventDefault();
            const submitBtn = loginForm.querySelector("button[type='submit']");
            submitBtn.disabled = true;
            submitBtn.innerHTML = "Authenticating...";

            const username = document.getElementById("username").value;
            const password = document.getElementById("password").value;

            const response = await makeApiCall("/login", { username, password });

            if (response && (response.status === "success" || response.message?.toLowerCase().includes("success"))) {
                isRedirecting = true;
                localStorage.setItem("isLoggedIn", "true");
                showAuthMessage("Authentication successful. Loading dashboard...", "success");
                setTimeout(() => window.location.replace("index.html"), 1200);
            } else {
                submitBtn.disabled = false;
                submitBtn.innerHTML = "Sign In";
                showAuthMessage(response?.message || "Invalid credentials. Please try again.", "error");
            }
        });
    }

    // 2. SIGNUP HANDLER
    const signupForm = document.getElementById("signupForm");
    if (signupForm) {
        signupForm.addEventListener("submit", async (e) => {
            e.preventDefault();

            const username = document.getElementById("reg-username").value;
            const password = document.getElementById("reg-password").value;
            const confirmPassword = document.getElementById("reg-confirm-password").value;

            if (password !== confirmPassword) {
                showAuthMessage("Passwords do not match. Please verify.", "error");
                return;
            }

            const submitBtn = signupForm.querySelector("button[type='submit']");
            submitBtn.disabled = true;
            submitBtn.innerHTML = "Creating Profile...";

            const response = await makeApiCall("/signup", { username, password });

            if (response && (response.status === "success" || response.success === true || response.id)) {
                isRedirecting = true;

                // Pura clean message bina kisi link ke
                showAuthMessage("Account created successfully! Redirecting to login...", "success");

                // Reliable absolute redirection (Fast: 2 seconds)
                setTimeout(() => {
                    const currentOrigin = window.location.origin;
                    const currentPath = window.location.pathname;
                    const basePath = currentPath.substring(0, currentPath.lastIndexOf('/'));
                    window.location.replace(`${currentOrigin}${basePath}/login.html`);
                }, 2000);

            } else {
                submitBtn.disabled = false;
                submitBtn.innerHTML = "Sign Up";
                showAuthMessage(response?.message || "Registration failed. Username might be taken.", "error");
            }
        });
    }

    // 3. ADD STUDY SESSION HANDLER
    const sessionForm = document.getElementById("sessionForm");
    if (sessionForm) {
        sessionForm.addEventListener("submit", (e) => {
            e.preventDefault();
            const subjectName = document.getElementById("subjectName").value.trim();
            const startTimeStr = document.getElementById("startTime").value;
            const endTimeStr = document.getElementById("endTime").value;
            const priority = parseInt(document.getElementById("priority").value);

            const startTime = parseInt(startTimeStr.replace(":", ""));
            const endTime = parseInt(endTimeStr.replace(":", ""));

            if (startTime >= endTime) {
                showToast("Start time must be earlier than End time.", "toast-error");
                return;
            }

            const session = { subjectName, startTime, endTime, priority };
            draftSessions.push(session);

            updateDraftTable();
            sessionForm.reset();
            document.getElementById("subjectName").focus();

            showToast(`${subjectName} added to draft pool.`, "toast-success");
        });
    }

    // 4. CORE ALGORITHM TRIGGER
    const optimizeBtn = document.getElementById("optimizeBtn");
    if (optimizeBtn) {
        optimizeBtn.addEventListener("click", async () => {
            if (draftSessions.length === 0) {
                showToast("Please add at least one study session to the pool.", "toast-warn");
                return;
            }

            optimizeBtn.disabled = true;
            optimizeBtn.innerText = "Computing...";
            showToast("Running Dynamic Programming Engine...", "toast-success");

            const optimizedPlan = await makeApiCall("/optimize", draftSessions);

            optimizeBtn.disabled = false;
            optimizeBtn.innerText = "Generate Optimal Plan";

            if (optimizedPlan && Array.isArray(optimizedPlan)) {
                renderOptimalTable(optimizedPlan);
                showToast("Optimal study plan generated successfully!", "toast-success");
            } else {
                showToast("Failed to compute optimal schedule. Check server logs.", "toast-error");
            }
        });
    }
}

// =========================================================
// DOM RENDERING ENGINE
// =========================================================
function updateDraftTable() {
    const tbody = document.getElementById("draftTableBody");
    if (!tbody) return;

    if (draftSessions.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--text-muted); padding:2rem;">No sessions added yet.</td></tr>`;
        return;
    }

    tbody.innerHTML = draftSessions.map(s => `
        <tr>
            <td><strong>${s.subjectName}</strong></td>
            <td>${formatTimeAMPM(s.startTime)}</td>
            <td>${formatTimeAMPM(s.endTime)}</td>
            <td>
                <span style="background: #f1f5f9; padding: 4px 10px; border-radius: 6px; font-weight: 600; color: var(--text-primary); border: 1px solid #e2e8f0;">
                    ${s.priority}
                </span>
            </td>
        </tr>
    `).join('');
}

function renderOptimalTable(plan) {
    const optimalSection = document.getElementById("optimalSection");
    const tbody = document.getElementById("optimalTableBody");
    if (!tbody || !optimalSection) return;

    if (plan.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" style="text-align:center; color:var(--accent-red); padding:2rem;">No viable schedule possible without overlaps. Adjust session times.</td></tr>`;
    } else {
        tbody.innerHTML = plan.map(s => `
            <tr>
                <td style="color: var(--accent-green);">✔ <strong>${s.subjectName}</strong></td>
                <td>${formatTimeAMPM(s.startTime)}</td>
                <td>${formatTimeAMPM(s.endTime)}</td>
                <td>
                    <span style="background: var(--grad-green); color: white; padding: 4px 10px; border-radius: 6px; font-weight: 700; box-shadow: 0 2px 5px rgba(16, 185, 129, 0.2);">
                        ${s.priority}
                    </span>
                </td>
            </tr>
        `).join('');
    }
    optimalSection.style.display = "block";

    // Smooth scroll to results
    setTimeout(() => {
        optimalSection.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }, 150);
}

// =========================================================
// UTILITIES & HELPER FUNCTIONS
// =========================================================
async function makeApiCall(endpoint, data) {
    try {
        const response = await fetch(`${API_BASE_URL}${endpoint}`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify(data)
        });
        return await response.json();
    } catch (error) {
        console.error("API Error context:", error);
        showToast("Server connection failed. Ensure Java backend is running on port 8080.", "toast-error");
        return null;
    }
}

function setupLogout() {
    const logoutBtn = document.getElementById("logoutBtn");
    if (logoutBtn) {
        logoutBtn.addEventListener("click", () => {
            isRedirecting = true;
            localStorage.removeItem("isLoggedIn");
            showToast("Logging out securely...", "toast-success");
            setTimeout(() => window.location.replace("login.html"), 800);
        });
    }
}

// Converts Military Time Int (e.g., 1430) to Standard String (e.g., "02:30 PM")
function formatTimeAMPM(militaryInt) {
    let timeStr = militaryInt.toString().padStart(4, '0');
    let hours = parseInt(timeStr.substring(0, 2));
    let minutes = timeStr.substring(2, 4);
    let ampm = hours >= 12 ? 'PM' : 'AM';

    hours = hours % 12;
    hours = hours ? hours : 12;

    let formattedHours = hours < 10 ? '0' + hours : hours;
    return `${formattedHours}:${minutes} ${ampm}`;
}

// Inline messaging for Auth Forms
function showAuthMessage(message, type = "error") {
    const msgDiv = document.getElementById("auth-message");
    if (!msgDiv) return;
    msgDiv.innerHTML = message;
    msgDiv.className = `auth-message ${type}`;
}

// Global Floating Toast Notifications
function showToast(message, typeClass = "toast-success") {
    const toast = document.getElementById("toast");
    if (!toast) return;

    toast.className = "toast";
    toast.innerText = message;

    toast.classList.add(typeClass);

    setTimeout(() => {
        toast.classList.add("show");
    }, 10);

    setTimeout(() => {
        toast.classList.remove("show");
    }, 4000);
}