
"""Parse-validate every MySQL SQL file with sqlglot."""

import sys
from pathlib import Path
import sqlglot


DATABASE_DIR = Path("database")

targets = sorted(DATABASE_DIR.rglob("*.sql"))

if not targets:
    print("ERROR: No SQL files found in database/")
    sys.exit(1)

failures = 0

for path in targets:
    dialect = "mysql"
    sql = path.read_text(encoding="utf-8")

    # sqlglot does not fully support MySQL LOAD DATA syntax,
    # so those statements are removed before parsing the remaining SQL.
    if "LOAD DATA" in sql.upper():
        kept = []

        for stmt in sql.split(";"):
            if "LOAD DATA" in stmt.upper():
                continue

            kept.append(stmt)

        sql = ";".join(kept)

    try:
        statements = sqlglot.parse(sql, dialect=dialect)
        statement_count = len(
            [statement for statement in statements if statement is not None]
        )

        print(
            f"OK   {path}  "
            f"[{dialect}]  {statement_count} statements"
        )

    except Exception as error:
        failures += 1
        message = str(error).split("\n")[0][:300]

        print(f"FAIL {path}  [{dialect}]  {message}")

print(f"\n{len(targets) - failures}/{len(targets)} files parsed successfully")

if failures:
    print(f"\nValidation failed: {failures} file(s) contain SQL parsing errors.")
    sys.exit(1)

print("\nAll MySQL SQL files parsed successfully.")
sys.exit(0)
