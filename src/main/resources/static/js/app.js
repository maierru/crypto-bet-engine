/* ========================================
   Crypto Bet Engine — App
   ======================================== */

(function () {
  'use strict';

  // --- Navigation ---

  var navLinks = document.querySelectorAll('.sidebar__link[data-view]');
  var views = document.querySelectorAll('.view');

  function switchView(viewName) {
    views.forEach(function (v) {
      v.classList.remove('view--active');
    });
    navLinks.forEach(function (link) {
      link.classList.remove('sidebar__link--active');
    });

    var target = document.getElementById('view-' + viewName);
    if (target) {
      target.classList.add('view--active');
    }

    navLinks.forEach(function (link) {
      if (link.getAttribute('data-view') === viewName) {
        link.classList.add('sidebar__link--active');
      }
    });
  }

  navLinks.forEach(function (link) {
    link.addEventListener('click', function (e) {
      e.preventDefault();
      var view = this.getAttribute('data-view');
      switchView(view);
    });
  });

  // --- Toast Notifications ---

  var toastContainer = document.getElementById('toast-container');

  function showToast(message, type) {
    type = type || 'info';
    var toast = document.createElement('div');
    toast.className = 'toast toast--' + type;
    toast.textContent = message;
    toastContainer.appendChild(toast);

    setTimeout(function () {
      toast.classList.add('toast--leaving');
      toast.addEventListener('animationend', function () {
        toast.remove();
      });
    }, 5000);
  }

  // --- API Client ---

  var API_BASE = '';

  function api(method, path, body) {
    var opts = {
      method: method,
      headers: { 'Content-Type': 'application/json' }
    };
    if (body) {
      opts.body = JSON.stringify(body);
    }
    return fetch(API_BASE + path, opts).then(function (res) {
      if (!res.ok) {
        return res.json().then(function (err) {
          var msg = (err && err.message) || 'Request failed';
          throw new Error(msg);
        });
      }
      return res.json();
    });
  }

  // --- Wallet State ---

  function getStoredWallet() {
    var id = localStorage.getItem('walletId');
    var name = localStorage.getItem('walletNickname');
    if (id && name) {
      return { id: id, nickname: name };
    }
    return null;
  }

  function storeWallet(id, nickname) {
    localStorage.setItem('walletId', id);
    localStorage.setItem('walletNickname', nickname);
  }

  function updateWalletIndicator(nickname, balance) {
    var nameEl = document.getElementById('wallet-name');
    var balanceEl = document.getElementById('wallet-balance');
    nameEl.textContent = nickname || 'No Wallet';
    if (balance !== undefined && balance !== null) {
      balanceEl.textContent = '$' + Number(balance).toFixed(2);
    } else {
      balanceEl.textContent = '';
    }
  }

  // --- Wallet Modal ---

  var walletModal = document.getElementById('wallet-modal');
  var walletForm = document.getElementById('create-wallet-form');
  var walletFormError = document.getElementById('wallet-form-error');

  function showWalletModal() {
    walletModal.style.display = '';
  }

  function hideWalletModal() {
    walletModal.style.display = 'none';
  }

  walletForm.addEventListener('submit', function (e) {
    e.preventDefault();
    walletFormError.textContent = '';

    var nickname = document.getElementById('wallet-nickname').value.trim();
    var balance = parseFloat(document.getElementById('wallet-initial-balance').value);

    if (nickname.length < 2) {
      walletFormError.textContent = 'Nickname must be at least 2 characters.';
      return;
    }
    if (!balance || balance <= 0) {
      walletFormError.textContent = 'Balance must be greater than 0.';
      return;
    }

    var submitBtn = walletForm.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    api('POST', '/api/wallets', { nickname: nickname, initialBalance: balance })
      .then(function (wallet) {
        storeWallet(wallet.id, wallet.nickname || nickname);
        updateWalletIndicator(wallet.nickname || nickname, wallet.balance);
        hideWalletModal();
        showToast('Wallet created!', 'success');
        loadWalletDetails();
      })
      .catch(function (err) {
        walletFormError.textContent = err.message;
      })
      .finally(function () {
        submitBtn.disabled = false;
      });
  });

  // --- Wallet Details ---

  function loadWalletDetails() {
    var stored = getStoredWallet();
    if (!stored) return;

    api('GET', '/api/wallets/' + stored.id)
      .then(function (wallet) {
        updateWalletIndicator(wallet.nickname || stored.nickname, wallet.balance);
        renderWalletCard(wallet);
      })
      .catch(function () {
        localStorage.removeItem('walletId');
        localStorage.removeItem('walletNickname');
        updateWalletIndicator(null);
        showWalletModal();
      });
  }

  function renderWalletCard(wallet) {
    var container = document.getElementById('wallet-details');
    var shortId = wallet.id.substring(0, 8) + '...';
    container.innerHTML =
      '<div style="display:flex;justify-content:space-between;align-items:center;flex-wrap:wrap;gap:12px">' +
        '<div>' +
          '<div style="font-size:0.8rem;color:var(--text-secondary)">Nickname</div>' +
          '<div style="font-size:1.1rem;font-weight:600">' + escapeHtml(wallet.nickname || 'Unnamed') + '</div>' +
        '</div>' +
        '<div>' +
          '<div style="font-size:0.8rem;color:var(--text-secondary)">Balance</div>' +
          '<div class="text--mono" style="font-size:1.3rem;color:var(--color-accent)">$' + Number(wallet.balance).toFixed(2) + '</div>' +
        '</div>' +
        '<div>' +
          '<div style="font-size:0.8rem;color:var(--text-secondary)">Wallet ID</div>' +
          '<div class="text--mono" style="font-size:0.8rem;color:var(--text-muted)">' + shortId + '</div>' +
        '</div>' +
      '</div>';
  }

  // --- Utility ---

  function escapeHtml(str) {
    var div = document.createElement('div');
    div.textContent = str;
    return div.innerHTML;
  }

  // --- Expose globals for future modules ---

  window.CryptoBet = {
    api: api,
    showToast: showToast,
    switchView: switchView,
    getStoredWallet: getStoredWallet,
    storeWallet: storeWallet,
    updateWalletIndicator: updateWalletIndicator,
    loadWalletDetails: loadWalletDetails,
    showWalletModal: showWalletModal,
    escapeHtml: escapeHtml
  };

  // --- Init ---

  function init() {
    var stored = getStoredWallet();
    if (!stored) {
      showWalletModal();
    } else {
      updateWalletIndicator(stored.nickname);
      loadWalletDetails();
    }
  }

  init();
})();
