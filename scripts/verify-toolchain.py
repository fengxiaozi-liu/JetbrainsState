"""Reproducible compiler/debugger smoke test; does not change IDE licensing."""
import json
from pathlib import Path
import socket
import subprocess
import time

ROOT = Path(__file__).resolve().parent.parent
PROJECT = ROOT / "smoke-project"
OUTPUT = ROOT / "verification"
GO = Path(r"C:\Program Files\Go\bin\go.exe")
DLV = Path(r"C:\Program Files\JetBrains\GoLand 2026.3\plugins\go-plugin\lib\dlv\windows\dlv.exe")
OUTPUT.mkdir(exist_ok=True)
report = {"scope": "Standalone Go compiler and bundled Delve; IDE checks are separate", "checks": {}}


def run(*args):
    result = subprocess.run([str(a) for a in args], cwd=PROJECT,
                            capture_output=True, text=True, timeout=90)
    if result.returncode:
        raise RuntimeError(f"{args[0]} exited {result.returncode}: {result.stdout}\n{result.stderr}")
    return (result.stdout + result.stderr).strip()


def main():
    report["go_version"] = run(GO, "version")
    report["delve_version"] = run(DLV, "version")
    report["checks"]["unit_test"] = run(GO, "test", "-count=1", "-v", "./...")
    binary = OUTPUT / "smoke.exe"
    run(GO, "build", "-gcflags=all=-N -l", "-o", binary, ".")
    report["checks"]["compile"] = "passed"
    actual = run(binary)
    if actual != "smoke result=42":
        raise AssertionError(actual)
    report["checks"]["run"] = actual
    with socket.socket() as reservation:
        reservation.bind(("127.0.0.1", 0))
        port = reservation.getsockname()[1]
    with (OUTPUT / "delve.log").open("w") as log:
        process = subprocess.Popen([str(DLV), "--headless", "--api-version=2",
                                    f"--listen=127.0.0.1:{port}", "--accept-multiclient",
                                    "exec", str(binary)], cwd=PROJECT, stdout=log,
                                   stderr=subprocess.STDOUT,
                                   creationflags=subprocess.CREATE_NO_WINDOW)
        connection = None
        try:
            for _ in range(100):
                if process.poll() is not None:
                    raise RuntimeError("Delve exited during startup; see verification/delve.log")
                try:
                    connection = socket.create_connection(("127.0.0.1", port), timeout=0.5)
                    break
                except OSError:
                    time.sleep(0.1)
            if connection is None:
                raise TimeoutError("Delve did not open its loopback endpoint")
            connection.settimeout(20)
            reader = connection.makefile("rb")
            sequence = 0

            def rpc(method, parameters):
                nonlocal sequence
                sequence += 1
                message = {"method": "RPCServer." + method,
                           "params": [parameters], "id": sequence}
                connection.sendall((json.dumps(message) + "\n").encode())
                response = json.loads(reader.readline())
                if response.get("error"):
                    raise RuntimeError(f"{method}: {response['error']}")
                return response["result"]

            breakpoint = rpc("CreateBreakpoint", {"Breakpoint": {
                "name": "smokeTwice", "file": str(PROJECT / "main.go"), "line": 7}})
            report["checks"]["breakpoint_created"] = breakpoint
            state = rpc("Command", {"name": "continue"})
            thread = state["State"]["currentThread"]
            if thread["line"] != 7 or not thread.get("breakPoint"):
                raise AssertionError(f"Expected breakpoint on line 7: {state}")
            report["checks"]["breakpoint_hit"] = {"file": thread["file"], "line": thread["line"]}
            evaluated = rpc("Eval", {"Scope": {"GoroutineID": -1, "Frame": 0},
                                     "Expr": "result", "Cfg": {"MaxVariableRecurse": 1,
                                     "MaxStringLen": 64, "MaxArrayValues": 64, "MaxStructFields": -1}})
            variable = evaluated["Variable"]
            if variable["value"] != "42":
                raise AssertionError(f"Unexpected local value: {evaluated}")
            report["checks"]["inspect_local"] = "result = " + variable["value"]
            stepped = rpc("Command", {"name": "next"})
            if stepped["State"].get("exited"):
                raise AssertionError("Program exited instead of stepping")
            report["checks"]["step_over"] = stepped["State"]["currentThread"]["line"]
            finished = rpc("Command", {"name": "continue"})
            if not finished["State"].get("exited") or finished["State"].get("exitStatus") != 0:
                raise AssertionError(f"Unexpected completion: {finished}")
            report["checks"]["debug_completion"] = "exit 0"
            report["passed"] = True
        finally:
            if connection:
                connection.close()
            if process.poll() is None:
                process.terminate()
                process.wait(timeout=10)


try:
    main()
except Exception as error:
    report["passed"] = False
    report["error"] = str(error)
    raise
finally:
    (OUTPUT / "toolchain-report.json").write_text(json.dumps(report, indent=2, ensure_ascii=False), encoding="utf-8")
    print(json.dumps(report, indent=2, ensure_ascii=False))
