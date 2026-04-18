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
      if (res.status === 204) return null;
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

  function clearStoredWallet() {
    localStorage.removeItem('walletId');
    localStorage.removeItem('walletNickname');
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
        wsConnect();
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
        clearStoredWallet();
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
          '<div style="font-size:0.8rem;color:var(--text-secondary)">Active Bets</div>' +
          '<div class="text--mono" style="font-size:1.1rem" id="active-bet-count">—</div>' +
        '</div>' +
        '<div>' +
          '<div style="font-size:0.8rem;color:var(--text-secondary)">Wallet ID</div>' +
          '<div class="text--mono" style="font-size:0.8rem;color:var(--text-muted)">' + shortId + '</div>' +
        '</div>' +
        '<div>' +
          '<button class="btn btn--primary btn--sm" id="deposit-toggle-btn">Deposit</button>' +
        '</div>' +
      '</div>';

    var depositBtn = document.getElementById('deposit-toggle-btn');
    depositBtn.addEventListener('click', function () {
      var section = document.getElementById('deposit-section');
      section.style.display = section.style.display === 'none' ? '' : 'none';
      document.getElementById('deposit-amount').focus();
    });

    loadActiveBetCount(wallet.id);
  }

  function loadActiveBetCount(walletId) {
    api('GET', '/api/wallets/' + walletId + '/bets?status=OPEN&page=0&size=1')
      .then(function (page) {
        var countEl = document.getElementById('active-bet-count');
        if (countEl) {
          countEl.textContent = page.totalElements || 0;
        }
      })
      .catch(function () {
        var countEl = document.getElementById('active-bet-count');
        if (countEl) countEl.textContent = '0';
      });
  }

  // --- Deposit ---

  var depositForm = document.getElementById('deposit-form');
  var depositError = document.getElementById('deposit-error');
  var depositCancel = document.getElementById('deposit-cancel');

  depositForm.addEventListener('submit', function (e) {
    e.preventDefault();
    depositError.textContent = '';

    var stored = getStoredWallet();
    if (!stored) return;

    var amount = parseFloat(document.getElementById('deposit-amount').value);
    if (!amount || amount <= 0) {
      depositError.textContent = 'Amount must be greater than 0.';
      return;
    }

    var submitBtn = depositForm.querySelector('button[type="submit"]');
    submitBtn.disabled = true;

    api('POST', '/api/wallets/' + stored.id + '/deposit', { amount: amount })
      .then(function (wallet) {
        updateWalletIndicator(wallet.nickname || stored.nickname, wallet.balance);
        renderWalletCard(wallet);
        document.getElementById('deposit-section').style.display = 'none';
        document.getElementById('deposit-amount').value = '';
        showToast('Deposited $' + amount.toFixed(2), 'success');
      })
      .catch(function (err) {
        depositError.textContent = err.message;
      })
      .finally(function () {
        submitBtn.disabled = false;
      });
  });

  depositCancel.addEventListener('click', function () {
    document.getElementById('deposit-section').style.display = 'none';
    document.getElementById('deposit-amount').value = '';
    depositError.textContent = '';
  });

  // --- WebSocket Connection Manager ---

  var stompClient = null;
  var wsReconnectTimer = null;
  var wsSubscriptions = {};
  var connStatusEl = document.getElementById('conn-status');

  function setConnStatus(status) {
    connStatusEl.className = 'conn-status conn-status--' + status;
    var labels = { connected: 'Connected', connecting: 'Connecting...', disconnected: 'Disconnected' };
    connStatusEl.title = labels[status] || status;
  }

  function wsConnect() {
    var stored = getStoredWallet();
    if (!stored) return;

    if (stompClient && stompClient.connected) return;

    setConnStatus('connecting');

    var socket = new SockJS('/ws');
    stompClient = new StompJs.Client({
      webSocketFactory: function () { return socket; },
      reconnectDelay: 5000,
      onConnect: function () {
        setConnStatus('connected');
        // Re-subscribe all registered subscriptions
        Object.keys(wsSubscriptions).forEach(function (topic) {
          var cb = wsSubscriptions[topic].callback;
          wsSubscriptions[topic].sub = stompClient.subscribe(topic, function (msg) {
            cb(JSON.parse(msg.body));
          });
        });
        // Subscribe to price feed
        subscribeToPrices();
      },
      onStompError: function () {
        setConnStatus('disconnected');
      },
      onWebSocketClose: function () {
        setConnStatus('disconnected');
      }
    });

    stompClient.activate();
  }

  function wsDisconnect() {
    if (stompClient) {
      stompClient.deactivate();
      stompClient = null;
    }
    wsSubscriptions = {};
    setConnStatus('disconnected');
  }

  function wsSubscribe(topic, callback) {
    wsSubscriptions[topic] = { callback: callback, sub: null };
    if (stompClient && stompClient.connected) {
      wsSubscriptions[topic].sub = stompClient.subscribe(topic, function (msg) {
        callback(JSON.parse(msg.body));
      });
    }
  }

  function wsUnsubscribe(topic) {
    if (wsSubscriptions[topic] && wsSubscriptions[topic].sub) {
      wsSubscriptions[topic].sub.unsubscribe();
    }
    delete wsSubscriptions[topic];
  }

  // --- Live Price Ticker ---

  var priceHistory = {}; // { symbol: [price1, price2, ...] }
  var lastPrices = {};   // { symbol: lastPrice }
  var MAX_HISTORY = 50;

  function handlePriceUpdate(data) {
    var symbol = data.symbol;
    var price = parseFloat(data.price);
    if (!symbol || isNaN(price)) return;

    var prev = lastPrices[symbol];
    lastPrices[symbol] = price;

    if (!priceHistory[symbol]) priceHistory[symbol] = [];
    priceHistory[symbol].push(price);
    if (priceHistory[symbol].length > MAX_HISTORY) {
      priceHistory[symbol].shift();
    }

    renderPriceCard(symbol, price, prev);
  }

  function renderPriceCard(symbol, price, prevPrice) {
    var container = document.getElementById('price-ticker');
    var cardId = 'price-card-' + symbol;
    var card = document.getElementById(cardId);

    if (!card) {
      // Clear placeholder text on first card
      if (!container.querySelector('.price-grid')) {
        container.innerHTML = '<div class="price-grid" id="price-grid"></div>';
      }
      var grid = document.getElementById('price-grid');
      card = document.createElement('div');
      card.className = 'price-card';
      card.id = cardId;
      card.innerHTML =
        '<div class="price-card__header">' +
          '<span class="price-card__symbol">' + escapeHtml(symbol) + '</span>' +
          '<span class="price-card__arrow" id="arrow-' + symbol + '"></span>' +
        '</div>' +
        '<div class="price-card__price" id="price-' + symbol + '"></div>' +
        '<canvas class="price-card__sparkline" id="spark-' + symbol + '"></canvas>';
      grid.appendChild(card);
    }

    // Update price with flash
    var priceEl = document.getElementById('price-' + symbol);
    var formatted = formatCryptoPrice(price);
    priceEl.textContent = '$' + formatted;

    // Flash animation
    priceEl.classList.remove('price-flash-up', 'price-flash-down');
    if (prevPrice !== undefined) {
      if (price > prevPrice) {
        priceEl.classList.add('price-flash-up');
      } else if (price < prevPrice) {
        priceEl.classList.add('price-flash-down');
      }
    }

    // Arrow
    var arrowEl = document.getElementById('arrow-' + symbol);
    if (prevPrice !== undefined) {
      if (price > prevPrice) {
        arrowEl.textContent = '\u25B2';
        arrowEl.style.color = 'var(--color-up)';
      } else if (price < prevPrice) {
        arrowEl.textContent = '\u25BC';
        arrowEl.style.color = 'var(--color-down)';
      } else {
        arrowEl.textContent = '\u25C6';
        arrowEl.style.color = 'var(--text-muted)';
      }
    }

    // Sparkline
    drawSparkline(symbol);
  }

  function formatCryptoPrice(price) {
    if (price >= 1) {
      return price.toFixed(2);
    }
    return price.toFixed(8);
  }

  function drawSparkline(symbol) {
    var canvas = document.getElementById('spark-' + symbol);
    if (!canvas) return;
    var history = priceHistory[symbol] || [];
    if (history.length < 2) return;

    var ctx = canvas.getContext('2d');
    var w = canvas.offsetWidth;
    var h = canvas.offsetHeight;
    canvas.width = w * 2;
    canvas.height = h * 2;
    ctx.scale(2, 2);

    var min = Math.min.apply(null, history);
    var max = Math.max.apply(null, history);
    var range = max - min || 1;
    var pad = 2;

    ctx.clearRect(0, 0, w, h);
    ctx.beginPath();
    ctx.strokeStyle = history[history.length - 1] >= history[0] ? 'var(--color-up)' : 'var(--color-down)';
    // Canvas doesn't support CSS vars, use computed style
    var styles = getComputedStyle(document.documentElement);
    ctx.strokeStyle = history[history.length - 1] >= history[0]
      ? styles.getPropertyValue('--color-up').trim()
      : styles.getPropertyValue('--color-down').trim();
    ctx.lineWidth = 1.5;
    ctx.lineJoin = 'round';

    for (var i = 0; i < history.length; i++) {
      var x = (i / (history.length - 1)) * (w - pad * 2) + pad;
      var y = h - pad - ((history[i] - min) / range) * (h - pad * 2);
      if (i === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    }
    ctx.stroke();
  }

  function subscribeToPrices() {
    wsSubscribe('/topic/prices', handlePriceUpdate);
  }

  // --- Bet Placement Form ---

  var betSymbolEl = document.getElementById('bet-symbol');
  var betAmountEl = document.getElementById('bet-amount');
  var betInfoEl = document.getElementById('bet-info');
  var betOddsEl = document.getElementById('bet-odds');
  var betExposureEl = document.getElementById('bet-exposure');
  var betPayoutEl = document.getElementById('bet-payout');
  var betSubmitBtn = document.getElementById('bet-submit-btn');
  var betFormError = document.getElementById('bet-form-error');
  var betDirBtns = document.querySelectorAll('.bet-dir-btn');
  var selectedDirection = null;
  var currentOdds = null;

  betDirBtns.forEach(function (btn) {
    btn.addEventListener('click', function () {
      selectedDirection = this.getAttribute('data-dir');
      betDirBtns.forEach(function (b) { b.classList.remove('bet-dir-btn--active'); });
      this.classList.add('bet-dir-btn--active');
      fetchBetInfo();
    });
  });

  betSymbolEl.addEventListener('change', function () {
    fetchBetInfo();
  });

  betAmountEl.addEventListener('input', function () {
    updateEstPayout();
    validateBetForm();
  });

  function fetchBetInfo() {
    var symbol = betSymbolEl.value;
    var dir = selectedDirection;
    if (!symbol || !dir) {
      betInfoEl.style.display = 'none';
      betSubmitBtn.disabled = true;
      return;
    }

    betInfoEl.style.display = '';

    // Fetch odds and exposure in parallel
    api('GET', '/api/odds?symbol=' + symbol + '&direction=' + dir)
      .then(function (data) {
        currentOdds = parseFloat(data.odds);
        betOddsEl.textContent = currentOdds.toFixed(4);
        updateEstPayout();
        validateBetForm();
      })
      .catch(function () {
        betOddsEl.textContent = 'Error';
        currentOdds = null;
      });

    api('GET', '/api/exposure/' + symbol)
      .then(function (data) {
        betExposureEl.textContent = '$' + Number(data.exposure).toFixed(2);
      })
      .catch(function () {
        betExposureEl.textContent = '—';
      });
  }

  function updateEstPayout() {
    var amount = parseFloat(betAmountEl.value);
    if (currentOdds && amount > 0) {
      betPayoutEl.textContent = '$' + (amount * currentOdds).toFixed(2);
    } else {
      betPayoutEl.textContent = '—';
    }
  }

  function validateBetForm() {
    var symbol = betSymbolEl.value;
    var amount = parseFloat(betAmountEl.value);
    betSubmitBtn.disabled = !symbol || !selectedDirection || !currentOdds || !amount || amount < 1;
  }

  var betConfirmModal = document.getElementById('bet-confirm-modal');
  var betConfirmDetails = document.getElementById('bet-confirm-details');
  var betConfirmBtn = document.getElementById('bet-confirm-btn');
  var betCancelBtn = document.getElementById('bet-cancel-btn');
  var pendingBet = null;

  betSubmitBtn.addEventListener('click', function () {
    var stored = getStoredWallet();
    if (!stored) return;

    var symbol = betSymbolEl.value;
    var amount = parseFloat(betAmountEl.value);
    if (!symbol || !selectedDirection || !amount || amount < 1) return;

    pendingBet = {
      walletId: stored.id,
      symbol: symbol,
      direction: selectedDirection,
      amount: amount
    };

    var payout = currentOdds ? (amount * currentOdds).toFixed(2) : '—';
    betConfirmDetails.innerHTML =
      '<div class="bet-confirm__row"><span class="text--muted">Symbol</span><span class="text--mono">' + escapeHtml(symbol) + '</span></div>' +
      '<div class="bet-confirm__row"><span class="text--muted">Direction</span><span class="' + (selectedDirection === 'UP' ? 'text--up' : 'text--down') + '">' + selectedDirection + '</span></div>' +
      '<div class="bet-confirm__row"><span class="text--muted">Amount</span><span class="text--mono">$' + amount.toFixed(2) + '</span></div>' +
      '<div class="bet-confirm__row"><span class="text--muted">Odds</span><span class="text--mono">' + (currentOdds ? currentOdds.toFixed(4) : '—') + '</span></div>' +
      '<div class="bet-confirm__row"><span class="text--muted">Est. Payout</span><span class="text--mono text--up">$' + payout + '</span></div>';

    betConfirmModal.style.display = '';
  });

  betCancelBtn.addEventListener('click', function () {
    betConfirmModal.style.display = 'none';
    pendingBet = null;
  });

  betConfirmBtn.addEventListener('click', function () {
    if (!pendingBet) return;

    betConfirmBtn.disabled = true;
    betFormError.textContent = '';

    api('POST', '/api/bets', pendingBet)
      .then(function () {
        showToast('Bet placed! ' + pendingBet.direction + ' ' + pendingBet.symbol + ' for $' + pendingBet.amount.toFixed(2), 'success');
        betConfirmModal.style.display = 'none';
        betAmountEl.value = '';
        currentOdds = null;
        betInfoEl.style.display = 'none';
        betDirBtns.forEach(function (b) { b.classList.remove('bet-dir-btn--active'); });
        selectedDirection = null;
        betSubmitBtn.disabled = true;
        pendingBet = null;
        loadWalletDetails();
      })
      .catch(function (err) {
        betConfirmModal.style.display = 'none';
        betFormError.textContent = err.message;
        pendingBet = null;
      })
      .finally(function () {
        betConfirmBtn.disabled = false;
        validateBetForm();
      });
  });

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
    clearStoredWallet: clearStoredWallet,
    updateWalletIndicator: updateWalletIndicator,
    loadWalletDetails: loadWalletDetails,
    loadActiveBetCount: loadActiveBetCount,
    showWalletModal: showWalletModal,
    escapeHtml: escapeHtml,
    handlePriceUpdate: handlePriceUpdate,
    lastPrices: lastPrices,
    priceHistory: priceHistory,
    wsConnect: wsConnect,
    wsDisconnect: wsDisconnect,
    wsSubscribe: wsSubscribe,
    wsUnsubscribe: wsUnsubscribe
  };

  // --- Init ---

  function init() {
    var stored = getStoredWallet();
    if (!stored) {
      showWalletModal();
    } else {
      updateWalletIndicator(stored.nickname);
      loadWalletDetails();
      wsConnect();
    }
  }

  init();
})();
