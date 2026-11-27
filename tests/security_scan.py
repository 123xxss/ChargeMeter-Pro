#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""PowerMeter 安全 / PRD 静态扫描。

不编译、不运行 App，只做静态事实检查：
  1. 清单不申请 INTERNET、不申请敏感/危险权限；
  2. 清单所有组件显式声明 android:exported，且除 launcher 外全部 exported=false；
  3. 清单 application 不开明文流量、allowBackup=false、不声明 sharedUserId；
  4. 源码无 Runtime.exec / ProcessBuilder / Process.start 等进程执行；
  5. 源码无「信任 uid 2000 / shell uid」逻辑；
  6. 源码无网络 API（java.net / Socket / WebView / HttpURLConnection / okhttp）；
  7. 核心层不调用系统 computeChargeTimeRemaining()（PRD 要求只做本地估算）。

输出约定（tests/run_tests.sh 依赖）：
  PASS <检查项>
  FAIL <检查项> → <原因/位置>
任一失败则退出码 1。
"""

import os
import re
import sys
import xml.etree.ElementTree as ET

ANDROID = "{http://schemas.android.com/apk/res/android}"

passed = 0
failed = 0


def ok(name):
    global passed
    passed += 1
    print("PASS " + name)


def bad(name, detail=""):
    global failed
    failed += 1
    print("FAIL " + name + (" → " + detail if detail else ""))


def read_text(path):
    with open(path, "r", encoding="utf-8", errors="replace") as f:
        return f.read()


def strip_java_comments(src):
    """去掉 // 与 /* */ 注释，保留字符串字面量（避免文档注释造成误报/漏报）。"""
    out = []
    i = 0
    n = len(src)
    while i < n:
        c = src[i]
        if c == "/" and i + 1 < n:
            nxt = src[i + 1]
            if nxt == "/":
                j = src.find("\n", i)
                i = n if j < 0 else j
                continue
            if nxt == "*":
                j = src.find("*/", i + 2)
                i = n if j < 0 else j + 2
                continue
        if c == '"' or c == "'":
            quote = c
            j = i + 1
            while j < n:
                if src[j] == "\\":
                    j += 2
                    continue
                if src[j] == quote:
                    j += 1
                    break
                j += 1
            out.append(src[i:j])
            i = j
            continue
        out.append(c)
        i += 1
    return "".join(out)


def find_hits(sources, root, patterns):
    """在去注释后的源码里找 patterns，返回 [(相对路径, 片段)]。"""
    hits = []
    for path, text in sources.items():
        rel = os.path.relpath(path, root)
        for pat in patterns:
            for m in re.finditer(pat, text):
                snippet = text[max(0, m.start() - 40):m.end() + 40]
                snippet = " ".join(snippet.split())
                hits.append((rel, snippet))
    return hits


def check_no_hits(sources, root, patterns, name, absent_ok_msg):
    hits = find_hits(sources, root, patterns)
    if not hits:
        ok(name + "（" + absent_ok_msg + "）")
    else:
        detail = "；".join("%s: %s" % (p, s) for p, s in hits[:3])
        bad(name, detail)


def main():
    root = sys.argv[1] if len(sys.argv) > 1 else os.path.dirname(
        os.path.dirname(os.path.abspath(__file__)))
    manifest_path = os.path.join(root, "AndroidManifest.xml")
    src_root = os.path.join(root, "src")

    print("PowerMeter 安全 / PRD 静态扫描")
    print("项目根目录：" + root)
    print()

    # ---------------------------------------------------------------- 清单
    print("## 清单安全（AndroidManifest.xml）")
    try:
        manifest = ET.parse(manifest_path).getroot()
    except Exception as exc:  # noqa: BLE001
        bad("清单可解析", "解析失败: %s" % exc)
        return summary()

    permissions = []
    for tag in ("uses-permission", "uses-permission-sdk-23"):
        for el in manifest.findall(tag):
            name = el.get(ANDROID + "name")
            if name:
                permissions.append(name)

    if "android.permission.INTERNET" in permissions:
        bad("清单不申请 INTERNET 权限", "发现 android.permission.INTERNET（纯本地 App 不应联网）")
    else:
        ok("清单不申请 INTERNET 权限（无网络出口）")

    dangerous = [
        "ACCESS_FINE_LOCATION", "ACCESS_COARSE_LOCATION", "ACCESS_BACKGROUND_LOCATION",
        "READ_EXTERNAL_STORAGE", "WRITE_EXTERNAL_STORAGE", "MANAGE_EXTERNAL_STORAGE",
        "READ_PHONE_STATE", "READ_PHONE_NUMBERS", "BATTERY_STATS", "SYSTEM_ALERT_WINDOW",
        "PACKAGE_USAGE_STATS", "QUERY_ALL_PACKAGES", "READ_CONTACTS", "CAMERA",
        "RECORD_AUDIO", "RECEIVE_SMS", "READ_SMS", "SEND_SMS", "READ_CALL_LOG",
        "ACCESS_WIFI_STATE", "BLUETOOTH_CONNECT",
    ]
    perm_hits = sorted(p for p in permissions
                       if any(p.endswith("." + d) for d in dangerous))
    if perm_hits:
        bad("清单不申请敏感 / 危险权限（隐私最小化）", "发现: " + ", ".join(perm_hits))
    else:
        ok("清单不申请敏感 / 危险权限（隐私最小化）")

    shared_uid = manifest.get(ANDROID + "sharedUserId")
    if shared_uid:
        bad("不声明 sharedUserId（避免共享系统 uid 提权）", "sharedUserId=" + shared_uid)
    else:
        ok("不声明 sharedUserId（避免共享系统 uid 提权）")

    app = manifest.find("application")
    if app is None:
        bad("清单包含 application 节点", "缺失 application")
    else:
        cleartext = app.get(ANDROID + "usesCleartextTraffic")
        if cleartext is not None and cleartext.lower() == "true":
            bad("application 未开启明文流量", "usesCleartextTraffic=true")
        else:
            ok("application 未开启明文流量（usesCleartextTraffic != true）")

        backup = app.get(ANDROID + "allowBackup")
        if backup is not None and backup.lower() == "false":
            ok("allowBackup=false（充电数据不参与云备份/迁移）")
        else:
            bad("allowBackup 必须显式 false", "实际 allowBackup=%s" % backup)

    # 组件导出面：activity / service / receiver / provider 都声明在 <application> 下，
    # 必须用 app.iter(tag) 做子树遍历（manifest.findall(tag) 只找直接子节点，会扫到 0 个）。
    component_tags = ("activity", "activity-alias", "service", "receiver", "provider")
    components = []
    if app is not None:
        for tag in component_tags:
            for el in app.iter(tag):
                components.append((tag, el))

    if components:
        ok("清单组件可解析（application 下共 %d 个组件）" % len(components))
    else:
        bad("清单组件可解析（application 下至少 1 个组件）",
            "app.iter() 未扫描到任何组件，导出面检查将失效")

    missing_exported = []
    exported_true = []
    non_launcher_exported = []
    launchers = []
    for tag, el in components:
        name = el.get(ANDROID + "name") or "(无名)"
        declared = el.get(ANDROID + "exported")
        is_launcher = False
        for f in el.findall("intent-filter"):
            actions = [a.get(ANDROID + "name") or "" for a in f.findall("action")]
            categories = [c.get(ANDROID + "name") or "" for c in f.findall("category")]
            if ("android.intent.action.MAIN" in actions
                    and "android.intent.category.LAUNCHER" in categories):
                is_launcher = True
        if is_launcher:
            launchers.append(name)
        if declared is None:
            missing_exported.append(name)
            continue
        if declared.lower() == "true":
            exported_true.append(name)
            if not is_launcher:
                non_launcher_exported.append(name)

    if missing_exported:
        bad("所有组件显式声明 android:exported（Android 12+ 强制）",
            "未声明: " + ", ".join(missing_exported))
    elif not components:
        bad("所有组件显式声明 android:exported（Android 12+ 强制）", "未扫描到组件，检查无效")
    else:
        ok("所有组件显式声明 android:exported（Android 12+ 强制）")

    if non_launcher_exported:
        bad("非 launcher 组件一律 exported=false", "违规: " + ", ".join(non_launcher_exported))
    elif not components:
        bad("非 launcher 组件一律 exported=false", "未扫描到组件，检查无效")
    else:
        ok("非 launcher 组件一律 exported=false")

    if len(launchers) == 1:
        ok("恰好一个 LAUNCHER 入口（启动页）")
    else:
        bad("恰好一个 LAUNCHER 入口（启动页）", "发现 %d 个: %s"
            % (len(launchers), ", ".join(launchers) or "无"))

    if exported_true and not non_launcher_exported:
        ok("唯一 allowed true 的导出组件就是 launcher")
    elif not exported_true:
        bad("启动页组件存在且可启动", "没有任何 exported=true 的组件")
    else:
        bad("唯一 allowed true 的导出组件就是 launcher",
            "发现额外导出: " + ", ".join(non_launcher_exported))

    # ---------------------------------------------------------------- 源码
    print()
    print("## 源码静态检查（src/）")
    sources = {}
    if os.path.isdir(src_root):
        for dirpath, _dirnames, filenames in os.walk(src_root):
            for fn in filenames:
                if fn.endswith(".java"):
                    full = os.path.join(dirpath, fn)
                    sources[full] = strip_java_comments(read_text(full))
    if not sources:
        bad("找到 Java 源文件", "src/ 下没有 .java")
        return summary()

    ok("找到 Java 源文件（%d 个）" % len(sources))

    check_no_hits(
        sources, root,
        [r"\bRuntime\s*\.\s*getRuntime\s*\(", r"\bRuntime\s*\.\s*exec\s*\(",
         r"\bProcessBuilder\b", r"\bProcess\s*\.\s*start\s*\(",
         r"\bexec\s*\(\s*new\s+String\s*\["],
        "源码无进程执行（Runtime.exec / ProcessBuilder）",
        "未发现 shell 调用")

    check_no_hits(
        sources, root,
        [r"\buid\b\s*[=!<>]+\s*2000\b", r"\b2000\b\s*[=!<>]+\s*\buid\b",
         r"android\.uid\.shell", r"\bUID_SHELL\b", r"\bisShellUid\b",
         r"getCallingUid\s*\(\s*\)\s*[=!<>]+\s*2000"],
        "源码无 uid 2000 / shell uid 信任（不按调用方 uid 放行）",
        "未发现 uid 2000 信任逻辑")

    check_no_hits(
        sources, root,
        [r"\bjava\.net\.", r"\bHttpURLConnection\b", r"\bOkHttpClient\b",
         r"\bokhttp\b", r"\bWebView\b", r"\bnew\s+Socket\b", r"\bServerSocket\b"],
        "源码无网络 API（java.net / Socket / WebView / okhttp）",
        "未发现网络代码")

    check_no_hits(
        sources, root,
        [r"\.computeChargeTimeRemaining\s*\("],
        "核心层不调用系统 computeChargeTimeRemaining()（只用本地估算）",
        "充满时间完全本地估算")

    return summary()


def summary():
    print()
    print("==== 静态扫描结果：通过 %d，失败 %d ====" % (passed, failed))
    return 0 if failed == 0 else 1


if __name__ == "__main__":
    sys.exit(main())
