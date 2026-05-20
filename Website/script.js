const navItems = [
  ["about", "About", "/about.html"],
  ["support", "Support", "/support.html"],
  ["faq", "FAQ", "/faq.html"],
  ["beta", "Download", "/beta.html"],
];

const footerItems = {
  privacy: ["Privacy", "/privacy.html"],
  legal: ["Legal", "/legal.html"],
  faq: ["FAQ", "/faq.html"],
  github: ["GitHub", "https://github.com/MobileSlicerApp/MobileSlicer"],
  beta: ["Download", "/beta.html"],
  home: ["Home", "/"],
};

class SiteHeader extends HTMLElement {
  connectedCallback() {
    const active = this.getAttribute("active") || "";
    const links = navItems
      .map(([key, label, href]) => {
        const className = key === active ? ' class="active"' : "";
        return `<a${className} href="${href}">${label}</a>`;
      })
      .join("");

    this.innerHTML = `
      <header class="site-header">
        <a class="brand" href="/" aria-label="MobileSlicer home">
          <img src="/assets/mobileslicer-logo.svg" alt="">
          <span><strong>MobileSlicer</strong><small>Local touch-first 3D slicer</small></span>
        </a>
        <nav class="tabs" aria-label="Primary navigation">${links}</nav>
      </header>
    `;
  }
}

class SiteFooter extends HTMLElement {
  connectedCallback() {
    const keys = (this.getAttribute("links") || "privacy,legal,faq,github,beta")
      .split(",")
      .map((key) => key.trim())
      .filter(Boolean);
    const links = keys
      .map((key) => footerItems[key])
      .filter(Boolean)
      .map(([label, href]) => {
        const external = href.startsWith("https://") ? ' target="_blank" rel="noopener noreferrer"' : "";
        return `<a href="${href}"${external}>${label}</a>`;
      })
      .join("");

    this.innerHTML = `
      <footer>
        <span>MobileSlicer</span>
        <nav class="footer-links" aria-label="Footer navigation">${links}</nav>
      </footer>
    `;
  }
}

if (!customElements.get("site-header")) {
  customElements.define("site-header", SiteHeader);
}

if (!customElements.get("site-footer")) {
  customElements.define("site-footer", SiteFooter);
}
