// Init Functions
function showTab(tabId, el) {
  if (el.classList.contains('oui-tab-link--active')) {
    return;
  }
  document.querySelectorAll('.tab-section').forEach(section => {
    section.classList.remove('active');
  });
  document.getElementById(tabId).classList.add('active');
  // Init Freq
  window.initFreqSettings();
  // Init Voltage
  window.initVoltageSettings();
  document.querySelectorAll('.oui-tab-link').forEach(link => {
    link.classList.remove('oui-tab-link--active');
  });
  el.classList.add('oui-tab-link--active');
  const scrollContainer = document.querySelector('.scroll-container');
  if (scrollContainer) {
    scrollContainer.scrollTop = 0;
  }
  disableScrollDuringAnimation(251);
}
const scrollContainer = document.querySelector('.scroll-container');
function disableScrollDuringAnimation(duration = 251) {
  if (!scrollContainer) return;
  scrollContainer.style.overflowY = 'hidden';
  setTimeout(() => {
    scrollContainer.style.overflowY = 'auto';
  }, duration);
}
disableScrollDuringAnimation(251);
// Glow Effect Behavior
document.addEventListener('DOMContentLoaded', () => {
  const topGlow = document.querySelector('.top-glow');
  const bottomGlow = document.querySelector('.bottom-glow');
  if (!topGlow || !bottomGlow) return;
  if (!topGlow.style.transition) topGlow.style.transition = 'opacity 200ms ease';
  if (!bottomGlow.style.transition) bottomGlow.style.transition = 'opacity 200ms ease';
  topGlow.style.opacity = topGlow.style.opacity || '0';
  bottomGlow.style.opacity = bottomGlow.style.opacity || '0';
  const setGlow = (on) => {
    topGlow.style.opacity = on ? '1' : '0';
    bottomGlow.style.opacity = on ? '1' : '0';
  };
  const isScrollable = (el) => el && (el.scrollHeight > el.clientHeight + 1);
  const findScrollableForSection = (section) => {
    if (!section) return null;
    let el = section;
    while (el && el !== document.body) {
      el = el.parentElement;
      if (!el) break;
      if (el.classList && el.classList.contains('scroll-container')) return el;
      const st = getComputedStyle(el);
      if (/(auto|scroll)/.test(st.overflowY)) return el;
    }
    const sectionStyle = getComputedStyle(section);
    if (/(auto|scroll)/.test(sectionStyle.overflowY)) return section;
    const global = document.querySelector('.scroll-container');
    if (global && global.contains(section)) return global;
    return document.scrollingElement || document.documentElement;
  };
  let rafId = null;
  const updateGlow = () => {
    if (rafId) cancelAnimationFrame(rafId);
    rafId = requestAnimationFrame(() => {
      const active = document.querySelector('section.active');
      if (!active) { setGlow(false); return; }
      if (active.hasAttribute('data-glow') && active.getAttribute('data-glow') === 'false') {
        setGlow(false);
        return;
      }
      const scrollable = findScrollableForSection(active);
      setGlow(Boolean(scrollable && isScrollable(scrollable)));
    });
  };
  updateGlow();
  const originalShowTab = window.showTab;
  if (typeof originalShowTab === 'function') {
    window.showTab = function(id, element) {
      originalShowTab(id, element);
      setTimeout(updateGlow, 40);
    };
  }
  const mo = new MutationObserver(updateGlow);
  document.querySelectorAll('section').forEach(s => mo.observe(s, { attributes: true, attributeFilter: ['class'] }));
  const ro = new ResizeObserver(updateGlow);
  ro.observe(document.documentElement);
  document.querySelectorAll('.scroll-container, section').forEach(el => ro.observe(el));
  const domMo = new MutationObserver(updateGlow);
  document.querySelectorAll('.scroll-container').forEach(sc => domMo.observe(sc, { childList: true, subtree: true }));
  window.addEventListener('resize', updateGlow);
});
// NavBar Behavior
function updateNavHeight() {
  const navbar = document.querySelector('.oui-tab');
  const height = navbar.offsetHeight;
  document.documentElement.style.setProperty('--oui-tab-height', height + 'px');
}
window.addEventListener('load', updateNavHeight);
window.addEventListener('resize', updateNavHeight);
const observer = new ResizeObserver(updateNavHeight);
observer.observe(document.querySelector('.oui-tab'));
