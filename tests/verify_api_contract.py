#!/usr/bin/env python3
"""
Verify that every endpoint the React client calls exists in the Spring Boot
controllers, and report any controller endpoint the client never uses.

This guards the requirement that frontend API calls match backend endpoints.

Run:  python tests/verify_api_contract.py
"""
from __future__ import annotations

import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
CONTROLLERS = ROOT / "backend/src/main/java/com/transitpulse/controller"
API_CLIENT = ROOT / "frontend/src/services/api.js"

METHOD_ANNOTATIONS = {
    "GetMapping": "GET",
    "PostMapping": "POST",
    "PutMapping": "PUT",
    "PatchMapping": "PATCH",
    "DeleteMapping": "DELETE",
}


def normalise(path: str) -> str:
    """Replace path variables with a placeholder so routes can be compared."""
    path = re.sub(r"\{[^}]+\}", "{}", path)
    path = re.sub(r"\$\{[^}]*\}", "{}", path)      # JS template expressions
    path = re.sub(r"/+", "/", path)
    return path.rstrip("/") or "/"


def backend_endpoints() -> set[tuple[str, str]]:
    endpoints: set[tuple[str, str]] = set()
    for file in sorted(CONTROLLERS.glob("*.java")):
        text = file.read_text(encoding="utf-8")
        base_match = re.search(r'@RequestMapping\("([^"]+)"\)', text)
        base = base_match.group(1) if base_match else ""
        for annotation, verb in METHOD_ANNOTATIONS.items():
            # @GetMapping("/x") and bare @GetMapping
            for m in re.finditer(rf'@{annotation}\("([^"]*)"\)', text):
                endpoints.add((verb, normalise(base + m.group(1))))
            for m in re.finditer(rf'@{annotation}\s*$', text, re.MULTILINE):
                endpoints.add((verb, normalise(base)))
            for m in re.finditer(rf'@{annotation}\s*\n', text):
                endpoints.add((verb, normalise(base)))
    return endpoints


def frontend_calls() -> set[tuple[str, str]]:
    text = API_CLIENT.read_text(encoding="utf-8")
    calls: set[tuple[str, str]] = set()
    verbs = {"get": "GET", "post": "POST", "put": "PUT", "del": "DELETE"}
    # matches:  get('/api/routes')  |  put(`/api/config/${key}`, body)
    pattern = re.compile(r"\b(get|post|put|del)\(\s*(['\"`])([^'\"`]+)\2")
    for match in pattern.finditer(text):
        verb = verbs[match.group(1)]
        path = match.group(3)
        if path.startswith("/api"):
            calls.add((verb, normalise(path)))
    return calls


def main() -> int:
    backend = backend_endpoints()
    frontend = frontend_calls()

    missing = sorted(frontend - backend)
    unused = sorted(backend - frontend)

    print(f"Backend endpoints declared : {len(backend)}")
    print(f"Frontend calls declared    : {len(frontend)}")
    print()

    if missing:
        print("FAIL - frontend calls with no matching backend endpoint:")
        for verb, path in missing:
            print(f"  {verb:6} {path}")
    else:
        print("OK - every frontend API call maps to a backend endpoint")

    if unused:
        print("\nINFO - backend endpoints the client does not call "
              "(fine for API completeness):")
        for verb, path in unused:
            print(f"  {verb:6} {path}")

    return 1 if missing else 0


if __name__ == "__main__":
    sys.exit(main())
