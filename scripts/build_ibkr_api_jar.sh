#!/usr/bin/env bash
set -euo pipefail

api_dir="${1:-${HOME}/IBJts}"
repo_dir="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
output_dir="${repo_dir}/target/lib"
classes_dir="${repo_dir}/target/ibkr-api-classes"
jar_path="${output_dir}/ibkr-tws-api-local.jar"
protobuf_path="${output_dir}/protobuf-java.jar"

if [[ ! -d "${api_dir}" ]]; then
  echo "IBKR API directory not found: ${api_dir}" >&2
  echo "Download the official TWS API for Mac/Unix and unzip it first." >&2
  exit 1
fi

client_file="$(find "${api_dir}" -path '*/com/ib/client/EClientSocket.java' -print -quit)"
tws_api_jar="$(find "${api_dir}" -path '*/source/JavaClient/TwsApi.jar' -print -quit)"
protobuf_jar="$(find "${api_dir}" -path '*/source/JavaClient/jars/protobuf-java-*.jar' -print -quit)"

mkdir -p "${output_dir}"

if [[ -n "${tws_api_jar}" ]]; then
  cp "${tws_api_jar}" "${jar_path}"
  if [[ -n "${protobuf_jar}" ]]; then
    cp "${protobuf_jar}" "${protobuf_path}"
  fi
  echo "${output_dir}"
  exit 0
fi

if [[ -z "${client_file}" ]]; then
  echo "Could not find com/ib/client/EClientSocket.java under: ${api_dir}" >&2
  echo "Pass the extracted official API directory as the first argument." >&2
  exit 1
fi

source_root="${client_file%/com/ib/client/EClientSocket.java}"
rm -rf "${classes_dir}"
mkdir -p "${classes_dir}" "${output_dir}"

sources_file="${classes_dir}/sources.txt"
find "${source_root}/com/ib/client" "${source_root}/com/ib/controller" -name '*.java' -print \
  > "${sources_file}"

javac -d "${classes_dir}" @"${sources_file}"

jar --create --file "${jar_path}" -C "${classes_dir}" com

echo "${jar_path}"
