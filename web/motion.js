// Motion for the site: GSAP + ScrollTrigger, Lenis for smooth scroll (see DIRECTION.md).
// Everything here is additive: without JS the page is complete, and with reduced motion every
// element is shown in its final state at once.
(function () {
  "use strict";
  var reduce = window.matchMedia("(prefers-reduced-motion: reduce)").matches;
  var sound = window.LikhtarSound || { tick: function () {}, chime: function () {} };
  var hasGsap = !!(window.gsap && window.ScrollTrigger);

  // Header gets a backdrop once the hero is scrolled past its top.
  var nav = document.querySelector(".nav");
  function onScroll() { nav.classList.toggle("scrolled", window.scrollY > 40); }
  onScroll();
  window.addEventListener("scroll", onScroll, { passive: true });

  // ── Hero: keep the light pool and the click target on the lantern, whatever the crop ──
  var LANTERN = { x: 1328 / 1672, y: 610 / 941 }; // where the lantern sits in hero-tent.webp
  var hero = document.querySelector(".hero");
  var heroImg = document.querySelector(".hero-scene img");
  var light = document.querySelector(".hero-light");
  var hit = document.querySelector("[data-lantern]");
  var lantern = { x: 0, y: 0 };

  function placeLantern() {
    if (!heroImg.naturalWidth) return;
    var box = hero.getBoundingClientRect();
    var iw = heroImg.naturalWidth, ih = heroImg.naturalHeight;
    var scale = Math.max(box.width / iw, box.height / ih);
    var rw = iw * scale, rh = ih * scale;
    var pos = getComputedStyle(heroImg).objectPosition.split(" ").map(parseFloat);
    var ox = (box.width - rw) * (pos[0] / 100), oy = (box.height - rh) * (pos[1] / 100);
    lantern.x = ox + LANTERN.x * rw;
    lantern.y = oy + LANTERN.y * rh;
    light.style.setProperty("--lx", lantern.x + "px");
    light.style.setProperty("--ly", lantern.y + "px");
    if (hit) {
      var w = rw * 0.085, h = rh * 0.3;
      hit.style.left = (lantern.x - w / 2) + "px";
      hit.style.top = (lantern.y - h * 0.62) + "px";
      hit.style.width = w + "px";
      hit.style.height = h + "px";
    }
  }
  if (heroImg.complete) placeLantern(); else heroImg.addEventListener("load", placeLantern);
  window.addEventListener("resize", placeLantern);

  // The light leans a little toward the pointer — additive, and it settles back when the pointer leaves.
  if (!reduce && window.matchMedia("(pointer: fine)").matches) {
    var pending = false, px = 0, py = 0;
    hero.addEventListener("pointermove", function (e) {
      px = e.clientX; py = e.clientY;
      if (pending) return;
      pending = true;
      requestAnimationFrame(function () {
        pending = false;
        var r = hero.getBoundingClientRect();
        var dx = (px - r.left - lantern.x) * 0.06, dy = (py - r.top - lantern.y) * 0.06;
        light.style.setProperty("--lx", (lantern.x + dx) + "px");
        light.style.setProperty("--ly", (lantern.y + dy) + "px");
      });
    });
    hero.addEventListener("pointerleave", placeLantern);
    window.addEventListener("blur", placeLantern);
  }

  if (hit) {
    hit.addEventListener("click", function () {
      sound.chime();
      if (reduce || !hasGsap) return;
      gsap.fromTo(light, { opacity: 1.4, scale: 1 }, { opacity: 1, duration: 1.6, ease: "power2.out" });
      gsap.fromTo(heroImg, { filter: "brightness(1.25)" }, { filter: "brightness(1)", duration: 1.6, ease: "power2.out" });
    });
  }

  // ── Tour: a TV you drive with the arrow keys ──
  var tabs = Array.prototype.slice.call(document.querySelectorAll(".channels [role=tab]"));
  var tvImg = document.querySelector("[data-tv]");
  var caption = document.querySelector("[data-tv-caption]");
  var root = document.querySelector('link[rel="icon"]').getAttribute("href").replace("assets/icon-180.png", "");
  var current = 0, swapTimer = null;

  function show(i, withSound) {
    i = (i + tabs.length) % tabs.length;
    if (i === current && caption.textContent) return;
    tabs[current].setAttribute("aria-selected", "false");
    tabs[current].tabIndex = -1;
    current = i;
    var tab = tabs[i];
    tab.setAttribute("aria-selected", "true");
    tab.tabIndex = 0;
    caption.textContent = tab.dataset.text;
    tvImg.alt = tab.textContent + " — " + tab.dataset.text;
    var src = root + "assets/" + tab.dataset.shot + ".webp";
    if (withSound) sound.tick();
    if (reduce) { tvImg.src = src; return; }
    clearTimeout(swapTimer);
    tvImg.classList.add("switching");
    swapTimer = setTimeout(function () {
      tvImg.src = src;
      tvImg.onload = function () { tvImg.classList.remove("switching"); };
    }, 180);
  }

  tabs.forEach(function (tab, i) {
    tab.tabIndex = i === 0 ? 0 : -1;
    tab.addEventListener("click", function () { show(i, true); });
    tab.addEventListener("keydown", function (e) {
      var next = { ArrowRight: 1, ArrowDown: 1, ArrowLeft: -1, ArrowUp: -1 }[e.key];
      if (e.key === "Home") next = -current;
      if (e.key === "End") next = tabs.length - 1 - current;
      if (next === undefined) return;
      e.preventDefault();
      show(current + next, true);
      tabs[current].focus();
    });
  });
  caption.textContent = tabs[0].dataset.text;
  tvImg.alt = tabs[0].textContent + " — " + tabs[0].dataset.text;

  // Arrow keys work like a remote while the tour is on screen, even before a tab has focus.
  var tour = document.getElementById("tour");
  var tourVisible = false;
  if ("IntersectionObserver" in window) {
    new IntersectionObserver(function (entries) { tourVisible = entries[0].intersectionRatio > 0.55; }, { threshold: [0, 0.55, 1] }).observe(tour);
  }
  document.addEventListener("keydown", function (e) {
    if (!tourVisible || e.altKey || e.metaKey || e.ctrlKey) return;
    var t = e.target;
    if (t.closest && t.closest("input, textarea, select, [role=tab]")) return;
    var step = { ArrowRight: 1, ArrowLeft: -1 }[e.key];
    if (!step) return;
    e.preventDefault();
    show(current + step, true);
  });

  if (!hasGsap) return;

  // ── Split headings: words rise one by one; screen readers get the whole sentence ──
  function split(el) {
    var whole = el.textContent;
    var visual = document.createElement("span");
    visual.setAttribute("aria-hidden", "true");
    Array.prototype.slice.call(el.childNodes).forEach(function (node) {
      var isEm = node.nodeType === 1 && node.tagName === "EM";
      var text = node.textContent;
      text.split(/(\s+)/).forEach(function (part) {
        if (!part) return;
        if (/^\s+$/.test(part)) { visual.appendChild(document.createTextNode(" ")); return; }
        var w = document.createElement("span");
        w.className = "w";
        var inner = document.createElement(isEm ? "em" : "span");
        inner.textContent = part;
        w.appendChild(inner);
        visual.appendChild(w);
      });
    });
    var label = document.createElement("span");
    label.className = "sr";
    label.textContent = whole;
    el.textContent = "";
    el.appendChild(label);
    el.appendChild(visual);
    return visual.querySelectorAll(".w > *");
  }

  if (reduce) return; // final states are the default CSS — nothing to animate

  gsap.registerPlugin(ScrollTrigger);

  var lenis = null;
  if (window.Lenis) {
    lenis = new Lenis({ lerp: 0.11, wheelMultiplier: 0.95 });
    lenis.on("scroll", ScrollTrigger.update);
    gsap.ticker.add(function (time) { lenis.raf(time * 1000); });
    gsap.ticker.lagSmoothing(0);
    document.querySelectorAll('a[href^="#"]').forEach(function (a) {
      a.addEventListener("click", function (e) {
        var id = a.getAttribute("href");
        if (id.length < 2) return;
        var target = document.querySelector(id);
        if (!target) return;
        e.preventDefault();
        lenis.scrollTo(target, { offset: -70 });
        target.setAttribute("tabindex", "-1");
        target.focus({ preventScroll: true });
      });
    });
  }

  // Hero intro: the scene settles in, the light warms up, then the words and the call to action.
  var heroWords = split(document.getElementById("hero-title"));
  var intro = gsap.timeline({ defaults: { ease: "power3.out" } });
  intro
    .from(heroImg, { scale: 1.08, filter: "brightness(.45)", duration: 2.2, ease: "power2.out" }, 0)
    .from(light, { opacity: 0, duration: 1.8 }, 0.4)
    .from(".hero .eyebrow", { y: 18, opacity: 0, duration: 0.8 }, 0.35)
    .from(heroWords, { yPercent: 110, duration: 1.05, stagger: 0.07 }, 0.45)
    .from(".hero .lead, .hero .cta, .hero .facts", { y: 22, opacity: 0, duration: 0.9, stagger: 0.12 }, 0.95)
    .from(".nav", { y: -20, opacity: 0, duration: 0.8 }, 0.6);

  // Other headings: words rise when the section comes into view.
  document.querySelectorAll("main h2.split").forEach(function (h) {
    var words = split(h);
    gsap.from(words, {
      yPercent: 110, duration: 0.95, ease: "power3.out", stagger: 0.06,
      scrollTrigger: { trigger: h, start: "top 85%", once: true },
    });
    var head = h.closest(".section-head") || h.parentElement;
    var rest = head.querySelectorAll(".eyebrow, .lead, .hint");
    if (rest.length) gsap.from(rest, { y: 18, opacity: 0, duration: 0.8, stagger: 0.08, ease: "power2.out", scrollTrigger: { trigger: h, start: "top 85%", once: true } });
  });

  // Cards and steps come in after their heading.
  [".name p", ".frame", ".steps li", ".privacy-grid p", ".ticks li", ".bubble", ".give", ".timeline li"].forEach(function (sel) {
    var items = document.querySelectorAll(sel);
    if (!items.length) return;
    gsap.from(items, { y: 28, opacity: 0, duration: 0.8, stagger: 0.1, ease: "power2.out", scrollTrigger: { trigger: items[0], start: "top 88%", once: true } });
  });

  // Timeline: the line of light grows as you read along it.
  var line = document.querySelector(".timeline");
  gsap.fromTo(line, { "--grow": "0%" }, { "--grow": "100%", ease: "none", scrollTrigger: { trigger: line, start: "top 80%", end: "bottom 55%", scrub: true } });

  // TV in the tour rises into place.
  gsap.from(".tv-screen", { y: 60, scale: 0.94, opacity: 0, duration: 1.2, ease: "power3.out", scrollTrigger: { trigger: ".tv", start: "top 85%", once: true } });

  // ── Before / after: pinned, the Likhtar screen wipes over the stock one as you scroll ──
  var stage = document.querySelector(".compare-stage");
  var mm = gsap.matchMedia();
  mm.add("(min-width: 641px)", function () {
    stage.classList.add("wipe");
    stage.style.setProperty("--cut", "100%");
    var chimed = false;
    var tween = gsap.to(stage, {
      "--cut": "0%",
      ease: "none",
      scrollTrigger: {
        trigger: ".compare-pin",
        start: "center center",
        end: "+=110%",
        pin: true,
        scrub: 0.6,
        onUpdate: function (self) {
          if (self.progress > 0.97 && !chimed) { chimed = true; sound.chime(); }
          if (self.progress < 0.5) chimed = false;
        },
      },
    });
    return function () { tween.scrollTrigger && tween.scrollTrigger.kill(); tween.kill(); stage.classList.remove("wipe"); stage.style.removeProperty("--cut"); };
  });

  // Fonts and images change measurements: refresh once they are in.
  if (document.fonts && document.fonts.ready) document.fonts.ready.then(function () { ScrollTrigger.refresh(); });
  window.addEventListener("load", function () { placeLantern(); ScrollTrigger.refresh(); });

  window.addEventListener("pagehide", function () {
    mm.revert();
    ScrollTrigger.getAll().forEach(function (t) { t.kill(); });
    if (lenis) lenis.destroy();
  });
})();
