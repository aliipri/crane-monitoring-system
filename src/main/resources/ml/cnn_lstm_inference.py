import argparse
import json
import math
import sys
import warnings
from pathlib import Path

import joblib
import numpy as np
import torch
from torch import nn


class CNNLSTMModel(nn.Module):
    def __init__(self, input_size, output_size, cnn_channels=32, lstm_hidden=64):
        super().__init__()
        self.cnn = nn.Sequential(
            nn.Conv1d(input_size, cnn_channels, kernel_size=3, padding=1),
            nn.ReLU(),
            nn.BatchNorm1d(cnn_channels),
        )
        self.lstm = nn.LSTM(
            input_size=cnn_channels,
            hidden_size=lstm_hidden,
            num_layers=1,
            batch_first=True,
        )
        self.fc = nn.Sequential(
            nn.Linear(lstm_hidden, 32),
            nn.ReLU(),
            nn.Linear(32, output_size),
        )

    def forward(self, values):
        values = values.permute(0, 2, 1)
        values = self.cnn(values)
        values = values.permute(0, 2, 1)
        lstm_output, _ = self.lstm(values)
        return self.fc(lstm_output[:, -1, :])


def calculate_load_distribution(features):
    return (
        0.25 * features["load_lt_40"]
        + 0.60 * features["load_40_80"]
        + 0.90 * features["load_80_100"]
        + 1.10 * features["load_gt_100"]
    )


def postprocess(values, target_columns):
    features = {
        column: float(value)
        for column, value in zip(target_columns, values)
    }
    load_columns = [
        "load_lt_40",
        "load_40_80",
        "load_80_100",
        "load_gt_100",
    ]
    for column in load_columns:
        features[column] = max(0.0, min(1.0, features[column]))

    load_sum = sum(features[column] for column in load_columns)
    if load_sum > 0:
        for column in load_columns:
            features[column] /= load_sum
    else:
        features["load_lt_40"] = 1.0
        features["load_40_80"] = 0.0
        features["load_80_100"] = 0.0
        features["load_gt_100"] = 0.0

    for column in [
        "cycles_total",
        "overload_cycles",
        "hoist_hours",
        "boom_angle_hours",
        "boom_length_hours",
        "rotation_hours",
        "broken_wires_count",
        "diameter_loss_percent",
    ]:
        features[column] = max(0.0, features[column])

    for column in [
        "corrosion_score",
        "abrasion_score",
        "deformation_score",
        "lubrication_score",
    ]:
        features[column] = max(0.0, min(5.0, features[column]))

    features["load_distribution_coef"] = calculate_load_distribution(features)
    return features


def calculate_damage(previous_damage, features, n_ref, exponent):
    load_ranges = [
        ("load_lt_40", 0.30),
        ("load_40_80", 0.60),
        ("load_80_100", 0.90),
        ("load_gt_100", 1.10),
    ]
    cycles_total = max(0.0, features["cycles_total"])
    delta_damage = 0.0
    for column, load_coefficient in load_ranges:
        share = max(0.0, min(1.0, features[column]))
        allowable_cycles = n_ref / (load_coefficient ** exponent)
        delta_damage += cycles_total * share / allowable_cycles

    visual_index = (
        0.25 * min(features["broken_wires_count"] / 20.0, 1.0)
        + 0.25 * min(features["diameter_loss_percent"] / 10.0, 1.0)
        + 0.15 * min(features["corrosion_score"] / 5.0, 1.0)
        + 0.15 * min(features["abrasion_score"] / 5.0, 1.0)
        + 0.15 * min(features["deformation_score"] / 5.0, 1.0)
        + 0.05 * min(features["lubrication_score"] / 5.0, 1.0)
    )
    new_damage = max(
        0.0,
        previous_damage + delta_damage * (1.0 + 0.35 * visual_index),
    )
    wear = 100.0 * min(new_damage, 1.0)
    return new_damage, wear, 100.0 - wear


def predict(model_dir, request):
    config = json.loads(
        (model_dir / "model_config.json").read_text(encoding="utf-8")
    )
    feature_columns = config["feature_columns"]
    target_columns = config["target_columns"]
    window_size = int(config["window_size"])
    history = request["history"]
    if len(history) != window_size:
        raise ValueError(
            f"Expected {window_size} history rows, received {len(history)}"
        )

    matrix = np.asarray(
        [
            [float(row[column]) for column in feature_columns]
            for row in history
        ],
        dtype=np.float32,
    )
    if not np.isfinite(matrix).all():
        raise ValueError("Input history contains non-finite values")

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        scaler_x = joblib.load(model_dir / config["scaler_x"])
        scaler_y = joblib.load(model_dir / config["scaler_y"])

    model = CNNLSTMModel(len(feature_columns), len(target_columns))
    state = torch.load(
        model_dir / config["model_file"],
        map_location="cpu",
        weights_only=True,
    )
    model.load_state_dict(state)
    model.eval()

    with warnings.catch_warnings():
        warnings.simplefilter("ignore")
        scaled = scaler_x.transform(matrix).astype(np.float32)
    tensor = torch.from_numpy(scaled).unsqueeze(0)
    with torch.no_grad():
        prediction_scaled = model(tensor).numpy()
    prediction_raw = scaler_y.inverse_transform(prediction_scaled)[0]
    features = postprocess(prediction_raw, target_columns)

    damage, wear, resource = calculate_damage(
        float(request["previous_damage"]),
        features,
        float(config["miner_n_ref"]),
        float(config["miner_m"]),
    )
    result = {
        "predicted_features": features,
        "predicted_damage_index": damage,
        "predicted_wear_percent": wear,
        "predicted_remaining_resource": resource,
    }
    if not all(
        math.isfinite(float(value))
        for value in [damage, wear, resource, *features.values()]
    ):
        raise ValueError("Model returned non-finite values")
    return result


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--model-dir", required=True)
    arguments = parser.parse_args()
    request = json.load(sys.stdin)
    result = predict(Path(arguments.model_dir), request)
    json.dump(result, sys.stdout, ensure_ascii=False)


if __name__ == "__main__":
    try:
        main()
    except Exception as exception:
        print(f"{type(exception).__name__}: {exception}", file=sys.stderr)
        sys.exit(1)
