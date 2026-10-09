"""Download only selected LiteRT-LM artifacts from Hugging Face."""

import argparse
import hashlib
import json
import re
import sys
from pathlib import Path

from huggingface_hub import HfApi, get_hf_file_metadata, hf_hub_download, hf_hub_url
from huggingface_hub.errors import (
    GatedRepoError,
    HfHubHTTPError,
    RepositoryNotFoundError,
)

ROOT = Path(__file__).resolve().parent
MODELS = ROOT / "models"
MANIFEST = ROOT / "model_manifest.json"
CANDIDATES = {
    "qwen": (
        "litert-community/Qwen2.5-1.5B-Instruct",
        "Qwen2.5-1.5B-Instruct_multi-prefill-seq_q8_ekv4096.litertlm",
    ),
    "gemma": (
        "litert-community/Gemma3-1B-IT",
        "Gemma3-1B-IT_multi-prefill-seq_q4_ekv4096.litertlm",
    ),
}


def sha256(path):
    digest = hashlib.sha256()
    with path.open("rb") as source:
        for chunk in iter(lambda: source.read(8 * 1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def download(name):
    repo, filename = CANDIDATES[name]
    info = HfApi().model_info(repo, files_metadata=True)
    sibling = next((item for item in info.siblings if item.rfilename == filename), None)
    if sibling is None:
        raise RuntimeError(f"Exact artifact missing from {repo}: {filename}")
    remote = get_hf_file_metadata(hf_hub_url(repo, filename, revision=info.sha))
    expected_size = remote.size or sibling.size
    expected_sha = (
        remote.etag if re.fullmatch(r"[a-f0-9]{64}", remote.etag or "") else None
    )
    if expected_sha is None:
        raise RuntimeError(
            "Remote SHA-256 unavailable; refusing an unverified artifact"
        )
    target = MODELS / filename
    valid_existing = (
        target.exists()
        and target.stat().st_size == expected_size
        and sha256(target) == expected_sha
    )
    if valid_existing:
        print(f"{name}: existing file passed SHA-256 and size checks")
    else:
        MODELS.mkdir(exist_ok=True)
        path = Path(
            hf_hub_download(
                repo_id=repo,
                filename=filename,
                revision=info.sha,
                local_dir=MODELS,
                force_download=target.exists(),
            )
        )
        if path != target:
            raise RuntimeError(f"Unexpected download path: {path}")
    actual_size = target.stat().st_size
    actual_sha = sha256(target)
    if expected_size is not None and actual_size != expected_size:
        raise RuntimeError(
            f"Size mismatch for {filename}: {actual_size} != {expected_size}"
        )
    if expected_sha and actual_sha != expected_sha:
        raise RuntimeError(f"SHA-256 mismatch for {filename}")
    manifest = (
        json.loads(MANIFEST.read_text(encoding="utf-8")) if MANIFEST.exists() else {}
    )
    manifest[name] = {
        "repository": repo,
        "filename": filename,
        "revision": info.sha,
        "size_bytes": actual_size,
        "sha256": actual_sha,
        "local_path": str(target.relative_to(ROOT)).replace("\\", "/"),
    }
    MANIFEST.write_text(json.dumps(manifest, indent=2) + "\n", encoding="utf-8")
    print(f"{name}: verified {actual_size} bytes, SHA-256 {actual_sha}")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", choices=[*CANDIDATES, "all"], required=True)
    args = parser.parse_args()
    failed = False
    for name in CANDIDATES if args.model == "all" else [args.model]:
        try:
            download(name)
        except GatedRepoError:
            print(
                f"{name}: accept its Hugging Face license, then authenticate with `hf auth login` or HF_TOKEN"
            )
            failed = True
        except (
            HfHubHTTPError,
            RepositoryNotFoundError,
            OSError,
            RuntimeError,
        ) as error:
            print(f"{name}: download failed: {type(error).__name__}: {error}")
            failed = True
    if failed:
        sys.exit(1)


if __name__ == "__main__":
    main()
