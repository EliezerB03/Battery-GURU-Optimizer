// Freqs Disabled Behavior/Animation
window.initFreqSettings = function() {
const toggle = document.getElementById("frq_on");
const toggleText = document.getElementById("frq_text");
const optionsSection = document.querySelector("#freq .options");
const optionsContainer = document.getElementById("frq_button_options");
const button = document.getElementById("frq_reset_button");
function updateUI() {
const isChecked = toggle.checked;
toggleText.textContent = isChecked ? "ON" : "OFF";
toggleText.style.color = isChecked ? "var(--app-accent)" : "";
toggleText.style.fontFamily = "'SamsungSharpSans-Bold', sans-serif";
toggleText.style.fontWeight = "normal";
toggleText.style.transform = 'translateY(2px)';
if (optionsSection) optionsSection.classList.toggle("disabled", !isChecked);
if (optionsContainer) optionsContainer.classList.toggle("disabled", !isChecked);
if (button) button.disabled = !isChecked;
}
toggle.addEventListener("change", updateUI);
updateUI();
};
