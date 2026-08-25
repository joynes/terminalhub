(() => {
  const input = document.querySelector('#doc-search');
  const status = document.querySelector('#search-status');
  const sections = [...document.querySelectorAll('.doc-section')];
  const navLinks = [...document.querySelectorAll('.section-nav a')];

  input?.addEventListener('input', () => {
    const query = input.value.trim().toLowerCase();
    let visible = 0;
    sections.forEach((section) => {
      const matches = !query || section.textContent.toLowerCase().includes(query);
      section.classList.toggle('search-hidden', !matches);
      if (matches) visible += 1;
    });
    status.textContent = query ? `${visible} section${visible === 1 ? '' : 's'} match` : '';
  });

  if ('IntersectionObserver' in window) {
    const observer = new IntersectionObserver((entries) => {
      const visible = entries.filter((entry) => entry.isIntersecting)
        .sort((a, b) => b.intersectionRatio - a.intersectionRatio)[0];
      if (!visible) return;
      navLinks.forEach((link) => link.classList.toggle('active', link.hash === `#${visible.target.id}`));
    }, { rootMargin: '-18% 0px -65% 0px', threshold: [0, 0.15, 0.5] });
    sections.forEach((section) => observer.observe(section));
  }
})();
