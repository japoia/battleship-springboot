let state = null;
let orientation = "H";
let showComputerBoardFull = false;

const el = (id) => document.getElementById(id);

async function api(path, method = "GET", body = null) {
  const opts = { method, headers: {}, credentials: 'same-origin' };
  if (body !== null) {
    opts.headers["Content-Type"] = "application/json";
    opts.body = JSON.stringify(body);
  }
  const res = await fetch(path, opts);
  const text = await res.text();
  let data = null;
  try { data = text ? JSON.parse(text) : null; } catch { /* ignore */ }
  if (!res.ok) {
    const msg = (data && data.message) ? data.message : (text || `HTTP ${res.status}`);
    console.error(`API Error: ${msg}`);
    throw new Error(msg);
  }
  return data;
}

function cellClass(v) {
  switch (v) {
    case "WATER": return "water";
    case "SHIP": return "ship";
    case "UNKNOWN": return "unknown";
    case "MISS": return "miss";
    case "HIT": return "hit";
    case "SUNK": return "sunk";
    default: return "unknown";
  }
}

function inBounds(x, y) {
  return x >= 0 && x < 10 && y >= 0 && y < 10;
}

function footprint(x, y, length, ori) {
  const cells = [];
  for (let i = 0; i < length; i++) {
    const cx = (ori === "H") ? (x + i) : x;
    const cy = (ori === "V") ? (y + i) : y;
    cells.push([cx, cy]);
  }
  return cells;
}

function canPlaceClientSide(x, y, length, ori, playerBoard) {
  const cells = footprint(x, y, length, ori);
  for (const [cx, cy] of cells) {
    if (!inBounds(cx, cy)) return false;
    if (playerBoard[cy][cx] === "SHIP") return false;

    for (let dy = -1; dy <= 1; dy++) {
      for (let dx = -1; dx <= 1; dx++) {
        const nx = cx + dx, ny = cy + dy;
        if (!inBounds(nx, ny)) continue;
        if (playerBoard[ny][nx] === "SHIP") return false;
      }
    }
  }
  return true;
}

function applyPreview(gridEl, x, y, length, ori, ok) {
  const cls = ok ? "preview-ok" : "preview-bad";
  const cells = footprint(x, y, length, ori);
  for (const [cx, cy] of cells) {
    if (!inBounds(cx, cy)) continue;
    const idx = cy * 10 + cx;
    const cellEl = gridEl.children[idx];
    if (cellEl) cellEl.classList.add(cls);
  }
}

function clearPreview(gridEl) {
  for (const c of gridEl.querySelectorAll(".preview-ok, .preview-bad")) {
    c.classList.remove("preview-ok", "preview-bad");
  }
}

function renderGrid(gridEl, board, shotTurns, onClick, onHover = null) {
  console.log("Rendering grid with board:", board);
  console.log("Rendering grid with shotTurns:", shotTurns);
  gridEl.innerHTML = "";
  for (let y = 0; y < 10; y++) {
    for (let x = 0; x < 10; x++) {
      const d = document.createElement("div");
      const cellType = board[y][x];
      d.className = `cell ${cellClass(cellType)}`;
      d.dataset.x = x;
      d.dataset.y = y;
      
      // Add turn number to cell if it was shot
      if (shotTurns && shotTurns[y][x] > 0) {
        d.textContent = shotTurns[y][x];
      } else {
        d.textContent = "";
      }
      
      d.addEventListener("click", () => onClick(x, y));
      if (onHover) {
        d.addEventListener("mouseenter", () => onHover(x, y));
      }
      gridEl.appendChild(d);
    }
  }
  gridEl.onmouseleave = onHover ? (() => clearPreview(gridEl)) : null;
}

function remainingText() {
  const req = state.requiredFleet;
  const placed = state.playerPlacedFleet || {};
  const parts = [];
  for (const len of Object.keys(req).map(Number).sort((a,b)=>b-a)) {
    const r = req[len];
    const p = placed[len] || 0;
    parts.push(`${len}: ${Math.max(0, r - p)} jäänud`);
  }
  return parts.join(" · ");
}

function updateLengthSelect() {
  const sel = el("lenSel");
  sel.innerHTML = "";
  const req = state.requiredFleet;
  const placed = state.playerPlacedFleet || {};
  const lens = Object.keys(req).map(Number).sort((a,b)=>b-a);
  for (const len of lens) {
    const r = req[len];
    const p = placed[len] || 0;
    const rem = r - p;
    const opt = document.createElement("option");
    opt.value = String(len);
    opt.textContent = `${len} (jäänud ${Math.max(0, rem)})`;
    if (rem <= 0) opt.disabled = true;
    sel.appendChild(opt);
  }
  // Ensure selected option is enabled
  if (sel.selectedOptions.length === 0 || sel.selectedOptions[0].disabled) {
    for (const o of sel.options) {
      if (!o.disabled) { sel.value = o.value; break; }
    }
  }
}

function setStatus(msg) {
  el("status").textContent = msg || "";
}

function syncControls() {
  el("oriBtn").textContent = `Suund: ${orientation}`;

  const placing = state.phase === "PLACING";
  const playing = state.phase === "PLAY";
  const finished = state.phase === "FINISHED";

  el("autoBtn").disabled = !placing;
  el("startBtn").disabled = !(placing && state.playerFleetComplete);

  if (finished) {
    setStatus(`Mäng läbi. Võitja: ${state.winner}`);
  }
  el("fleetInfo").textContent = placing
    ? `Paiguta laevad. ${remainingText()}`
    : (playing ? "Tulista arvuti lauda." : `Võitja: ${state.winner}`);
}

function render() {
  if (!state) return;

  console.log("Rendering with state:", state);
  console.log("playerShotTurns:", state.playerShotTurns);
  console.log("computerShotTurns:", state.computerShotTurns);
  updateLengthSelect();
  syncControls();

  const playerGrid = el("playerGrid");
  renderGrid(playerGrid, state.playerBoard, state.playerShotTurns, async (x, y) => {
    if (state.phase !== "PLACING") return;
    const len = Number(el("lenSel").value);
    try {
      state = await api("/api/game/place", "POST", { x, y, length: len, orientation });
      setStatus("Paigutatud.");
      render();
    } catch (e) {
      setStatus(e.message);
    }
  }, (x, y) => {
    if (state.phase !== "PLACING") return;
    clearPreview(playerGrid);
    const len = Number(el("lenSel").value);
    const ok = canPlaceClientSide(x, y, len, orientation, state.playerBoard);
    applyPreview(playerGrid, x, y, len, orientation, ok);
  });

   // Render computer board - use full view if showComputerBoardFull is true
   const boardToRender = showComputerBoardFull ? state.computerBoardFull : state.computerBoard;
     renderGrid(el("computerGrid"), boardToRender, state.computerShotTurns, async (x, y) => {
     if (state.phase !== "PLAY") return;
     try {
       const res = await api("/api/game/fire", "POST", { x, y });
       state = res.state;
       const p = res.playerShot;
       let msg = `Sina: ${p.outcome}`;
       if (p.outcome === "HIT" && p.shipSunk) msg += " (uppus)";
       setStatus(msg);
       render();
       
       // Play sound if game ended
       if (state.winner) {
         playGameEndSound();
       }
       
        // Animate computer shots if there are any
         if (res.computerShots && res.computerShots.length > 0) {
           for (let i = 0; i < res.computerShots.length; i++) {
             const computerShot = res.computerShots[i];
             // Wait for animation delay
             await new Promise(resolve => setTimeout(resolve, 1000));
             // Update status with computer shot information
             let computerMsg = `Sina: ${p.outcome}`;
             computerMsg += ` | Arvuti (${computerShot.x},${computerShot.y}): ${computerShot.result.outcome}`;
             if (computerShot.result.outcome === "HIT" && computerShot.result.shipSunk) {
               computerMsg += " (uppus)";
             }
             setStatus(computerMsg);
             // Update the board to show the computer shot
             // We don't need to refresh the entire state from the server
             // Just update the player's board cell where the computer shot
             if (computerShot.result.outcome === "HIT") {
               state.playerBoard[computerShot.y][computerShot.x] = "HIT";
               if (computerShot.result.shipSunk) {
                 state.playerBoard[computerShot.y][computerShot.x] = "SUNK";
               }
             } else if (computerShot.result.outcome === "MISS") {
               state.playerBoard[computerShot.y][computerShot.x] = "MISS";
             }
             // Update shot turn number
             state.playerShotTurns[computerShot.y][computerShot.x] = res.state.turnNumber;
             render();
            
            // Play sound if game ended after this computer shot
            if (state.winner) {
              playGameEndSound();
              break; // No more shots after game ends
            }
          }
        }
     } catch (e) {
       setStatus(e.message);
     }
   });
}

// Game end sound effect
function playGameEndSound() {
  // Create audio context
  const audioContext = new (window.AudioContext || window.webkitAudioContext)();
  
  // Create oscillator for the sound
  const oscillator = audioContext.createOscillator();
  const gainNode = audioContext.createGain();
  
  oscillator.connect(gainNode);
  gainNode.connect(audioContext.destination);
  
  // Configure the sound
  oscillator.frequency.setValueAtTime(523.25, audioContext.currentTime); // C5
  oscillator.frequency.exponentialRampToValueAtTime(1046.50, audioContext.currentTime + 0.2); // C6
  
  gainNode.gain.setValueAtTime(0.3, audioContext.currentTime);
  gainNode.gain.exponentialRampToValueAtTime(0.01, audioContext.currentTime + 0.5);
  
  oscillator.start(audioContext.currentTime);
  oscillator.stop(audioContext.currentTime + 0.5);
}

async function init() {
  console.log("DOM ready");
  // Wait for DOM to be fully loaded
  if (document.readyState !== "complete") {
    await new Promise((resolve) => window.addEventListener("load", resolve));
  }
  console.log("Initializing app...");
  console.log("newBtn element:", el("newBtn"));
  
  el("newBtn").addEventListener("click", async () => {
    console.log("newBtn clicked");
    try {
      state = await api("/api/game/new", "POST");
      console.log("New game state:", state);
      setStatus("Uus mäng alustatud.");
      render();
    } catch (e) {
      console.error("Error starting new game:", e);
      setStatus(e.message);
    }
  });

  console.log("autoBtn element:", el("autoBtn"));
  el("autoBtn").addEventListener("click", async (event) => {
    console.log("autoBtn clicked event:", event);
    try {
      state = await api("/api/game/auto-place", "POST");
      console.log("Auto-place state:", state);
      setStatus("Paigutasin ülejäänud automaatselt.");
      render();
    } catch (e) {
      console.error("Error auto-placing ships:", e);
      setStatus(e.message);
    }
  });

  el("startBtn").addEventListener("click", async () => {
    try {
      state = await api("/api/game/start", "POST");
      setStatus("Mäng algas. Tulista arvuti lauda!");
      render();
    } catch (e) {
      setStatus(e.message);
    }
  });

  el("oriBtn").addEventListener("click", () => {
    orientation = (orientation === "H") ? "V" : "H";
    syncControls();
  });

  // Add global function for onclick
  window.toggleComputerBoard = () => {
    showComputerBoardFull = !showComputerBoardFull;
    const toggleBtn = el("toggleBtn");
    toggleBtn.textContent = showComputerBoardFull ? "Sule" : "Ava";
    render();
  };

document.addEventListener("keydown", (e) => {
    if (e.key === "Shift" && !e.repeat) {
      orientation = (orientation === "H") ? "V" : "H";
      syncControls();
    }
  });

  try {
    state = await api("/api/game/new", "POST");
    render();
  } catch (e) {
    setStatus(e.message);
  }
}

init();

