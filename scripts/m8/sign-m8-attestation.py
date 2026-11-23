#!/usr/bin/env python3
"""Create one role-scoped M8 Ed25519 attestation using an encrypted external private key."""
from __future__ import annotations

import argparse
import base64
import datetime as dt
import os
import re
import subprocess
import sys
import tempfile
from pathlib import Path

ROLE_POLICY = {
    "endpoint": ("m8-endpoint-v1", {"M8_ENDPOINT_HOLDOUT", "M8_ENDPOINT_TRANSFER"}),
    "safety": ("m8-safety-v1", {"M8_SAFETY_TRAIN"}),
    "ci": ("m8-ci-v1", {"M8_CI_FREEZE"}),
    "b1": ("m8-b1-v1", {"M8_B1_DOWNSTREAM_OUTCOME"}),
    "b2": ("m8-b2-v1", {"M8_B2_APPROVED_PROCEDURE"}),
    "b3": ("m8-b3-v1", {"M8_B3_COALITION_OUTCOME"}),
    "b6": ("m8-b6-v1", {"M8_B6_SIMULATOR_LABEL"}),
}


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--role", choices=sorted(ROLE_POLICY), required=True)
    parser.add_argument("--evidence-type", required=True)
    parser.add_argument("--subject-fingerprint", required=True)
    parser.add_argument("--private-key", type=Path, required=True,
                        help="encrypted Ed25519 PKCS#8 PEM outside the repository")
    parser.add_argument("--attestation-directory", type=Path, required=True)
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parents[2])
    args = parser.parse_args()

    issuer, allowed_types = ROLE_POLICY[args.role]
    if args.evidence_type not in allowed_types:
        raise RuntimeError(f"role {args.role} ({issuer}) cannot sign evidence type {args.evidence_type}")
    if not re.fullmatch(r"[0-9a-f]{64}", args.subject_fingerprint):
        raise RuntimeError("subject fingerprint must be lowercase SHA-256")
    root = args.root.resolve()
    raw_private_key = args.private_key.expanduser().absolute()
    raw_attestation_dir = args.attestation_directory.expanduser().absolute()
    if raw_private_key.is_symlink() or raw_attestation_dir.is_symlink():
        raise RuntimeError("private key and attestation directory cannot be symlinks")
    private_key = raw_private_key.resolve()
    attestation_dir = raw_attestation_dir.resolve()
    if private_key.is_relative_to(root) or attestation_dir.is_relative_to(root):
        raise RuntimeError("private keys and signed attestation storage must be outside the Git checkout")
    if private_key.is_symlink() or not private_key.is_file():
        raise RuntimeError("private key must be a regular, non-symlink file")
    if private_key.stat().st_mode & 0o077:
        raise RuntimeError("private key permissions must be 0600 or stricter")
    with private_key.open("rb") as private_stream:
        pem_header = private_stream.read(128)
    if b"BEGIN ENCRYPTED PRIVATE KEY" not in pem_header:
        raise RuntimeError("private key must be an encrypted PKCS#8 PEM; unencrypted keys are rejected")
    if attestation_dir.is_symlink() or not attestation_dir.is_dir():
        raise RuntimeError("attestation directory must be an existing real directory")

    issued_at = dt.datetime.now(dt.timezone.utc).replace(microsecond=0).isoformat().replace("+00:00", "Z")
    canonical = ("M8-ED25519-ATTESTATION-V1\n" + args.evidence_type + "\n"
                 + args.subject_fingerprint + "\n" + issuer + "\n" + issued_at + "\n").encode("utf-8")
    destination = attestation_dir / f"{args.evidence_type}--{args.subject_fingerprint}.attestation"
    if destination.exists():
        raise RuntimeError(f"refusing to overwrite immutable attestation: {destination}")

    # OpenSSL handles passphrase input interactively. The passphrase is never accepted as an argument.
    with tempfile.TemporaryDirectory(prefix="m8-ed25519-sign-") as temp_name:
        temp = Path(temp_name)
        os.chmod(temp, 0o700)
        payload = temp / "payload.bin"
        signature_file = temp / "signature.bin"
        payload.write_bytes(canonical)
        os.chmod(payload, 0o600)
        subprocess.run(["openssl", "pkeyutl", "-sign", "-rawin", "-inkey", str(private_key),
                        "-in", str(payload), "-out", str(signature_file)], check=True)
        signature = signature_file.read_bytes()
    if len(signature) != 64:
        raise RuntimeError(f"OpenSSL returned a non-Ed25519 signature length: {len(signature)}")

    b64 = lambda value: base64.b64encode(value.encode("utf-8")).decode("ascii")
    record = ("M8-ATTESTATION-FILE-V1\n" + b64(args.evidence_type) + "\n"
              + args.subject_fingerprint + "\n" + b64(issuer) + "\n"
              + issued_at + "\n" + base64.b64encode(signature).decode("ascii") + "\n").encode("ascii")
    descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    try:
        with os.fdopen(descriptor, "wb") as stream:
            stream.write(record)
            stream.flush()
            os.fsync(stream.fileno())
    except Exception:
        destination.unlink(missing_ok=True)
        raise
    print(f"Created {destination}")
    print(f"issuer={issuer}")
    print(f"evidenceType={args.evidence_type}")
    print(f"subjectFingerprint={args.subject_fingerprint}")
    print("privateKeyMaterialWritten=false")
    return 0


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except Exception as error:
        print(f"M8 attestation signing: FAILED: {error}", file=sys.stderr)
        raise SystemExit(1)
