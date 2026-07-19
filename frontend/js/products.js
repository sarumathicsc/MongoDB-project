/* =====================================================
   products.js
   Handles everything on products.html:
    - Loading and rendering the product table
    - Loading dashboard stat cards (via aggregation API)
    - Add / Edit / Delete products
    - Search & filter (by keyword, category, stock status)
    - Form validation
   Depends on common.js being loaded first (API_BASE, showToast, escapeHtml).
   ===================================================== */

const PRODUCTS_API = `${API_BASE}/products`;

const productForm = document.getElementById("productForm");

// Only run this file's logic if we're actually on products.html
// (products.html is the only page with a #productForm element).
if (productForm) {

  const tableBody = document.getElementById("productTableBody");
  const formTitle = document.getElementById("formTitle");
  const submitBtn = document.getElementById("submitBtn");
  const cancelEditBtn = document.getElementById("cancelEditBtn");
  const mongoIdField = document.getElementById("mongoId");

  const searchInput = document.getElementById("searchInput");
  const filterCategory = document.getElementById("filterCategory");
  const filterStockStatus = document.getElementById("filterStockStatus");
  const searchBtn = document.getElementById("searchBtn");
  const resetBtn = document.getElementById("resetBtn");

  const deleteModal = document.getElementById("deleteModal");
  const deleteProductName = document.getElementById("deleteProductName");
  const confirmDeleteBtn = document.getElementById("confirmDeleteBtn");
  const cancelDeleteBtn = document.getElementById("cancelDeleteBtn");

  let productPendingDeleteId = null;

  /* -----------------------------------------------------
     LOAD DATA WHEN PAGE OPENS
  ----------------------------------------------------- */
  window.addEventListener("DOMContentLoaded", () => {
    loadProducts();
    loadStats();
  });

  /* -----------------------------------------------------
     FETCH & RENDER PRODUCTS
  ----------------------------------------------------- */
  async function loadProducts(queryParams = "") {
    tableBody.innerHTML = `<tr><td colspan="9" class="loading-row">Loading products...</td></tr>`;
    try {
      const response = await fetch(`${PRODUCTS_API}${queryParams}`);
      if (!response.ok) throw new Error("Failed to fetch products");
      const products = await response.json();
      renderTable(products);
    } catch (err) {
      tableBody.innerHTML = `<tr><td colspan="9" class="empty-row">
        Could not load data. Is the Java backend running on port 8080? (${err.message})
      </td></tr>`;
    }
  }

  function renderTable(products) {
    if (!products || products.length === 0) {
      tableBody.innerHTML = `<tr><td colspan="9" class="empty-row">No products found.</td></tr>`;
      return;
    }

    tableBody.innerHTML = products.map(p => {
      const statusInfo = getStockStatus(p.stockQuantity, p.reorderLevel);
      return `
        <tr>
          <td>${escapeHtml(p.productId)}</td>
          <td>${escapeHtml(p.name)}</td>
          <td>${escapeHtml(p.category)}</td>
          <td>${formatCurrency(p.price)}</td>
          <td>${p.stockQuantity}</td>
          <td>${p.reorderLevel}</td>
          <td>${escapeHtml(p.supplier)}</td>
          <td><span class="badge ${statusInfo.className}">${statusInfo.label}</span></td>
          <td class="action-buttons">
            <button class="btn btn-secondary btn-small" onclick="editProduct('${p._id}')">
              <i class="fa-solid fa-pen"></i>
            </button>
            <button class="btn btn-danger btn-small" onclick="askDeleteProduct('${p._id}', '${escapeHtml(p.name)}')">
              <i class="fa-solid fa-trash"></i>
            </button>
          </td>
        </tr>
      `;
    }).join("");
  }

  // Determines the stock badge (In Stock / Low Stock / Out of Stock)
  // by comparing current stock against the reorder level.
  function getStockStatus(stock, reorderLevel) {
    if (stock <= 0) return { label: "Out of Stock", className: "badge-out-stock" };
    if (stock <= reorderLevel) return { label: "Low Stock", className: "badge-low-stock" };
    return { label: "In Stock", className: "badge-in-stock" };
  }

  /* -----------------------------------------------------
     LOAD DASHBOARD STATS (Aggregation API)
  ----------------------------------------------------- */
  async function loadStats() {
    try {
      const response = await fetch(`${PRODUCTS_API}/stats`);
      if (!response.ok) throw new Error("Failed to fetch stats");
      const stats = await response.json();

      document.getElementById("statTotalProducts").textContent = stats.totalProducts ?? 0;
      document.getElementById("statStockValue").textContent = formatCurrency(stats.totalStockValue ?? 0);
      document.getElementById("statLowStock").textContent = stats.lowStockCount ?? 0;
      document.getElementById("statCategories").textContent = stats.categoryCount ?? 0;
    } catch (err) {
      console.error("Could not load product stats:", err);
    }
  }

  /* -----------------------------------------------------
     FORM VALIDATION
  ----------------------------------------------------- */
  function validateForm(data) {
    let isValid = true;
    clearErrors();

    if (!data.productId.trim()) { showError("productId", "Product ID is required."); isValid = false; }
    if (!data.name.trim()) { showError("name", "Product name is required."); isValid = false; }
    if (!data.category) { showError("category", "Please select a category."); isValid = false; }
    if (data.price === "" || data.price < 0) { showError("price", "Enter a valid price."); isValid = false; }
    if (data.stockQuantity === "" || data.stockQuantity < 0) { showError("stockQuantity", "Enter a valid stock quantity."); isValid = false; }
    if (data.reorderLevel === "" || data.reorderLevel < 0) { showError("reorderLevel", "Enter a valid reorder level."); isValid = false; }
    if (!data.supplier.trim()) { showError("supplier", "Supplier is required."); isValid = false; }
    if (!data.description.trim()) { showError("description", "Description is required."); isValid = false; }

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
     FORM SUBMIT: ADD or UPDATE
  ----------------------------------------------------- */
  productForm.addEventListener("submit", async (e) => {
    e.preventDefault();

    const rawData = {
      productId: document.getElementById("productId").value,
      name: document.getElementById("name").value,
      category: document.getElementById("category").value,
      price: document.getElementById("price").value,
      stockQuantity: document.getElementById("stockQuantity").value,
      reorderLevel: document.getElementById("reorderLevel").value,
      supplier: document.getElementById("supplier").value,
      description: document.getElementById("description").value
    };

    if (!validateForm(rawData)) {
      showToast("Please fix the errors in the form.", "error");
      return;
    }

    const data = {
      ...rawData,
      price: parseFloat(rawData.price),
      stockQuantity: parseInt(rawData.stockQuantity, 10),
      reorderLevel: parseInt(rawData.reorderLevel, 10)
    };

    const existingId = mongoIdField.value;

    try {
      let response;
      if (existingId) {
        response = await fetch(`${PRODUCTS_API}/${existingId}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data)
        });
      } else {
        response = await fetch(PRODUCTS_API, {
          method: "POST",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify(data)
        });
      }

      if (!response.ok) {
        const errText = await response.text();
        throw new Error(errText || "Server error");
      }

      showToast(existingId ? "Product updated successfully!" : "Product added successfully!", "success");
      resetForm();
      loadProducts();
      loadStats();

    } catch (err) {
      showToast("Error: " + err.message, "error");
    }
  });

  /* -----------------------------------------------------
     EDIT PRODUCT
  ----------------------------------------------------- */
  window.editProduct = async function (id) {
    try {
      const response = await fetch(`${PRODUCTS_API}/${id}`);
      if (!response.ok) throw new Error("Product not found");
      const p = await response.json();

      mongoIdField.value = p._id;
      document.getElementById("productId").value = p.productId;
      document.getElementById("name").value = p.name;
      document.getElementById("category").value = p.category;
      document.getElementById("price").value = p.price;
      document.getElementById("stockQuantity").value = p.stockQuantity;
      document.getElementById("reorderLevel").value = p.reorderLevel;
      document.getElementById("supplier").value = p.supplier;
      document.getElementById("description").value = p.description;

      formTitle.textContent = "Edit Product";
      submitBtn.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Update Product';
      cancelEditBtn.style.display = "inline-flex";

      document.getElementById("add-section").scrollIntoView({ behavior: "smooth" });
    } catch (err) {
      showToast("Error loading product: " + err.message, "error");
    }
  };

  cancelEditBtn.addEventListener("click", resetForm);

  function resetForm() {
    productForm.reset();
    mongoIdField.value = "";
    formTitle.textContent = "Add New Product";
    submitBtn.innerHTML = '<i class="fa-solid fa-floppy-disk"></i> Save Product';
    cancelEditBtn.style.display = "none";
    clearErrors();
  }

  /* -----------------------------------------------------
     DELETE PRODUCT (with confirmation modal)
  ----------------------------------------------------- */
  window.askDeleteProduct = function (id, name) {
    productPendingDeleteId = id;
    deleteProductName.textContent = name;
    deleteModal.classList.add("active");
  };

  cancelDeleteBtn.addEventListener("click", () => {
    deleteModal.classList.remove("active");
    productPendingDeleteId = null;
  });

  confirmDeleteBtn.addEventListener("click", async () => {
    if (!productPendingDeleteId) return;
    try {
      const response = await fetch(`${PRODUCTS_API}/${productPendingDeleteId}`, { method: "DELETE" });
      if (!response.ok) throw new Error("Failed to delete product");

      showToast("Product deleted successfully!", "success");
      deleteModal.classList.remove("active");
      productPendingDeleteId = null;
      loadProducts();
      loadStats();
    } catch (err) {
      showToast("Error: " + err.message, "error");
    }
  });

  /* -----------------------------------------------------
     SEARCH & FILTER
  ----------------------------------------------------- */
  searchBtn.addEventListener("click", performSearch);
  searchInput.addEventListener("keyup", (e) => { if (e.key === "Enter") performSearch(); });

  function performSearch() {
    const keyword = searchInput.value.trim();
    const category = filterCategory.value;
    const stockStatus = filterStockStatus.value;

    const params = new URLSearchParams();
    if (keyword) params.append("query", keyword);
    if (category) params.append("category", category);
    if (stockStatus) params.append("stockStatus", stockStatus);

    const queryString = params.toString() ? `/search?${params.toString()}` : "";
    loadProducts(queryString);
  }

  resetBtn.addEventListener("click", () => {
    searchInput.value = "";
    filterCategory.value = "";
    filterStockStatus.value = "";
    loadProducts();
  });

} // end of if(productForm) block
