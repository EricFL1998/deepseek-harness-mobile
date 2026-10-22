/**
 * Demo 插件的内存状态。
 *
 * 只用进程内状态，不读写文件、不访问网络——Demo 要证明的是「原生端通过 RPC
 * 读取 Host 状态」，不是又一个真实业务插件。Host 重启后状态回到初始值，这本身
 * 也是演示的一部分：原生端永远以 Host 的返回值为准，不自己维护业务状态。
 */

/** 初始计数：42，取自《银河系漫游指南》，方便在截图/文档里认出来。 */
const INITIAL_COUNT = 42;

/**
 * 创建一个 Demo 状态仓。
 *
 * 每次 `apply()` 建一个，测试里可以建多个互不影响的实例。
 *
 * @returns {{ snapshot: () => object, bump: (by?: number) => object, reset: () => object, touch: (action: string) => object, startedAt: number }}
 */
export function createDemoState() {
  const startedAt = Date.now();
  let count = INITIAL_COUNT;
  let lastAction = 'boot';
  let updatedAt = startedAt;

  /** 当前状态的纯展示快照：可以直接 JSON 序列化给原生端。 */
  function snapshot() {
    return {
      greeting: 'Hello from dsh-plugin-demo',
      count,
      lastAction,
      updatedAt,
      startedAt,
      uptimeMs: Date.now() - startedAt,
    };
  }

  /**
   * 服务端 +n。
   *
   * @param {number} [by] 增量，默认 1；非有限数按 1 处理，避免 Demo 被 NaN 污染。
   * @returns {object} 更新后的快照。
   */
  function bump(by = 1) {
    const delta = Number.isFinite(by) ? Math.trunc(by) : 1;
    count += delta;
    lastAction = `bump(${delta})`;
    updatedAt = Date.now();
    return snapshot();
  }

  /** 重置回初始状态。 */
  function reset() {
    count = INITIAL_COUNT;
    lastAction = 'reset';
    updatedAt = Date.now();
    return snapshot();
  }

  /**
   * 记录一次由原生事件触发的动作。
   *
   * @param {string} action 动作名（`bump` / `reset` / ...）。
   * @returns {object} 更新后的快照。
   */
  function touch(action) {
    lastAction = action;
    updatedAt = Date.now();
    return snapshot();
  }

  return { snapshot, bump, reset, touch, startedAt };
}
