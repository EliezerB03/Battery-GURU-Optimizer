// Script for Main Settings
document.addEventListener("DOMContentLoaded", () => {
  const serviceFile = "/data/adb/modules/battery-guru-optimizer-9810/service.sh";
  const execSafe = (cmd) => ksu.exec(cmd).catch(() => "");
  const buildSedReplace = (path, value) =>
    `sed -i "s|echo ['\\\"][^'\\\"]*['\\\"] > ${path}|echo '${value}' > ${path}|g" ${serviceFile}; `;
  function scheduleIdle(fn) {
    if ('requestIdleCallback' in window) requestIdleCallback(fn);
    else setTimeout(fn, 0);
  }
  function initToggle({ id, paths, probePath, marker, defaultState = true }) {
    const toggle = document.getElementById(id);
    if (!toggle) return;
    const container = toggle.closest(".oui-container-toggle");
    if (container) container.classList.add("no-animation");
    const probe = probePath && paths[probePath] ? probePath : Object.keys(paths)[0];
    const activeProbeVal = paths[probe].active;
    const inactiveProbeVal = paths[probe].inactive;
    const cacheKey = `toggle_state_${id}`;
    let initialState = defaultState;
    const cached = localStorage.getItem(cacheKey);
    if (cached !== null) initialState = cached === "1";
    toggle.checked = !!initialState;
    if (container) requestAnimationFrame(() => container.classList.remove("no-animation"));
    scheduleIdle(() => {
      try {
        if (marker) {
          execSafe(`grep -F "# ${marker}=" ${serviceFile} || echo "# ${marker}=0"`).then((svcLine) => {
            const liveState = svcLine.includes("=1");
            localStorage.setItem(cacheKey, liveState ? "1" : "0");
            if (paths && Object.keys(paths).length > 0) {
              let cmd = "";
              for (const p in paths) {
                let val = liveState ? paths[p].active : paths[p].inactive;
                if (id === "frq_on" && liveState) {
                  if (p.includes("policy0") || p.includes("policy4")) {
                    const name = p.includes("policy0") ? "lit" : "big";
                    cmd += `val=$(grep -F "# ${name}=" ${serviceFile} | awk '{print $2}' | sed 's/Mhz//'); `;
                    cmd += `val=$((val*1000)); echo $val > ${p}; `;
                    cmd += buildSedReplace(p, `$((${name}*1000))`) + " ";
                    continue;
                  }
                }
                cmd += `echo '${val}' > ${p}; `;
                cmd += buildSedReplace(p, val);
              }
              scheduleIdle(() => execSafe(cmd));
            }
            toggle.checked = liveState;
          });
        } else {
          execSafe(`grep -F "${probe}" ${serviceFile} || true`).then((svc) => {
            let liveState = initialState;
            const hasActive = svc.includes(`echo '${activeProbeVal}' > ${probe}`) || svc.includes(`echo "${activeProbeVal}" > ${probe}"`);
            const hasInactive = svc.includes(`echo '${inactiveProbeVal}' > ${probe}`) || svc.includes(`echo "${inactiveProbeVal}" > ${probe}"`);
            if (hasActive && !hasInactive) liveState = true;
            else if (hasInactive && !hasActive) liveState = false;
            else execSafe(`cat ${probe} || true`).then((live) => {
              liveState = (live.trim() === activeProbeVal);
            });
            const shouldBe = liveState ? activeProbeVal : inactiveProbeVal;
            execSafe(`cat ${probe} || true`).then((live) => {
              if (live.trim() !== "" && live.trim() !== shouldBe) {
                let cmd = "";
                for (const p in paths) cmd += `echo '${liveState ? paths[p].active : paths[p].inactive}' > ${p}; `;
                execSafe(cmd);
              }
              localStorage.setItem(cacheKey, liveState ? "1" : "0");
            });
            toggle.checked = liveState;
          });
        }
      } catch { /* ¯\_(ツ)_/¯ */ }
    });
    toggle.addEventListener("change", () => {
      const state = toggle.checked;
      localStorage.setItem(cacheKey, state ? "1" : "0");
      let cmd = "";
      if (marker) {
        cmd += `sed -i "s|# ${marker}=.*$|# ${marker}=${state ? 1 : 0}|" ${serviceFile}; `;
      }
      if (id === "thermal_opt") {
        const tmpBlockPath = `${serviceFile}.thermal_block.tmp`;
        const tmpService = `${serviceFile}.tmp`;
        if (state) {
          const activeBlock = `# thermalset_on=1
system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0
chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres
echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms
echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres
echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres
chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus
pm disable com.google.android.gms/.chimera.GmsIntentOperationService`;
          cmd += `cat > ${tmpBlockPath} <<'EOB'\n${activeBlock}\nEOB\n`;
          cmd += `awk -v blk='${tmpBlockPath}' -v marker='^# thermalset_on=' 'BEGIN{skip=0;found=0} { if($0 ~ marker){ found=1; while((getline line < blk) > 0) print line; skip=1; next } if(skip){ if($0 ~ /^$/ || $0 ~ /^#-+/){ skip=0; print $0; next } else next } print $0 } END{ if(!found){ while((getline line < blk) > 0) print line } }' ${serviceFile} > ${tmpService}; `;
          cmd += `mv ${tmpService} ${serviceFile}; rm -f ${tmpBlockPath}; `;
        } else {
          const inactiveBlock = `# thermalset_on=0
system_table_unset activity_manager_constants
pm enable com.google.android.gms/.chimera.GmsIntentOperationService`;
          cmd += `cat > ${tmpBlockPath} <<'EOB'\n${inactiveBlock}\nEOB\n`;
          cmd += `awk -v blk='${tmpBlockPath}' -v marker='^# thermalset_on=' 'BEGIN{skip=0;found=0} { if($0 ~ marker){ found=1; while((getline line < blk) > 0) print line; skip=1; next } if(skip){ if($0 ~ /^$/ || $0 ~ /^#-+/){ skip=0; print $0; next } else next } print $0 } END{ if(!found){ while((getline line < blk) > 0) print line } }' ${serviceFile} > ${tmpService}; `;
          cmd += `mv ${tmpService} ${serviceFile}; rm -f ${tmpBlockPath}; `;
        }
      } else if (paths && Object.keys(paths).length > 0) {
        for (const p in paths) {
          let val = state ? paths[p].active : paths[p].inactive;
          if (id === "frq_on" && state) {
            if (p.includes("policy0") || p.includes("policy4")) {
              const name = p.includes("policy0") ? "lit" : "big";
              cmd += `val=$(grep -F "# ${name}=" ${serviceFile} | awk '{print $2}' | sed 's/Mhz//'); `;
              cmd += `val=$((val*1000)); echo $val > ${p}; `;
              cmd += buildSedReplace(p, `$((${name}*1000))`) + " ";
              continue;
            }
          }
          cmd += `echo '${val}' > ${p}; `;
          cmd += buildSedReplace(p, val);
        }
      }
      scheduleIdle(() => execSafe(cmd));
    });
  }
  // General Settings (Toggles)
  const toggles = [
    { id: "cpu_hotplug", probePath: "CPUHOTPLUG_STATE_MARKER", marker: "hotplugset_on",
      defaultState: true, 
      paths: {
        "/sys/power/cpuhotplug/governor/user_mode": { active: "0", inactive: "1" },
        "/sys/power/cpuhotplug/governor/enabled":   { active: "0", inactive: "1" }
    }},
    { id: "power_eff", probePath: "POWEREFFI_STATE_MARKER", marker: "powereffiset_on",
      defaultState: true, 
      paths: { "/sys/module/workqueue/parameters/power_efficient": { active: "Y", inactive: "N" } 
    }},
    { id: "thermal_opt", probePath: "THERMAL_STATE_MARKER", marker: "thermalset_on",
      defaultState: true,
      paths: { "THERMAL_STATE_MARKER": {
        active: `system_table_set activity_manager_constants max_cached_processes=0,background_settle_time=0,fgservice_min_shown_time=0,fgservice_min_report_time=0,fgservice_screen_on_before_time=0,fgservice_screen_on_after_time=0,content_provider_retain_time=0,gc_timeout=0,gc_min_interval=0,full_pss_min_interval=0,full_pss_lowered_interval=0,power_check_interval=0,power_check_max_cpu_1=0,power_check_max_cpu_2=0,power_check_max_cpu_3=0,power_check_max_cpu_4=0,service_usage_interaction_time=0,usage_stats_interaction_interval=0,service_restart_duration=0,service_reset_run_duration=0,service_restart_duration_factor=0,service_min_restart_time_between=0,service_max_inactivity=0,service_bg_start_timeout=0,CUR_MAX_CACHED_PROCESSES=0,CUR_MAX_EMPTY_PROCESSES=0,CUR_TRIM_EMPTY_PROCESSES=0,CUR_TRIM_CACHED_PROCESSES=0; chmod 666 /sys/devices/system/cpu/cpu[0-7]/max_cpus; chmod 666 /sys/devices/system/cpu/cpu[0-7]/min_cpus; echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/max_cpus; echo 0 > /sys/devices/system/cpu/cpu4/core_ctl/min_cpus; echo 60 > /sys/devices/system/cpu/cpu4/core_ctl/busy_down_thres; echo 4 > /sys/devices/system/cpu/cpu4/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu4/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu4/core_ctl/offline_delay_ms; echo 2 > /sys/devices/system/cpu/cpu0/core_ctl/min_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/max_cpus; echo 4 > /sys/devices/system/cpu/cpu0/core_ctl/task_thres; echo 80 > /sys/devices/system/cpu/cpu0/core_ctl/busy_up_thres; echo 100 > /sys/devices/system/cpu/cpu0/core_ctl/offline_delay_ms; echo 60 > /sys/devices/system/cpu/cpu0/core_ctl/busy_down_thres; chmod 444 /sys/devices/system/cpu/cpu[0-7]/max_cpus; pm disable com.google.android.gms/.chimera.GmsIntentOperationService`.replace(/\n/g," "),
        inactive: `system_table_unset activity_manager_constants; pm enable com.google.android.gms/.chimera.GmsIntentOperationService`.replace(/\n/g," ")
    }}},
    { id: "swap_opt", probePath: "SWAP_STATE_MARKER", marker: "swapset_on",
      defaultState: true,
      paths: {
        "/proc/sys/vm/dirty_ratio": { active: "35", inactive: "0" },
        "/proc/sys/vm/dirty_expire_centisecs": { active: "200000", inactive: "200" },
        "/proc/sys/vm/dirty_writeback_centisecs": { active: "500000", inactive: "500" },
        "/proc/sys/vm/laptop_mode": { active: "5", inactive: "0" },
        "/proc/sys/vm/swappiness": { active: "0", inactive: "100" }
    }},
    { id: "storage_opt", probePath: "STORAGE_STATE_MARKER", marker: "storageset_on",
      defaultState: true,
      paths: {
        "/sys/block/sda/queue/rotational": { active: "1", inactive: "0" },
        "/sys/block/sda/queue/add_random": { active: "1", inactive: "0" },
        "/sys/block/sda/queue/rq_affinity": { active: "2", inactive: "1" }
    }},
    // Freq Settings (Toggle/Popup)
    { id: "frq_on", probePath: "FRQ_STATE_MARKER", marker: "frqset_on",
      defaultState: true,
      paths: {
        "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq": { active: null, inactive: "1794000" },
        "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq": { active: null, inactive: "2704000" }
    }},
    // Voltage Settings (Toggle/Sliders)
    { id: "volt_on", 
      probePath: "VOLTAGE_STATE_MARKER", 
      marker: "voltset_on",
      defaultState: true,
      paths: {
        "/sys/power/percent_margin/big_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/lit_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/g3d_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/mif_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/aud_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/cam_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/cp_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/disp_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/fsys0_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/int_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/intcam_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/iva_margin_percent": { active: null, inactive: "0" },
        "/sys/power/percent_margin/score_margin_percent": { active: null, inactive: "0" }
      }},
    // Labs (Toggles)
    { id: "kernel_debug", probePath: "DEBUG_STATE_MARKER", marker: "kerneldebug_on",
      defaultState: false,
      paths: {
        "/proc/sys/kernel/panic": { active: "1", inactive: "0" },
        "/sys/kernel/debug/debug_enabled": { active: "Y", inactive: "N" },
        "/sys/kernel/debug/seclog/seclog_enabled": { active: "Y", inactive: "N" }
    }}
  ];
  toggles.forEach(cfg => initToggle(cfg));
  // Freq Settings (Popup Menu)
  (function initPopup() {
    const popup = document.getElementById("popup-menu");
    if (!popup) return;
    const popupContent = popup.querySelector(".popup-content");
    const optionsList = document.getElementById("popup-options");
    let pointerDownOutside = false;
    let popupOpen = false;
    let optionsEnabled = true;
    function disableOptionsTemporarily(ms = 300) {
      optionsEnabled = false;
      setTimeout(() => { optionsEnabled = true; }, ms);
    }
    document.addEventListener("pointerdown", (e) => {
      pointerDownOutside = !popup.contains(e.target);
    }, true);
    document.addEventListener("pointerup", (e) => {
      if (popupOpen && pointerDownOutside && !popup.contains(e.target)) {
        closePopup();
        e.preventDefault();
        e.stopPropagation();
      }
    }, true);
    ["click","pointerdown","pointerup","touchstart","touchend"].forEach(evt => {
      document.addEventListener(evt, (e) => {
        if (popupOpen && !popup.contains(e.target)) {
          e.preventDefault();
          e.stopPropagation();
        }
      }, { capture: true, passive: false });
    });
    function closePopup() {
      popup.classList.remove("show");
      popupOpen = false;
      optionsEnabled = true;
      document.querySelectorAll(".popup-label-active").forEach(el => el.classList.remove("popup-label-active"));
    }
    ["frq_little","frq_big"].forEach(id => {
      const trigger = document.getElementById(id);
      if (!trigger) return;
      const defaults = {
        frq_little: "1690 Mhz",
        frq_big: "1794 Mhz"
      };
      let storedValue = localStorage.getItem(id) || defaults[id];
      trigger.dataset.selected = storedValue;
      let rightSpan = trigger.querySelector(".popup-value");
      if (!rightSpan) {
        rightSpan = document.createElement("span");
        rightSpan.className = "popup-value";
        trigger.appendChild(rightSpan);
      }
      rightSpan.textContent = storedValue;
      trigger.addEventListener("click", (e) => {
        e.stopPropagation();
        if (trigger.classList.contains("popup-label-active")) {
          closePopup();
          return;
        }
        document.querySelectorAll(".popup-label-active").forEach(el => el.classList.remove("popup-label-active"));
        trigger.classList.add("popup-label-active");
        disableOptionsTemporarily(300);
        const options = JSON.parse(trigger.dataset.options);
        const selected = trigger.dataset.selected;
        const isOpen = popup.classList.contains("show");
        if (isOpen) {
          positionPopup(trigger);
          popupContent.classList.add("switching");
          setTimeout(() => {
            fillPopup(options, selected, trigger);
            popupContent.classList.remove("switching");
          }, 150);
        } else {
          fillPopup(options, selected, trigger);
          requestAnimationFrame(() => {
            positionPopup(trigger);
            popup.classList.add("show");
            popupOpen = true;
          });
        }
      });
    });
    function fillPopup(options, selected, trigger) {
      optionsList.innerHTML = "";
      options.forEach((opt,index) => {
        const li = document.createElement("li");
        li.className = "oui-bubble-item";
        let effectClass = "";
        if (options.length === 1) effectClass = "rounded-all";
        else if (index === 0) effectClass = "rounded-top";
        else if (index === options.length-1) effectClass = "rounded-bottom";
        li.innerHTML = `<div class="popup-inner"><span class="popup-text">${opt}</span></div><span class="popup-effect ${effectClass}"></span>`;
        if (opt === selected) {
          li.classList.add("selected");
          const check = document.createElementNS("http://www.w3.org/2000/svg","svg");
          check.setAttribute("viewBox","0 0 24 24");
          check.setAttribute("class","popup-icon-selected checkmark");
          check.innerHTML = `<path style="fill:none;stroke:currentColor;stroke-linecap:round;stroke-linejoin:round;stroke-width:3" d="m5 12 5 5 9-9"/>`;
          li.querySelector(".popup-inner").appendChild(check);
        }
        const optionInner = li.querySelector(".popup-inner");
        const touchEffect = li.querySelector(".popup-effect");
        let pointerDown = false;
        let pointerInside = false;
        let cancelled = false;
        const cleanupEffect = () => {
          if (touchEffect.classList.contains("animate-in")) {
            touchEffect.classList.remove("animate-in");
            touchEffect.classList.add("animate-out");
            const onAnimEnd = () => {
              touchEffect.classList.remove("animate-out");
              touchEffect.removeEventListener("animationend", onAnimEnd);
            };
            touchEffect.addEventListener("animationend", onAnimEnd);
          }
          optionInner.classList.remove("pressed-effect");
        };
        li.addEventListener("pointerdown", (ev) => {
          if (!optionsEnabled) { ev.stopPropagation(); return; }
          pointerDown = true;
          pointerInside = true;
          cancelled = false;
          optionInner.classList.add("pressed-effect");
          touchEffect.classList.add("animate-in");
          setTimeout(() => { if(!cancelled) cleanupEffect(); },400);
        });
        li.addEventListener("pointermove",(ev)=>{
          if(!pointerDown) return;
          const rect = li.getBoundingClientRect();
          pointerInside = ev.clientX >= rect.left && ev.clientX <= rect.right && ev.clientY >= rect.top && ev.clientY <= rect.bottom;
          if(!pointerInside && !cancelled){ cancelled=true; pointerDown=false; cleanupEffect(); ev.preventDefault(); ev.stopPropagation(); }
        });
        li.addEventListener("pointerleave",()=>{ if(pointerDown && !cancelled){ cancelled=true; pointerDown=false; cleanupEffect(); }});
        li.addEventListener("pointerup",()=>{ pointerDown=false; cleanupEffect(); });
        li.addEventListener("pointercancel",()=>{ pointerDown=false; cancelled=true; cleanupEffect(); });
        li.addEventListener("click",(e)=>{
          if(!optionsEnabled) return;
          e.stopPropagation();
          popup.classList.remove("show");
          popupOpen=false;
          optionsEnabled=true;
          document.querySelectorAll(".popup-label-active").forEach(el=>el.classList.remove("popup-label-active"));
          trigger.dataset.selected=opt;
          let valueSpan = trigger.querySelector(".popup-value");
          if(!valueSpan){ valueSpan=document.createElement("span"); valueSpan.className="popup-value"; trigger.appendChild(valueSpan);}
          valueSpan.textContent=opt;
          localStorage.setItem(trigger.id, opt);
          const path = trigger.id === "frq_little" ? "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
                    : "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq";
          const val = opt.replace("Mhz","")*1000;
          const markerLine = trigger.id === "frq_little" ? "# lit=" : "# big=";
          const cmd = `echo '${val}' > ${path}; sed -i "s|echo ['\\\"][^'\\\"]*['\\\"] > ${path}|echo '${val}' > ${path}|g" ${serviceFile}; sed -i "s|${markerLine}.*|${markerLine}${opt}|" ${serviceFile}`;
          scheduleIdle(()=>execSafe(cmd));
        });
        optionsList.appendChild(li);
      });
      let valueSpan = trigger.querySelector(".popup-value");
      if(valueSpan) valueSpan.textContent=selected;
    }
    function positionPopup(trigger){
      const rect=trigger.getBoundingClientRect();
      const popupHeight=popup.offsetHeight;
      const popupWidth=popup.offsetWidth;
      const screenWidth=window.innerWidth;
      const optionCount=JSON.parse(trigger.dataset.options).length;
      const extraOffset=optionCount>10?40:0;
      let top=rect.top+window.scrollY+(rect.height/2)-(popupHeight/2)-extraOffset-177;
      let left=rect.right+8;
      if(left+popupWidth>screenWidth) left=screenWidth-popupWidth-26;
      popup.style.top=`${top}px`;
      popup.style.left=`${left}px`;
    }
  })();
  const voltageSliders = [
    { id: "volt_big", path: "/sys/power/percent_margin/big_margin_percent", marker: "big_volt" },
    { id: "volt_little", path: "/sys/power/percent_margin/lit_margin_percent", marker: "lit_volt" },
    { id: "volt_gpu", path: "/sys/power/percent_margin/g3d_margin_percent", marker: "g3d_volt" },
    { id: "volt_mif", path: "/sys/power/percent_margin/mif_margin_percent", marker: "mif_volt" },
    { id: "volt_audio", path: "/sys/power/percent_margin/aud_margin_percent", marker: "aud_volt" },
    { id: "volt_camera", path: "/sys/power/percent_margin/cam_margin_percent", marker: "cam_volt" },
    { id: "volt_cp", path: "/sys/power/percent_margin/cp_margin_percent", marker: "cp_volt" },
    { id: "volt_display", path: "/sys/power/percent_margin/disp_margin_percent", marker: "disp_volt" },
    { id: "volt_fsys", path: "/sys/power/percent_margin/fsys0_margin_percent", marker: "fsys0_volt" },
    { id: "volt_int", path: "/sys/power/percent_margin/int_margin_percent", marker: "int_volt" },
    { id: "volt_intcamera", path: "/sys/power/percent_margin/intcam_margin_percent", marker: "intcam_volt" },
    { id: "volt_iva", path: "/sys/power/percent_margin/iva_margin_percent", marker: "iva_volt" },
    { id: "volt_score", path: "/sys/power/percent_margin/score_margin_percent", marker: "score_volt" }
  ];
  function sliderValueToVoltage(sliderValue) {
    return Math.round(sliderValue) - 10;
  }
  function voltageToSliderValue(voltageValue) {
    return Math.round(parseInt(voltageValue)) + 10;
  }
  voltageSliders.forEach(config => {
    const slider = document.getElementById(config.id);
    if (!slider) return;
  const defaultVoltage = config.id === "volt_cp" ? 0 : -4;
  const cacheKey = `voltage_${config.id}`;
  let storedVoltage = localStorage.getItem(cacheKey);
  if (storedVoltage === null) storedVoltage = defaultVoltage;
  else storedVoltage = parseInt(storedVoltage);
  slider.value = voltageToSliderValue(storedVoltage);
  scheduleIdle(() => {
    try {
      const fallbackDefault = config.id === "volt_cp" ? 0 : -4; 
      execSafe(`grep -F "# ${config.marker}=" ${serviceFile} || echo "# ${config.marker}=${fallbackDefault}"`).then((markerLine) => {
        const match = markerLine.match(new RegExp(`# ${config.marker}=(-?\\d+)`));
        if (match) {
          const currentVoltage = parseInt(match[1]);
          slider.value = voltageToSliderValue(currentVoltage);
          localStorage.setItem(cacheKey, currentVoltage);
          const volToggle = document.getElementById("volt_on");
          if (volToggle && volToggle.checked) {
            const cmd = `echo '${currentVoltage}' > ${config.path}; ` +
                      buildSedReplace(config.path, currentVoltage);
            scheduleIdle(() => execSafe(cmd));
          }
        }
      });
    } catch { /* ¯\_(ツ)_/¯ */ }
  });
  let changeTimeout;
  slider.addEventListener("input", () => {
    const sliderValue = Math.round(parseFloat(slider.value));
    const voltageValue = sliderValueToVoltage(sliderValue);
    localStorage.setItem(cacheKey, voltageValue);
    clearTimeout(changeTimeout);
    changeTimeout = setTimeout(() => {
      const volToggle = document.getElementById("volt_on");
      const isEnabled = volToggle && volToggle.checked;
      let cmd = "";
      cmd += `sed -i "s|# ${config.marker}=.*|# ${config.marker}=${voltageValue}|" ${serviceFile}; `;
      if (isEnabled) {
        cmd += `echo '${voltageValue}' > ${config.path}; `;
        cmd += buildSedReplace(config.path, voltageValue);
      } else {
        cmd += `echo '0' > ${config.path}; `;
        cmd += buildSedReplace(config.path, "0");
      }
      scheduleIdle(() => execSafe(cmd));
    }, 150);
  });
});
const originalVolToggle = document.getElementById("volt_on");
if (originalVolToggle) {
  originalVolToggle.addEventListener("change", () => {
    const isEnabled = originalVolToggle.checked;
    voltageSliders.forEach(config => {
      const slider = document.getElementById(config.id);
      if (!slider) return;
      const sliderValue = Math.round(parseFloat(slider.value));
      const voltageValue = sliderValueToVoltage(sliderValue);
      const actualValue = isEnabled ? voltageValue : 0;
      const cmd = `echo '${actualValue}' > ${config.path}; ` +
                buildSedReplace(config.path, actualValue);
      scheduleIdle(() => execSafe(cmd));
    });
  });
}
// LocalStorage Auto-Apply (Checker)
let isInitialLoadComplete = false;
let commandQueue = [];
let isProcessingCommands = false;
const processCommandQueue = async () => {
  if (isProcessingCommands || commandQueue.length === 0) return;
  isProcessingCommands = true;
  while (commandQueue.length > 0) {
    const command = commandQueue.shift();
    try {
      await execSafe(command);
      await new Promise(resolve => setTimeout(resolve, 15));
    } catch { /* ¯\_(ツ)_/¯ */ }
  }
  isProcessingCommands = false;
};
const queueCommand = (cmd) => {
  commandQueue.push(cmd);
  setTimeout(processCommandQueue, 0);
};
scheduleIdle(() => {
  try {
    const readServiceValue = async (marker) => {
      try {
        const result = await execSafe(
          `grep -m1 "^[#][[:space:]]*${marker}=" ${serviceFile} | head -n1 | cut -d'=' -f2`
        );
        return result.trim();
      } catch {
        return null;
      }
    };
    const compareFreqValues = (stored, serviceValue) => {
      if (!stored || !serviceValue) return false;
      const storedNumeric = Number(stored.replace(/\s*Mhz\s*/i, ""));
      const serviceNumeric = Number(serviceValue.replace(/\s*Mhz\s*/i, ""));
      return storedNumeric === serviceNumeric;
    };
    setTimeout(() => {
      toggles.forEach(cfg => {
        const id = cfg.id;
        const cacheKey = `toggle_state_${id}`;
        const el = document.getElementById(id);
        if (!el) return;
        let storedState;
        if (localStorage.getItem(cacheKey) === null) {
          const defaultState = cfg.defaultState !== undefined ? cfg.defaultState : true;
          storedState = defaultState;
          localStorage.setItem(cacheKey, defaultState ? "1" : "0");
        } else {
          storedState = localStorage.getItem(cacheKey) === "1";
        }
        el.checked = storedState;
        const ev = new Event('change', { bubbles: true });
        el.dispatchEvent(ev);
      });
    }, 0);
    setTimeout(async () => {
      for (const id of ["frq_little", "frq_big"]) {
        const trigger = document.getElementById(id);
        if (!trigger) continue;
        const defaults = { frq_little: "1690 Mhz", frq_big: "1794 Mhz" };
        const marker = id === "frq_little" ? "lit" : "big";
        let storedValue;
        if (localStorage.getItem(id) === null) {
          storedValue = defaults[id];
          localStorage.setItem(id, storedValue);
        } else {
          storedValue = localStorage.getItem(id);
        }
        trigger.dataset.selected = storedValue;
        let rightSpan = trigger.querySelector(".popup-value");
        if (!rightSpan) {
          rightSpan = document.createElement("span");
          rightSpan.className = "popup-value";
          trigger.appendChild(rightSpan);
        }
        rightSpan.textContent = storedValue;
        setTimeout(async () => {
          try {
            const serviceValue = await readServiceValue(marker);
            const needsUpdate = !compareFreqValues(storedValue, serviceValue);
            if (needsUpdate) {
              const path = id === "frq_little"
                ? "/sys/devices/system/cpu/cpufreq/policy0/scaling_max_freq"
                : "/sys/devices/system/cpu/cpufreq/policy4/scaling_max_freq";
              const freqToggle = document.getElementById("frq_on");
              const isFreqEnabled = freqToggle && freqToggle.checked;
              const defaultValues = {
                frq_little: 1794000,
                frq_big: 2704000
              };
              let actualValue;
              if (isFreqEnabled) {
                const numeric = Number(storedValue.replace(/\s*Mhz\s*/i, ""));
                actualValue = numeric * 1000;
              } else {
                actualValue = defaultValues[id];
              }
              const markerLine = id === "frq_little" ? "# lit=" : "# big=";
              const cmd = `sed -i "s|${markerLine}.*|${markerLine}${storedValue}|" ${serviceFile}; ` +
                        `echo '${actualValue}' > ${path}; ` +
                        buildSedReplace(path, actualValue);
              queueCommand(cmd);
            }
          } catch { /* ¯\_(ツ)_/¯ */ }
        }, id === "frq_little" ? 120 : 200); 
      }
    }, 50); 
    setTimeout(async () => {
      for (let i = 0; i < voltageSliders.length; i++) {
        const config = voltageSliders[i];
        setTimeout(async () => {
          try {
            const cacheKey = `voltage_${config.id}`;
            const slider = document.getElementById(config.id);
            if (!slider) return;
            let storedVoltage;
            if (localStorage.getItem(cacheKey) === null) {
              const fallbackDefault = config.id === "volt_cp" ? 0 : -4;
              storedVoltage = fallbackDefault;
              localStorage.setItem(cacheKey, String(storedVoltage));
            } else {
              storedVoltage = Number(localStorage.getItem(cacheKey));
            }    
            slider.value = voltageToSliderValue(storedVoltage);
            const serviceValue = await readServiceValue(config.marker);
            const needsUpdate = serviceValue === null || Number(serviceValue) !== storedVoltage;
            if (needsUpdate) {
              const volToggle = document.getElementById("volt_on");
              const isEnabled = volToggle ? volToggle.checked : false;
              const actualValue = isEnabled ? storedVoltage : 0;
              const cmd = `
                if grep -m1 -q "^[#][[:space:]]*${config.marker}=" "${serviceFile}"; then
                  sed -i 's|^[#][[:space:]]*${config.marker}=.*|# ${config.marker}=${storedVoltage}|' "${serviceFile}";
                else
                  printf "# ${config.marker}=%s\\n" "${storedVoltage}" >> "${serviceFile}";
                fi
                echo '${actualValue}' > ${config.path};
                ${buildSedReplace(config.path, actualValue)}
              `.trim();
              queueCommand(cmd);
            }
          } catch { /* ¯\_(ツ)_/¯ */ }
        }, i * 80); 
      }
      setTimeout(() => {
        isInitialLoadComplete = true;
      }, voltageSliders.length * 80 + 450); 
    }, 250);
  } catch {
    setTimeout(() => {
      isInitialLoadComplete = true;
    }, 350);
  }
});
window.isInitialLoadComplete = () => isInitialLoadComplete;
});
