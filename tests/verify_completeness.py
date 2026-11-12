
#!/usr/bin/env python3
"""Verify every deliverable required by the CAP-15 specification."""
from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

REQUIRED = {
    "Frontend": [
        "frontend/package.json",
        "frontend/vite.config.js",
        "frontend/index.html",
        "frontend/src/main.jsx",
        "frontend/src/App.jsx",
        "frontend/src/services/api.js",
        "frontend/src/services/format.js",
        "frontend/src/services/useApi.js",
        "frontend/src/styles/global.css",
        "frontend/src/styles/theme.css",
        "frontend/src/components/Layout.jsx",
        "frontend/src/components/Sidebar.jsx",
        "frontend/src/components/Navbar.jsx",
        "frontend/src/components/Card.jsx",
        "frontend/src/components/DataTable.jsx",
        "frontend/src/components/KpiCard.jsx",
        "frontend/src/components/Badge.jsx",
        "frontend/src/components/Loading.jsx",
        "frontend/src/components/ErrorState.jsx",
        "frontend/src/components/EmptyState.jsx",
        "frontend/src/components/CalculationSteps.jsx",
        "frontend/src/components/EvidenceList.jsx",
        "frontend/src/auth/AuthContext.jsx",
        "frontend/src/components/ProtectedRoute.jsx",
    ],

    "Frontend pages (8 required)": [
        "frontend/src/pages/Dashboard.jsx",
        "frontend/src/pages/RoutesPage.jsx",
        "frontend/src/pages/Stops.jsx",
        "frontend/src/pages/DelayInvestigation.jsx",
        "frontend/src/pages/ConnectionRisk.jsx",
        "frontend/src/pages/Incidents.jsx",
        "frontend/src/pages/PassengerImpact.jsx",
        "frontend/src/pages/Analytics.jsx",
        "frontend/src/pages/Login.jsx",
        "frontend/src/pages/Register.jsx",
    ],

    "Backend": [
        "backend/pom.xml",
        "backend/src/main/resources/application.yml",
        "backend/src/main/java/com/transitpulse/TransitPulseApplication.java",
        "backend/src/main/java/com/transitpulse/exception/GlobalExceptionHandler.java",
        "backend/src/main/java/com/transitpulse/config/WebConfig.java",
        "backend/src/main/java/com/transitpulse/config/OpenApiConfig.java",

        # JWT authentication
        "backend/src/main/java/com/transitpulse/config/SecurityConfig.java",
        "backend/src/main/java/com/transitpulse/controller/AuthController.java",
        "backend/src/main/java/com/transitpulse/entity/AppUser.java",
        "backend/src/main/java/com/transitpulse/repository/AppUserRepository.java",
        "backend/src/main/java/com/transitpulse/service/AuthService.java",
        "backend/src/main/java/com/transitpulse/security/JwtService.java",
        "backend/src/main/java/com/transitpulse/security/JwtAuthenticationFilter.java",
        "backend/src/main/java/com/transitpulse/security/AppUserDetailsService.java",
    ],

    "Python data engine": [
        "data-engine/requirements.txt",
        "data-engine/config.json",
        "data-engine/generate_datasets.py",
        "data-engine/transitpulse_engine/config.py",
        "data-engine/transitpulse_engine/ingestion.py",
        "data-engine/transitpulse_engine/delay_calculator.py",
        "data-engine/transitpulse_engine/propagation.py",
        "data-engine/transitpulse_engine/connection_risk.py",
        "data-engine/transitpulse_engine/passenger_impact.py",
        "data-engine/transitpulse_engine/sensitivity.py",
        "data-engine/transitpulse_engine/worked_example.py",
        "data-engine/transitpulse_engine/pipeline.py",
    ],

    "Datasets (11 CSVs)": [
        f"datasets/{name}.csv"
        for name in [
            "routes",
            "stops",
            "route_stops",
            "vehicles",
            "trips",
            "scheduled_movements",
            "actual_movements",
            "passenger_volumes",
            "connections",
            "incidents",
            "diversions",
        ]
    ],

    "MySQL": [
        "database/mysql/01_schema.sql",
        "database/mysql/02_seed_data.sql",
        "database/mysql/03_load_engine_results.sql",
        "database/mysql/04_sample_queries.sql",
        "database/generate_seed_sql.py",
    ],

    "AWS": [
        "aws/s3/bucket_structure.md",
        "aws/s3/upload_datasets.py",
        "aws/ec2/deploy.sh",
        "aws/ec2/README.md",
    ],

    "Docker": [
        "docker/Dockerfile.backend",
        "docker/Dockerfile.frontend",
        "docker/Dockerfile.data-engine",
        "docker/nginx.conf",
        "docker-compose.yml",
    ],

    "CI/CD": [
        ".github/workflows/ci.yml",
    ],

    "Tests": [
        # Python tests
        "data-engine/tests/conftest.py",
        "data-engine/tests/test_delay_calculation.py",
        "data-engine/tests/test_missing_gps.py",
        "data-engine/tests/test_propagation.py",
        "data-engine/tests/test_connection_risk.py",
        "data-engine/tests/test_passenger_impact.py",
        "data-engine/tests/test_sensitivity.py",
        "data-engine/tests/test_diversions.py",
        "data-engine/tests/test_ingestion.py",
        "data-engine/tests/test_worked_example.py",
        "data-engine/tests/test_pipeline.py",

        # Backend tests
        "backend/src/test/java/com/transitpulse/ApiIntegrationTest.java",
        "backend/src/test/java/com/transitpulse/ConfigurationServiceTest.java",
        "backend/src/test/java/com/transitpulse/DelayAnalysisServiceTest.java",
        "backend/src/test/java/com/transitpulse/DatasetLoaderServiceTest.java",
        "backend/src/test/java/com/transitpulse/AuthIntegrationTest.java",

        # Frontend tests
        "frontend/src/__tests__/components.test.jsx",
        "frontend/src/__tests__/format.test.js",
        "frontend/src/__tests__/api.test.js",

        # Contract and SQL validation
        "tests/validate_sql.py",
        "tests/verify_api_contract.py",
    ],

    "Documentation": [
        "README.md",
        "documentation/PROJECT_ARCHITECTURE.md",
        "documentation/DATABASE_SCHEMA.md",
        "documentation/API_DOCUMENTATION.md",
        "documentation/DELAY_PROPAGATION_LOGIC.md",
        "documentation/AWS_DEPLOYMENT.md",
        "documentation/SAMPLE_SCENARIOS.md",
    ],

    "Config": [
        ".env.example",
        ".gitignore",
    ],
}


# Directories that must contain at least N files of a kind.
COUNTS = [
    ("backend/src/main/java/com/transitpulse/controller", "*.java", 7),
    ("backend/src/main/java/com/transitpulse/service", "*.java", 8),
    ("backend/src/main/java/com/transitpulse/repository", "*.java", 11),
    ("backend/src/main/java/com/transitpulse/entity", "*.java", 11),
    ("backend/src/main/java/com/transitpulse/dto", "*.java", 7),
    ("backend/src/main/java/com/transitpulse/exception", "*.java", 5),
    ("frontend/src/pages", "*.jsx", 10),
]


def main() -> int:
    missing: list[str] = []
    total = 0
    present = 0

    for group, paths in REQUIRED.items():
        group_missing = [
            path for path in paths
            if not (ROOT / path).is_file()
        ]

        group_present = len(paths) - len(group_missing)
        total += len(paths)
        present += group_present

        status = "OK  " if not group_missing else "FAIL"
        print(
            f"{status} {group}: "
            f"{group_present}/{len(paths)}"
        )

        missing.extend(group_missing)

    print()

    for directory, pattern, expected in COUNTS:
        path = ROOT / directory

        found = (
            len(list(path.glob(pattern)))
            if path.is_dir()
            else 0
        )

        status = "OK  " if found >= expected else "FAIL"

        print(
            f"{status} {directory}: "
            f"{found} (expected >= {expected})"
        )

        if found < expected:
            missing.append(
                f"{directory} has {found}, expected >= {expected}"
            )

    print(f"\n{present}/{total} required files present")

    if missing:
        print("\nMISSING:")
        for item in missing:
            print(f"  {item}")

        return 1

    print("\nAll required deliverables present.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
