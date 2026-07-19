/* =====================================================
   orders.js
   Handles everything on orders.html:
    - Dynamic "add item" rows for creating multi-product orders
    - Loading/rendering the orders table
    - Updating order status (with automatic stock restore on cancel, handled server-side)
    - Viewing full order details in a modal
    - Deleting orders
    - Loading dashboard stats and the two aggregation reports
      (Sales by Category using $lookup, and Top Selling Products)
   Depends on common.js being loaded first.
   ===================================================== */

const ORDERS_API = `${API_BASE}/orders`;
const PRODUCTS_API_FOR_ORDERS = `${API_BASE}/products`;

const orderForm = document.getElementById("orderForm");

// Only run this file's logic if we're on orders.html.
if (orderForm) {

  const orderItemsContainer = document.getElementById("orderItemsContainer");
  const addItemBtn = document.getElementById("addItemBtn");
  const orderTotalDisplay = document.getElementById("orderTotalDisplay");
  const orderTableBody = document.getElementById("orderTableBody");

  const searchInput = document.getElementById("searchInput");
  const filterStatus = document.getElementById("filterStatus");
  const searchBtn = document.getElementById("searchBtn");
  const resetBtn = document.getElementById("resetBtn");

  const viewOrderModal = document.getElementById("viewOrderModal");
  const orderDetailsContent = document.getElementById("orderDetailsContent");
  const closeViewModalBtn = document.getElementById("closeViewModalBtn");

  const deleteModal = document.getElementById("deleteModal");
  const deleteOrderIdSpan = document.getElementById("deleteOrderId");
  const confirmDeleteBtn = document.getElementById("confirmDeleteBtn");
  const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");

  let cachedProducts = [];     // list of all products, used to populate item dropdowns
  let itemRowCounter = 0;      // gives each dynamic row a unique id
  let orderPendingDeleteId = null;

  /* -----------------------------------------------------
     LOAD EVERYTHING WHEN PAGE OPENS
  ----------------------------------------------------- */
  window.addEventListener("DOMContentLoaded", async () => {
    await loadProductsForDropdown();
    addItemRow(); // start with one empty item row
    loadOrders();
    loadStats();
    loadCategoryReport();
    loadTopProductsReport();
  });

  /* -----------------------------------------------------
     LOAD PRODUCTS (used inside each order-item dropdown)
  ----------------------------------------------------- */
  async function loadProductsForDropdown() {
    try {
      const response = await fetch(PRODUCTS_API_FOR_ORDERS);
      cachedProducts = await response.json();
    } catch (err) {
      showToast("Could not load product list. Is the backend running?", "error");
      cachedProducts = [];
    }
  }

  /* -----------------------------------------------------
     DYNAMIC ORDER ITEM ROWS
  ----------------------------------------------------- */
  addItemBtn.addEventListener("click", () => addItemRow());

  function addItemRow() {
    itemRowCounter++;
    const rowId = `item-row-${itemRowCounter}`;

    const productOptions = cachedProducts.map(p =>
      `<option value="${p.productId}" data-price="${p.price}" data-stock="${p.stockQuantity}">
        ${escapeHtml(p.name)} (${escapeHtml(p.productId)}) - ${formatCurrency(p.price)}
      </option>`
    ).join("");

    const rowHtml = `
      <div class="order-item-row" id="${rowId}">
        <select class="item-product-select">
          <option value="">-- Select Product --</option>
          ${productOptions}
        </select>
        <input type="number" class="item-quantity-input" min="1" value="1" placeholder="Qty" />
        <span class="item-row-subtotal">₹0.00</span>
        <button type="button" class="remove-item-btn" onclick="removeItemRow('${rowId}')">
          <i class="fa-solid fa-xmark"></i>
        </button>
      </div>
    `;
    orderItemsContainer.insertAdjacentHTML("beforeend", rowHtml);

    // Recalculate totals whenever this row's product or quantity changes.
    const newRow = document.getElementById(rowId);
    newRow.querySelector(".item-product-select").addEventListener("change", recalculateOrderTotal);
    newRow.querySelector(".item-quantity-input").addEventListener("input", recalculateOrderTotal);
  }

  window.removeItemRow = function (rowId) {
    const row = document.getElementById(rowId);
    if (row) row.remove();
    recalculateOrderTotal();
  };

  // Recomputes each row's subtotal (price * qty) and the grand order total.
  function recalculateOrderTotal() {
    let grandTotal = 0;

    document.querySelectorAll(".order-item-row").forEach(row => {
      const select = row.querySelector(".item-product-select");
      const qtyInput = row.querySelector(".item-quantity-input");
      const subtotalSpan = row.querySelector(".item-row-subtotal");

      const selectedOption = select.options[select.selectedIndex];
      const price = selectedOption ? parseFloat(selectedOption.dataset.price || 0) : 0;
      const qty = parseInt(qtyInput.value, 10) || 0;
      const subtotal = price * qty;

      subtotalSpan.textContent = formatCurrency(subtotal);
      grandTotal += subtotal;
    });

    orderTotalDisplay.textContent = formatCurrency(grandTotal);
  }

  /* -----------------------------------------------------
     FORM VALIDATION
  ----------------------------------------------------- */
  function validateOrderForm(customerName, customerEmail, customerPhone, shippingAddress, items) {
    let isValid = true;
    clearErrors();

    if (!customerName.trim()) { showError("customerName", "Customer name is required."); isValid = false; }
    if (!customerEmail.trim() || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(customerEmail)) {
      showError("customerEmail", "Enter a valid email address."); isValid = false;
    }
    if (!customerPhone.trim() || !/^\d{10}$/.test(customerPhone)) {
      showError("customerPhone", "Phone must be exactly 10 digits."); isValid = false;
    }
    if (!shippingAddress.trim()) { showError("shippingAddress", "Shipping address is required."); isValid = false; }

    if (items.length === 0) {
      document.getElementById("err-items").textContent = "Add at least one product to the order.";
      isValid = false;
    } else {
      document.getElementById("err-items").textContent = "";
    }

    return isValid;
  }

  function showError(fieldId, message) {
    document.getElementById(`err-${fieldId}`).textContent = message;
    document.getElementById(fieldId).classList.add("invalid");
  }

  function clearErrors() {
    document.querySelectorAll(".error-text").forEach(el => el.textContent = "");
    document.querySelectorAll(".form-group input, .form-group select").forEach(el => el.classList.remove("invalid"));
  }

  /* -----------------------------------------------------
     FORM SUBMIT: PLACE ORDER
  ----------------------------------------------------- */
  orderForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const customerName = document.getElementById("customerName").value;
    const customerEmail = document.getElementById("customerEmail").value;
    const customerPhone = document.getElementById("customerPhone").value;
    const shippingAddress = document.getElementById("shippingAddress").value;

    // Collect items: only rows where a product was actually selected.
    const items = [];
    document.querySelectorAll(".order-item-row").forEach(row => {
      const productId = row.querySelector(".item-product-select").value;
      const quantity = parseInt(row.querySelector(".item-quantity-input").value, 10);
      if (productId && quantity > 0) {
        items.push({ productId, quantity });
      }
    });

    if (!validateOrderForm(customerName, customerEmail, customerPhone, shippingAddress, items)) {
      showToast("Please fix the errors in the form.", "error");
      return;
    }

    const orderData = { customerName, customerEmail, customerPhone, shippingAddress, items };

    try {
      const response = await fetch(ORDERS_API, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(orderData)
      });

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(errText || "Server error");
      }

      showToast("Order placed successfully! Stock has been updated.", "success");
      orderForm.reset();
      orderItemsContainer.innerHTML = "";
      addItemRow();
      orderTotalDisplay.textContent = formatCurrency(0);

      // Refresh everything that could have changed because of this order.
      await loadProductsForDropdown(); // stock quantities changed
      loadOrders();
      loadStats();
      loadCategoryReport();
      loadTopProductsReport();

    } catch (err) {
      showToast("Error: " + err.message, "error");
    }
  });

  /* -----------------------------------------------------
     LOAD & RENDER ORDERS TABLE
  ----------------------------------------------------- */
  async function loadOrders(queryParams = "") {
    orderTableBody.innerHTML = `<tr><td colspan="7" class="loading-row">Loading orders...</td></tr>`;
    try {
      const response = await fetch(`${ORDERS_API}${queryParams}`);
      if (!response.ok) throw new Error("Failed to fetch orders");
      const orders = await response.json();
      renderOrdersTable(orders);
    } catch (err) {
      orderTableBody.innerHTML = `<tr><td colspan="7" class="empty-row">
        Could not load data. Is the Java backend running on port 8080? (${err.message})
      </td></tr>`;
    }
  }

  function renderOrdersTable(orders) {
    if (!orders || orders.length === 0) {
      orderTableBody.innerHTML = `<tr><td colspan="7" class="empty-row">No orders found.</td></tr>`;
      return;
    }

    const statusClasses = {
      Pending: "badge-pending",
      Shipped: "badge-shipped",
      Delivered: "badge-delivered",
      Cancelled: "badge-cancelled"
    };

    orderTableBody.innerHTML = orders.map(o => `
      <tr>
        <td>${escapeHtml(o.orderId)}</td>
        <td>${escapeHtml(o.customerName)}</td>
        <td>${o.items.length} item(s)</td>
        <td>${formatCurrency(o.totalAmount)}</td>
        <td>
          <select class="status-select ${statusClasses[o.orderStatus] || ''}" onchange="updateOrderStatus('${o._id}', this.value)">
            ${["Pending", "Shipped", "Delivered", "Cancelled"].map(s =>
              `<option value="${s}" ${s === o.orderStatus ? "selected" : ""}>${s}</option>`
            ).join("")}
          </select>
        </td>
        <td>${escapeHtml(o.orderDate)}</td>
        <td class="action-buttons">
          <button class="btn btn-secondary btn-small" onclick="viewOrder('${o._id}')">
            <i class="fa-solid fa-eye"></i>
          </button>
          <button class="btn btn-danger btn-small" onclick="askDeleteOrder('${o._id}', '${escapeHtml(o.orderId)}')">
            <i class="fa-solid fa-trash"></i>
          </button>
        </td>
      </tr>
    `).join("");
  }

  /* -----------------------------------------------------
     UPDATE ORDER STATUS
     Note: if changed to "Cancelled", the Java backend
     automatically restores the stock quantities that were
     deducted when the order was placed.
  ----------------------------------------------------- */
  window.updateOrderStatus = async function (id, newStatus) {
    try {
      const response = await fetch(`${ORDERS_API}/${id}/status`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ orderStatus: newStatus })
      });
      if (!response.ok) throw new Error("Failed to update status");

      showToast(`Order status updated to "${newStatus}"`, "success");
      loadOrders();
      loadStats();
      await loadProductsForDropdown(); // stock may have been restored if cancelled
    } catch (err) {
      showToast("Error: " + err.message, "error");
      loadOrders(); // revert dropdown to real value
    }
  };

  /* -----------------------------------------------------
     VIEW ORDER DETAILS (MODAL)
  ----------------------------------------------------- */
  window.viewOrder = async function (id) {
    try {
      const response = await fetch(`${ORDERS_API}/${id}`);
      if (!response.ok) throw new Error("Order not found");
      const o = await response.json();

      const itemsRows = o.items.map(item => `
        <tr>
          <td>${escapeHtml(item.productName)}</td>
          <td>${item.quantity}</td>
          <td>${formatCurrency(item.unitPrice)}</td>
          <td>${formatCurrency(item.subtotal)}</td>
        </tr>
      `).join("");

      orderDetailsContent.innerHTML = `
        <div class="order-detail-row"><span>Order ID</span><strong>${escapeHtml(o.orderId)}</strong></div>
        <div class="order-detail-row"><span>Customer</span><strong>${escapeHtml(o.customerName)}</strong></div>
        <div class="order-detail-row"><span>Email</span><span>${escapeHtml(o.customerEmail)}</span></div>
        <div class="order-detail-row"><span>Phone</span><span>${escapeHtml(o.customerPhone)}</span></div>
        <div class="order-detail-row"><span>Shipping Address</span><span>${escapeHtml(o.shippingAddress)}</span></div>
        <div class="order-detail-row"><span>Status</span><span>${escapeHtml(o.orderStatus)}</span></div>
        <div class="order-detail-row"><span>Date</span><span>${escapeHtml(o.orderDate)}</span></div>
        <div class="order-detail-items">
          <table>
            <thead><tr><th>Product</th><th>Qty</th><th>Unit Price</th><th>Subtotal</th></tr></thead>
            <tbody>${itemsRows}</tbody>
          </table>
        </div>
        <div class="order-detail-row" style="border-bottom:none; font-size:1.1rem;">
          <span>Total</span><strong>${formatCurrency(o.totalAmount)}</strong>
        </div>
      `;

      viewOrderModal.classList.add("active");
    } catch (err) {
      showToast("Error loading order: " + err.message, "error");
    }
  };

  closeViewModalBtn.addEventListener("click", () => viewOrderModal.classList.remove("active"));

  /* -----------------------------------------------------
     DELETE ORDER
  ----------------------------------------------------- */
  window.askDeleteOrder = function (id, orderId) {
    orderPendingDeleteId = id;
    deleteOrderIdSpan.textContent = orderId;
    deleteModal.classList.add("active");
  };

  cancelDeleteBtn.addEventListener("click", () => {
    deleteModal.classList.remove("active");
    orderPendingDeleteId = null;
  });

  confirmDeleteBtn.addEventListener("click", async () => {
    if (!orderPendingDeleteId) return;
    try {
      const response = await fetch(`${ORDERS_API}/${orderPendingDeleteId}`, { method: "DELETE" });
      if (!response.ok) throw new Error("Failed to delete order");

      showToast("Order deleted successfully!", "success");
      deleteModal.classList.remove("active");
      orderPendingDeleteId = null;
      loadOrders();
      loadStats();
    } catch (err) {
      showToast("Error: " + err.message, "error");
    }
  });

  /* -----------------------------------------------------
     LOAD DASHBOARD STATS
  ----------------------------------------------------- */
  async function loadStats() {
    try {
      const response = await fetch(`${ORDERS_API}/stats`);
      if (!response.ok) throw new Error("Failed to fetch stats");
      const stats = await response.json();

      document.getElementById("statTotalOrders").textContent = stats.totalOrders ?? 0;
      document.getElementById("statTotalRevenue").textContent = formatCurrency(stats.totalRevenue ?? 0);
      document.getElementById("statPendingOrders").textContent = stats.pendingOrders ?? 0;
      document.getElementById("statAvgOrderValue").textContent = formatCurrency(stats.averageOrderValue ?? 0);
    } catch (err) {
      console.error("Could not load order stats:", err);
    }
  }

  /* -----------------------------------------------------
     LOAD SALES-BY-CATEGORY REPORT ($lookup aggregation)
  ----------------------------------------------------- */
  async function loadCategoryReport() {
    const tbody = document.getElementById("categoryReportBody");
    try {
      const response = await fetch(`${ORDERS_API}/reports/sales-by-category`);
      if (!response.ok) throw new Error("Failed to fetch report");
      const rows = await response.json();

      if (!rows || rows.length === 0) {
        tbody.innerHTML = `<tr><td colspan="3" class="empty-row">No sales data yet.</td></tr>`;
        return;
      }

      tbody.innerHTML = rows.map(r => `
        <tr>
          <td>${escapeHtml(r.category)}</td>
          <td>${r.unitsSold}</td>
          <td>${formatCurrency(r.revenue)}</td>
        </tr>
      `).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="3" class="empty-row">Could not load report.</td></tr>`;
    }
  }

  /* -----------------------------------------------------
     LOAD TOP SELLING PRODUCTS REPORT
  ----------------------------------------------------- */
  async function loadTopProductsReport() {
    const tbody = document.getElementById("topProductsReportBody");
    try {
      const response = await fetch(`${ORDERS_API}/reports/top-products`);
      if (!response.ok) throw new Error("Failed to fetch report");
      const rows = await response.json();

      if (!rows || rows.length === 0) {
        tbody.innerHTML = `<tr><td colspan="4" class="empty-row">No sales data yet.</td></tr>`;
        return;
      }

      tbody.innerHTML = rows.map((r, index) => `
        <tr>
          <td>#${index + 1}</td>
          <td>${escapeHtml(r.productName)}</td>
          <td>${r.unitsSold}</td>
          <td>${formatCurrency(r.revenue)}</td>
        </tr>
      `).join("");
    } catch (err) {
      tbody.innerHTML = `<tr><td colspan="4" class="empty-row">Could not load report.</td></tr>`;
    }
  }

  /* -----------------------------------------------------
     SEARCH & FILTER ORDERS
  ----------------------------------------------------- */
  searchBtn.addEventListener("click", performSearch);
  searchInput.addEventListener("keyup", (e) => { if (e.key === "Enter") performSearch(); });

  function performSearch() {
    const keyword = searchInput.value.trim();
    const status = filterStatus.value;

    const params = new URLSearchParams();
    if (keyword) params.append("query", keyword);
    if (status) params.append("status", status);

    const queryString = params.toString() ? `/search?${params.toString()}` : "";
    loadOrders(queryString);
  }

  resetBtn.addEventListener("click", () => {
    searchInput.value = "";
    filterStatus.value = "";
    loadOrders();
  });

} // end of if(orderForm) block
