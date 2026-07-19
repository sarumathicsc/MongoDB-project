/* =====================================================
   common.js
   Shared across ALL pages (index.html, products.html, orders.html):
    - Mobile hamburger menu toggle
    - Toast notification helper
    - Shared API base URL and small utility functions
   ===================================================== */

// Base URL of our backend REST API. Every fetch() call is prefixed with this.
const API_BASE = "http://localhost:8080/api";

/* -----------------------------------------------------
   HAMBURGER MENU (mobile navigation toggle)
----------------------------------------------------- */
const hamburger = document.getElementById("hamburger");
const navLinks = document.querySelector(".nav-links");

if (hamburger) {
  hamburger.addEventListener("click", () => {
    navLinks.classList.toggle("show");
  });
}

/* -----------------------------------------------------
   TOAST MESSAGE (success / error banner)
   Reused on both products.html and orders.html, each of
   which has its own <div id="toast"> element.
----------------------------------------------------- */
function showToast(message, type = "success") {
  const toast = document.getElementById("toast");
  if (!toast) return;
  toast.textContent = message;
  toast.className = `toast ${type}`;
  setTimeout(() => {
    toast.className = "toast";
  }, 3000);
}

/* -----------------------------------------------------
   Prevents user input from breaking our HTML structure
   (basic protection against HTML/script injection).
----------------------------------------------------- */
function escapeHtml(str) {
  if (str === undefined || str === null) return "";
  return String(str)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;");
}

/* -----------------------------------------------------
   Formats a number as Indian Rupees, e.g. 1234.5 -> "₹1,234.50"
----------------------------------------------------- */
function formatCurrency(amount) {
  const num = Number(amount) || 0;
  return "₹" + num.toLocaleString("en-IN", { minimumFractionDigits: 2, maximumFractionDigits: 2 });
}
