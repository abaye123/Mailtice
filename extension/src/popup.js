import { localize } from "./i18n.js";

localize();
document.getElementById("open").addEventListener("click", async () => {
  await chrome.tabs.create({ url: chrome.runtime.getURL("export.html") });
  window.close();
});
