from __future__ import annotations

import json
import sys
from pathlib import Path
from typing import Any

from langchain_core.tools import StructuredTool
from pydantic import BaseModel, Field, create_model

REPO_ROOT = Path(__file__).resolve().parents[4]
LOCAL_MCP_SERVER_DIR = REPO_ROOT / "backend-python" / "gzgk-mcp"


def _json_schema_to_pydantic(name: str, schema: dict[str, Any]) -> type[BaseModel]:
    props = schema.get("properties") or {}
    required = set(schema.get("required") or [])
    fields: dict[str, Any] = {}
    type_map = {"string": str, "integer": int, "number": float, "boolean": bool, "array": list, "object": dict}
    for field_name, meta in props.items():
        py_type = type_map.get(meta.get("type", "string"), Any)
        desc = meta.get("description", "")
        default = ... if field_name in required else meta.get("default", None)
        if default is None and field_name not in required:
            fields[field_name] = (py_type | None, Field(default=None, description=desc))
        else:
            fields[field_name] = (py_type, Field(default=default, description=desc))
    return create_model(f"{name}_Args", **fields)


class McpToolRegistry:
    """
    Dynamic Model Context Protocol (MCP) Client & Tool Loader.
    Discovers tools from:
      1. In-process official FastMCP server (`backend-python/gzgk-mcp/server.py`) for low-latency local/monorepo execution
      2. Optional external remote MCP servers configured via `MCP_REMOTE_URLS` (Streamable HTTP / SSE)
    Every discovered MCP tool is dynamically wrapped into a LangChain `StructuredTool`
    and bound seamlessly alongside native gRPC tools into the LangGraph ReAct agent.
    """

    def __init__(self) -> None:
        self._mcp_tools: list[StructuredTool] = []
        self._tool_catalog: list[dict[str, Any]] = []
        self._loaded = False

    async def load_tools(self) -> list[StructuredTool]:
        if self._loaded:
            return self._mcp_tools
        self._mcp_tools = []
        self._tool_catalog = []

        # 1. Discover official local FastMCP server tools via MCP protocol introspection
        if str(LOCAL_MCP_SERVER_DIR) not in sys.path:
            sys.path.insert(0, str(LOCAL_MCP_SERVER_DIR))
        try:
            import server as local_mcp_module  # type: ignore

            mcp_server = local_mcp_module.mcp
            listed = await mcp_server.list_tools()
            for tool_def in listed:
                t_name = tool_def.name
                t_desc = tool_def.description or f"MCP tool {t_name}"
                t_schema = getattr(tool_def, "input_schema", None) or getattr(tool_def, "inputSchema", None) or {"type": "object", "properties": {}}
                args_model = _json_schema_to_pydantic(t_name, t_schema)

                async def _invoke_mcp(_tool_name: str = t_name, **kwargs: Any) -> str:
                    clean_args = {k: v for k, v in kwargs.items() if v is not None}
                    result = await mcp_server.call_tool(_tool_name, clean_args)
                    # MCP SDK v2 returns CallToolResult(content=[TextContent(...)]); v1 returns list or tuple
                    if hasattr(result, "content"):
                        items = result.content
                    elif isinstance(result, tuple):
                        items = result[0]
                    else:
                        items = result
                    if isinstance(items, list):
                        texts = [getattr(c, "text", str(c)) for c in items]
                        return "\n".join(texts)
                    return str(items)

                lc_tool = StructuredTool.from_function(
                    coroutine=_invoke_mcp,
                    name=t_name,
                    description=f"[MCP] {t_desc}",
                    args_schema=args_model,
                )
                self._mcp_tools.append(lc_tool)
                self._tool_catalog.append(
                    {
                        "name": t_name,
                        "source": "mcp",
                        "server": "gzgk-gaokao-mcp",
                        "description": t_desc,
                    }
                )
        except Exception as exc:
            self._tool_catalog.append(
                {
                    "name": "mcp_loader_notice",
                    "source": "mcp-error",
                    "server": "gzgk-gaokao-mcp",
                    "description": f"MCP discovery fallback: {exc}",
                }
            )

        self._loaded = True
        return self._mcp_tools

    def catalog(self) -> list[dict[str, Any]]:
        return list(self._tool_catalog)
