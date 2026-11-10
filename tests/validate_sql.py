"""Parse-validate every SQL file with sqlglot for its target dialect."""
import sys
from pathlib import Path
import sqlglot

targets = []
for p in sorted(Path("database").rglob("*.sql")):
    dialect = "snowflake" if "snowflake" in str(p) else "mysql"
    targets.append((p, dialect))

failures = 0
for path, dialect in targets:
    sql = path.read_text(encoding="utf-8")
    # sqlglot has no grammar for MySQL LOAD DATA / Snowflake COPY INTO <table>,
    # so those statements are removed before parsing the rest of the file.
    if "LOAD DATA" in sql.upper() or "COPY INTO" in sql.upper():
        kept, skip = [], False
        for stmt in sql.split(";"):
            u = stmt.upper()
            if "LOAD DATA" in u or ("COPY INTO" in u and "SELECT" not in u):
                skip = True
                continue
            kept.append(stmt)
        sql = ";".join(kept)
    try:
        stmts = sqlglot.parse(sql, dialect=dialect)
        n = len([s for s in stmts if s is not None])
        print(f"OK   {path}  [{dialect}]  {n} statements")
    except Exception as e:
        failures += 1
        msg = str(e).split("\n")[0][:300]
        print(f"FAIL {path}  [{dialect}]  {msg}")
print(f"\n{len(targets) - failures}/{len(targets)} files parsed")
sys.exit(1 if failures else 0)
