# Model artifacts

The CNN-LSTM inference module expects the following generated training artifacts in this directory:

- `model_config.json`
- `cnn_lstm_model.pt`
- `scaler_x.pkl`
- `scaler_y.pkl`

They are intentionally not committed to the repository. The training notebook requires the prepared `crane_training_dataset_1080.csv` dataset, which was not present in the supplied project archive. When that dataset is available, run `ml/cnn_lstm_training.ipynb` to train the model and produce the artifacts, then set `CRANE_NEURAL_MODEL_DIR` to this directory and enable inference with `CRANE_NEURAL_ENABLED=true`.
