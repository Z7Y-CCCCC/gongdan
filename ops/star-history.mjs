import { mkdir, rename, writeFile } from "node:fs/promises";
import { dirname } from "node:path";

const token = process.env.GITHUB_TOKEN;
const repositoryName = process.env.GITHUB_REPOSITORY || "Z7Y-CCCCC/gongdan";
const [owner, name] = repositoryName.split("/");

if (!token) {
  throw new Error("GITHUB_TOKEN is required");
}
if (!owner || !name || repositoryName.split("/").length !== 2) {
  throw new Error(`Invalid repository name: ${repositoryName}`);
}

const query = `
  query($owner: String!, $name: String!, $after: String) {
    repository(owner: $owner, name: $name) {
      stargazerCount
      stargazers(
        first: 100
        after: $after
        orderBy: { field: STARRED_AT, direction: ASC }
      ) {
        edges {
          starredAt
        }
        pageInfo {
          hasNextPage
          endCursor
        }
      }
    }
  }
`;

async function githubGraphql(variables) {
  const response = await fetch("https://api.github.com/graphql", {
    method: "POST",
    headers: {
      Accept: "application/vnd.github+json",
      Authorization: `Bearer ${token}`,
      "Content-Type": "application/json",
      "User-Agent": "gongdan-star-history"
    },
    body: JSON.stringify({ query, variables })
  });
  const payload = await response.json();
  const errors = payload.errors || [];
  if (!response.ok || errors.length > 0) {
    const detail = errors.map((error) => error.message).join("; ");
    throw new Error(`GitHub GraphQL request failed (${response.status}): ${detail}`);
  }
  const repository = payload.data?.repository;
  if (!repository) {
    throw new Error(`Repository is not visible to the workflow token: ${repositoryName}`);
  }
  return repository;
}

async function collectStars() {
  let after;
  let pageCount = 0;
  let repository;
  const dates = [];

  do {
    repository = await githubGraphql({ owner, name, after });
    dates.push(
      ...repository.stargazers.edges
        .map((edge) => edge.starredAt)
        .filter(Boolean)
    );
    after = repository.stargazers.pageInfo.hasNextPage
      ? repository.stargazers.pageInfo.endCursor
      : undefined;
    pageCount += 1;
    if (pageCount > 1000) {
      throw new Error("Refusing to paginate more than 1000 Star History pages");
    }
  } while (after);

  return {
    count: repository.stargazerCount,
    dates: dates.map((date) => new Date(date)).sort((a, b) => a - b)
  };
}

function escapeXml(value) {
  return String(value)
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&apos;");
}

function formatDate(date) {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}`;
}

function formatDateTime(date) {
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, "0")}-${String(date.getUTCDate()).padStart(2, "0")}`;
}

function buildSvg(theme, { count, dates }) {
  const dark = theme === "dark";
  const colors = dark
    ? {
        background: "#0d1117",
        panel: "#161b22",
        border: "#30363d",
        grid: "#30363d",
        text: "#f0f6fc",
        muted: "#8b949e",
        line: "#58a6ff",
        area: "#1f6feb"
      }
    : {
        background: "#ffffff",
        panel: "#f6f8fa",
        border: "#d0d7de",
        grid: "#d8dee4",
        text: "#1f2328",
        muted: "#59636e",
        line: "#0969da",
        area: "#54aeff"
      };
  const width = 900;
  const height = 520;
  const left = 78;
  const right = 34;
  const top = 82;
  const bottom = 72;
  const plotWidth = width - left - right;
  const plotHeight = height - top - bottom;
  const day = 24 * 60 * 60 * 1000;
  const start = dates[0] || new Date(Date.UTC(2020, 0, 1));
  const dataEnd = dates.at(-1) || new Date(Date.UTC(2020, 1, 1));
  const end = new Date(Math.max(dataEnd.getTime() + 30 * day, start.getTime() + 30 * day));
  const maxObserved = Math.max(count, dates.length, 1);
  const tickStep = maxObserved <= 5 ? 1 : Math.ceil(maxObserved / 5);
  const axisMax = tickStep * 5;
  const sourcePoints = [{ date: start, value: 0 }];
  dates.forEach((date, index) => sourcePoints.push({ date, value: index + 1 }));
  if (count > dates.length) {
    sourcePoints.push({ date: dataEnd, value: count });
  }
  const x = (date) => left + ((date.getTime() - start.getTime()) / (end.getTime() - start.getTime())) * plotWidth;
  const y = (value) => top + plotHeight - (value / axisMax) * plotHeight;
  const points = sourcePoints.map((point) => `${x(point.date).toFixed(2)},${y(point.value).toFixed(2)}`);
  const firstX = x(sourcePoints[0].date).toFixed(2);
  const lastX = x(sourcePoints[sourcePoints.length - 1].date).toFixed(2);
  const baseline = (top + plotHeight).toFixed(2);
  const linePath = points.length > 1 ? `M ${points.join(" L ")}` : `M ${firstX},${baseline} L ${lastX},${baseline}`;
  const areaPath = `${linePath} L ${lastX},${baseline} L ${firstX},${baseline} Z`;
  const yTicks = Array.from({ length: 6 }, (_, index) => index * tickStep);
  const xTicks = Array.from({ length: 5 }, (_, index) => new Date(start.getTime() + ((end.getTime() - start.getTime()) * index) / 4));
  const lastStar = dates.at(-1);
  const subtitle = lastStar
    ? `Current stars: ${count} · Last recorded star: ${formatDateTime(lastStar)}`
    : `Current stars: ${count} · No stars recorded yet`;
  const grid = yTicks.map((tick) => {
    const yPosition = y(tick).toFixed(2);
    return `<line x1="${left}" y1="${yPosition}" x2="${width - right}" y2="${yPosition}" stroke="${colors.grid}" stroke-width="1" />
      <text x="${left - 14}" y="${Number(yPosition) + 5}" text-anchor="end" fill="${colors.muted}" font-size="13">${tick}</text>`;
  }).join("\n");
  const xLabels = xTicks.map((tick) => {
    const xPosition = x(tick).toFixed(2);
    return `<text x="${xPosition}" y="${height - 34}" text-anchor="middle" fill="${colors.muted}" font-size="13">${formatDate(tick)}</text>`;
  }).join("\n");
  const emptyState = count === 0
    ? `<text x="${width / 2}" y="${top + plotHeight / 2 + 6}" text-anchor="middle" fill="${colors.muted}" font-size="18">No stars recorded yet</text>`
    : "";

  return `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" role="img" aria-labelledby="title desc" viewBox="0 0 ${width} ${height}" width="${width}" height="${height}">
  <title id="title">Star History for ${escapeXml(repositoryName)}</title>
  <desc id="desc">${escapeXml(subtitle)}</desc>
  <rect width="${width}" height="${height}" rx="10" fill="${colors.background}" />
  <rect x="18" y="18" width="${width - 36}" height="${height - 36}" rx="8" fill="${colors.panel}" stroke="${colors.border}" />
  <text x="${left}" y="45" fill="${colors.text}" font-size="22" font-weight="600">Star History</text>
  <text x="${left}" y="66" fill="${colors.muted}" font-size="13">${escapeXml(subtitle)}</text>
  ${grid}
  ${xLabels}
  <path d="${areaPath}" fill="${colors.area}" fill-opacity="0.16" />
  <path d="${linePath}" fill="none" stroke="${colors.line}" stroke-width="3" stroke-linecap="round" stroke-linejoin="round" />
  ${emptyState}
  <text x="${left}" y="${height - 12}" fill="${colors.muted}" font-size="12">Stars</text>
</svg>
`;
}

async function writeAtomic(path, content) {
  await mkdir(dirname(path), { recursive: true });
  const temporaryPath = `${path}.tmp-${process.pid}`;
  await writeFile(temporaryPath, content, "utf8");
  await rename(temporaryPath, path);
}

const starData = await collectStars();
const lightPath = process.env.STAR_HISTORY_LIGHT_PATH || "docs/star-history.svg";
const darkPath = process.env.STAR_HISTORY_DARK_PATH || "docs/star-history-dark.svg";
await writeAtomic(lightPath, buildSvg("light", starData));
await writeAtomic(darkPath, buildSvg("dark", starData));
console.log(`Generated ${lightPath} and ${darkPath} for ${repositoryName}: ${starData.count} stars`);
