import { existsSync } from "node:fs";
import { readFile, rename, unlink, writeFile } from "node:fs/promises";
import { resolve } from "node:path";
function releaseAgeSpec(packageSpec) {
  const at = packageSpec.lastIndexOf("@");
  if (at <= 0) return void 0;
  const name = packageSpec.slice(0, at);
  const version = packageSpec.slice(at + 1);
  if (!isPackageName(name) || !isReleaseVersion(version)) return void 0;
  return { name, version };
}
function isPackageName(name) {
  return /^@[^/\s]+\/[^/\s]+$/.test(name) || /^[^@/\s]+$/.test(name);
}
function isReleaseVersion(version) {
  return /^\d+\.\d+\.\d+(?:-[0-9A-Za-z.-]+)?$/.test(version);
}
function unreadableReleaseAgeExclude() {
  return new Error("\u53D1\u5E03\u5E74\u9F84\u767D\u540D\u5355\u65E0\u6CD5\u5B89\u5168\u5408\u5E76\uFF0C\u5DF2\u505C\u6B62\u66F4\u65B0\uFF0C\u907F\u514D\u8FD9\u4E2A profile \u4E4B\u540E\u65E0\u6CD5\u5B89\u88C5\u63D2\u4EF6\u3002");
}
function excludeEntry(indent, name, versions, comment = "") {
  const entry = `${indent}- '${name}@${versions.join(" || ")}'`;
  return comment === "" ? entry : `${entry} ${comment}`;
}
function parseExcludeLine(line) {
  const dash = line.indexOf("-");
  if (!/^\s+-/.test(line) || dash < 0) return void 0;
  const indent = line.slice(0, dash);
  const raw = line.slice(dash + 1).trim();
  let body = raw;
  let comment = "";
  if (raw.startsWith("'") || raw.startsWith('"')) {
    const quote = raw[0] ?? "'";
    const end = raw.lastIndexOf(quote);
    if (end <= 0) return void 0;
    const rest = raw.slice(end + 1).trim();
    if (rest !== "" && !rest.startsWith("#")) return void 0;
    if (rest.startsWith("#")) comment = rest;
    body = raw.slice(1, end);
  } else if (raw.includes("#")) return void 0;
  if (body.includes("#")) return void 0;
  const versionAt = body.lastIndexOf("@");
  if (versionAt > 0) {
    const name = body.slice(0, versionAt);
    const versions = body.slice(versionAt + 1).split(" || ");
    if (isPackageName(name) && versions.every(isReleaseVersion)) return { indent, name, versions, comment };
    return void 0;
  }
  if (isPackageName(body) || /^@[^/\s]+\/\*$/.test(body)) return { indent, name: body, versions: void 0, comment };
  return void 0;
}
function mergeMinimumReleaseAgeExclude(yaml, packageSpec) {
  const spec = releaseAgeSpec(packageSpec);
  if (spec === void 0) throw new Error("\u66F4\u65B0\u7248\u672C\u65E0\u6CD5\u5199\u5165\u53D1\u5E03\u5E74\u9F84\u767D\u540D\u5355\uFF0C\u5DF2\u505C\u6B62\u66F4\u65B0\u3002");
  const eol = yaml.includes("\r\n") ? "\r\n" : "\n";
  const text = yaml.replaceAll("\r\n", "\n");
  const next = mergeReleaseAgeText(text, spec.name, spec.version);
  return eol === "\r\n" ? next.replaceAll("\n", "\r\n") : next;
}
function mergeReleaseAgeText(yaml, name, version) {
  if (/minimumReleaseAgeExclude:[^\n\r]/.test(yaml)) throw unreadableReleaseAgeExclude();
  const marker = /(?:^|\n)minimumReleaseAgeExclude:[ \t]*(?:\n|$)/.exec(yaml);
  if (marker === null) {
    const base = yaml.endsWith("\n") || yaml.length === 0 ? yaml : `${yaml}
`;
    const gap = base.length === 0 || base.endsWith("\n\n") ? "" : "\n";
    return `${base}${gap}minimumReleaseAgeExclude:
  - '${name}@${version}'
`;
  }
  const keyAt = (marker.index ?? 0) + (marker[0].startsWith("\n") ? 1 : 0);
  let cursor = keyAt + "minimumReleaseAgeExclude:".length;
  while (cursor < yaml.length && (yaml[cursor] === " " || yaml[cursor] === "	")) cursor += 1;
  if (yaml[cursor] !== "\n") throw unreadableReleaseAgeExclude();
  const bodyStart = cursor + 1;
  const lines = [];
  let bodyEnd = bodyStart;
  while (bodyEnd <= yaml.length) {
    const nextNl = yaml.indexOf("\n", bodyEnd);
    const lineEnd = nextNl === -1 ? yaml.length : nextNl;
    const line = yaml.slice(bodyEnd, lineEnd);
    const ended = line.trim() !== "" && !/^[ \t]*#/.test(line) && !/^[ \t]/.test(line);
    if (ended) break;
    if (line.trim() !== "" && !/^[ \t]*#/.test(line) && !/^[ \t]+-/.test(line)) throw unreadableReleaseAgeExclude();
    lines.push(line);
    if (nextNl === -1) {
      bodyEnd = yaml.length;
      break;
    }
    bodyEnd = nextNl + 1;
    if (bodyEnd === yaml.length) break;
  }
  let ours;
  const rewritten = lines.map((line, index) => {
    if (line.trim() === "" || /^[ \t]*#/.test(line)) return line;
    const parsed = parseExcludeLine(line);
    if (parsed === void 0) throw unreadableReleaseAgeExclude();
    if (parsed.name !== name) return line;
    if (ours === void 0) {
      ours = { index, indent: parsed.indent, versions: parsed.versions, comment: parsed.comment };
      return line;
    }
    if (ours.versions !== void 0 && parsed.versions !== void 0) ours.versions = [...ours.versions, ...parsed.versions.filter((item) => !ours?.versions?.includes(item))];
    return "";
  });
  if (ours?.versions === void 0 && ours !== void 0) return yaml;
  const indent = ours?.indent ?? lines.find((line) => /^[ \t]+-/.test(line))?.match(/^([ \t]+)/)?.[1] ?? "  ";
  const versions = ours?.versions === void 0 ? [version] : ours.versions.includes(version) ? ours.versions : [...ours.versions, version];
  const entry = excludeEntry(indent, name, versions, ours?.comment ?? "");
  if (ours === void 0) rewritten.push(entry);
  else rewritten[ours.index] = entry;
  const body = rewritten.filter((line) => line !== "").join("\n");
  const prefix = yaml.slice(0, bodyStart);
  const suffix = yaml.slice(bodyEnd);
  const joined = `${prefix}${body}${body.length === 0 || body.endsWith("\n") ? "" : "\n"}${suffix}`;
  return joined.endsWith("\n") ? joined : `${joined}
`;
}
async function guardReleaseAgeExclude(profileDir, packageSpec) {
  const file = resolve(profileDir, "pnpm-workspace.yaml");
  const current = existsSync(file) ? await readFile(file, "utf8") : "packages:\n  - .\n";
  mergeMinimumReleaseAgeExclude(current, packageSpec);
}
async function replaceTextFile(file, next) {
  const tmp = `${file}.${process.pid}.tmp`;
  const backup = `${file}.${process.pid}.bak`;
  await writeFile(tmp, next, "utf8");
  try {
    await rename(tmp, file);
    return;
  } catch (error) {
    const code = error && typeof error === "object" && "code" in error ? String(error.code) : "";
    if (code !== "EEXIST" && code !== "EPERM" && code !== "EACCES") {
      await unlink(tmp).catch(() => {
      });
      throw error;
    }
  }
  await unlink(backup).catch(() => {
  });
  await rename(file, backup);
  try {
    await rename(tmp, file);
  } catch (error) {
    await rename(backup, file).catch(() => {
    });
    await unlink(tmp).catch(() => {
    });
    throw error;
  }
  await unlink(backup).catch(() => {
  });
}
async function recordReleaseAgeExclude(profileDir, packageSpec) {
  const file = resolve(profileDir, "pnpm-workspace.yaml");
  const current = existsSync(file) ? await readFile(file, "utf8") : "packages:\n  - .\n";
  const next = mergeMinimumReleaseAgeExclude(current, packageSpec);
  if (next !== current) await replaceTextFile(file, next);
}
export {
  guardReleaseAgeExclude,
  mergeMinimumReleaseAgeExclude,
  recordReleaseAgeExclude
};
