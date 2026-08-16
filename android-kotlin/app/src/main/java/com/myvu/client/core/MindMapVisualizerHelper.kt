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

        return """
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=5.0, user-scalable=yes">
<style>
  * { box-sizing: border-box; margin: 0; padding: 0; user-select: none; }
  body {
    background-color: #0C0E10;
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
  }
  .node {
    display: inline-flex;
    align-items: center;
    padding: 8px 14px;
    border-radius: 12px;
    font-size: 13px;
    font-weight: 600;
    box-shadow: 0 4px 12px rgba(0,0,0,0.5);
    white-space: nowrap;
    transition: transform 0.15s ease, box-shadow 0.15s ease;
    pointer-events: auto;
    cursor: pointer;
  }
  .node.root {
    background: linear-gradient(135deg, #00B4D8, #00F0FF);
    color: #000000;
    font-size: 15px;
    font-weight: 800;
    border: 2px solid #FFFFFF;
  }
  .node.level-1 {
    background: #1A222D;
    color: #00F0FF;
    border: 1.5px solid #00F0FF;
  }
  .node.level-2 {
    background: #161B22;
    color: #E2E8F0;
    border: 1px solid #334155;
    font-weight: 500;
    font-size: 12px;
  }
  .link {
    fill: none;
    stroke: #00F0FF;
    stroke-width: 2;
    stroke-opacity: 0.5;
  }
  .controls {
    position: fixed;
    bottom: 16px;
    right: 16px;
    display: flex;
    gap: 8px;
    z-index: 100;
  }
  .btn {
    background: #1A222D;
    color: #00F0FF;
    border: 1px solid #334155;
    border-radius: 8px;
    width: 36dp;
    height: 36dp;
    padding: 6px 12px;
    font-weight: bold;
    font-size: 14px;
    box-shadow: 0 2px 8px rgba(0,0,0,0.6);
  }
</style>
</head>
<body>
<div id="canvas-container">
  <svg id="svg-layer"></svg>
  <div id="nodes-layer"></div>
</div>

<div class="controls">
  <button class="btn" onclick="zoomIn()">+</button>
  <button class="btn" onclick="zoomOut()">−</button>
  <button class="btn" onclick="resetView()">⟲</button>
</div>

<script>
const data = $jsonTree;

let scale = 1;
let panX = 40;
let panY = window.innerHeight / 2 - 40;
let isDragging = false;
let startX = 0, startY = 0;

const container = document.getElementById('canvas-container');
const svg = document.getElementById('svg-layer');
const nodesLayer = document.getElementById('nodes-layer');

function renderTree() {
  nodesLayer.innerHTML = '';
  svg.innerHTML = '';

  const nodes = [];
  const links = [];

  let currentY = 0;
  const LEVEL_WIDTH = 220;
  const NODE_HEIGHT = 46;
  const VERTICAL_GAP = 16;

  function layout(node, level, parentX, parentY) {
    const x = level * LEVEL_WIDTH;
    let y = 0;

    if (!node.children || node.children.length === 0) {
      y = currentY;
      currentY += NODE_HEIGHT + VERTICAL_GAP;
    } else {
      const childYs = [];
      node.children.forEach(child => {
        childYs.push(layout(child, level + 1, x, 0));
      });
      y = childYs.reduce((a, b) => a + b, 0) / childYs.length;
    }

    nodes.push({ name: node.name, level, x, y });

    if (parentX !== null && parentY !== null) {
      links.push({ x1: parentX, y1: parentY, x2: x, y2: y });
    }

    return y;
  }

  layout(data, 0, null, null);

  // Render links
  links.forEach(l => {
    const sx = l.x1 * scale + panX + 80;
    const sy = l.y1 * scale + panY + 16;
    const ex = l.x2 * scale + panX;
    const ey = l.y2 * scale + panY + 16;
    const mx = (sx + ex) / 2;

    const path = document.createElementNS('http://www.w3.org/2000/svg', 'path');
    path.setAttribute('d', `M ${'$'}{sx} ${'$'}{sy} C ${'$'}{mx} ${'$'}{sy}, ${'$'}{mx} ${'$'}{ey}, ${'$'}{ex} ${'$'}{ey}`);
    path.setAttribute('class', 'link');
    svg.appendChild(path);
  });

  // Render nodes
  nodes.forEach(n => {
    const div = document.createElement('div');
    div.className = 'node-wrapper';
    div.style.left = (n.x * scale + panX) + 'px';
    div.style.top = (n.y * scale + panY) + 'px';
    div.style.transform = `scale(${'$'}{scale})`;

    const inner = document.createElement('div');
    inner.className = 'node ' + (n.level === 0 ? 'root' : (n.level === 1 ? 'level-1' : 'level-2'));
    inner.textContent = n.name;
    div.appendChild(inner);

    nodesLayer.appendChild(div);
  });
}

function zoomIn() { scale = Math.min(scale * 1.2, 2.5); renderTree(); }
function zoomOut() { scale = Math.max(scale / 1.2, 0.4); renderTree(); }
function resetView() { scale = 1; panX = 40; panY = window.innerHeight / 2 - 40; renderTree(); }

// Pan handling
container.addEventListener('pointerdown', e => {
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

// Touch pinch zoom
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
    scale = Math.min(Math.max(initialScale * (dist / initialDist), 0.4), 2.5);
    renderTree();
  }
});

window.addEventListener('resize', () => { renderTree(); });
renderTree();
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
            if (text.trim().startsWith("{")) {
                val json = JSONObject(text)
                return parseJsonNode(json, fallbackTitle)
            }
        } catch (_: Exception) {}

        // Fallback to parsing indentation / markdown lists
        val root = Node(fallbackTitle)
        val stack = mutableListOf<Pair<Int, Node>>()
        stack.add(0 to root)

        val lines = text.lines()
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isBlank()) continue

            // Determine depth by leading spaces or tabs or # / -
            val indent = line.takeWhile { it == ' ' || it == '\t' }.length
            val cleanName = trimmed
                .removePrefix("#").removePrefix("#").removePrefix("#")
                .removePrefix("-").removePrefix("*").removePrefix("•")
                .trim()

            if (cleanName.isBlank()) continue

            val node = Node(cleanName)

            while (stack.size > 1 && stack.last().first >= indent) {
                stack.removeAt(stack.lastIndex)
            }

            stack.last().second.children.add(node)
            stack.add(indent to node)
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
                } else if (item is String) {
                    node.children.add(Node(item))
                }
            }
        }
        return node
    }
}
