#!/usr/bin/env python3
"""Confere um projeto Hub de IA reconstruido contra os diagramas Mermaid desta pasta.

Uso:   python verify_project.py [caminho-do-projeto]        (padrao: diretorio atual)
Sai com codigo 0 se tudo bate e 1 se ha divergencias.

Le somente os .mmd desta pasta (nada e gerado nem alterado no projeto):
  06-arvore-de-arquivos.mmd     -> todo arquivo da arvore existe (e avisa de arquivos a mais)
  02-dependencias-completo.mmd  -> cada classe recebe no construtor exatamente as dependencias do diagrama
  05-modelo-de-dados.mmd        -> cada colecao e tipo embutido tem os mesmos campos, nomes e tipos
Python 3.8+, sem dependencias externas.
"""
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
PROJECT = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
JAVA_BASE = "src/main/java/com/lamorim/hubdeia/"
IGNORED_TOP = {".git", "target", "specs", "Modelling", "Mermaid", ".idea", ".vscode", ".codex", "js-hub", "scripts", "node_modules", "logs"}
IGNORED_FILES = {"HELP.md", ".env"}

problems = []
warnings = []


def read_mmd(name):
    with open(os.path.join(HERE, name), encoding="utf-8") as handle:
        return handle.read()


def read_project(rel):
    with open(os.path.join(PROJECT, rel), encoding="utf-8", newline="") as handle:
        return handle.read().replace("\r\n", "\n")


# ------------------------------------------------------------------ Java helpers (same rules the diagrams were generated with)
SAFE = str.maketrans({"(": "❨", ")": "❩", "<": "❰", ">": "❱", ",": "❲", "{": "❴", "}": "❵"})


def strip_comments(text):
    out, i, n = [], 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith("//", i):
            j = text.find("\n", i)
            i = n if j == -1 else j
        elif text.startswith("/*", i):
            j = text.find("*/", i + 2)
            i = n if j == -1 else j + 2
        elif c == '"':
            j = i + 1
            while j < n and text[j] != '"':
                j += 2 if text[j] == "\\" else 1
            out.append('"' + text[i + 1:j].translate(SAFE) + '"')
            i = j + 1
        elif c == "'":
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == "\\" else 1
            out.append("'x'")
            i = j + 1
        else:
            out.append(c)
            i += 1
    return "".join(out)


def split_top(text):
    parts, depth, current = [], 0, ""
    for char in text:
        if char in "<(":
            depth += 1
        elif char in ">)":
            depth -= 1
        if char == "," and depth == 0:
            parts.append(current)
            current = ""
        else:
            current += char
    if current.strip():
        parts.append(current)
    return [p.strip() for p in parts if p.strip()]


def balanced_paren(text, start):
    depth = 0
    for index in range(start, len(text)):
        if text[index] == "(":
            depth += 1
        elif text[index] == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("parenteses desbalanceados")


JAVA_TYPE = {"String": "string", "int": "int", "long": "int", "boolean": "bool", "Instant": "date"}


def er_type(java, enums):
    java = java.strip()
    if java in JAVA_TYPE:
        return JAVA_TYPE[java]
    generic = re.match(r"(\w+)<(.+)>$", java)
    if generic:
        outer, inner = generic.group(1), generic.group(2).strip()
        if outer == "Map":
            return "object"
        if outer == "List":
            return "list_" + JAVA_TYPE.get(inner, inner)
    if java in enums:
        return "enum_" + java
    return re.sub(r"\W", "_", java)


# ------------------------------------------------------------------ 1. file tree
tree_text = read_mmd("06-arvore-de-arquivos.mmd")
stack, expected_files = [], []
for line in tree_text.split("\n"):
    line = line.strip()
    folder = re.match(r'subgraph \w+\["(.+?)/"\]', line)
    leaf = re.match(r'f\d+\["(.+?)"\]', line)
    if folder:
        stack.append(folder.group(1))
    elif line == "end":
        stack.pop()
    elif leaf:
        expected_files.append("/".join(stack + [leaf.group(1)]))

missing = [f for f in expected_files if not os.path.isfile(os.path.join(PROJECT, f))]
for f in missing:
    problems.append("ARQUIVO AUSENTE: " + f)

known = set(expected_files)
for dirpath, dirnames, filenames in os.walk(PROJECT):
    rel_dir = os.path.relpath(dirpath, PROJECT).replace("\\", "/")
    if rel_dir == ".":
        dirnames[:] = [d for d in dirnames if d.lower() not in IGNORED_TOP]
    for filename in filenames:
        rel = filename if rel_dir == "." else rel_dir + "/" + filename
        if rel not in known and filename not in IGNORED_FILES and not filename.endswith((".log", ".iml")):
            warnings.append("ARQUIVO A MAIS (fora da arvore): " + rel)

# ------------------------------------------------------------------ 2. constructor injection and implements
dep_text = read_mmd("02-dependencias-completo.mmd")
node_names = {}
for m in re.finditer(r'^\s+(\w+)\["([A-Za-z0-9_]+)(?:<br/>[^"]*)?"\]', dep_text, re.M):
    node_names[m.group(1)] = m.group(2)
expected_ctor, expected_impl = {}, {}
for m in re.finditer(r"^\s+(\w+) --> (\w+)\s*$", dep_text, re.M):
    expected_ctor.setdefault(node_names[m.group(1)], set()).add(node_names[m.group(2)])
for m in re.finditer(r"^\s+(\w+) -\.->\|implements\| (\w+)\s*$", dep_text, re.M):
    expected_impl.setdefault(node_names[m.group(1)], set()).add(node_names[m.group(2)])

java_files = {os.path.basename(f)[:-5]: f for f in expected_files if f.startswith(JAVA_BASE) and f.endswith(".java")}
project_types = set(java_files)
checked_classes = 0
for cls in sorted(set(node_names.values())):
    path = java_files.get(cls)
    if not path or not os.path.isfile(os.path.join(PROJECT, path)):
        continue  # ja reportado como arquivo ausente
    text = strip_comments(read_project(path))
    checked_classes += 1
    actual_ctor, actual_impl = set(), set()
    match = re.search(r"\bpublic\s+" + cls + r"\s*\(", text)
    if match:
        start = match.end() - 1
        for param in split_top(text[start + 1:balanced_paren(text, start)]):
            cleaned = re.sub(r"@\w+(\([^)]*\))?\s*", "", param)
            for token in re.findall(r"[A-Z]\w+", cleaned.rsplit(" ", 1)[0]):
                if token in project_types and token != cls:
                    actual_ctor.add(token)
    for chunk in re.findall(r"\bimplements\s+([\w,\s<>]+?)\s*\{", text):
        for item in chunk.split(","):
            name = re.sub(r"<.*>", "", item.strip())
            if name in project_types:
                actual_impl.add(name)
    want_ctor, want_impl = expected_ctor.get(cls, set()), expected_impl.get(cls, set())
    for dep in sorted(want_ctor - actual_ctor):
        problems.append("DEPENDENCIA AUSENTE: %s deveria receber %s no construtor" % (cls, dep))
    for dep in sorted(actual_ctor - want_ctor):
        problems.append("DEPENDENCIA A MAIS: %s recebe %s no construtor (nao esta no diagrama)" % (cls, dep))
    for impl in sorted(want_impl - actual_impl):
        problems.append("IMPLEMENTS AUSENTE: %s deveria implementar %s" % (cls, impl))
    for impl in sorted(actual_impl - want_impl):
        problems.append("IMPLEMENTS A MAIS: %s implementa %s (nao esta no diagrama)" % (cls, impl))

# ------------------------------------------------------------------ 3. data model (collections and embedded types)
er_text = read_mmd("05-modelo-de-dados.mmd")
entities = {}
for m in re.finditer(r"^\s+(\w+) \{\n(.*?)^\s+\}", er_text, re.M | re.S):
    attrs = []
    for line in m.group(2).strip().split("\n"):
        parts = line.split()
        attrs.append((parts[0], parts[1]))
    entities[m.group(1)] = attrs

domain_files = [f for f in expected_files if f.startswith(JAVA_BASE + "domain/model/")]
sources = {}
for f in domain_files:
    if os.path.isfile(os.path.join(PROJECT, f)):
        sources[os.path.basename(f)[:-5]] = strip_comments(read_project(f))
enums = {n for n, t in sources.items() if re.search(r"\bpublic\s+enum\s+" + n + r"\b", t)}
by_collection = {}
for n, t in sources.items():
    doc = re.search(r'@Document\("([^"]+)"\)', t)
    if doc:
        by_collection[doc.group(1)] = n

for entity, attrs in sorted(entities.items()):
    record = by_collection.get(entity, entity if entity in sources else None)
    if record is None or record not in sources:
        problems.append("MODELO: colecao/tipo '%s' nao encontrado em domain/model" % entity)
        continue
    text = sources[record]
    match = re.search(r"\bpublic\s+record\s+" + record + r"\s*\(", text)
    if not match:
        problems.append("MODELO: %s nao e um record" % record)
        continue
    start = match.end() - 1
    actual = []
    for param in split_top(text[start + 1:balanced_paren(text, start)]):
        field = re.search(r'@Field\("([^"]+)"\)', param)
        cleaned = re.sub(r"@\w+(\([^)]*\))?\s*", "", param).strip()
        java_type, name = cleaned.rsplit(" ", 1)
        column = "_id" if name == "id" else (field.group(1) if field else name)
        actual.append((er_type(java_type, enums), column))
    if actual != attrs:
        want, got = set(attrs), set(actual)
        for t, c in sorted(want - got):
            problems.append("MODELO %s: falta/diverge o campo %s %s" % (entity, t, c))
        for t, c in sorted(got - want):
            problems.append("MODELO %s: campo a mais/diferente %s %s" % (entity, t, c))
        if want == got:
            problems.append("MODELO %s: os campos existem mas a ORDEM dos componentes do record difere" % entity)

# ------------------------------------------------------------------ report
print("Projeto verificado: " + PROJECT)
print("  arquivos esperados: %d | ausentes: %d" % (len(expected_files), len(missing)))
print("  classes com dependencias conferidas: %d" % checked_classes)
print("  entidades do modelo de dados conferidas: %d" % len(entities))
for w in warnings[:25]:
    print("  AVISO  " + w)
if len(warnings) > 25:
    print("  AVISO  ... e mais %d arquivo(s) a mais" % (len(warnings) - 25))
for p in problems:
    print("  FALHA  " + p)
print("RESULTADO: " + ("TUDO BATE com os diagramas." if not problems else "%d divergencia(s)." % len(problems)))
sys.exit(1 if problems else 0)
