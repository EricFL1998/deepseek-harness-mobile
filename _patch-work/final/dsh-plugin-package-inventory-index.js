import { createRequire } from "node:module";
import { existsSync, readFileSync } from "node:fs";
import { dirname, isAbsolute, join, parse } from "node:path";
import { fileURLToPath, pathToFileURL } from "node:url";
import z from "@deepseek-ai/schemastery";
import { brandString } from "@deepseek-ai/dsh-brand";
//#region lib/types/index.js
/**
* Active Loader-backed plugin package inventory for official DeepSeek requests.
* Host entries and the requesting agent's standing preset are resolved at request time;
* installed dependencies and plugin fibers without Loader-backed package identity are excluded.
* @module @deepseek-ai/dsh-plugin-package-inventory-deepseek
*/
/** Cordis plugin name. */
const name = "plugin-package-inventory-deepseek";
/** Services required to locate host/requesting-agent entries and contribute the field. */
const inject = [
	"agents",
	"deepseekLlmApiExtensions",
	"loader"
];
/** Validated plugin-package request contribution configuration. */
const Config = z.object({ enabled: z.boolean().default(true) });
/** Parse a bare package or package-subpath specifier into its package name. */
function barePackageName(specifier) {
	if (specifier.startsWith(".") || specifier.includes(":") || isAbsolute(specifier)) return void 0;
	const [first = "", second = ""] = specifier.split("/");
	return first.startsWith("@") ? `${first}/${second}` : first;
}
/** Read a named identity, including its version only when it is a non-blank string. */
function identityFromManifest(path) {
	const manifest = JSON.parse(readFileSync(path, "utf8"));
	if (typeof manifest.name !== "string" || manifest.name.trim().length === 0) return void 0;
	return typeof manifest.version === "string" && manifest.version.trim().length > 0 ? {
		name: manifest.name,
		version: manifest.version
	} : { name: manifest.name };
}
/** Resolve a bare package without requiring it to export `./package.json`. */
function barePackageManifest(packageName, anchors, packages) {
	for (const anchor of anchors) {
		const pkg = packages?.packageOf(packageName, anchor);
		if (pkg !== void 0) return pkg.manifestPath;
		if (packages !== void 0) continue;
		for (const searchPath of createRequire(anchor).resolve.paths(packageName)) {
			const manifest = join(searchPath, packageName, "package.json");
			if (existsSync(manifest)) return manifest;
		}
	}
}
/** Find the nearest owning manifest for a relative or absolute plugin module. */
function nearestManifest(modulePath) {
	let current = dirname(modulePath);
	const root = parse(current).root;
	while (true) {
		const manifest = join(current, "package.json");
		if (existsSync(manifest)) return manifest;
		if (current === root) return void 0;
		current = dirname(current);
	}
}
/** Exact package identity resolver with immutable per-process manifest caching. */
var PackageIdentityResolver = class {
	hostBaseUrl;
	packages;
	cache = /* @__PURE__ */ new Map();
	constructor(hostBaseUrl, packages) {
		this.hostBaseUrl = hostBaseUrl;
		this.packages = packages;
	}
	/** Resolve one Loader entry's owning package, or absence for a non-package loose module. */
	resolve({ moduleName, baseUrl, bareBaseUrl }) {
		/* v8 ignore next -- Loader entry trees inherit a base URL; the fallback supports direct embedders. */
		const treeBase = baseUrl ?? this.hostBaseUrl;
		const anchors = [...new Set([
			bareBaseUrl ?? treeBase,
			treeBase,
			this.hostBaseUrl,
			import.meta.url
		])];
		const key = `${anchors.join("\0")}\u0000${moduleName}`;
		if (this.cache.has(key)) return this.cache.get(key);
		const packageName = barePackageName(moduleName);
		let manifest;
		if (packageName !== void 0) {
			manifest = barePackageManifest(packageName, anchors, this.packages);
			if (manifest === void 0) throw new Error(`plugin-package-inventory-deepseek: cannot resolve active package ${JSON.stringify(packageName)}`);
		} else if (!moduleName.startsWith("cordis:")) {
			const moduleUrl = isAbsolute(moduleName) ? pathToFileURL(moduleName) : new URL(moduleName, treeBase);
			if (moduleUrl.protocol === "file:") manifest = nearestManifest(fileURLToPath(moduleUrl));
		}
		const identity = manifest === void 0 ? void 0 : identityFromManifest(manifest);
		this.cache.set(key, identity);
		return identity;
	}
};
/** Yield active, non-structural entries from one Loader tree. */
function activeEntries(tree) {
	return [...tree.entries()].filter((entry) => !entry.options.group && !entry.disabled && entry.fiber?.state === 2).map((entry) => ({
		moduleName: entry.options.name,
		...entry.parent.tree.ctx.baseUrl === void 0 ? {} : { baseUrl: entry.parent.tree.ctx.baseUrl }
	}));
}
/** Deterministic text order independent of the host's ICU data and locale. */
function compareWireText(left, right) {
	return left < right ? -1 : left > right ? 1 : 0;
}
/** Collect available identities from active packages for one request. */
function collectActivePluginPackages(ctx, resolver, hostBaseUrl, unreadable, sessionId) {
	const entries = activeEntries(ctx.loader);
	const presets = ctx.get("agentPresets");
	if (sessionId !== void 0 && presets !== void 0) {
		const agent = ctx.agents.get(brandString(sessionId));
		if (agent !== void 0) for (const composition of presets.inspectCompositions(agent.ctx)) entries.push(...composition.modules.map(({ useHostBase, ...entry }) => ({
			...entry,
			...useHostBase ? { bareBaseUrl: hostBaseUrl } : {}
		})));
	}
	const unique = /* @__PURE__ */ new Map();
	for (const activeEntry of entries) {
		let identity;
		try {
			identity = resolver.resolve(activeEntry);
		} catch (error) {
			if (!unreadable.has(activeEntry.moduleName)) {
				unreadable.add(activeEntry.moduleName);
				ctx.logger.warn("plugin-package-inventory-deepseek: omitting unreadable package identity for %s: %o", activeEntry.moduleName, error);
			}
			continue;
		}
		if (identity === void 0) continue;
		unique.set(`${identity.name}\u0000${identity.version ?? ""}`, identity);
	}
	return [...unique.values()].sort((left, right) => compareWireText(left.name, right.name) || compareWireText(left.version ?? "", right.version ?? ""));
}
/**
* Register the complete `dsh_plugin_packages` request contribution when enabled.
* @param ctx - plugin context carrying Loader entry metadata and the DeepSeek request-extension registry.
* @param config - validated default-on configuration.
*/
function apply(ctx, config) {
	if (config.enabled === false) return;
	const hostBaseUrl = ctx.baseUrl ?? import.meta.url;
	const resolver = new PackageIdentityResolver(hostBaseUrl, ctx.get("pluginPackages"));
	const unreadable = /* @__PURE__ */ new Set();
	ctx.deepseekLlmApiExtensions.register("dsh_plugin_packages", { prepare: (request) => {
		return { value: {
			version: 1,
			packages: collectActivePluginPackages(ctx, resolver, hostBaseUrl, unreadable, request.sessionId)
		} };
	} });
}
//#endregion
export { Config, apply, inject, name };
