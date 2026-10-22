window.__ModuleLoader__.load({
  id: "dsh-plugin-demo",
  factory: (require) => {
    const React = require("react");

    /**
     * 浏览器半边的文案表。
     *
     * 桥接层会静态读取这里的对象字面量（`const zh = { ... }`），把
     * `label: () => t("nav")` 解析成当前语言下的标题，因此键值必须是静态字符串。
     */
    const zh = {
      nav: "Demo 插件",
      intro: "SDK 接入示例：JS 插件负责 Host 逻辑与 RPC，伴生 APK 负责原生 UI。",
      webNote: "当前是 Web 回退界面。安装 Demo 伴生 APK 后，这一页会被原生页面接管。",
      refresh: "刷新",
      bump: "服务端 +1",
      reset: "重置",
      count: "计数",
      lastAction: "上次动作",
      updatedAt: "更新时间",
      endpoints: "RPC 端点",
      loading: "读取中…",
      unavailable: "Host RPC 未连接",
      rpcError: "RPC 失败",
      dockHint: "Demo · 点按查看",
    };
    const en = {
      nav: "Demo plugin",
      intro: "SDK sample: the JS plugin owns Host logic and RPC; the companion APK owns the native UI.",
      webNote: "This is the Web fallback. Install the Demo companion APK to let the native settings page take over.",
      refresh: "Refresh",
      bump: "Server +1",
      reset: "Reset",
      count: "Count",
      lastAction: "Last action",
      updatedAt: "Updated",
      endpoints: "RPC endpoints",
      loading: "Loading…",
      unavailable: "Host RPC not connected",
      rpcError: "RPC failed",
      dockHint: "Demo · tap to inspect",
    };

    /** 选一份字典；桥接 VM 里没有 navigator，固定英文即可。 */
    const t = (key) => {
      const language = typeof navigator === "undefined"
        ? "en"
        : String(navigator.language || "en");
      const table = /^zh/i.test(language) ? zh : en;
      return table[key] ?? key;
    };

    /**
     * 绑定本插件的 RPC 调用器。
     *
     * Web 与原生走同一个通道：`connection.rpc.call("/api", "demo.status", payload)`
     * 返回 Host 的信封结果 `{ ok, value | error }`。
     *
     * @param {any} rpc 浏览器连接的 RPC transport。
     * @returns {(endpoint: string, payload?: object) => Promise<any>}
     */
    function createDemoCall(rpc) {
      return async function call(endpoint, payload = {}) {
        let result;
        try {
          result = await rpc.call("/api", `demo.${endpoint}`, payload);
        } catch (error) {
          throw new Error(error instanceof Error ? error.message : String(error));
        }
        if (!result?.ok) {
          throw new Error(result?.error?.message ?? "demo RPC failed");
        }
        return result.value;
      };
    }

    /**
     * 设置页 Web 回退：读一次 `demo.status`，演示 RPC 数据驱动 UI。
     * 装上伴生 APK 后，App 会直接用原生页面接管这个 `settings.section`。
     *
     * @param {{ call: Function }} props 注册时注入的能力。
     */
    function DemoSettingsSection(props) {
      const call = props.call;
      const [status, setStatus] = React.useState(null);
      const [error, setError] = React.useState(null);
      const [busy, setBusy] = React.useState(false);

      const load = React.useCallback(async () => {
        if (typeof call !== "function") {
          setError(t("unavailable"));
          return;
        }
        try {
          setStatus(await call("status"));
          setError(null);
        } catch (failure) {
          setError(`${t("rpcError")}: ${failure.message ?? failure}`);
        }
      }, [call]);

      React.useEffect(() => {
        void load();
      }, [load]);

      const run = async (endpoint) => {
        if (typeof call !== "function" || busy) return;
        setBusy(true);
        try {
          setStatus(await call(endpoint));
          setError(null);
        } catch (failure) {
          setError(`${t("rpcError")}: ${failure.message ?? failure}`);
        } finally {
          setBusy(false);
        }
      };

      const row = (label, value) => React.createElement(
        "div",
        { style: { display: "flex", justifyContent: "space-between", gap: 12 } },
        React.createElement("span", { style: { opacity: 0.7 } }, label),
        React.createElement("span", { style: { fontVariantNumeric: "tabular-nums" } }, String(value)),
      );

      return React.createElement(
        "div",
        { style: { display: "flex", flexDirection: "column", gap: 12, padding: "16px 4px" } },
        React.createElement("p", { style: { margin: 0, opacity: 0.8 } }, t("intro")),
        React.createElement("p", { style: { margin: 0, fontSize: 12, opacity: 0.6 } }, t("webNote")),
        status === null
          ? React.createElement("p", { style: { margin: 0, opacity: 0.7 } }, error ?? t("loading"))
          : React.createElement(
            "div",
            {
              style: {
                display: "flex",
                flexDirection: "column",
                gap: 8,
                padding: 12,
                border: "1px solid color-mix(in srgb, currentColor 18%, transparent)",
                borderRadius: 12,
              },
            },
            row(t("count"), status.count),
            row(t("lastAction"), status.lastAction),
            row(t("updatedAt"), new Date(status.updatedAt).toLocaleTimeString()),
          ),
        error !== null && status !== null
          ? React.createElement("p", { style: { margin: 0, fontSize: 12, color: "crimson" } }, error)
          : null,
        React.createElement(
          "div",
          { style: { display: "flex", gap: 8, flexWrap: "wrap" } },
          React.createElement("button", { type: "button", disabled: busy, onClick: () => run("status") }, t("refresh")),
          React.createElement("button", { type: "button", disabled: busy, onClick: () => run("bump") }, t("bump")),
          React.createElement("button", { type: "button", disabled: busy, onClick: () => run("reset") }, t("reset")),
        ),
      );
    }

    /** 输入栏 dock 的 Web 回退：一个小胶囊，点击即调 RPC。 */
    function DemoDockChip(props) {
      const call = props.call;
      const [status, setStatus] = React.useState(null);

      React.useEffect(() => {
        if (typeof call !== "function") return;
        let active = true;
        call("status").then((value) => {
          if (active) setStatus(value);
        }).catch(() => {});
        return () => {
          active = false;
        };
      }, [call]);

      return React.createElement(
        "span",
        {
          role: "button",
          tabIndex: 0,
          title: t("dockHint"),
          onClick: () => {
            if (typeof call === "function") {
              call("bump").then(setStatus).catch(() => {});
            }
          },
          style: { cursor: "pointer", fontSize: 12, opacity: 0.85 },
        },
        `Demo ${status?.count ?? "·"}`,
      );
    }

    /** 需要的最小服务：槽位注册 + RPC 调用；connection 用来建调用器。 */
    const inject = ["slots", "connection"];

    /**
     * 注册两个 Web 落点。
     *
     * 这两个注册同时是 App 感知组件身份的来源：`settings.section` 的 `demo`
     * 与伴生 APK 的 `PageEntry(id = "demo")` 配对后，设置页会被原生接管。
     *
     * @param {any} ctx 客户端 Cordis 上下文。
     */
    function apply(ctx) {
      const connection = typeof ctx.get === "function" ? ctx.get("connection") : undefined;
      const call = connection?.rpc ? createDemoCall(connection.rpc) : null;

      ctx.slots.inject("settings.section", () => ctx.slots.register({
        name: "settings.section",
        id: "demo",
        order: 10,
        label: () => t("nav"),
        inject: () => ({ call, t }),
      }, DemoSettingsSection));

      ctx.slots.inject("conversation.composer.dock", () => ctx.slots.register({
        name: "conversation.composer.dock",
        id: "demo-dock",
        order: 10,
        inject: () => ({ call }),
      }, DemoDockChip));
    }

    return { name: "dsh-plugin-demo", inject, apply };
  },
});
