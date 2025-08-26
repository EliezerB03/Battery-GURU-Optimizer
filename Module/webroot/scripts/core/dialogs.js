// Dialog Window Behavior
document.addEventListener("DOMContentLoaded", () => {
  const mask = document.querySelector('.oui-dialog-mask');
  let activeDialog = null;
  const preventTouch = (e) => e.preventDefault();
  let startY = 0;
  const onDialogTouchStart = (e) => {
    startY = e.touches[0].clientY;
  };
  const onDialogTouchMove = (e) => {
    if (!activeDialog) return;
    const dialog = activeDialog;
    const scrollTop = dialog.scrollTop;
    const scrollHeight = dialog.scrollHeight;
    const offsetHeight = dialog.offsetHeight;
    const currentY = e.touches[0].clientY;
    const isScrollingDown = currentY > startY;
    const isScrollingUp = currentY < startY;
    if (
      (scrollTop === 0 && isScrollingDown) ||
      (scrollTop + offsetHeight >= scrollHeight && isScrollingUp)
    ) {
      e.preventDefault();
    }
    startY = currentY;
  };
  const openDialog = (dialog) => {
    dialog.classList.add('show');
    mask.classList.add('show');
    activeDialog = dialog;
    document.body.classList.add('no-scroll');
    document.addEventListener('touchmove', preventTouch, { passive: false });
    dialog.addEventListener('touchstart', onDialogTouchStart, { passive: false });
    dialog.addEventListener('touchmove', onDialogTouchMove, { passive: false });
  };
  const closeDialog = () => {
    if (!activeDialog) return;
    activeDialog.classList.remove('show');
    activeDialog.classList.add('hide');
    mask.classList.remove('show');
    document.body.classList.remove('no-scroll');
    document.removeEventListener('touchmove', preventTouch);
    activeDialog.removeEventListener('touchstart', onDialogTouchStart);
    activeDialog.removeEventListener('touchmove', onDialogTouchMove);
    setTimeout(() => {
      activeDialog.classList.remove('hide');
      activeDialog = null;
    }, 5);
  };
  document.querySelectorAll('[data-dialog-target]').forEach(button => {
    button.addEventListener('click', () => {
      const targetId = button.getAttribute('data-dialog-target');
      const dialog = document.getElementById(targetId);
      if (dialog) openDialog(dialog);
    });
  });
  document.querySelectorAll('.close-dialog-btn').forEach(closeBtn => {
    closeBtn.addEventListener('click', closeDialog);
  });
  mask.addEventListener('click', (e) => {
    const closingAppDialog = document.getElementById('closing_app_dialog');
    if (closingAppDialog && closingAppDialog.classList.contains('show')) {
      e.preventDefault();
      e.stopPropagation();
      return false;
    }
    closeDialog();
  });
});
