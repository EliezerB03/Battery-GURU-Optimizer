// Loading Animation
document.addEventListener("DOMContentLoaded", () => {
  const mask = document.querySelector(".oui-loading-mask");
  const loader = document.querySelector(".oui-loading");
  const circle = document.querySelector(".oui-loading-circle");
  function blockInteractions() {
    document.body.style.pointerEvents = 'none';
    document.body.style.userSelect = 'none';
    document.body.style.overflow = 'hidden';
    document.documentElement.style.pointerEvents = 'none';
    document.documentElement.style.userSelect = 'none';
  }
  function unblockInteractions() {
    document.body.style.pointerEvents = '';
    document.body.style.userSelect = '';
    document.body.style.overflow = '';
    document.documentElement.style.pointerEvents = '';
    document.documentElement.style.userSelect = '';
  }
  blockInteractions();
  setTimeout(() => {
    mask.classList.add("active");
    circle.classList.add("active"); 
    loader.classList.add("active");
    document.body.classList.add("no-scroll"); 
  }, 5);
  setTimeout(() => {
    mask.classList.remove("active");
    circle.classList.remove("active");
    loader.classList.remove("active");
    document.body.classList.remove("no-scroll");
  }, 3300);
  mask.addEventListener('transitionend', (event) => {
    if (event.target === mask && !mask.classList.contains('active')) {
      setTimeout(() => {
        unblockInteractions();
      }, 5);
    }
  });
});
// Option/Toggle Select Behavior-Animation
document.querySelectorAll('.oui-container-toggle, .oui-bubble-item').forEach(option => {
  let pointerDown = false;
  let pointerInside = false;
  let cancelled = false;
  let moved = false;
  let animationTimeout = null;
  let startY = 0;
  const createEffect = () => {
    if (cancelled) return;
    option.querySelector('.toggle-effect')?.remove();
    const effect = document.createElement('span');
    effect.className = 'toggle-effect animate-in';
    const allOptions = [...option.parentElement.querySelectorAll('.oui-container-toggle, .oui-bubble-item')];
    const isFirst = option === allOptions[0];
    const isLast = option === allOptions[allOptions.length - 1];
    if (allOptions.length === 1) {
      effect.classList.add('rounded-all');
    } else if (isFirst) {
      effect.classList.add('rounded-top');
    } else if (isLast) {
      effect.classList.add('rounded-bottom');
    }
    option.appendChild(effect);
    const inner = option.querySelector('.oui-inner');
    if (inner) {
      inner.classList.add('pressed-effect');
    }
  };
  const cleanupEffect = () => {
    clearTimeout(animationTimeout);
    const effect = option.querySelector('.toggle-effect');
    if (!effect) return;
    effect.classList.remove('animate-in');
    effect.classList.add('animate-out');
    const inner = option.querySelector('.oui-inner');
    if (inner) {
      inner.classList.remove('pressed-effect');
      inner.classList.add('released-effect');
    }
    animationTimeout = setTimeout(() => {
      option.classList.remove('released-effect');
      effect.remove();
    }, 550);
  };
  const onMove = (e) => {
    if (!pointerDown) return;
    const clientY = e.touches ? e.touches[0].clientY : e.clientY;
    const deltaY = Math.abs(clientY - startY);
    if (!moved && deltaY > 8) {
      cancelled = true;
      moved = true;
      cleanupEffect();
      pointerInside = false;
      const inner = option.querySelector('.oui-inner');
      if (inner) {
        inner.classList.remove('pressed-effect');
      }
      return;
    }
  };
  const onUp = (e) => {
    if (!pointerDown) return;
    pointerDown = false;
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    document.removeEventListener('touchmove', onMove);
    document.removeEventListener('touchend', onUp);
    if (pointerInside && !cancelled && !moved) {
      option.click();
    }
    cleanupEffect();
    pointerInside = false;
    cancelled = false;
    moved = false;
  };
  option.addEventListener('click', (e) => {
    if (!pointerInside) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);
  option.addEventListener('touchstart', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startY = e.touches[0].clientY;
    clearTimeout(animationTimeout);
    const inner = option.querySelector('.oui-inner');
    if (inner) {
      inner.classList.remove('released-effect');
      inner.classList.add('pressed-effect');
    }
    createEffect();
    document.addEventListener('touchmove', onMove);
    document.addEventListener('touchend', onUp);
  });
  option.addEventListener('pointerdown', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startY = e.touches ? e.touches[0].clientY : e.clientY;
    clearTimeout(animationTimeout);
    const inner = option.querySelector('.oui-inner');
    if (inner) {
      inner.classList.remove('released-effect');
      inner.classList.add('pressed-effect');
    }
    createEffect();
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
    document.addEventListener('touchmove', onMove);
    document.addEventListener('touchend', onUp);
  });
});
// Button Press Behavior-Animation
document.querySelectorAll('.oui-button').forEach(button => {
  let pointerDown = false;
  let pointerInside = false;
  let cancelled = false;
  let moved = false;
  let animationTimeout = null;
  let startY = 0;
  const createEffect = () => {
    if (cancelled) return;
    button.querySelector('.button-effect')?.remove();
    const effect = document.createElement('span');
    effect.className = 'button-effect animate-in';
    const allButtons = [...button.parentElement.querySelectorAll('.oui-button')];
    if (allButtons.length === 1) {
      effect.classList.add('rounded-all');
    }
    button.appendChild(effect);
    button.classList.add('pressed-effect');
  };
  const cleanupEffect = () => {
    clearTimeout(animationTimeout);
    const effect = button.querySelector('.button-effect');
    if (!effect) return;
    effect.classList.remove('animate-in');
    effect.classList.add('animate-out');
    button.classList.remove('pressed-effect');
    button.classList.add('released-effect');
    animationTimeout = setTimeout(() => {
      button.classList.remove('released-effect');
      effect.remove();
    }, 550);
  };
  const onMove = (e) => {
    if (!pointerDown) return;
    const deltaY = Math.abs(e.clientY - startY);
    if (!moved && deltaY > 8) {
      cancelled = true;
      moved = true;
      cleanupEffect();
      pointerInside = false;
      button.classList.remove('pressed-effect');
      return;
    }
  };
  const onUp = (e) => {
    if (!pointerDown) return;
    pointerDown = false;
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    if (pointerInside && !cancelled && !moved) {
      button.click();
    }
    cleanupEffect();
    pointerInside = false;
    cancelled = false;
    moved = false;
  };
  button.addEventListener('click', (e) => {
    if (!pointerInside) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);
  button.addEventListener('pointerdown', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startY = e.clientY;
    clearTimeout(animationTimeout);
    button.classList.remove('released-effect');
    button.classList.add('pressed-effect');
    createEffect();
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
  });
});
// NavBar Press Behavior-Animation
document.querySelectorAll('.oui-tab-link').forEach(tab => {
  let pointerDown = false;
  let pointerInside = false;
  let cancelled = false;
  let moved = false;
  let animationTimeout = null;
  let startX = 0;
  const tabInner = tab.closest('.oui-tab-inner');
  const createEffect = () => {
    if (cancelled) return;
    tab.querySelector('.tab-button-effect')?.remove();
    const effect = document.createElement('span');
    effect.className = 'tab-button-effect animate-in rounded-all';
    tab.appendChild(effect);
    if (tabInner) {
      tabInner.classList.add('pressed-effect');
      tabInner.classList.remove('released-effect');
    }
  };
  const cleanupEffect = () => {
    clearTimeout(animationTimeout);
    const effect = tab.querySelector('.tab-button-effect');
    if (!effect) return;
    effect.classList.remove('animate-in');
    effect.classList.add('animate-out');
    if (tabInner) {
      tabInner.classList.remove('pressed-effect');
      tabInner.classList.add('released-effect');
    }
    animationTimeout = setTimeout(() => {
      if (tabInner) {
        tabInner.classList.remove('released-effect');
      }
      effect.remove();
    }, 550);
  };
  const onMove = (e) => {
    if (!pointerDown) return;
    const clientX = e.touches ? e.touches[0].clientX : e.clientX;
    const deltaX = Math.abs(clientX - startX);
    if (!moved && deltaX > 8) {
      cancelled = true;
      moved = true;
      cleanupEffect();
      pointerInside = false;
      if (tabInner) {
        tabInner.classList.remove('pressed-effect');
      }
      return;
    }
  };
  const onUp = (e) => {
    if (!pointerDown) return;
    pointerDown = false;
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    document.removeEventListener('touchmove', onMove);
    document.removeEventListener('touchend', onUp);
    if (pointerInside && !cancelled && !moved) {
      tab.click();
    }
    cleanupEffect();
    pointerInside = false;
    cancelled = false;
    moved = false;
  };
  tab.addEventListener('click', (e) => {
    if (!pointerInside) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);
  tab.addEventListener('touchstart', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startX = e.touches[0].clientX;
    clearTimeout(animationTimeout);
    if (tabInner) {
      tabInner.classList.remove('released-effect');
      tabInner.classList.add('pressed-effect');
    }
    createEffect();
    document.addEventListener('touchmove', onMove);
    document.addEventListener('touchend', onUp);
  });
  tab.addEventListener('pointerdown', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startX = e.touches ? e.touches[0].clientX : e.clientX;

    clearTimeout(animationTimeout);
    if (tabInner) {
      tabInner.classList.remove('released-effect');
      tabInner.classList.add('pressed-effect');
    }
    createEffect();
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
    document.addEventListener('touchmove', onMove);
    document.addEventListener('touchend', onUp);
  });
});
// Dialog Button Behavior-Animation
document.querySelectorAll('.oui-dialog-action-button').forEach(dialog => {
  let pointerDown = false;
  let pointerInside = false;
  let cancelled = false;
  let moved = false;
  let animationTimeout = null;
  let startY = 0;
  const createEffect = () => {
    if (cancelled) return;
    dialog.querySelector('.dialog-button-effect')?.remove();
    const effect = document.createElement('span');
    effect.className = 'dialog-button-effect animate-in rounded-all';
    dialog.appendChild(effect);
    dialog.classList.add('pressed-effect');
    dialog.classList.remove('released-effect');
  };
  const cleanupEffect = () => {
    clearTimeout(animationTimeout);
    const effect = dialog.querySelector('.dialog-button-effect');
    if (!effect) return;
    effect.classList.remove('animate-in');
    effect.classList.add('animate-out');
    dialog.classList.remove('pressed-effect');
    dialog.classList.add('released-effect');
    animationTimeout = setTimeout(() => {
      dialog.classList.remove('released-effect');
      effect.remove();
    }, 550);
  };
  const onMove = (e) => {
    if (!pointerDown) return;
    const deltaY = Math.abs(e.clientY - startY);
    if (!moved && deltaY > 8) {
      cancelled = true;
      moved = true;
      cleanupEffect();
      pointerInside = false;
      return;
    }
  };
  const onUp = (e) => {
    if (!pointerDown) return;
    pointerDown = false;
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    if (pointerInside && !cancelled && !moved) {
      dialog.click();
    }
    cleanupEffect();
    pointerInside = false;
    cancelled = false;
    moved = false;
  };
  dialog.addEventListener('pointerdown', (e) => {
    pointerDown = true;
    pointerInside = true;
    cancelled = false;
    moved = false;
    startY = e.clientY;
    clearTimeout(animationTimeout);
    createEffect();
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
  });
  dialog.addEventListener('click', (e) => {
    if (!pointerInside) {
      e.preventDefault();
      e.stopPropagation();
    }
  }, true);
});
// Info Button Animation only
document.querySelectorAll('.oui-overlay-bubble-info').forEach(info => {
  let animationTimeout = null;
  let pointerDown = false;
  let cancelled = false;
  const createEffect = () => {
    info.querySelector('.oui-icon-info-effect')?.remove();
    const effect = document.createElement('span');
    effect.className = 'oui-icon-info-effect animate-in rounded-all';
    info.appendChild(effect);
  };
  const cleanupEffect = () => {
    clearTimeout(animationTimeout);
    const effect = info.querySelector('.oui-icon-info-effect');
    if (!effect) return;
    effect.classList.remove('animate-in');
    effect.classList.add('animate-out');
    info.classList.remove('pressed-effect');
    info.classList.add('released-effect');
    animationTimeout = setTimeout(() => {
      info.classList.remove('released-effect');
      effect.remove();
    }, 550);
  };
  const isInside = (e) => {
    const rect = info.getBoundingClientRect();
    return e.clientX >= rect.left && e.clientX <= rect.right &&
           e.clientY >= rect.top && e.clientY <= rect.bottom;
  };
  const startAnimation = () => {
    clearTimeout(animationTimeout);
    info.classList.remove('released-effect');
    info.classList.add('pressed-effect');
    createEffect();
    animationTimeout = setTimeout(() => {
      cleanupEffect();
    }, 400);
  };
  info.addEventListener('pointerdown', (e) => {
    pointerDown = true;
    cancelled = false;
    startAnimation();
    document.addEventListener('pointermove', onMove);
    document.addEventListener('pointerup', onUp);
    document.addEventListener('pointercancel', onCancel);
  });
  const onMove = (e) => {
    if (!pointerDown || cancelled) return;
    if (!isInside(e)) {
      cancelled = true;
      clearTimeout(animationTimeout);
      if (info.classList.contains('pressed-effect')) {
        cleanupEffect();
      }
    }
  };
  const onUp = (e) => {
    if (!pointerDown) return;
    pointerDown = false;
    clearTimeout(animationTimeout);
    cleanupEffect();
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    document.removeEventListener('pointercancel', onCancel);
  };
  const onCancel = (e) => {
    pointerDown = false;
    clearTimeout(animationTimeout);
    cleanupEffect();
    document.removeEventListener('pointermove', onMove);
    document.removeEventListener('pointerup', onUp);
    document.removeEventListener('pointercancel', onCancel);
  };
});
// Glow Effect
(function(){
  const nav = document.querySelector('.oui-tab');
  const root = document.documentElement;
  function updateNavHeight(){
    const h = nav ? nav.getBoundingClientRect().height : 60;
    root.style.setProperty('--nav-height', `${Math.round(h)}px`);
  }
  window.addEventListener('load', updateNavHeight);
  window.addEventListener('resize', updateNavHeight);
  if (nav && 'ResizeObserver' in window) {
    new ResizeObserver(updateNavHeight).observe(nav);
  }
})();
