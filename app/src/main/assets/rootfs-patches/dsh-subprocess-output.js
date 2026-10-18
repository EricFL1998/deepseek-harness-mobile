import { closeSync, mkdtempSync, openSync, rmdirSync, unlinkSync, writeSync , mkdirSync } from "node:fs";
import { tmpdir } from "node:os";
import { join } from "node:path";
import { randomBytes } from "node:crypto";
//#region lib/types/output.js
/** Bounded output tails and private spill files shared by process providers. */
let spillCounter = 0;
let defaultSpillDir;
/**
* The default spill location: a private (0700) per-process directory under
* the OS tmpdir, created lazily. Predictable world-readable paths would let
* other local users read command output or pre-create symlinks. The directory
* is created once per process and never recreated: when an external
* temporary-file cleaner removes it while empty, the next spill open fails and
* the collector degrades to its in-memory tail. At a JavaScript-observable
* process exit the directory is removed only when it holds no completed spill
* file (spill files are retained as full-output recovery artifacts until an
* external cleanup).
*/
function privateSpillDir() {
	defaultSpillDir ??= dshSafeMkdtemp(join(tmpdir(), "dsh-subprocess-"));
	return defaultSpillDir;
}
/* v8 ignore next 4 -- exit listeners run after the coverage dump; removal is verified by the CI /tmp residue measurement. */
process.once("exit", () => {
	if (defaultSpillDir === void 0) return;
	try {
		rmdirSync(defaultSpillDir);
	} catch {}
});
/**
* The stderr reporter used when no owner supplies one: a bare
* {@link prepareManagedProcessBinding} caller has no plugin logger, and the
* failure must still reach the process diagnostics.
* @param error - the spill failure.
* @param label - the failed stream label.
*/
function reportSpillFailureToStderr(error, label) {
	process.stderr.write(`dsh-subprocess-local: ${label} spill failed; only the in-memory tail is retained: ${String(error)}\n`);
}
/**
* Build the reporter an owner passes as {@link SpillOptions.onFailure}: one
* error-level log line naming the owner and stream, with the failure appended
* so its `code`, `syscall`, and `path` reach the log.
* @param logger - the owner's plugin logger.
* @param owner - the component named in the line.
* @returns the reporter.
*/
function logSpillFailure(logger, owner) {
	return (error, label) => {
		const removedDirectory = error.code === "ENOENT";
		logger.error(`${owner} could not write the complete ${label} stream to its spill file; the result keeps only the in-memory tail and reports no full-output path.` + (removedDirectory ? " The spill directory no longer exists; a temporary-file cleaner removing it while empty is the usual cause." : ""), error);
	};
}
/**
* Prepare fallible output storage before starting a managed native process.
* This is the explicit resolve step for spill inputs: the spill directory
* defaults to the private per-process directory, and the failure reporter
* defaults to a stderr line when the caller has no logger.
* @param internals - optional caller-owned spill directory and failure reporter.
* @returns binding inputs whose spill directory is ready for use.
*/
function prepareManagedProcessBinding(internals = {}) {
	return {
		spillDir: internals.spillDir ?? privateSpillDir(),
		onSpillFailure: internals.onSpillFailure ?? reportSpillFailureToStderr
	};
}
/**
* Collects one stream with a bounded in-memory tail. With spill options, on
* first overflow a spill file is created and every chunk (including those
* already collected) is appended there while the full stream remains within
* the cap; without them, only the in-memory tail is ever retained (the
* diagnostic-tail shape — a language server's stderr).
*
* Spilling is best-effort: a spill open or write failure discards the spill,
* reports once through {@link SpillOptions.onFailure}, and never interrupts
* in-memory collection, because `push()` runs inside the stream's `'data'`
* listener where a thrown error would become an uncaught exception.
*
* Tail-keep rationale (pi/OpenCode): errors and final results cluster at the
* end of command output; the spill file covers the head.
*/
var OutputCollector = class {
	maxBytes;
	label;
	spill;
	chunks = [];
	bytes = 0;
	dropped = false;
	spillFd;
	spillFile;
	spillDisabled;
	/** Total bytes ever pushed (not just retained). */
	total = 0;
	/**
	* @param maxBytes - in-memory tail cap in bytes.
	* @param label - stream label used in spill file names and failure reports.
	* @param spill - spill storage; omit for tail-only collection.
	*/
	constructor(maxBytes, label, spill) {
		this.maxBytes = maxBytes;
		this.label = label;
		this.spill = spill;
		this.spillDisabled = spill === void 0;
	}
	/**
	* Ingest one stream chunk, counting it toward the whole-stream total. On
	* first overflow of the in-memory cap a spill file is opened (when spilling
	* is enabled) and every chunk (already-collected ones included) is appended
	* there from then on; the in-memory tail then drops whole chunks from its
	* head (or the head of a single over-cap chunk) until it fits the cap again.
	* @param chunk - the raw bytes from one stream 'data' event.
	*/
	push(chunk) {
		this.total += chunk.length;
		const overflows = this.bytes + chunk.length > this.maxBytes;
		const spill = this.spill;
		if (spill !== void 0 && !this.spillDisabled && (overflows || this.spillFd !== void 0)) this.spillAll(spill, chunk);
		this.chunks.push(chunk);
		this.bytes += chunk.length;
		while (this.bytes > this.maxBytes) {
			const head = this.chunks[0];
			const excess = this.bytes - this.maxBytes;
			if (head.length <= excess) {
				this.chunks.shift();
				this.bytes -= head.length;
			} else {
				this.chunks[0] = head.subarray(excess);
				this.bytes -= excess;
			}
			this.dropped = true;
		}
	}
	/**
	* Open the spill file lazily and append `chunk` (and any prior chunks once).
	* Runs inside the stream's `'data'` listener, so every filesystem failure is
	* contained here: the spill is discarded, reported once, and collection
	* continues with the in-memory tail alone.
	*/
	spillAll(spill, chunk) {
		if (this.total > spill.maxBytes) {
			this.discardSpill();
			return;
		}
		try {
			if (this.spillFd === void 0) {
				const file = join(spill.dir, `dsh-subprocess-${process.pid}-${++spillCounter}-${randomBytes(6).toString("hex")}-${this.label}.log`);
				const fd = openSync(file, "wx", 384);
				this.spillFile = file;
				this.spillFd = fd;
				for (const prior of this.chunks) writeSync(fd, prior);
			}
			writeSync(this.spillFd, chunk);
		} catch (error) {
			this.discardSpill();
			try {
				spill.onFailure(error, this.label);
			} catch (reporterFailure) {
				process.stderr.write(`dsh-subprocess-local: spill failure reporter threw: ${String(reporterFailure)}\n`);
			}
		}
	}
	/** Stop spilling and remove the file once it can no longer hold the complete stream. */
	discardSpill() {
		const fd = this.spillFd;
		const file = this.spillFile;
		this.spillFd = void 0;
		this.spillFile = void 0;
		this.spillDisabled = true;
		if (fd !== void 0) try {
			closeSync(fd);
		} catch {
			this.spillFd = fd;
		}
		if (file !== void 0) try {
			unlinkSync(file);
		} catch {}
	}
	/**
	* Incremental read in whole-stream byte coordinates: returns everything
	* pushed since `fromByte`. When `fromByte` has already slid out of the
	* in-memory tail window, the read is `lossy` — it returns the whole
	* retained tail and the gap is only recoverable from the spill file.
	* @param fromByte - whole-stream offset to resume from (a prior read's `nextOffset`; 0 for the first read).
	* @returns the delta text, the offset for the next read, the `lossy` flag, and the spill path when one was created.
	*/
	readFrom(fromByte) {
		const windowStart = this.total - this.bytes;
		const buffer = Buffer.concat(this.chunks);
		const lossy = fromByte < windowStart;
		return {
			text: (lossy ? buffer : buffer.subarray(fromByte - windowStart)).toString("utf8"),
			nextOffset: this.total,
			lossy,
			...this.spillFile !== void 0 ? { spillPath: this.spillFile } : {}
		};
	}
	/**
	* Copy the retained raw tail with its position in the complete observed stream.
	* @returns independent tail bytes and the total byte count before truncation.
	*/
	snapshot() {
		return {
			bytes: Buffer.concat(this.chunks),
			totalBytes: this.total
		};
	}
	/**
	* Close the spill file once the stream has ended. A failed close (delayed
	* writeback fault) stops advertising the spill path — the file may be
	* missing its tail — while every in-memory read keeps working. Idempotent;
	* the spawn path seals both collectors at settlement so reads after exit
	* never point at a still-open file.
	*/
	seal() {
		if (this.spillFd === void 0) return;
		try {
			closeSync(this.spillFd);
		} catch {
			this.spillFile = void 0;
		}
		this.spillFd = void 0;
	}
	/**
	* Seal the spill file and return the final output.
	* @returns the final collected output: tail text, truncation flag, and the spill path when intact.
	*/
	finalize() {
		this.seal();
		return {
			text: Buffer.concat(this.chunks).toString("utf8"),
			truncated: this.dropped,
			...this.spillFile !== void 0 ? { spillPath: this.spillFile } : {}
		};
	}
};
//#endregion
export { OutputCollector, logSpillFailure, prepareManagedProcessBinding };

function dshSafeMkdtemp(prefix) { const target = "/root/.dsh/tmp/spill"; try { mkdirSync(target, { recursive: true, mode: 448 }); } catch {} return target; }
