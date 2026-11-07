/** Fills every [data-i18n] element from _locales and sets the page direction (Hebrew is right to left). */
export function localize(root = document) {
  document.documentElement.lang = chrome.i18n.getUILanguage();
  document.documentElement.dir = chrome.i18n.getMessage("@@bidi_dir") || "ltr";
  for (const el of root.querySelectorAll("[data-i18n]")) el.textContent = chrome.i18n.getMessage(el.dataset.i18n);
}

export const t = (key, ...args) => chrome.i18n.getMessage(key, args.map(String));
