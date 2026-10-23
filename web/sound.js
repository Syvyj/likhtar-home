// Small synthesised sounds (Web Audio, no files): a wooden tick when the TV changes channel,
// a warm two-note chime for the lantern and when the before/after finishes. Only ever after the
// visitor's own action; one toggle in the header, remembered in this browser.
(function () {
  "use strict";
  var KEY = "likhtar-sound";
  var ctx = null;
  var enabled = true;
  try { enabled = localStorage.getItem(KEY) !== "off"; } catch (e) { /* private mode: keep default */ }
  var toggle = document.querySelector("[data-sound-toggle]");

  function reflect() { if (toggle) toggle.setAttribute("aria-pressed", String(enabled)); }

  function audio() {
    if (!enabled) return null;
    var AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return null;
    if (!ctx) ctx = new AC();
    if (ctx.state === "suspended") ctx.resume();
    return ctx;
  }

  // One soft partial with a quick attack and an exponential tail.
  function tone(c, freq, start, length, gain, type) {
    var osc = c.createOscillator();
    var amp = c.createGain();
    osc.type = type || "sine";
    osc.frequency.setValueAtTime(freq, start);
    amp.gain.setValueAtTime(0.0001, start);
    amp.gain.exponentialRampToValueAtTime(gain, start + 0.012);
    amp.gain.exponentialRampToValueAtTime(0.0001, start + length);
    osc.connect(amp).connect(c.destination);
    osc.start(start);
    osc.stop(start + length + 0.05);
  }

  var api = {
    // Remote click: a short knock of filtered noise plus a low body.
    tick: function () {
      var c = audio(); if (!c) return;
      var t = c.currentTime;
      var len = Math.floor(c.sampleRate * 0.04);
      var buffer = c.createBuffer(1, len, c.sampleRate);
      var data = buffer.getChannelData(0);
      for (var i = 0; i < len; i++) data[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / len, 3);
      var noise = c.createBufferSource();
      var band = c.createBiquadFilter();
      var amp = c.createGain();
      noise.buffer = buffer;
      band.type = "bandpass"; band.frequency.value = 1800; band.Q.value = 1.2;
      amp.gain.value = 0.18;
      noise.connect(band).connect(amp).connect(c.destination);
      noise.start(t);
      tone(c, 180, t, 0.08, 0.06, "triangle");
    },
    // Warm chime: a fifth, softly detuned, like a small bell in a glass lantern.
    chime: function () {
      var c = audio(); if (!c) return;
      var t = c.currentTime;
      tone(c, 659.25, t, 1.4, 0.07);
      tone(c, 662, t, 1.2, 0.03);
      tone(c, 987.77, t + 0.11, 1.6, 0.05);
      tone(c, 1318.5, t + 0.11, 0.8, 0.015);
    },
  };

  if (toggle) {
    reflect();
    toggle.addEventListener("click", function () {
      enabled = !enabled;
      try { localStorage.setItem(KEY, enabled ? "on" : "off"); } catch (e) { /* not remembered */ }
      reflect();
      if (enabled) api.chime();
    });
  }
  document.querySelectorAll("[data-chime]").forEach(function (el) {
    el.addEventListener("click", function () { api.chime(); });
  });

  window.LikhtarSound = api;
})();
