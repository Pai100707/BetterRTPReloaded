#!/usr/bin/env python3
"""
One-off converter used to move the bundled language files from the 3.6.x layout (one string per message)
to the 3.7.1 delivery-type layout. Line based, so comments and quoting of the original files are preserved.
usage: convert_lang.py <lang-dir>
"""
import re, sys, glob, os

PATHS = ["Success.Paid", "Success.Bypass", "Success.Loading", "Success.Teleport", "Failed.NotSafe", "Failed.Price",
         "Failed.Hunger", "Other.NotSafe", "Other.Success", "Other.Biome", "NotExist", "Reload", "Update",
         "NoPermission.Basic", "NoPermission.World", "DisabledWorld", "Cooldown", "Locked", "Invalid", "NotOnline", "Delay",
         "Sign", "Moved", "Already", "Edit.Error", "Edit.Set", "Edit.Remove"]

# English-only short texts for the actionbar/title defaults of the success messages (design example of the spec)
EN_EXTRA = {
    "Success.Paid": ("'&aTeleported to x=%x% y=%y% z=%z%'", "'&aTeleport successful'", "'&7Enjoy your destination!'"),
    "Success.Bypass": ("'&aTeleported to x=%x% y=%y% z=%z%'", "'&aTeleport successful'", "'&7Enjoy your destination!'"),
}

HEADER_EN = """# Help translate this file into more languages!
#
# Every message below can be delivered in several ways at the same time. Each delivery type has its own
# `enable` switch and its own `text`; disabled types are simply not sent.
#   chat:      normal chat message, the Messages.Prefix is put in front of it
#   actionbar: text above the hotbar (no prefix)
#   title:     big text in the middle of the screen. `subtitle` is the smaller line below it and can be
#              enabled independently. Optional: fade-in / stay / fade-out (in ticks) next to `text`.
#              Without them the normal server title timings are used.
# Placeholders (%x%, %y%, %z%, %price%, %player%, %world%, %time%...) and & color codes work in every type.
# Actionbar and title can only be shown to players. When the receiver is the console, the message is sent as
# plain chat instead. If every delivery type of a message is disabled, the message is not sent at all.
# Messages written the old way (`Paid: 'text'`) still work and are converted automatically on startup
# (a backup of your file is kept next to it as <file>.pre-3.7.1.bak).
"""
HEADER_OTHER = """# Every message can be delivered as chat, actionbar and/or title. See en.yml for a description of the options.
"""

def quote_closed(v):
    """True when the quoted scalar in v ends on the same line."""
    q = v[0]
    i = 1
    while i < len(v):
        if q == "'" and v[i] == "'":
            if i + 1 < len(v) and v[i + 1] == "'":
                i += 2
                continue
            return True
        if q == '"':
            if v[i] == "\\":
                i += 2
                continue
            if v[i] == '"':
                return True
        i += 1
    return False

def split_scalar(value):
    """returns (raw_scalar, trailing_comment) for a YAML scalar that may be quoted."""
    v = value.strip()
    if v.startswith("'"):
        i = 1
        while i < len(v):
            if v[i] == "'":
                if i + 1 < len(v) and v[i + 1] == "'":
                    i += 2
                    continue
                break
            i += 1
        return v[:i + 1], v[i + 1:].strip()
    if v.startswith('"'):
        i = 1
        while i < len(v):
            if v[i] == "\\":
                i += 2
                continue
            if v[i] == '"':
                break
            i += 1
        return v[:i + 1], v[i + 1:].strip()
    m = re.match(r"^(.*?)(\s+#.*)?$", v)
    return m.group(1).strip(), (m.group(2) or "").strip()

def convert(text, is_en):
    out, stack = [], []   # stack of (indent, key)
    in_messages = False
    src = text.split("\n")
    idx = 0
    while idx < len(src):
        line = src[idx]
        idx += 1
        stripped = line.strip()
        if not stripped or stripped.startswith("#"):
            out.append(line)
            continue
        indent = len(line) - len(line.lstrip(" "))
        m = re.match(r"^(\s*)([^:#\s][^:]*?):(?:\s+(.*))?$", line) or re.match(r"^(\s*)([^:#\s][^:]*?):$", line)
        if not m:
            out.append(line)
            continue
        key, value = m.group(2), (m.group(3) or "")
        while stack and stack[-1][0] >= indent:
            stack.pop()
        if indent == 0:
            in_messages = key == "Messages"
        path = ".".join([k for _, k in stack] + [key])
        has_scalar = value and not value.startswith("#")
        if in_messages and has_scalar and path.startswith("Messages.") and path[len("Messages."):] in PATHS:
            rel = path[len("Messages."):]
            cont = []   # continuation lines of a multi-line quoted scalar
            if value[0] in "'\"" and not quote_closed(value):
                joined = value
                while idx < len(src):
                    nxt = src[idx]
                    idx += 1
                    cont.append(nxt.strip())
                    joined += "\n" + nxt.strip()
                    if quote_closed(joined):
                        break
                else:
                    raise SystemExit("unterminated quoted scalar at " + path)
                value = joined
            raw, comment = split_scalar(value.split("\n")[0] if not cont else value)
            pad = " " * indent
            if cont:
                # keep the scalar folded over several lines, re-indented below its new `text:` key
                first, *rest = raw.split("\n")
                def fold(prefix_pad):
                    return first + "".join("\n" + prefix_pad + r for r in rest)
                raw_multi = fold
            else:
                raw_multi = None
            ab, ti, st = (EN_EXTRA[rel] if is_en and rel in EN_EXTRA else (raw, raw, "''"))
            if raw_multi:
                raw = raw_multi(pad + "      ")
                ab, ti = raw, raw
            out.append(f"{pad}{key}:" + (f" {comment}" if comment else ""))
            out += [f"{pad}  chat:", f"{pad}    enable: true", f"{pad}    text: {raw}",
                    f"{pad}  actionbar:", f"{pad}    enable: false", f"{pad}    text: {ab}",
                    f"{pad}  title:", f"{pad}    enable: false", f"{pad}    text: {ti}",
                    f"{pad}    subtitle:", f"{pad}      enable: false", f"{pad}      text: {st}"]
            continue
        if not has_scalar:
            stack.append((indent, key))
        out.append(line)
    return "\n".join(out)

def main(d):
    for f in sorted(glob.glob(os.path.join(d, "*.yml"))):
        name = os.path.basename(f)
        text = open(f, encoding="utf-8").read()
        is_en = name == "en.yml"
        converted = convert(text, is_en)
        lines = converted.split("\n")
        # replace/insert the header comment
        if is_en and lines[0].startswith("# Help translate this file"):
            lines = HEADER_EN.rstrip("\n").split("\n") + lines[1:]
        else:
            lines = HEADER_OTHER.rstrip("\n").split("\n") + lines
        open(f, "w", encoding="utf-8", newline="\n").write("\n".join(lines))
        print("converted", name)

if __name__ == "__main__":
    main(sys.argv[1])
