//! Параметры приложения которые передаются при запуске

/// Указывает url для api midi
pub const PARAM_MIDI_HTTP_URL: &str = "MIDIED_MIDI_READ_HTTP";

pub const PARAM_FS_HOME_TYPE: &str = "MIDIED_FS_HOME_TYPE";
pub const PARAM_FS_HOME_TYPE_NATIVE: &str = "native";
pub const PARAM_FS_HOME_TYPE_HTTP: &str = "http";
pub const PARAM_FS_HOME_TYPE_RAM: &str = "ram";

pub const PARAM_FS_HOME_MOUNT_STRING: &str = "MIDIED_FS_HOME_MOUNT";

pub const ENV_HOME: &str = "MIDIED_HOME";

/// Какие значение параметров должны быть скопированы из env
pub const COPY_PARAMS_FROM_ENV: &[&str] = &[
  PARAM_MIDI_HTTP_URL,
  PARAM_FS_HOME_MOUNT_STRING,
  PARAM_FS_HOME_TYPE,
];
