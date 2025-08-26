// Voltage Disabled Behavior/Animation
window.initVoltageSettings = function() {
const toggle = document.getElementById("volt_on");
const toggleText = document.getElementById("volt_text");
const optionsSections = document.querySelectorAll("#voltage .options");
function updateUI() {
const isChecked = toggle.checked;
toggleText.textContent = isChecked ? "ON" : "OFF";
toggleText.style.color = isChecked ? "var(--app-accent)" : "";
toggleText.style.fontFamily = "'SamsungSharpSans-Bold', sans-serif";
toggleText.style.fontWeight = "normal";
toggleText.style.transform = 'translateY(2px)';
optionsSections.forEach(section => {
  section.classList.toggle("disabled", !isChecked);
});
}
toggle.addEventListener("change", updateUI);
updateUI();
// Slider Dots/Values calculation
const sliderIds = ['volt_big','volt_little','volt_gpu','volt_mif','volt_audio',
'volt_camera','volt_cp','volt_display','volt_fsys','volt_int','volt_intcamera',
'volt_iva','volt_score'];
const thumbWidth = 20;
function placeDotsForSliders() {
sliderIds.forEach(id => {
const slider = document.getElementById(id);
if (!slider) return;
const dotsContainer = slider.nextElementSibling;
if (!dotsContainer) return;
const steps = parseInt(slider.max) - parseInt(slider.min);
const sliderWidth = slider.offsetWidth;
const trackWidth = sliderWidth - thumbWidth;
dotsContainer.innerHTML = '';
for (let i = 0; i <= steps; i++) {
  const dot = document.createElement('span');
  const left = (thumbWidth / 2) + (trackWidth * (i / steps)) + 2;
  dot.style.left = `${left}px`;
  dotsContainer.appendChild(dot);
}
});
}
function placeSliderValues(sliderId) {
const slider = document.getElementById(sliderId);
if (!slider) return;
const valuesContainer = slider.parentElement.querySelector('.slider-values');
if (!valuesContainer) return;
const sliderWidth = slider.offsetWidth;
const trackWidth = sliderWidth - thumbWidth;
const valueSpans = valuesContainer.querySelectorAll('span');
if (valueSpans.length !== 3) return;
const positions = [
(thumbWidth / 2),
(thumbWidth / 2) + (trackWidth * 0.5) + 1,
(thumbWidth / 2) + trackWidth
];
valueSpans.forEach((span, index) => {
span.style.position = 'absolute';
span.style.top = '0';
span.style.transform = 'translateX(-50%)';
span.style.left = positions[index] + 'px';
});
}
function updateFloatingValue(slider) {
  let floatEl = slider.parentElement.querySelector('.slider-value-float');
  if (!floatEl) {
    floatEl = document.createElement('div');
    floatEl.className = 'slider-value-float';
    const textEl = document.createElement('span');
    textEl.className = 'slider-value-text';
    floatEl.appendChild(textEl);
    slider.parentElement.appendChild(floatEl);
  }
  const textEl = floatEl.querySelector('.slider-value-text');
  const value = parseInt(slider.value, 10);
  const displayValue = value === 10 ? '0' : (value < 10 ? `-${10 - value}` : `+${value - 10}`);
  textEl.textContent = displayValue;
  const sliderWidth = slider.offsetWidth;
  const trackWidth = sliderWidth - thumbWidth;
  const percentage = (value - slider.min) / (slider.max - slider.min);
  const left = (thumbWidth / 2) + (trackWidth * percentage);
  floatEl.style.left = `${left}px`;
}
function showFloatingValue(slider) {
  const floatEl = slider.parentElement.querySelector('.slider-value-float');
  if (floatEl) {
    floatEl.classList.add('visible');
  }
}
function hideFloatingValue(slider) {
  const floatEl = slider.parentElement.querySelector('.slider-value-float');
  if (floatEl) {
    floatEl.classList.remove('visible');
  }
}
function setUIInteractions(enabled) {
  const elements = document.querySelectorAll('button, .oui-container-toggle, .popup-trigger, .oui-tab-link, .oui-overlay-bubble-info');
  elements.forEach(el => {
    el.style.pointerEvents = enabled ? '' : 'none';
  });
}
function initSliderFloatingValues() {
  sliderIds.forEach(id => {
    const slider = document.getElementById(id);
    if (!slider) return;
    updateFloatingValue(slider);
    slider.addEventListener('touchstart', () => {
      setUIInteractions(false);
      updateFloatingValue(slider);
      showFloatingValue(slider);
    });
    slider.addEventListener('touchend', () => {
      setUIInteractions(true);
      setTimeout(() => {
        hideFloatingValue(slider);
      }, 250);
    });
    slider.addEventListener('input', () => updateFloatingValue(slider));
    slider.addEventListener('mousedown', () => {
      setUIInteractions(false);
      updateFloatingValue(slider);
      showFloatingValue(slider);
    });
    window.addEventListener('mouseup', () => {
      setUIInteractions(true);
      hideFloatingValue(slider);
    });
    window.addEventListener('resize', () => updateFloatingValue(slider));
  });
}
// Slider Dot Behavior
const dragThreshold = 5;
sliderIds.forEach(id => {
const slider = document.getElementById(id);
if (!slider) return;
let pointerDown = false;
let startX = 0;
let startValue = 0;
let moved = false;
let initialValue = 0;
let shouldFireInput = false;
slider.addEventListener('pointerdown', (e) => {
  pointerDown = true;
  moved = false;
  shouldFireInput = false;
  initialValue = slider.valueAsNumber;
  const rect = slider.getBoundingClientRect();
  const clickX = e.clientX - rect.left;
  const min = slider.min ? Number(slider.min) : 0;
  const max = slider.max ? Number(slider.max) : 100;
  const range = max - min;
  const newValue = min + (clickX / rect.width) * range;
  slider.value = Math.round(Math.min(max, Math.max(min, newValue)));
  requestAnimationFrame(() => {
    updateFloatingValue(slider);
  });
  if (slider.valueAsNumber !== initialValue) {
    shouldFireInput = true;
  }
  startX = e.clientX;
  startValue = slider.valueAsNumber;
  slider.setPointerCapture(e.pointerId);
  showFloatingValue(slider);
  slider.classList.add('active-thumb'); 
  e.preventDefault();
}, { passive: false });
slider.addEventListener('pointermove', (e) => {
  if (!pointerDown) return;
  e.preventDefault();
  const deltaX = e.clientX - startX;
  if (Math.abs(deltaX) > dragThreshold) moved = true;
  if (moved) {
    const sliderWidth = slider.clientWidth;
    const min = slider.min ? Number(slider.min) : 0;
    const max = slider.max ? Number(slider.max) : 100;
    const range = max - min;
    let newValue = startValue + (deltaX / sliderWidth) * range;
    newValue = Math.min(max, Math.max(min, newValue));
    slider.value = Math.round(newValue);
    requestAnimationFrame(() => {
      updateFloatingValue(slider);
    });
    shouldFireInput = true;
  }
}, { passive: false });
slider.addEventListener('pointerup', (e) => {
if (!pointerDown) return;
pointerDown = false;
slider.releasePointerCapture(e.pointerId);
if (shouldFireInput && slider.valueAsNumber !== initialValue) {
  const inputEvent = new Event('input', { bubbles: true, cancelable: true });
  slider.dispatchEvent(inputEvent);
}
setTimeout(() => {
  hideFloatingValue(slider);
}, 260);
slider.classList.remove('active-thumb');
}, { passive: false });
slider.addEventListener('pointercancel', (e) => {
pointerDown = false;
slider.releasePointerCapture(e.pointerId);
hideFloatingValue(slider);
slider.classList.remove('active-thumb');
});
});
// Inits for Tabs
placeDotsForSliders();
sliderIds.forEach(id => placeSliderValues(id));
initSliderFloatingValues();
// Window Recalculation for Slider
window.addEventListener('resize', () => {
placeDotsForSliders();
sliderIds.forEach(id => placeSliderValues(id));
});
}
