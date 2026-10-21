import { isAbsolute, join } from "node:path";
import { createHash, randomBytes, randomUUID } from "node:crypto";
import { mkdir, rename, rm, stat } from "node:fs/promises";
import { MAX_TIMER_DELAY_MS, TimeoutReason, deadline, timeoutOf } from "@deepseek-ai/dsh-timeout";
import { z } from "zod";
import "@deepseek-ai/dsh-experimental-speech-to-text/wave";
import { createReadStream, createWriteStream, readFileSync } from "node:fs";
import { Transform } from "node:stream";
import { pipeline } from "node:stream/promises";
import { fileURLToPath } from "node:url";
import z$1 from "@deepseek-ai/schemastery";
//#region lib/types/input.js
/** Language hints accepted by both provider metadata and native inference. */
const languages = [
	"auto",
	"zh",
	"en",
	"yue",
	"ja",
	"ko"
];
/** A request rejected before native inference; the loaded worker remains reusable. */
var SpeechInputError = class extends Error {};
//#endregion
//#region lib/types/download-error.js
const codes = [
	["dns", /^(ENOTFOUND|EAI_AGAIN)$/],
	["timeout", /^(ETIMEDOUT|ERR_SOCKET_CONNECTION_TIMEOUT|UND_ERR_(CONNECT|HEADERS|BODY)_TIMEOUT)$/],
	["certificate", /^(CERT_[A-Z_]+|ERR_TLS_CERT_ALTNAME_INVALID|DEPTH_ZERO_SELF_SIGNED_CERT|SELF_SIGNED_CERT_IN_CHAIN|UNABLE_TO_VERIFY_LEAF_SIGNATURE|UNABLE_TO_GET_ISSUER_CERT_LOCALLY)$/],
	["storage", /^(ENOSPC|EDQUOT|EACCES|EPERM|EROFS)$/],
	["network", /^(ECONNREFUSED|ECONNRESET|ENETUNREACH|EHOSTUNREACH|EPIPE|UND_ERR_SOCKET)$/]
];
/**
* Inspect native fetch causes, including aggregate connection attempts, without publishing their messages.
* @param failure - error received from the network or filesystem.
* @returns an actionable category and recognized diagnostic code, or a generic failure.
*/
function classifyDownloadFailure(failure) {
	const pending = [failure], visited = /* @__PURE__ */ new Set();
	let reason = "unknown";
	while (pending.length > 0) {
		const error = pending.shift();
		if (!(error instanceof Error) || visited.has(error)) continue;
		visited.add(error);
		if (error instanceof TimeoutReason || error.name === "TimeoutError") return { reason: "timeout" };
		if ("code" in error && typeof error.code === "string") {
			for (const [kind, pattern] of codes) if (pattern.test(error.code)) return {
				reason: kind,
				code: error.code
			};
		}
		if (error instanceof TypeError && error.message === "fetch failed") reason = "network";
		pending.push(error.cause);
		if (error instanceof AggregateError) {
			const causes = error.errors;
			pending.push(...causes);
		}
	}
	return { reason };
}
/** A preparation error whose public details exclude raw causes, credentials, signed URLs and local paths. */
var SpeechDownloadError = class extends Error {
	download;
	constructor(download, options) {
		super(`Unable to prepare ${download.resource} from ${download.source}: ${download.reason}${download.code ? ` (${download.code})` : ""}${download.status === void 0 ? "" : ` (HTTP ${download.status})`}`, options);
		this.download = download;
	}
};
//#endregion
//#region lib/types/model-sources.js
/** Host-side response comparison for pinned model files on Hugging Face-compatible origins. */
var __addDisposableResource$1 = function(env, value, async) {
	if (value !== null && value !== void 0) {
		if (typeof value !== "object" && typeof value !== "function") throw new TypeError("Object expected.");
		var dispose, inner;
		if (async) {
			if (!Symbol.asyncDispose) throw new TypeError("Symbol.asyncDispose is not defined.");
			dispose = value[Symbol.asyncDispose];
		}
		if (dispose === void 0) {
			if (!Symbol.dispose) throw new TypeError("Symbol.dispose is not defined.");
			dispose = value[Symbol.dispose];
			if (async) inner = dispose;
		}
		if (typeof dispose !== "function") throw new TypeError("Object not disposable.");
		if (inner) dispose = function() {
			try {
				inner.call(this);
			} catch (e) {
				return Promise.reject(e);
			}
		};
		env.stack.push({
			value,
			dispose,
			async
		});
	} else if (async) env.stack.push({ async: true });
	return value;
};
var __disposeResources$1 = (function(SuppressedError) {
	return function(env) {
		function fail(e) {
			env.error = env.hasError ? new SuppressedError(e, env.error, "An error was suppressed during disposal.") : e;
			env.hasError = true;
		}
		var r, s = 0;
		function next() {
			while (r = env.stack.pop()) try {
				if (!r.async && s === 1) return s = 0, env.stack.push(r), Promise.resolve().then(next);
				if (r.dispose) {
					var result = r.dispose.call(r.value);
					if (r.async) return s |= 2, Promise.resolve(result).then(next, function(e) {
						fail(e);
						return next();
					});
				} else s |= 1;
			} catch (e) {
				fail(e);
			}
			if (s === 1) return env.hasError ? Promise.reject(env.error) : Promise.resolve();
			if (env.hasError) throw env.error;
		}
		return next();
	};
})(typeof SuppressedError === "function" ? SuppressedError : function(error, suppressed, message) {
	var e = new Error(message);
	return e.name = "SuppressedError", e.error = error, e.suppressed = suppressed, e;
});
/**
* Prefer the first successful HEAD response while retaining other sources for download fallback.
* All probes settle before returning; if every probe fails, the configured order is preserved.
* @param assetUrl - revision-pinned upstream file URL.
* @param origins - nonempty configured origins, or one explicit deployment origin.
* @param timeoutMs - maximum probe duration, including redirects.
* @param signal - preparation cancellation or deadline.
* @returns deduplicated download URLs with the first responding source first; a single source needs no probe.
*/
async function orderModelSources(assetUrl, origins, timeoutMs, signal) {
	const env_1 = {
		stack: [],
		error: void 0,
		hasError: false
	};
	try {
		signal.throwIfAborted();
		const path = new URL(assetUrl).pathname;
		const urls = [...new Set(origins.map((origin) => new URL(path, origin).href))];
		if (urls.length === 1) return urls;
		const finished = new AbortController();
		const timeout = __addDisposableResource$1(env_1, deadline(signal, timeoutMs, "SPEECH_SOURCE_PROBE_TIMEOUT"), false);
		const probing = AbortSignal.any([timeout.signal, finished.signal]);
		const requests = urls.map(async (url) => ({
			url,
			response: await fetch(url, {
				method: "HEAD",
				signal: probing
			})
		}));
		let preferred;
		try {
			preferred = await Promise.any(requests.map(async (request) => {
				const { url, response } = await request;
				if (!response.ok) throw new Error(`Model source probe returned HTTP ${response.status}`);
				return url;
			}));
		} catch (_unavailableSources) {} finally {
			finished.abort();
			const settled = await Promise.allSettled(requests);
			await Promise.allSettled(settled.map(async (result) => {
				if (result.status === "fulfilled") await result.value.response.body?.cancel();
			}));
		}
		signal.throwIfAborted();
		return preferred === void 0 ? urls : [preferred, ...urls.filter((url) => url !== preferred)];
	} catch (e_1) {
		env_1.error = e_1;
		env_1.hasError = true;
	} finally {
		__disposeResources$1(env_1);
	}
}
//#endregion
//#region lib/types/runtime.js
async function matchesAsset(path, asset, signal) {
  signal.throwIfAborted();
  try {
    const info = await stat(path);
    if (!info.isFile()) throw new Error(`Speech asset is not a regular file: ${path}`);
    if (info.size !== asset.bytes) return false;
    const digest = createHash("sha256");
    for await (const chunk of createReadStream(path, { signal })) digest.update(chunk);
    return digest.digest("hex") === asset.sha256;
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    return false;
  }
}
function resolveRuntime(config) {
  if (!["darwin-arm64", "darwin-x64", "linux-arm64", "linux-x64", "win32-x64"].includes(`${process.platform}-${process.arch}`)) throw new Error(`Local speech is unavailable for ${process.platform}-${process.arch}`);
  const lock = JSON.parse(readFileSync(new URL("../runtime/assets.json", import.meta.url), "utf8"));
  const modelRoot = config.modelDirectory ?? join(config.dataRoot, "models", "sensevoice-onnx");
  return {
    lock,
    modelRoot,
    paths: {
      model: join(modelRoot, lock.models[config.precision].name),
      tokens: join(modelRoot, lock.tokens.name),
      vad: config.vadModelPath ?? join(config.dataRoot, "models", "silero", lock.vad.name),
      worker: fileURLToPath(new URL(import.meta.url.endsWith(".ts") ? "./worker.ts" : "./worker.js", import.meta.url))
    }
  };
}
async function verifyRuntime(config, paths, lock, signal) {
  signal.throwIfAborted();
  // Android/proot returns ENOSYS for fs.access(); stat is supported and retains
  // the original existence check, followed by the pinned size and SHA-256 checks.
  try {
    await Promise.all([paths.model, paths.tokens, paths.vad].map((path) => stat(path)));
  } catch (error) {
    if (error.code !== "ENOENT") throw error;
    return false;
  }
  const verified = [...config.modelDirectory === void 0 ? [[paths.model, lock.models[config.precision]], [paths.tokens, lock.tokens]] : [], ...config.vadModelPath === void 0 ? [[paths.vad, lock.vad]] : []];
  for (const [path, asset] of verified) if (!await matchesAsset(path, asset, signal)) return false;
  signal.throwIfAborted();
  return true;
}
export async function inspectRuntime(config, signal) {
  const { lock, paths } = resolveRuntime(config);
  return await verifyRuntime(config, paths, lock, signal) ? paths : void 0;
}
export async function downloadAsset(asset, root, signal, report = () => {}) {
  signal.throwIfAborted();
  const destination = join(root, asset.name);
  const partial = `${destination}.${randomUUID()}.part`;
  let source = new URL(asset.url).origin;
  try {
    await mkdir(root, { recursive: true });
    if (await matchesAsset(destination, asset, signal)) return destination;
    try {
      const response = await fetch(asset.url, { signal });
      source = new URL(response.url || asset.url).origin;
      if (!response.ok || !response.body) {
        await response.body?.cancel();
        throw new SpeechDownloadError({ resource: asset.name, source, reason: "http", status: response.status });
      }
      const digest = createHash("sha256");
      let completedBytes = 0;
      const publish = () => {
        report({ phase: "downloading", resource: asset.name, completedBytes, totalBytes: asset.bytes });
      };
      publish();
      const hashing = new Transform({
        transform(chunk, _encoding, callback) {
          completedBytes += chunk.length;
          if (completedBytes > asset.bytes) {
            callback(new SpeechDownloadError({ resource: asset.name, source, reason: "integrity" }));
            return;
          }
          digest.update(chunk);
          publish();
          callback(null, chunk);
        }
      });
      await pipeline(response.body, hashing, createWriteStream(partial, { flags: "wx", mode: 384 }), { signal });
      if (completedBytes !== asset.bytes || digest.digest("hex") !== asset.sha256) {
        throw new SpeechDownloadError({ resource: asset.name, source, reason: "integrity" });
      }
      signal.throwIfAborted();
      await rename(partial, destination);
      return destination;
    } finally {
      await rm(partial, { force: true });
    }
  } catch (error) {
    const timedOut = timeoutOf(signal);
    if (signal.aborted && !timedOut || error instanceof SpeechDownloadError) throw error;
    throw new SpeechDownloadError({ resource: asset.name, source, ...timedOut ? { reason: "timeout" } : classifyDownloadFailure(error) }, { cause: error });
  }
}
export async function prepareRuntime(_ctx, config, signal, report = () => {}) {
  const { lock, modelRoot, paths } = resolveRuntime(config);
  const download = async (asset, root, step) => {
    if (await matchesAsset(join(root, asset.name), asset, signal)) return;
    const origins = config.modelOrigin === void 0 ? config.modelOrigins : [config.modelOrigin];
    const urls = await orderModelSources(asset.url, origins, config.modelProbeTimeoutMs, signal);
    for (const [index, url] of urls.entries()) {
      try {
        await downloadAsset({ ...asset, url }, root, signal, (state) => {
          report({ ...state, step });
        });
        return;
      } catch (error) {
        if (signal.aborted || !(error instanceof SpeechDownloadError) || error.download.reason === "storage" || error.download.reason === "unknown" || index === urls.length - 1) throw error;
      }
    }
  };
  if (config.modelDirectory === void 0) {
    report({ phase: "checking", step: "model", startedAt: Date.now() });
    await download(lock.models[config.precision], modelRoot, "model");
    await download(lock.tokens, modelRoot, "model");
  }
  if (config.vadModelPath === void 0) {
    report({ phase: "checking", step: "vad", startedAt: Date.now() });
    await download(lock.vad, join(config.dataRoot, "models", "silero"), "vad");
  }
  report({ phase: "checking", step: "verify", startedAt: Date.now() });
  if (!await verifyRuntime(config, paths, lock, signal)) throw new Error("Speech model verification failed: missing or corrupted model files");
  return paths;
}
//#region lib/types/recognizer.js
/** One serial SenseVoice worker with request-owned cancellation and idle reclamation. */
var __addDisposableResource = function(env, value, async) {
	if (value !== null && value !== void 0) {
		if (typeof value !== "object" && typeof value !== "function") throw new TypeError("Object expected.");
		var dispose, inner;
		if (async) {
			if (!Symbol.asyncDispose) throw new TypeError("Symbol.asyncDispose is not defined.");
			dispose = value[Symbol.asyncDispose];
		}
		if (dispose === void 0) {
			if (!Symbol.dispose) throw new TypeError("Symbol.dispose is not defined.");
			dispose = value[Symbol.dispose];
			if (async) inner = dispose;
		}
		if (typeof dispose !== "function") throw new TypeError("Object not disposable.");
		if (inner) dispose = function() {
			try {
				inner.call(this);
			} catch (e) {
				return Promise.reject(e);
			}
		};
		env.stack.push({
			value,
			dispose,
			async
		});
	} else if (async) env.stack.push({ async: true });
	return value;
};
var __disposeResources = (function(SuppressedError) {
	return function(env) {
		function fail(e) {
			env.error = env.hasError ? new SuppressedError(e, env.error, "An error was suppressed during disposal.") : e;
			env.hasError = true;
		}
		var r, s = 0;
		function next() {
			while (r = env.stack.pop()) try {
				if (!r.async && s === 1) return s = 0, env.stack.push(r), Promise.resolve().then(next);
				if (r.dispose) {
					var result = r.dispose.call(r.value);
					if (r.async) return s |= 2, Promise.resolve(result).then(next, function(e) {
						fail(e);
						return next();
					});
				} else s |= 1;
			} catch (e) {
				fail(e);
			}
			if (s === 1) return env.hasError ? Promise.reject(env.error) : Promise.resolve();
			if (env.hasError) throw env.error;
		}
		return next();
	};
})(typeof SuppressedError === "function" ? SuppressedError : function(error, suppressed, message) {
	var e = new Error(message);
	return e.name = "SuppressedError", e.error = error, e.suppressed = suppressed, e;
});
const transcriptSchema = z.object({
	text: z.string(),
	audioSeconds: z.number().nonnegative(),
	inferenceSeconds: z.number().nonnegative()
}).strict();
/** Wait for a cancellable operation without losing ownership of its eventual settlement. */
function waitFor(pending, signal) {
	signal.throwIfAborted();
	return new Promise((resolve, reject) => {
		const abort = () => {
			reject(signal.reason instanceof Error ? signal.reason : /* @__PURE__ */ new Error("Speech operation cancelled"));
		};
		signal.addEventListener("abort", abort, { once: true });
		pending.then(resolve, reject).finally(() => {
			signal.removeEventListener("abort", abort);
		});
	});
}
/**
* Read one bounded worker readiness frame and reject exit before readiness.
* @param handle - newly spawned worker with piped stdout.
* @param limit - maximum readiness bytes.
* @param signal - startup deadline or caller cancellation.
* @returns dynamically allocated loopback port.
*/
async function readReady(handle, limit, signal) {
	if (!handle.stdout) throw new Error("Speech worker stdout is unavailable");
	signal.throwIfAborted();
	const stdout = handle.stdout;
	let text = "";
	const pending = Promise.withResolvers();
	const data = (chunk) => {
		text += chunk.toString("utf8");
		if (Buffer.byteLength(text) > limit) {
			pending.reject(/* @__PURE__ */ new Error("Speech worker readiness exceeded its byte limit"));
			return;
		}
		const end = text.indexOf("\n");
		if (end < 0) return;
		try {
			const value = z.object({ port: z.number().int().min(1).max(65535) }).strict().parse(JSON.parse(text.slice(0, end)));
			pending.resolve(value.port);
		} catch (error) {
			pending.reject(new Error("Invalid speech worker readiness", { cause: error }));
		}
	};
	stdout.on("data", data);
	stdout.on("error", pending.reject);
	handle.done.then(() => {
		pending.reject(/* @__PURE__ */ new Error(`Speech worker exited before readiness: ${handle.collected.stderr?.readFrom(0).text}`));
	}, pending.reject);
	try {
		return await waitFor(pending.promise, signal);
	} finally {
		stdout.off("data", data);
		stdout.off("error", pending.reject);
		stdout.resume();
	}
}
/**
* Decode a bounded worker HTTP response; malformed worker output fails the request.
* @param response - private authenticated worker response.
* @param limit - maximum bytes retained before JSON parsing.
* @returns validated final transcript; marked input rejections throw SpeechInputError.
*/
async function readTranscript(response, limit) {
	if (!response.body) throw new Error("Speech worker returned no response");
	const reader = response.body.getReader();
	const chunks = [];
	let length = 0;
	try {
		for (;;) {
			const { done, value } = await reader.read();
			if (done) break;
			length += value.length;
			if (length > limit) throw new Error("Speech transcript exceeded its byte limit");
			chunks.push(value);
		}
	} finally {
		await reader.cancel();
		reader.releaseLock();
	}
	const value = JSON.parse(Buffer.concat(chunks).toString("utf8"));
	if (!response.ok) {
		const failure = z.object({
			error: z.string(),
			code: z.literal("invalid-input").optional()
		}).parse(value);
		if ((response.status === 400 || response.status === 413) && failure.code === "invalid-input") throw new SpeechInputError(failure.error);
		throw new Error(failure.error);
	}
	return transcriptSchema.parse(value);
}
/** Own one worker across recordings, and join every accepted job on disposal. */
var SenseVoiceWorker = class {
	ctx;
	config;
	worker;
	tail = Promise.resolve();
	pending = 0;
	lifetime = new AbortController();
	idle;
	runtime;
	state;
	listeners = /* @__PURE__ */ new Set();
	lastProgressAt = 0;
	/** Configured origins available for explicit downloads; offline deployments expose no choices. */
	downloadSources;
	preparing;
	constructor(ctx, config) {
		this.ctx = ctx;
		this.config = config;
		this.downloadSources = config.modelDirectory !== void 0 && config.vadModelPath !== void 0 ? [] : [...new Set((config.modelOrigin === void 0 ? config.modelOrigins : [config.modelOrigin]).map((origin) => new URL(origin).origin))];
		const kinds = ["check"];
		if (config.modelDirectory === void 0) kinds.push("model");
		if (config.vadModelPath === void 0) kinds.push("vad");
		kinds.push("verify", "load");
		this.state = {
			phase: "unprepared",
			steps: kinds.map((kind) => ({
				kind,
				status: "pending"
			}))
		};
	}
	/**
	* Read preparation readiness.
	* @returns the current Host-owned state.
	*/
	snapshot() {
		return this.state;
	}
	/**
	* Observe readiness.
	* @param listener - invalidation callback.
	* @returns subscription disposer.
	*/
	subscribe(listener) {
		this.listeners.add(listener);
		return () => {
			this.listeners.delete(listener);
		};
	}
	publish(state) {
		if (this.preparing && [
			"ready",
			"standby",
			"unprepared",
			"failed",
			"cancelled"
		].includes(state.phase)) this.preparing.completed = true;
		const previous = this.state;
		const steps = state.steps ?? previous.steps.map((item) => {
			if (state.phase === "ready") return {
				...item,
				status: "complete"
			};
			if (state.step === "check" && item.kind !== "check") return {
				kind: item.kind,
				status: "pending"
			};
			if (state.step === item.kind) return item.status === "running" ? item : {
				kind: item.kind,
				status: "running",
				startedAt: Date.now()
			};
			if (item.status !== "running") return item;
			if (state.phase === "standby") return {
				...item,
				status: "cancelled"
			};
			if (state.phase === "failed" || state.phase === "cancelled") return {
				...item,
				status: state.phase
			};
			return state.step === void 0 ? item : {
				...item,
				status: "complete"
			};
		});
		this.state = {
			...state,
			steps
		};
		const now = performance.now();
		if (state.phase === "downloading" && previous.phase === "downloading" && state.resource === previous.resource && state.completedBytes !== state.totalBytes && now - this.lastProgressAt < this.config.progressIntervalMs) return;
		this.lastProgressAt = now;
		for (const listener of this.listeners) listener();
	}
	/** Inspect disk caches on activation; valid resources enter standby without starting a worker. */
	inspect() {
		this.runPreparation(async (signal) => {
			const env_1 = {
				stack: [],
				error: void 0,
				hasError: false
			};
			try {
				const inspection = __addDisposableResource(env_1, deadline(signal, this.config.prepareTimeoutMs, "SPEECH_PREPARE_TIMEOUT"), false);
				this.publish({
					phase: "checking",
					step: "check",
					startedAt: Date.now()
				});
				const runtime = await inspectRuntime(this.config, inspection.signal);
				inspection.signal.throwIfAborted();
				this.runtime = runtime;
				this.publish({
					phase: runtime ? "standby" : "unprepared",
					steps: this.state.steps.map(({ kind }) => ({
						kind,
						status: kind === "check" || runtime && kind !== "load" ? "complete" : "pending"
					}))
				});
			} catch (e_1) {
				env_1.error = e_1;
				env_1.hasError = true;
			} finally {
				__disposeResources(env_1);
			}
		});
	}
	/**
	* Start or join one Host-owned preparation task with a fixed download source.
	* @param options - omitted source uses deployment policy; a manual source must be advertised and disables fallback.
	*/
	prepare(options = {}) {
		const source = options.downloadSource;
		if (source !== void 0 && !this.downloadSources.includes(source)) throw new Error("Speech download source is unavailable");
		const config = source === void 0 ? this.config : Object.assign({}, this.config, { modelOrigin: source });
		this.runPreparation(async (signal) => {
			await this.start(signal, config);
		}, source);
	}
	runPreparation(run, downloadSource) {
		if (this.preparing && this.preparing.downloadSource !== downloadSource) throw new Error("Cancel preparation before changing its download source");
		if (this.preparing || this.worker) return;
		this.lifetime.signal.throwIfAborted();
		const abort = new AbortController();
		const task = {
			abort,
			settled: Promise.resolve(),
			completed: false,
			downloadSource
		};
		this.preparing = task;
		task.settled = this.enqueue(run, abort.signal).catch((error) => {
			if (this.lifetime.signal.aborted) return;
			this.publish(abort.signal.aborted ? { phase: "cancelled" } : {
				phase: "failed",
				message: error instanceof Error ? error.message : String(error),
				...error instanceof SpeechDownloadError ? { download: error.download } : {}
			});
		}).finally(() => {
			this.preparing = void 0;
		});
	}
	/** Cancel unfinished preparation; completed readiness is retained. @returns after its queued or active work settles. */
	async cancel() {
		const task = this.preparing;
		if (!task) return;
		if (!task.completed) {
			this.publish({
				phase: "cancelling",
				startedAt: Date.now()
			});
			task.abort.abort(/* @__PURE__ */ new Error("Speech preparation cancelled"));
		}
		await task.settled;
	}
	/**
	* Queue one bounded recording; cancellation never leaves inference running after settlement.
	* Verified resources accept recordings while the worker wakes; other preparation states reject without downloading.
	* @param input - complete WAV and language hint.
	* @param signal - caller cancellation.
	* @returns recognized text; cancelled waiting jobs never acquire the worker.
	*/
	async transcribe(input, signal) {
		return await this.enqueue(async (combined) => await this.execute(input, combined), signal);
	}
	async enqueue(run, signal) {
		const combined = AbortSignal.any([signal, this.lifetime.signal]);
		combined.throwIfAborted();
		if (this.pending >= this.config.maxPending) throw new Error("Speech transcription queue is full");
		clearTimeout(this.idle);
		this.pending++;
		const job = this.tail.then(async () => {
			combined.throwIfAborted();
			return await run(combined);
		});
		this.tail = job.then(() => void 0, () => void 0).finally(() => {
			this.pending--;
			if (this.pending === 0 && !this.lifetime.signal.aborted && this.config.idleTimeoutMs > 0) this.idle = setTimeout(() => {
				this.tail = this.tail.then(async () => {
					await this.stop();
				});
				this.tail.catch((error) => {
					this.ctx.logger.warn("Speech worker idle cleanup failed", error);
				});
			}, this.config.idleTimeoutMs);
		});
		return await job;
	}
	async start(signal, preparationConfig = this.config) {
		const env_2 = {
			stack: [],
			error: void 0,
			hasError: false
		};
		try {
			if (this.worker?.closed) await this.stop();
			if (this.worker) return this.worker;
			const setup = __addDisposableResource(env_2, deadline(signal, this.config.prepareTimeoutMs, "SPEECH_PREPARE_TIMEOUT"), false);
			const cached = this.runtime !== void 0;
			if (!cached) this.publish({
				phase: "checking",
				step: "check",
				startedAt: Date.now()
			});
			const runtime = this.runtime ?? await prepareRuntime(this.ctx, preparationConfig, setup.signal, (state) => {
				this.publish(state);
			});
			this.runtime = runtime;
			setup.signal.throwIfAborted();
			this.publish({
				phase: cached ? "waking" : "loading",
				step: "load",
				startedAt: Date.now()
			});
			await mkdir(this.config.dataRoot, { recursive: true });
			const token = randomBytes(32).toString("hex");
			const handle = this.ctx.subprocess.spawn({
				argv: [
					process.execPath,
					...runtime.worker.endsWith(".ts") ? ["--import", import.meta.resolve("tsx/esm")] : [],
					runtime.worker,
					JSON.stringify(Object.assign({}, this.config, runtime))
				],
				cwd: this.config.dataRoot,
				graceMs: this.config.graceMs,
				env: {
					DSH_SPEECH_TOKEN: token,
					ELECTRON_RUN_AS_NODE: "1"
				},
				stdio: {
					stdin: "ignore",
					stdout: "pipe",
					stderr: { maxBytes: this.config.maxLogBytes }
				}
			});
			try {
				const worker = {
					handle,
					url: `http://127.0.0.1:${await readReady(handle, this.config.maxLogBytes, setup.signal)}`,
					token,
					closed: false
				};
				this.worker = worker;
				this.publish({ phase: "ready" });
				const exited = () => {
					if (!worker.closed) {
						worker.closed = true;
						this.publish({ phase: "standby" });
					}
				};
				handle.done.then(exited, exited);
				return worker;
			} catch (error) {
				handle.terminate();
				await handle.waitForExit();
				throw error;
			}
		} catch (e_2) {
			env_2.error = e_2;
			env_2.hasError = true;
		} finally {
			__disposeResources(env_2);
		}
	}
	async execute(input, signal) {
		const phase = this.state.phase;
		if (phase !== "ready" && phase !== "standby") throw new Error("Prepare the local speech provider before recording");
		try {
			const env_3 = {
				stack: [],
				error: void 0,
				hasError: false
			};
			try {
				const worker = await this.start(signal);
				const call = __addDisposableResource(env_3, deadline(signal, this.config.inferenceTimeoutMs, "SPEECH_INFERENCE_TIMEOUT"), false);
				const result = await readTranscript(await fetch(`${worker.url}/transcribe?language=${encodeURIComponent(input.language)}`, {
					method: "POST",
					headers: {
						authorization: `Bearer ${worker.token}`,
						"content-type": "audio/wav"
					},
					body: Buffer.from(input.audio),
					signal: call.signal
				}), this.config.maxResponseBytes).catch((error) => {
					call.signal.throwIfAborted();
					throw error;
				});
				call.signal.throwIfAborted();
				return result;
			} catch (e_3) {
				env_3.error = e_3;
				env_3.hasError = true;
			} finally {
				__disposeResources(env_3);
			}
		} catch (error) {
			if (error instanceof SpeechInputError) throw error;
			await this.stop();
			this.publish({ phase: "standby" });
			throw error;
		}
	}
	async stop() {
		const worker = this.worker;
		if (!worker) return;
		worker.closed = true;
		worker.handle.terminate();
		await worker.handle.waitForExit();
		this.worker = void 0;
		this.publish({ phase: "standby" });
	}
	/** Stop the local recognizer. @returns after admission closes, queued jobs settle, and the managed worker exits. */
	async dispose() {
		this.listeners.clear();
		this.lifetime.abort(/* @__PURE__ */ new Error("SenseVoice provider disposed"));
		clearTimeout(this.idle);
		try {
			await this.tail;
		} finally {
			await this.stop();
		}
		await this.preparing?.settled;
	}
};
//#endregion
//#region lib/types/config.js
/** Deployment configuration for the managed local SenseVoice recognizer. */
/** Validate deployment-varying runtime choices at plugin activation. */
const Config = z$1.object({
	providerId: z$1.string().min(1).default("sensevoice-local"),
	dataRoot: z$1.string().min(1).required(),
	modelDirectory: z$1.union([z$1.string().min(1), z$1.const(void 0)]),
	vadModelPath: z$1.union([z$1.string().min(1), z$1.const(void 0)]),
	precision: z$1.union(["int8", "fp32"]).default("int8"),
	modelOrigin: z$1.union([z$1.string().pattern(/^https?:\/\/[^/\s?#@]+\/?$/), z$1.const(void 0)]),
	modelOrigins: z$1.array(z$1.string().pattern(/^https?:\/\/[^/\s?#@]+\/?$/)).min(1).default(["https://huggingface.co", "https://hf-mirror.com"]),
	modelProbeTimeoutMs: z$1.natural().min(1).max(MAX_TIMER_DELAY_MS).default(3e3),
	threads: z$1.natural().min(1).default(2),
	segmentSeconds: z$1.number().min(1).max(120).default(30),
	vadThreshold: z$1.number().min(0).max(1).default(.5),
	minSpeechSeconds: z$1.number().min(0).default(.25),
	minSilenceSeconds: z$1.number().min(.01).default(.5),
	vadOnsetPaddingSeconds: z$1.number().min(0).max(1).default(.3),
	maxAudioBytes: z$1.natural().min(46).default(4 * 1024 * 1024),
	prepareTimeoutMs: z$1.natural().min(1).max(MAX_TIMER_DELAY_MS).default(36e5),
	inferenceTimeoutMs: z$1.natural().min(1).max(MAX_TIMER_DELAY_MS).default(12e4),
	idleTimeoutMs: z$1.natural().max(MAX_TIMER_DELAY_MS).default(3e5),
	maxPending: z$1.natural().min(1).default(4),
	graceMs: z$1.natural().min(1).max(MAX_TIMER_DELAY_MS).default(1e3),
	maxLogBytes: z$1.natural().min(1).default(64 * 1024),
	maxResponseBytes: z$1.natural().min(1).default(128 * 1024),
	progressIntervalMs: z$1.natural().min(1).max(MAX_TIMER_DELAY_MS).default(100)
});
//#endregion
//#region lib/types/index.js
/** Optional local SenseVoice provider; activation performs no downloads or model loading. */
const name = "experimental-speech-to-text-sensevoice";
const inject = ["speechToText", "subprocess"];
/**
* Register the local recognizer and inspect disk caches without downloading or loading models.
* @param ctx - Host registry and subprocess owner.
* @param config - validated runtime configuration.
*/
function apply(ctx, config) {
	for (const path of [
		config.dataRoot,
		config.modelDirectory,
		config.vadModelPath
	]) if (path !== void 0 && !isAbsolute(path)) throw new Error(`SenseVoice paths must be absolute: ${path}`);
	for (const origin of config.modelOrigin === void 0 ? config.modelOrigins : [config.modelOrigin]) new URL(origin);
	const worker = new SenseVoiceWorker(ctx, config);
	const estimatedBytes = config.precision === "int8" ? 1e9 : 2e9;
	ctx.effect(() => {
		const unregister = ctx.speechToText.register({
			info: {
				id: config.providerId,
				name: `SenseVoiceSmall (${config.precision.toUpperCase()})`,
				location: "host-local",
				languages,
				downloadSources: worker.downloadSources,
				setupEstimate: {
					recommendedDiskBytes: estimatedBytes,
					expectedMemoryBytes: estimatedBytes,
					minimumMinutes: 1,
					maximumMinutes: 10
				}
			},
			preparation: worker,
			transcribe: async (input, signal) => await worker.transcribe(input, signal)
		});
		worker.inspect();
		return async () => {
			const removing = unregister();
			await worker.dispose();
			await removing;
		};
	});
}
//#endregion
export { Config, apply, inject, name };
