package com.myvu.client.core

import org.json.JSONArray
import org.json.JSONObject

object MindMapVisualizerHelper {

    /**
     * Generates a self-contained, responsive HTML5 + SVG Interactive Mind Map with pan/zoom
     * tailored to the Cyber-HUD Obsidian aesthetic.
     */
    fun buildMindMapHtml(title: String, rawMindMapText: String): String {
        val rootNode = parseToNode(title, rawMindMapText)
        val jsonTree = rootNode.toJson().toString()
        val d = "$"

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
  body {
    background-color: #06080A;
    background-image: 
      radial-gradient(circle at 50% 50%, rgba(0, 240, 255, 0.05) 0%, transparent 70%),
      linear-gradient(rgba(255, 255, 255, 0.02) 1px, transparent 1px),
      linear-gradient(90deg, rgba(255, 255, 255, 0.02) 1px, transparent 1px);
    background-size: 100% 100%, 30px 30px, 30px 30px;
    color: #E2E8F0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
    overflow: hidden;
    width: 100vw;
    height: 100vh;
    touch-action: none;
  }
  #canvas-container {
    width: 100%;
    height: 100%;
    position: relative;
    cursor: grab;
  }
  #canvas-container:active { cursor: grabbing; }
  svg {
    position: absolute;
    top: 0; left: 0;
    width: 100%; height: 100%;
    pointer-events: none;
  }
  .node-wrapper {
    position: absolute;
    transform-origin: 0 0;
    transition: left 0.3s cubic-bezier(0.25, 1, 0.5, 1), top 0.3s cubic-bezier(0.25, 1, 0.5, 1), opacity 0.25s ease;
  }
  .node {
    display: inline-flex;
    align-items: center;
    gap: 8px;
    padding: 10px 16px;
    border-radius: 14px;
    font-size: 13px;
    font-weight: 600;
    white-space: nowrap;
    pointer-events: auto;
    cursor: pointer;
    backdrop-filter: blur(12px);
    -webkit-backdrop-filter: blur(12px);
    transition: all 0.2s cubic-bezier(0.34, 1.56, 0.64, 1);
    box-shadow: 0 6px 20px rgba(0, 0, 0, 0.6);
  }
  .node:hover, .node:active {
    transform: scale(1.04);
  }
  .node.root {
    background: linear-gradient(135deg, #0077B6 0%, #00F0FF 60%, #9D4EDD 100%);
    color: #FFFFFF;
    font-size: 15px;
    font-weight: 800;
    border: 2px solid rgba(255, 255, 255, 0.8);
    box-shadow: 0 0 25px rgba(0, 240, 255, 0.5), 0 8px 32px rgba(0, 0, 0, 0.7);
    letter-spacing: 0.3px;
  }
  
  /* Branch Color Themes */
  .branch-0 { border: 1.5px solid #00F0FF; background: rgba(10, 25, 40, 0.88); color: #7DF9FF; box-shadow: 0 0 14px rgba(0, 240, 255, 0.25); }
  .branch-1 { border: 1.5px solid #B026FF; background: rgba(28, 12, 45, 0.88); color: #E0AAFF; box-shadow: 0 0 14px rgba(176, 38, 255, 0.25); }
  .branch-2 { border: 1.5px solid #00FF9D; background: rgba(8, 32, 22, 0.88); color: #A7F3D0; box-shadow: 0 0 14px rgba(0, 255, 157, 0.25); }
  .branch-3 { border: 1.5px solid #FFB703; background: rgba(35, 25, 8, 0.88); color: #FDE047; box-shadow: 0 0 14px rgba(255, 183, 3, 0.25); }
  .branch-4 { border: 1.5px solid #FF2E93; background: rgba(40, 10, 28, 0.88); color: #FF85A1; box-shadow: 0 0 14px rgba(255, 46, 147, 0.25); }
  .branch-5 { border: 1.5px solid #3A86FF; background: rgba(12, 22, 48, 0.88); color: #93C5FD; box-shadow: 0 0 14px rgba(58, 134, 255, 0.25); }

  .node.level-2 {
    font-size: 12px;
    font-weight: 500;
    padding: 8px 12px;
    border-radius: 10px;
    opacity: 0.95;
  }
  .node.level-3 {
    font-size: 11px;
    font-weight: 400;
    padding: 6px 10px;
    border-radius: 8px;
    opacity: 0.88;
  }

  .badge {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    width: 18px;
    height: 18px;
    border-radius: 50%;
    background: rgba(255, 255, 255, 0.15);
    font-size: 10px;
    font-weight: 700;
    margin-left: 4px;
  }

  .link {
    fill: none;
    stroke-width: 2.2;
    stroke-linecap: round;
    transition: stroke-dashoffset 0.3s ease, stroke-opacity 0.2s ease;
  }
  .link.dimmed { stroke-opacity: 0.15 !important; }
  .node-wrapper.dimmed { opacity: 0.25 !important; }

  .controls {
    position: fixed;
    bottom: 20px;
    right: 20px;
    display: flex;
    gap: 8px;
    z-index: 100;
    background: rgba(12, 16, 22, 0.8);
    padding: 6px;
    border-radius: 14px;
    border: 1px solid rgba(255, 255, 255, 0.12);
    backdrop-filter: blur(10px);
    box-shadow: 0 8px 24px rgba(0,0,0,0.6);
  }
  .btn {
    background: rgba(26, 34, 48, 0.9);
    color: #00F0FF;
    border: 1px solid rgba(0, 240, 255, 0.3);
    border-radius: 10px;
    min-width: 36px;
    height: 36px;
    padding: 0 10px;
    font-weight: bold;
    font-size: 13px;
    cursor: pointer;
    display: inline-flex;
    align-items: center;
    justify-content: center;
    transition: all 0.15s ease;
  }
  .btn:active {
    transform: scale(0.92);
    background: #00F0FF;
    color: #000;
  }
</style>
</head>
<body>
<div id="canvas-container">
  <svg id="svg-layer">
    <defs>
      <filter id="glow-cyan" x="-20%" y="-20%" width="140%" height="140%">
        <feGaussianBlur stdDeviation="3" result="blur" />
        <feMerge><feMergeNode in="blur"/><feMergeNode in="SourceGraphic"/></feMerge>
      </filter>
    </defs>
  </svg>
  <div id="nodes-layer"></div>
</div>

<div class="controls">
  <button class="btn" onclick="zoomIn()" title="Zoom In">+</button>
  <button class="btn" onclick="zoomOut()" title="Zoom Out">−</button>
  <button class="btn" onclick="fitView()" title="Centrar">🎯</button>
  <button class="btn" onclick="toggleExpandAll()" title="Expandir/Contraer">↕</button>
</div>

<script>
const rawData = $jsonTree;

const BRANCH_COLORS = [
  { stroke: '#00F0FF', class: 'branch-0' },
  { stroke: '#B026FF', class: 'branch-1' },
  { stroke: '#00FF9D', class: 'branch-2' },
  { stroke: '#FFB703', class: 'branch-3' },
  { stroke: '#FF2E93', class: 'branch-4' },
  { stroke: '#3A86FF', class: 'branch-5' }
];

let scale = 1;
let panX = 40;
let panY = window.innerHeight / 2 - 40;
let isDragging = false;
let startX = 0, startY = 0;
let allExpanded = true;
let activePathNodeId = null;

const container = document.getElementById('canvas-container');
const svg = document.getElementById('svg-layer');
const nodesLayer = document.getElementById('nodes-layer');

// Initialize internal node IDs and collapse states
let nodeIdCounter = 0;
function prepareTree(node, depth = 0, branchIdx = 0) {
  node.id = 'n_' + (++nodeIdCounter);
  node.depth = depth;
  node.branchIdx = branchIdx;
  node.collapsed = false;

  if (node.children && node.children.length > 0) {
    node.children.forEach((child, i) => {
      const bIdx = depth === 0 ? (i % BRANCH_COLORS.length) : branchIdx;
      prepareTree(child, depth + 1, bIdx);
    });
  }
}
prepareTree(rawData);

function renderTree() {
  nodesLayer.innerHTML = '';
  svg.innerHTML = '';

  const nodes = [];
  const links = [];

  let currentY = 0;
  const LEVEL_WIDTH = 220;
  const NODE_HEIGHT = 44;
  const VERTICAL_GAP = 14;

  function layout(node, parentX, parentY) {
    const level = node.depth;
    const x = level * LEVEL_WIDTH;
    let y = 0;

    const visibleChildren = (node.collapsed || !node.children) ? [] : node.children;

    if (visibleChildren.length === 0) {
      y = currentY;
      currentY += NODE_HEIGHT + VERTICAL_GAP;
    } else {
      const childYs = [];
      visibleChildren.forEach(child => {
        childYs.push(layout(child, x, 0));
      });
      y = (childYs[0] + childYs[childYs.length - 1]) / 2;
    }

    nodes.push({
      id: node.id,
      name: node.name,
      depth: node.depth,
      branchIdx: node.branchIdx,
      hasChildren: node.children && node.children.length > 0,
      collapsed: node.collapsed,
      childCount: node.children ? node.children.length : 0,
      refNode: node,
      x, y
    });

    if (parentX !== null && parentY !== null) {
      links.push({
        sourceId: node.id,
        branchIdx: node.branchIdx,
        x1: parentX, y1: parentY,
        x2: x, y2: y
      });
    }

    return y;
  }

  layout(rawData, null, null);

  // Render SVG Links
  links.forEach(l => {
    const sx = l.x1 * scale + panX + 130;
    const sy = l.y1 * scale + panY + 20;
    const ex = l.x2 * scale + panX;
    const ey = l.y2 * scale + panY + 20;
    const mx = (sx + ex) / 2;

    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', `M ${d}{sx} ${d}{sy} C ${d}{mx} ${d}{sy}, ${d}{mx} ${d}{ey}, ${d}{ex} ${d}{ey}`);
    
    const colorObj = BRANCH_COLORS[l.branchIdx % BRANCH_COLORS.length];
    path.setAttribute('class', 'link');
    path.setAttribute('stroke', colorObj.stroke);
    path.setAttribute('stroke-opacity', '0.65');
    svg.appendChild(path);
  });

  // Render HTML Nodes
  nodes.forEach(n => {
    const div = document.createElement('div');
    div.className = 'node-wrapper';
    div.style.left = (n.x * scale + panX) + 'px';
    div.style.top = (n.y * scale + panY) + 'px';

    const inner = document.createElement('div');
    const branchClass = n.depth === 0 ? 'root' : BRANCH_COLORS[n.branchIdx % BRANCH_COLORS.length].class;
    inner.className = `node ${d}{n.depth === 0 ? "root" : "level-" + Math.min(n.depth, 3)} ${d}{branchClass}`;

    // Node icon / title
    const label = document.createElement('span');
    label.textContent = (n.depth === 0 ? '🧠 ' : '') + n.name;
    inner.appendChild(label);

    // Expand/Collapse badge if has children
    if (n.hasChildren && n.depth > 0) {
      const badge = document.createElement('span');
      badge.className = 'badge';
      badge.textContent = n.collapsed ? `+${d}{n.childCount}` : '−';
      inner.appendChild(badge);
    }

    // Toggle collapse on click
    inner.addEventListener('click', (e) => {
      e.stopPropagation();
      if (n.hasChildren) {
        n.refNode.collapsed = !n.refNode.collapsed;
        renderTree();
      }
    });

    div.appendChild(inner);
    nodesLayer.appendChild(div);
  });
}

function fitView() {
  scale = 0.9;
  panX = 30;
  panY = window.innerHeight / 2 - 60;
  renderTree();
}

function zoomIn() { scale = Math.min(scale * 1.25, 3.0); renderTree(); }
function zoomOut() { scale = Math.max(scale / 1.25, 0.3); renderTree(); }

function setCollapseAll(node, collapse) {
  if (node.depth > 0 && node.children && node.children.length > 0) {
    node.collapsed = collapse;
  }
  if (node.children) {
    node.children.forEach(c => setCollapseAll(c, collapse));
  }
}

function toggleExpandAll() {
  allExpanded = !allExpanded;
  setCollapseAll(rawData, !allExpanded);
  renderTree();
}

// Drag & Pan handling
container.addEventListener('pointerdown', e => {
  if (e.target.closest('.btn')) return;
  isDragging = true;
  startX = e.clientX - panX;
  startY = e.clientY - panY;
});
window.addEventListener('pointermove', e => {
  if (!isDragging) return;
  panX = e.clientX - startX;
  panY = e.clientY - startY;
  renderTree();
});
window.addEventListener('pointerup', () => { isDragging = false; });
window.addEventListener('pointercancel', () => { isDragging = false; });

// Touch Pinch Zoom
let initialDist = 0;
let initialScale = 1;
window.addEventListener('touchstart', e => {
  if (e.touches.length === 2) {
    initialDist = Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);
    initialScale = scale;
  }
});
window.addEventListener('touchmove', e => {
  if (e.touches.length === 2 && initialDist > 0) {
    const dist = Math.hypot(e.touches[0].clientX - e.touches[1].clientX, e.touches[0].clientY - e.touches[1].clientY);
    scale = Math.min(Math.max(initialScale * (dist / initialDist), 0.3), 3.0);
    renderTree();
  }
});

window.addEventListener('resize', () => { fitView(); });
fitView();
</script>
</body>
</html>
        """.trimIndent()
    }

    private data class Node(val name: String, val children: MutableList<Node> = mutableListOf()) {
        fun toJson(): JSONObject {
            val obj = JSONObject()
            obj.put("name", name)
            val arr = JSONArray()
            children.forEach { arr.put(it.toJson()) }
            obj.put("children", arr)
            return obj
        }
    }

    private fun parseToNode(fallbackTitle: String, text: String): Node {
        if (text.isBlank()) return Node(fallbackTitle)

        // Try JSON parsing first
        try {
            val trimmed = text.trim()
            if (trimmed.startsWith("{")) {
                val json = JSONObject(trimmed)
                return parseJsonNode(json, fallbackTitle)
            }
        } catch (_: Exception) {}

        val root = Node(fallbackTitle)
        val stack = mutableListOf<Pair<Int, Node>>()
        stack.add(-1 to root)

        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank() || trimmed.startsWith("mindmap") || trimmed.startsWith("```")) continue

            // Compute depth based on leading indentation / list markers / headers
            val rawIndent = line.takeWhile { it == ' ' || it == '\t' }.length
            
            var depth = rawIndent
            if (trimmed.startsWith("#")) {
                val headerLevel = trimmed.takeWhile { it == '#' }.length
                depth = headerLevel * 4
            }

            val cleanName = trimmed
                .removePrefix("#").removePrefix("#").removePrefix("#").removePrefix("#")
                .removePrefix("-").removePrefix("*").removePrefix("•").removePrefix("+")
                .trim()
                .removePrefix("root((").removeSuffix("))")
                .trim()

            if (cleanName.isBlank()) continue

            val node = Node(cleanName)

            while (stack.size > 1 && stack.last().first >= depth) {
                stack.removeAt(stack.lastIndex)
            }

            stack.last().second.children.add(node)
            stack.add(depth to node)
        }

        return root
    }

    private fun parseJsonNode(obj: JSONObject, fallbackTitle: String): Node {
        val name = obj.optString("topic", obj.optString("title", obj.optString("name", fallbackTitle)))
        val node = Node(name)
        val childrenArr = obj.optJSONArray("subtopics")
            ?: obj.optJSONArray("children")
            ?: obj.optJSONArray("branches")

        if (childrenArr != null) {
            for (i in 0 until childrenArr.length()) {
                val item = childrenArr.opt(i)
                if (item is JSONObject) {
                    node.children.add(parseJsonNode(item, "Idea"))
                } else if (item != null) {
                    val str = item.toString().trim()
                    if (str.isNotBlank()) {
                        node.children.add(Node(str))
                    }
                }
            }
        }
        return node
    }
}

