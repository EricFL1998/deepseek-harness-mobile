/**
 * DSH Mobile - Built-in Surface Adapter (v4.0 §7.3)
 * Injected before web client bundles in index.html
 */
(function() {
  if (window.__DSH_NATIVE_SURFACE__) return;

  const catalog = {
    clientId: "dsh-mobile-native",
    dshVersion: "4.0.0",
    surfaces: []
  };

  const listeners = [];

  function notifyNative(type, payload) {
    if (window.DshNativeBridge && typeof window.DshNativeBridge.postMessage === 'function') {
      window.DshNativeBridge.postMessage(JSON.stringify({ type, payload }));
    }
  }

  // 1. Hook ctx.slots.register
  function hookSlotRegistry(target) {
    if (!target || target.__dsh_hooked) return;
    target.__dsh_hooked = true;

    const originalRegister = target.register;
    if (typeof originalRegister === 'function') {
      target.register = function(slotDescriptor, component, options) {
        let slotName = typeof slotDescriptor === 'string' ? slotDescriptor : (slotDescriptor && slotDescriptor.name);
        const pluginId = (options && options.pluginId) || (slotDescriptor && slotDescriptor.id) || (component && component.pluginId) || "unknown";
        const order = (options && options.order) || (slotDescriptor && slotDescriptor.order) || 0;
        const title = (options && options.title) || (slotDescriptor && slotDescriptor.title) || (component && component.title) || null;

        let kind = "page";
        if (slotName.startsWith("sidebar")) kind = "sidebar";
        else if (slotName.includes("dock") || slotName.includes("composer")) kind = "dock";
        else if (slotName.includes("overlay") || slotName.includes("toast")) kind = "overlay";
        else if (slotName.includes("message") || slotName.includes("assistant-actions")) kind = "message-action";
        else if (slotName.includes("toolview") || slotName.includes("tool.call")) kind = "tool";
        else if (slotName.includes("theme")) kind = "theme";
        else if (slotName.includes("settings") || slotName.startsWith("plugins.")) kind = "page";

        const item = {
          pluginId,
          slot: slotName,
          order,
          title,
          kind,
          capabilities: ["open"]
        };

        const exists = catalog.surfaces.some(s => s.pluginId === pluginId && s.slot === slotName);
        if (!exists) {
          catalog.surfaces.push(item);
          notifyNative("surface_catalog_updated", catalog);
        }

        return originalRegister.apply(this, arguments);
      };
    }
  }

  // 2. 检查隔离模式参数: ?__dsh_surface=<slot>&__dsh_plugin=<id>&__dsh_shell=none
  function checkIsolationMode() {
    const params = new URLSearchParams(window.location.search);
    const targetSurface = params.get("__dsh_surface");
    const targetPlugin = params.get("__dsh_plugin");
    const shellMode = params.get("__dsh_shell");

    if (targetSurface && shellMode === "none") {
      document.documentElement.classList.add("dsh-surface-isolated");
      const style = document.createElement("style");
      style.textContent = `
        html.dsh-surface-isolated, body.dsh-surface-isolated {
          background: transparent !important;
          overflow: auto !important;
        }
        .dsh-shell-header, .dsh-shell-sidebar, .dsh-shell-footer {
          display: none !important;
        }
      `;
      document.head.appendChild(style);

      // 上报尺寸与 ready 信号
      window.addEventListener("DOMContentLoaded", () => {
        setTimeout(() => {
          notifyNative("surface_isolated_ready", {
            surface: targetSurface,
            plugin: targetPlugin,
            width: document.body.scrollWidth,
            height: document.body.scrollHeight
          });
        }, 500);
      });
    }
  }

  // 3. 暴露 window.__DSH_NATIVE_SURFACE__
  window.__DSH_NATIVE_SURFACE__ = {
    ready: true,
    catalog: catalog,
    hookSlotRegistry: hookSlotRegistry,
    openSurface: function(slot, pluginId) {
      notifyNative("open_surface", { slot, pluginId });
    }
  };

  checkIsolationMode();
  notifyNative("surface_bridge_init", { ready: true });
})();
