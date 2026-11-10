"""
Central configuration for the TransitPulse data engine.

Every analytical threshold used anywhere in the engine is defined here so
that it can be overridden from a JSON file, from environment variables, or
programmatically from the Spring Boot `/api/config` endpoint.
"""
from __future__ import annotations

import json
import os
from dataclasses import dataclass, asdict, field
from pathlib import Path
from typing import Any, Dict


@dataclass
class EngineConfig:
    """Analytical parameters for delay, propagation and impact analysis."""

    # --- Delay classification thresholds (minutes, inclusive lower bound) ---
    on_time_threshold_min: float = 2.0
    minor_delay_threshold_min: float = 5.0
    moderate_delay_threshold_min: float = 15.0
    # anything >= moderate_delay_threshold_min is "SEVERE"

    # Vehicles arriving more than this many minutes early are flagged
    early_arrival_threshold_min: float = -2.0

    # --- Propagation engine ---
    propagation_window_min: int = 90
    min_propagated_delay_min: float = 2.0
    vehicle_turnaround_min: float = 5.0
    recovery_min_reduction_min: float = 1.0

    # --- Connection risk engine ---
    required_transfer_buffer_min: float = 5.0
    max_transfer_wait_min: float = 30.0
    missed_connection_margin_min: float = 0.0

    # --- Passenger impact estimation ---
    low_volume_stop_threshold: int = 20
    transfer_rate: float = 0.18
    additional_wait_on_missed_connection_min: float = 15.0

    # --- Missing GPS handling: "EXCLUDE" | "ESTIMATE" ---
    missing_gps_strategy: str = "EXCLUDE"

    # --- Sensitivity analysis ---
    sensitivity_buffers_min: tuple = (3.0, 5.0, 8.0, 10.0)

    # --- Paths ---
    datasets_dir: str = field(default_factory=lambda: os.getenv(
        "TRANSITPULSE_DATASETS_DIR", str(Path(__file__).resolve().parents[2] / "datasets")))
    output_dir: str = field(default_factory=lambda: os.getenv(
        "TRANSITPULSE_OUTPUT_DIR", str(Path(__file__).resolve().parents[1] / "output")))

    # ------------------------------------------------------------------ #

    def __post_init__(self) -> None:
        self.validate()

    def validate(self) -> None:
        """Reject configurations that would produce meaningless analytics."""
        if not (self.on_time_threshold_min
                < self.minor_delay_threshold_min
                < self.moderate_delay_threshold_min):
            raise ValueError(
                "Delay thresholds must be strictly increasing: "
                f"on_time={self.on_time_threshold_min}, "
                f"minor={self.minor_delay_threshold_min}, "
                f"moderate={self.moderate_delay_threshold_min}")
        if self.propagation_window_min <= 0:
            raise ValueError("propagation_window_min must be positive")
        if self.required_transfer_buffer_min < 0:
            raise ValueError("required_transfer_buffer_min cannot be negative")
        if not 0.0 <= self.transfer_rate <= 1.0:
            raise ValueError("transfer_rate must be a fraction between 0 and 1")
        if self.missing_gps_strategy not in ("EXCLUDE", "ESTIMATE"):
            raise ValueError("missing_gps_strategy must be EXCLUDE or ESTIMATE")

    def classify_delay(self, delay_min: float | None) -> str:
        """Map a delay in minutes onto a delay class."""
        if delay_min is None:
            return "MISSING"
        if delay_min <= self.early_arrival_threshold_min:
            return "EARLY"
        if delay_min < self.on_time_threshold_min:
            return "ON_TIME"
        if delay_min < self.minor_delay_threshold_min:
            return "MINOR"
        if delay_min < self.moderate_delay_threshold_min:
            return "MODERATE"
        return "SEVERE"

    def to_dict(self) -> Dict[str, Any]:
        data = asdict(self)
        data["sensitivity_buffers_min"] = list(self.sensitivity_buffers_min)
        return data

    @classmethod
    def from_dict(cls, data: Dict[str, Any]) -> "EngineConfig":
        allowed = {f for f in cls.__dataclass_fields__}  # noqa: F821
        clean = {k: v for k, v in data.items() if k in allowed}
        if "sensitivity_buffers_min" in clean:
            clean["sensitivity_buffers_min"] = tuple(clean["sensitivity_buffers_min"])
        return cls(**clean)

    @classmethod
    def load(cls, path: str | Path | None = None) -> "EngineConfig":
        """Load config from JSON, falling back to defaults when absent."""
        path = Path(path) if path else Path(
            os.getenv("TRANSITPULSE_CONFIG",
                      Path(__file__).resolve().parents[1] / "config.json"))
        if path.exists():
            with path.open("r", encoding="utf-8") as fh:
                return cls.from_dict(json.load(fh))
        return cls()

    def save(self, path: str | Path) -> None:
        path = Path(path)
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8") as fh:
            json.dump(self.to_dict(), fh, indent=2)
